package com.mocyx.biosocks.udp;

import com.mocyx.biosocks.util.ConfigDto;
import com.mocyx.biosocks.Global;
import com.mocyx.biosocks.protocol.TunnelUdpProtocol;
import com.mocyx.biosocks.protocol.TunnelUdpProtocol.TunnelUdpRequest;
import com.mocyx.biosocks.protocol.TunnelUdpProtocol.TunnelUdpResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Administrator
 */
@Slf4j
public class UdpServer implements Runnable {

    private ConfigDto configDto;
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
    }


    @Data
    private static class UdpTunnel {
        private Selector selector;
        private DatagramChannel upChannel;
        private ConcurrentHashMap<String, Pipe> pipes = new ConcurrentHashMap<>();
        private BlockingQueue<Pipe> pipeQueue = new ArrayBlockingQueue<>(1024);
    }

    private String pipeKey(InetSocketAddress source, InetSocketAddress client, InetSocketAddress remote) {
        return String.format("%s:%d %s:%d %s:%d", source.getHostString(), source.getPort(),
                client.getHostString(), client.getPort(),
                remote.getHostString(), remote.getPort());
    }

    private ConcurrentHashMap<String, UdpTunnel> tunnels = new ConcurrentHashMap<>();


    private static void sendUdpResponse(UdpTunnel tunnel, Pipe pipe, byte[] data) {
        TunnelUdpResponse response = new TunnelUdpResponse();
        response.setData(data);
        response.setRemote(pipe.getRemote());
        response.setSource(pipe.getSource());
        response.setType(TunnelUdpProtocol.TunnelUdpMsgType.RES_RECV.getV());

        ByteBuffer buffer = ByteBuffer.allocate(Global.largeBufferSize);
        response.write(buffer);
        buffer.flip();

        try {
            int w = tunnel.upChannel.send(buffer, pipe.client);
            log.debug("udp write {} {}", w, pipe.client);
        } catch (IOException e) {
            log.error("udp write error ", e);
        }

    }

    private static class UdpDownStreamWorker implements Runnable {
        UdpTunnel udpTunnel;


        public UdpDownStreamWorker(UdpTunnel udpTunnel) {
            this.udpTunnel = udpTunnel;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Selector selector = udpTunnel.selector;

                    while (true) {
                        Pipe pipe = udpTunnel.pipeQueue.poll();
                        if (pipe == null) {
                            break;
                        }
                        pipe.channel.register(udpTunnel.selector, SelectionKey.OP_READ, pipe);
                    }

                    int readyChannels = selector.select();
                    if (readyChannels == 0) {
                        selector.selectedKeys().clear();
                        continue;
                    }
                    Set<SelectionKey> keys = selector.selectedKeys();
                    Iterator<SelectionKey> keyIterator = keys.iterator();
                    while (keyIterator.hasNext()) {
                        SelectionKey key = keyIterator.next();
                        keyIterator.remove();
                        if (key.isValid() && key.isReadable()) {
                            DatagramChannel inputChannel = (DatagramChannel) key.channel();

                            ByteBuffer receiveBuffer = ByteBuffer.allocate(Global.largeBufferSize);
                            InetSocketAddress address = (InetSocketAddress) inputChannel.receive(receiveBuffer);
                            receiveBuffer.flip();

                            log.debug("udp read {}", receiveBuffer.remaining());

                            byte[] data = new byte[receiveBuffer.remaining()];
                            receiveBuffer.get(data);

                            Pipe pipe = (Pipe) key.attachment();

                            sendUdpResponse(udpTunnel, pipe, data);
                        }
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
            udpTunnel.selector = Selector.open();
            udpTunnel.upChannel = datagramChannel;

            Thread t = new Thread(new UdpDownStreamWorker(udpTunnel));
            t.start();

            log.info("udp listen on {}", datagramChannel.getLocalAddress());

            while (true) {
                ByteBuffer buffer = ByteBuffer.allocate(Global.largeBufferSize);
                InetSocketAddress remoteAddres = (InetSocketAddress) datagramChannel.receive(buffer);
                buffer.flip();
                TunnelUdpRequest request = TunnelUdpRequest.tryRead(buffer);
                if (request == null) {
                    throw new RuntimeException("decode error");
                } else {
                    String key = pipeKey(request.getSource(), remoteAddres, request.getRemote());
                    Pipe pipe = udpTunnel.getPipes().getOrDefault(key, null);
                    if (pipe == null) {
                        Pipe newPipe = new Pipe();
                        newPipe.source = request.getSource();
                        newPipe.client = remoteAddres;
                        newPipe.remote = request.getRemote();
                        newPipe.channel = DatagramChannel.open();
                        newPipe.channel.bind(null);
                        newPipe.channel.connect(request.getRemote());
                        newPipe.channel.configureBlocking(false);
                        newPipe.key = key;
                        udpTunnel.getPipes().put(key, newPipe);
                        udpTunnel.pipeQueue.add(newPipe);
                        udpTunnel.selector.wakeup();
                        pipe = newPipe;

                        log.info("create udp pipe {}", pipe.key);
                    }
                    ByteBuffer tmpBuffer = ByteBuffer.wrap(request.getData());
                    try {
                        int w = pipe.channel.write(tmpBuffer);
                        log.debug("write udp to remote {} {}", w, pipe.key);
                    } catch (IOException e) {
                        log.error("fail write udp to remote {}", pipe.key, e);
                    }
                }
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.exit(0);
        }
    }

}
