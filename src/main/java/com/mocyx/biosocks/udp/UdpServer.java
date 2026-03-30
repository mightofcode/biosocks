package com.mocyx.biosocks.udp;

import com.mocyx.biosocks.protocol.TunnelUdpProtocol;
import com.mocyx.biosocks.protocol.TunnelUdpProtocol.TunnelUdpRequest;
import com.mocyx.biosocks.protocol.TunnelUdpProtocol.TunnelUdpResponse;
import com.mocyx.biosocks.util.ConfigDto;
import com.mocyx.biosocks.util.Global;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Administrator
 */
@Slf4j
public class UdpServer implements Runnable {

    private static final long IDLE_TIMEOUT_MS = 120_000L;

    private final ConfigDto configDto;
    private UdpTunnel udpTunnel;

    public UdpServer(ConfigDto configDto) {
        this.configDto = configDto;
    }

    @Data
    private static class Pipe {
        private InetSocketAddress source;
        private InetSocketAddress client;
        private InetSocketAddress remote;
        private DatagramChannel channel;
        private SelectionKey selectionKey;
        private String key;
        private long lastActiveTime = System.currentTimeMillis();
    }

    @Data
    private static class UdpTunnel {
        private Selector selector;
        private DatagramChannel upChannel;
        private ConcurrentHashMap<String, Pipe> pipes = new ConcurrentHashMap<>();
        private BlockingQueue<Pipe> pipeQueue = new ArrayBlockingQueue<>(1024);
    }

    private String pipeKey(InetSocketAddress source, InetSocketAddress client, InetSocketAddress remote) {
        return source.getHostString() + ':' + source.getPort()
                + ' ' + client.getHostString() + ':' + client.getPort()
                + ' ' + remote.getHostString() + ':' + remote.getPort();
    }

    private static void sendUdpResponse(UdpTunnel tunnel, Pipe pipe, ByteBuffer sendBuffer, byte[] data) {
        TunnelUdpResponse response = new TunnelUdpResponse();
        response.setData(data);
        response.setRemote(pipe.getRemote());
        response.setSource(pipe.getSource());
        response.setType(TunnelUdpProtocol.TunnelUdpMsgType.RES_RECV.getV());

        sendBuffer.clear();
        response.write(sendBuffer);
        sendBuffer.flip();

        try {
            tunnel.getUpChannel().send(sendBuffer, pipe.getClient());
        } catch (IOException e) {
            log.error("udp write error", e);
        }
    }

    private void closePipe(UdpTunnel tunnel, Pipe pipe) {
        if (pipe == null) {
            return;
        }
        tunnel.getPipes().remove(pipe.getKey(), pipe);
        try {
            if (pipe.getSelectionKey() != null) {
                pipe.getSelectionKey().cancel();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        try {
            if (pipe.getChannel() != null) {
                pipe.getChannel().close();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        log.info("close udp pipe {}", pipe.getKey());
    }

    private void cleanupIdlePipes(UdpTunnel tunnel) {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Pipe>> it = tunnel.getPipes().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Pipe> entry = it.next();
            Pipe pipe = entry.getValue();
            if (now - pipe.getLastActiveTime() > IDLE_TIMEOUT_MS) {
                it.remove();
                closePipe(tunnel, pipe);
            }
        }
        tunnel.getSelector().wakeup();
    }

    private static class UdpDownStreamWorker implements Runnable {
        private final UdpTunnel udpTunnel;

        public UdpDownStreamWorker(UdpTunnel udpTunnel) {
            this.udpTunnel = udpTunnel;
        }

        @Override
        public void run() {
            ByteBuffer receiveBuffer = ByteBuffer.allocate(Global.largeBufferSize);
            ByteBuffer sendBuffer = ByteBuffer.allocate(Global.largeBufferSize);
            try {
                while (true) {
                    Selector selector = udpTunnel.getSelector();

                    while (true) {
                        Pipe pipe = udpTunnel.getPipeQueue().poll();
                        if (pipe == null) {
                            break;
                        }
                        SelectionKey key = pipe.getChannel().register(selector, SelectionKey.OP_READ, pipe);
                        pipe.setSelectionKey(key);
                    }

                    int readyChannels = selector.select();
                    if (readyChannels == 0) {
                        continue;
                    }
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        if (!key.isValid() || !key.isReadable()) {
                            continue;
                        }

                        DatagramChannel inputChannel = (DatagramChannel) key.channel();
                        receiveBuffer.clear();
                        InetSocketAddress address = (InetSocketAddress) inputChannel.receive(receiveBuffer);
                        if (address == null) {
                            continue;
                        }
                        receiveBuffer.flip();

                        Pipe pipe = (Pipe) key.attachment();
                        if (pipe == null) {
                            continue;
                        }
                        pipe.setLastActiveTime(System.currentTimeMillis());

                        byte[] data = new byte[receiveBuffer.remaining()];
                        receiveBuffer.get(data);
                        sendUdpResponse(udpTunnel, pipe, sendBuffer, data);
                    }
                }
            } catch (Exception e) {
                log.error("fail UdpDownStreamWorker", e);
            }
            log.info("UdpDownStreamWorker quit");
        }
    }

    @Override
    public void run() {
        try {
            DatagramChannel datagramChannel = DatagramChannel.open();
            datagramChannel.bind(new InetSocketAddress(configDto.getServer(), configDto.getServerPort()));
            datagramChannel.configureBlocking(true);

            udpTunnel = new UdpTunnel();
            udpTunnel.setSelector(Selector.open());
            udpTunnel.setUpChannel(datagramChannel);

            Thread t = new Thread(new UdpDownStreamWorker(udpTunnel));
            t.start();

            log.info("udp listen on {}", datagramChannel.getLocalAddress());

            ByteBuffer receiveBuffer = ByteBuffer.allocate(Global.largeBufferSize);
            long tick = 0;

            while (true) {
                receiveBuffer.clear();
                InetSocketAddress remoteAddress = (InetSocketAddress) datagramChannel.receive(receiveBuffer);
                receiveBuffer.flip();

                TunnelUdpRequest request = TunnelUdpRequest.tryRead(receiveBuffer);
                if (request == null) {
                    throw new RuntimeException("decode error");
                }

                String key = pipeKey(request.getSource(), remoteAddress, request.getRemote());
                Pipe pipe = udpTunnel.getPipes().get(key);
                if (pipe == null) {
                    Pipe newPipe = new Pipe();
                    newPipe.setSource(request.getSource());
                    newPipe.setClient(remoteAddress);
                    newPipe.setRemote(request.getRemote());
                    newPipe.setChannel(DatagramChannel.open());
                    newPipe.getChannel().bind(null);
                    newPipe.getChannel().connect(request.getRemote());
                    newPipe.getChannel().configureBlocking(false);
                    newPipe.setKey(key);
                    udpTunnel.getPipes().put(key, newPipe);
                    udpTunnel.getPipeQueue().add(newPipe);
                    udpTunnel.getSelector().wakeup();
                    pipe = newPipe;

                    log.info("create udp pipe {}", pipe.getKey());
                }

                pipe.setLastActiveTime(System.currentTimeMillis());
                try {
                    pipe.getChannel().write(ByteBuffer.wrap(request.getData()));
                } catch (IOException e) {
                    log.error("fail write udp to remote {}", pipe.getKey(), e);
                }

                tick += 1;
                if (tick % 256 == 0) {
                    cleanupIdlePipes(udpTunnel);
                }
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.exit(0);
        }
    }
}
