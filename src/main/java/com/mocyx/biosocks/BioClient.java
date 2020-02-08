package com.mocyx.biosocks;

import com.alibaba.fastjson.JSON;
import com.mocyx.biosocks.util.BioUtil;
import com.mocyx.biosocks.entity.ConfigDto;
import com.mocyx.biosocks.exception.ProxyException;
import com.mocyx.biosocks.protocol.SocksProtocol;
import com.mocyx.biosocks.protocol.SocksProtocol.SocksConnectRequestDto;
import com.mocyx.biosocks.protocol.SocksProtocol.SocksShakeRequestDto;
import com.mocyx.biosocks.protocol.SocksProtocol.SocksShakeResponseDto;
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

/**
 * @author Administrator
 */
@Slf4j
@Component
public class BioClient implements Runnable {

    static void closePipe(Pipe pipe) {

        try {
            synchronized (pipe) {
                if (pipe.local != null) {
                    pipe.local.close();
                }
                if (pipe.remote != null) {
                    pipe.remote.close();
                }
            }

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

    }

    class ClientWorkerA implements Runnable {
        private Pipe pipe;
        ByteBuffer inBuffer = ByteBuffer.allocate(4096);
        ByteBuffer tmpBuffer = ByteBuffer.allocate(4096);

        public ClientWorkerA(Pipe pipe) {
            this.pipe = pipe;

        }

        private void handleShake() throws IOException {
            inBuffer.clear();
            while (true) {
                int len = BioUtil.read(pipe.local, inBuffer);
                if (len == -1) {
                    throw new ProxyException("error");
                }
                inBuffer.flip();
                SocksShakeRequestDto requestDto = SocksShakeRequestDto.tryRead(inBuffer);
                if (requestDto == null) {
                    inBuffer.compact();
                } else {
                    SocksShakeResponseDto res = new SocksShakeResponseDto();
                    res.setVer((byte) 0x05);
                    res.setMethod((byte) 0x00);
                    tmpBuffer.clear();
                    res.write(tmpBuffer);
                    tmpBuffer.flip();
                    BioUtil.write(pipe.local, tmpBuffer);
                    break;
                }
            }
        }

        private void sendConnectResponse(Pipe pipe, byte code) throws IOException {
            SocksProtocol.SocksConnectResponseDto res = new SocksProtocol.SocksConnectResponseDto();
            res.setVer((byte) 0x05);
            res.setCmd(code);
            res.setRsv((byte) 0x00);
            res.setAtyp((byte) 0x01);
            res.setAddr(Inet4Address.getByName("0.0.0.0"));
            res.setPort((short) 0x0843);
            tmpBuffer.clear();
            res.write(tmpBuffer);
            tmpBuffer.flip();
            BioUtil.write(pipe.local, tmpBuffer);
        }

        private void sendConnectRequest(Pipe pipe, String domain, int port) throws IOException {
            TunnelRequest request = new TunnelRequest();
            request.setDomain(domain);
            request.setType( TunnelProtocol.TunnelMsgType.REQ_CONNECT_DOMAIN.getV());
            request.setPort(port);
            tmpBuffer.clear();
            request.write(tmpBuffer);
            tmpBuffer.flip();
            BioUtil.write(pipe.remote, tmpBuffer);
        }

        private void waitForConnectResult() throws IOException {
            inBuffer.clear();
            while (true) {
                int len = BioUtil.read(pipe.remote, inBuffer);
                if (len == -1) {
                    log.info("disconnect {}", pipe.remote);
                    throw new ProxyException("error");
                }
                inBuffer.flip();
                TunnelResponse response = TunnelResponse.tryRead(inBuffer);
                if (response == null) {
                    inBuffer.compact();
                } else {
                    if (response.getType() == TunnelProtocol.TunnelMsgType.RES_CONNECT_SUCCESS.getV()) {
                        sendConnectResponse(pipe, (byte) 0x00);
                    } else if (response.getType() == TunnelProtocol.TunnelMsgType.RES_CONNECT_FAIL.getV()) {
                        sendConnectResponse(pipe, (byte) 0x04);
                        throw new ProxyException("connect fail");
                    } else {
                        throw new ProxyException("data error");
                    }
                    break;
                }
            }
        }

        private void startTransfer() throws IOException {
            Thread t = new Thread(new ClientWorkerB(pipe));
            t.start();
            while (true) {
                inBuffer.clear();
                int n = BioUtil.read(pipe.local, inBuffer);
                if (n == -1) {
                    closePipe(pipe);
                }
                inBuffer.flip();

                EncodeUtil.simpleXorEncrypt(inBuffer.array(), 0, inBuffer.limit());

                BioUtil.write(pipe.remote, inBuffer);
            }
        }

        private void handleConnect() throws IOException {
            inBuffer.clear();
            SocksConnectRequestDto request = null;
            while (true) {
                int len = BioUtil.read(pipe.local, inBuffer);
                if (len == -1) {
                    log.info("disconnect {}", pipe.local);
                    throw new ProxyException("disconnect");
                }
                inBuffer.flip();
                request = SocksConnectRequestDto.tryRead(inBuffer);
                if (request == null) {
                    inBuffer.compact();
                } else {
                    break;
                }
            }
            if (request.getAtyp() == 0x01) {
                log.info("tunnel connect {} {}", request.getAddr().toString(), request.getPort());
                sendConnectRequest(pipe, request.getAddr().getHostAddress(), request.getPort());
            } else if (request.getAtyp() == 0x03) {
                log.info("tunnel connect {} {}", request.getDomain(), request.getPort());
                sendConnectRequest(pipe, request.getDomain(), request.getPort());
            }
            waitForConnectResult();

        }

        @Override
        public void run() {
            try {
                SocketChannel remote = SocketChannel.open();

                boolean success = false;

                try {

                    InetSocketAddress address = new InetSocketAddress(clientConfigDto.getServer(), clientConfigDto.getServerPort());
                    success = remote.connect(address);
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                    throw new ProxyException(String.format("连接远程服务器失败 %s %d", clientConfigDto.getServer(), clientConfigDto.getServerPort()));
                }

                if (!success) {
                    throw new ProxyException("connect server fail");
                }
                pipe.remote = remote;
                handleShake();
                handleConnect();
                startTransfer();
            } catch (ClosedChannelException e) {
                log.debug("channel closed {}", e.getMessage());
                closePipe(pipe);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                closePipe(pipe);
            }
            log.debug("ClientWorkerA exit");
        }
    }

    class ClientWorkerB implements Runnable {
        Pipe pipe;

        ByteBuffer buffer = ByteBuffer.allocate(4096);

        public ClientWorkerB(Pipe pipe) {
            this.pipe = pipe;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    buffer.clear();
                    int n = BioUtil.read(pipe.remote, buffer);
                    if (n == -1) {
                        closePipe(pipe);
                    }
                    buffer.flip();

                    EncodeUtil.simpleXorEncrypt(buffer.array(), 0, buffer.limit());

                    BioUtil.write(pipe.local, buffer);
                }
            } catch (ClosedChannelException e) {
                log.debug("channel closed {}", e.getMessage());
                closePipe(pipe);
            } catch (IOException e) {
                log.error(e.getMessage(), e);
                closePipe(pipe);
            }
            log.debug("ClientWorkerB exit");
        }
    }

    private class Pipe {
        SocketChannel local;
        SocketChannel remote;
    }

    private ServerSocketChannel serverSocketChannel;
    ConfigDto clientConfigDto;

    @Override
    public void run() {
        try {
            String str = FileUtils.readFileToString(new File("client.json"), "utf-8");
            clientConfigDto = JSON.parseObject(str, ConfigDto.class);
            EncodeUtil.setSecret(clientConfigDto.getSecret());
            serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(true);
            serverSocketChannel.bind(new InetSocketAddress(Inet4Address.getByName(clientConfigDto.getClient()), clientConfigDto.getClientPort()));
            log.info("listen on {}", serverSocketChannel.getLocalAddress());
            while (true) {
                SocketChannel socketChannel = serverSocketChannel.accept();
                log.info("accept {}", socketChannel);
                Pipe pipe = new Pipe();
                pipe.local = socketChannel;
                Thread t = new Thread(new ClientWorkerA(pipe));
                t.start();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            System.exit(0);
        }


    }
}
