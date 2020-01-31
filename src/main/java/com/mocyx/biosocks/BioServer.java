package com.mocyx.biosocks;

import com.alibaba.fastjson.JSON;
import com.mocyx.biosocks.util.BioUtil;
import com.mocyx.biosocks.entity.ConfigDto;
import com.mocyx.biosocks.exception.ProxyException;
import com.mocyx.biosocks.protocol.TunnelProtocol;
import com.mocyx.biosocks.protocol.TunnelProtocol.TunnelRequest;
import com.mocyx.biosocks.protocol.TunnelProtocol.TunnelResponse;
import com.mocyx.biosocks.util.EncodeUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;

@Slf4j
@Component
public class BioServer implements Runnable {
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

    private class ServerWorkerA implements Runnable {
        Tunnel tunnel;
        ByteBuffer buffer = ByteBuffer.allocate(4096);

        public ServerWorkerA(Tunnel tunnel) {
            this.tunnel = tunnel;
        }

        private void sendConnectFail(Tunnel tunnel) throws IOException {
            buffer.clear();
            TunnelResponse response = new TunnelResponse();
            response.setType((byte) TunnelProtocol.ResMsgType.CONNECT_FAIL.getV());
            response.write(buffer);
            buffer.flip();
            BioUtil.write(tunnel.local, buffer);
        }

        private void sendConnectSuccess(Tunnel tunnel) throws IOException {
            buffer.clear();
            TunnelResponse response = new TunnelResponse();
            response.setType((byte) TunnelProtocol.ResMsgType.CONNECT_SUCCESS.getV());
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
                    boolean success = false;
                    InetSocketAddress address = new InetSocketAddress(requestDto.getDomain(), requestDto.getPort());
                    try {
                        success = remote.connect(address);
                    } catch (IOException | UnresolvedAddressException e) {

                    }
                    if (!success) {
                        sendConnectFail(tunnel);
                        log.error("connect remote fail {}", address);
                        throw new ProxyException("connect remote fail");
                    } else {
                        tunnel.remote = remote;
                        sendConnectSuccess(tunnel);
                    }
                    break;
                }
            }
        }

        private void startTransfer() {
            Thread t = new Thread(new ServerWorkerB(tunnel));
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

    class ServerWorkerB implements Runnable {
        Tunnel tunnel;

        ByteBuffer buffer = ByteBuffer.allocate(4096);

        public ServerWorkerB(Tunnel pipe) {
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

    ConfigDto configDto;

    ServerSocketChannel serverSocketChannel;

    @Override
    public void run() {
        try {
            String str = FileUtils.readFileToString(new File("data/server.json"), "utf-8");
            configDto = JSON.parseObject(str, ConfigDto.class);
            EncodeUtil.setSecret(configDto.getSecret());
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(true);
            serverSocketChannel.bind(new InetSocketAddress(Inet4Address.getByName(configDto.getServer()), configDto.getServerPort()));
            log.info("listen on {}", serverSocketChannel.getLocalAddress());
            while (true) {
                SocketChannel socketChannel = serverSocketChannel.accept();
                log.info("accept {}", socketChannel);
                Tunnel pipe = new Tunnel();
                pipe.local = socketChannel;
                Thread t = new Thread(new ServerWorkerA(pipe));
                t.start();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.exit(0);
        }
    }
}
