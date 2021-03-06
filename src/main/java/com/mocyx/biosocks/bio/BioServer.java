package com.mocyx.biosocks.bio;

import com.mocyx.biosocks.Global;
import com.mocyx.biosocks.bio.protocol.TunnelMsgType;
import com.mocyx.biosocks.bio.protocol.TunnelProtocol.TunnelRequest;
import com.mocyx.biosocks.bio.protocol.TunnelProtocol.TunnelResponse;
import com.mocyx.biosocks.util.BioUtil;
import com.mocyx.biosocks.ConfigDto;
import com.mocyx.biosocks.exception.ProxyException;
import com.mocyx.biosocks.util.EncodeUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;

/**
 * @author Administrator
 */
@Slf4j
public class BioServer implements Runnable {

    private ConfigDto configDto;
    public BioServer(ConfigDto configDto){
        this.configDto=configDto;
    }

    static void closeTunnel(Tunnel tunnel) {
        try {
            synchronized (tunnel) {
                if (tunnel.local != null) {
                    tunnel.local.close();
                }
                if (tunnel.remote != null) {
                    tunnel.remote.close();
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

    }

    private static class Tunnel {
        SocketChannel local;
        SocketChannel remote;
    }

    private class ServerUpStreamWorker implements Runnable {
        Tunnel tunnel;
        ByteBuffer buffer = ByteBuffer.allocate(Global.smallBufferSize);

        public ServerUpStreamWorker(Tunnel tunnel) {
            this.tunnel = tunnel;
        }

        private void sendConnectFail(Tunnel tunnel) throws IOException {
            buffer.clear();
            TunnelResponse response = new TunnelResponse();
            response.setType((short) TunnelMsgType.RES_CONNECT_FAIL.getV());
            response.write(buffer);
            buffer.flip();
            BioUtil.write(tunnel.local, buffer);
        }

        private void sendConnectSuccess(Tunnel tunnel) throws IOException {
            buffer.clear();
            TunnelResponse response = new TunnelResponse();
            response.setType((short) TunnelMsgType.RES_CONNECT_SUCCESS.getV());
            response.write(buffer);
            buffer.flip();
            BioUtil.write(tunnel.local, buffer);
        }

        private void handleConnect() throws IOException {
            buffer.clear();
            while (true) {
                int len = BioUtil.read(tunnel.local, buffer);
                if (len == -1) {
                    throw new ProxyException("error");
                }
                buffer.flip();
                TunnelRequest requestDto = TunnelRequest.tryRead(buffer);
                if (requestDto == null) {
                    buffer.compact();
                } else {
                    SocketChannel remote = SocketChannel.open();
                    InetSocketAddress address = new InetSocketAddress(requestDto.getDomain(), requestDto.getPort());
                    try {
                        remote.socket().connect(address, 5000);
                        log.info("connect remote success {}", address);
                    } catch (IOException | UnresolvedAddressException e) {
                        sendConnectFail(tunnel);
                        log.error("connect remote fail {}", address, e);
                        throw new ProxyException("connect remote fail");
                    }

                    tunnel.remote = remote;
                    sendConnectSuccess(tunnel);
                    break;
                }
            }
        }

        private void startTransfer() {
            Thread t = new Thread(new ServerDownStreamWorker(tunnel));
            t.start();

            try {
                while (true) {
                    buffer.clear();
                    int n = BioUtil.read(tunnel.local, buffer);
                    if (n == -1) {
                        closeTunnel(tunnel);
                    }
                    buffer.flip();
                    EncodeUtil.simpleXorEncrypt(buffer.array(), 0, buffer.limit());
                    BioUtil.write(tunnel.remote, buffer);
                }

            } catch (ClosedChannelException e) {
                log.debug("channel closed {}", e.getMessage());
                closeTunnel(tunnel);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                closeTunnel(tunnel);
            }
        }

        @Override
        public void run() {
            try {
                handleConnect();
                startTransfer();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                closeTunnel(tunnel);
            }
            log.debug("ServerWorkerA exit");
        }
    }

    class ServerDownStreamWorker implements Runnable {
        Tunnel tunnel;

        ByteBuffer buffer = ByteBuffer.allocate(Global.smallBufferSize);

        public ServerDownStreamWorker(Tunnel pipe) {
            this.tunnel = pipe;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    buffer.clear();
                    int n = BioUtil.read(tunnel.remote, buffer);
                    if (n == -1) {
                        closeTunnel(tunnel);
                    }
                    buffer.flip();

                    EncodeUtil.simpleXorEncrypt(buffer.array(), 0, buffer.limit());

                    BioUtil.write(tunnel.local, buffer);
                }

            } catch (ClosedChannelException e) {
                log.debug("channel closed {}", e.getMessage());
                closeTunnel(tunnel);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                closeTunnel(tunnel);
            }
            log.debug("ServerWorkerB exit");
        }
    }


    ServerSocketChannel serverSocketChannel;

    @Override
    public void run() {
        try {
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(true);
            serverSocketChannel.bind(new InetSocketAddress(Inet4Address.getByName(configDto.getServer()),configDto.getServerPort()));
            log.info("tcp listen on {}", serverSocketChannel.getLocalAddress());
            while (true) {
                SocketChannel socketChannel = serverSocketChannel.accept();
                log.info("accept {}", socketChannel);
                Tunnel pipe = new Tunnel();
                pipe.local = socketChannel;
                Thread t = new Thread(new ServerUpStreamWorker(pipe));
                t.start();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.exit(0);
        }
    }
}
