package com.mocyx.biosocks.nio;

import com.mocyx.biosocks.ConfigDto;
import com.mocyx.biosocks.bio.protocol.SocksProtocol;
import com.mocyx.biosocks.bio.protocol.SocksProtocol.*;
import com.mocyx.biosocks.bio.protocol.TunnelMsgType;
import com.mocyx.biosocks.bio.protocol.TunnelProtocol;
import com.mocyx.biosocks.bio.protocol.TunnelProtocol.TunnelRequest;
import com.mocyx.biosocks.bio.protocol.TunnelProtocol.TunnelResponse;
import com.mocyx.biosocks.util.EncodeUtil;
import com.mocyx.biosocks.util.ObjAttrUtil;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;


@Slf4j
public class NioClient implements Runnable {

    ConfigDto configDto;

    public NioClient(ConfigDto configDto) {
        this.configDto = configDto;
    }

    private Selector selector;

    private ObjAttrUtil objAttrUtil = new ObjAttrUtil();

    //private ConcurrentHashMap<String, ClientChannel> clientChannels = new ConcurrentHashMap<>();

    @Data
    static class ClientPipe {
        private ByteBuffer localInBuffer = ByteBuffer.allocate(4 * 1024);
        private ByteBuffer localOutBuffer = ByteBuffer.allocate(8 * 1024);
        private ByteBuffer remoteInBuffer = ByteBuffer.allocate(4 * 1024);
        private ByteBuffer remoteOutBuffer = ByteBuffer.allocate(8 * 1024);
        //
        private SocketChannel localChannel;
        private SocketChannel remoteChannel;
        private Socks5State state = Socks5State.shake;
        private InetSocketAddress targetAddr;

        private SocketChannel otherChannel(SocketChannel channel) {
            if (channel == localChannel) {
                return remoteChannel;
            } else {
                return localChannel;
            }
        }
    }

    private void doAccept(ServerSocketChannel serverChannel) throws IOException {
        System.out.println("doAccept");
        SocketChannel channel = serverChannel.accept();
        channel.configureBlocking(false);
        SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
        ClientPipe clientPipe = new ClientPipe();
        clientPipe.setLocalChannel(channel);
        objAttrUtil.setAttr(channel, "type", "local");
        objAttrUtil.setAttr(channel, "pipe", clientPipe);
        objAttrUtil.setAttr(channel, "key", key);
        System.currentTimeMillis();
    }

    @SneakyThrows
    private void handleLocalIn(ClientPipe pipe) {
        System.currentTimeMillis();

        if (pipe.state == Socks5State.shake) {
            SocksShakeRequestDto requestDto = SocksShakeRequestDto.tryRead(pipe.getLocalInBuffer());
            SocksShakeResponseDto responseDto = new SocksShakeResponseDto();
            responseDto.setVer((byte) 0x05);
            responseDto.setMethod((byte) 0x00);
            ByteBuffer tmpBuffer = ByteBuffer.allocate(16);
            responseDto.write(tmpBuffer);
            tmpBuffer.flip();
            pipe.getLocalChannel().write(tmpBuffer);
            //
            pipe.state = Socks5State.connect;
            System.currentTimeMillis();
        } else if (pipe.state == Socks5State.connect) {
            SocksConnectRequestDto connectRequestDto = SocksConnectRequestDto.tryRead(pipe.getLocalInBuffer());
            SocketChannel remote = SocketChannel.open();
            pipe.setRemoteChannel(remote);
            objAttrUtil.setAttr(remote, "type", "remote");
            objAttrUtil.setAttr(remote, "pipe", pipe);
            remote.configureBlocking(false);
            //
            InetSocketAddress targetAddr = new InetSocketAddress(connectRequestDto.getDomain(),
                    connectRequestDto.getPort());
            pipe.setTargetAddr(targetAddr);
            //
            InetSocketAddress address = new InetSocketAddress(configDto.getServer(),
                    configDto.getServerPort());
            SelectionKey key = remote.register(selector, SelectionKey.OP_CONNECT);
            objAttrUtil.setAttr(remote, "key", key);
            boolean b1 = remote.connect(address);
            System.currentTimeMillis();
        } else if (pipe.state == Socks5State.transfer) {
            ByteBuffer buffer = pipe.getLocalInBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            EncodeUtil.simpleXorEncrypt(data, 0, data.length);
            pipe.getRemoteOutBuffer().put(data);
            pipe.getRemoteOutBuffer().flip();
            tryFlushWrite(pipe, pipe.getRemoteChannel());
            System.currentTimeMillis();
        }
        System.currentTimeMillis();
    }

    @SneakyThrows
    private void handleRemoteIn(ClientPipe pipe) {
        System.currentTimeMillis();
        ByteBuffer buffer = pipe.getRemoteInBuffer();

        if (pipe.state == Socks5State.connect) {
            TunnelResponse response = TunnelResponse.tryRead(buffer);
            SocksConnectResponseDto res = new SocksConnectResponseDto();
            res.setVer((byte) 0x05);
            res.setRsv((byte) 0x00);
            res.setAtyp((byte) 0x01);
            res.setAddr(Inet4Address.getByName("0.0.0.0"));
            res.setPort((short) 0x0843);
            //
            if (response.getType() == TunnelMsgType.RES_CONNECT_SUCCESS.getV()) {
                res.setCmd((byte) 0x00);
                res.write(pipe.getLocalOutBuffer());
                pipe.getLocalOutBuffer().flip();
                tryFlushWrite(pipe, pipe.getLocalChannel());
                System.currentTimeMillis();
                pipe.state = Socks5State.transfer;
            } else if (response.getType() == TunnelMsgType.RES_CONNECT_FAIL.getV()) {
                res.setCmd((byte) 0x04);
                res.write(pipe.getLocalOutBuffer());
                pipe.getLocalOutBuffer().flip();
                tryFlushWrite(pipe, pipe.getLocalChannel());
                log.warn("cloud connect remote fail");
            }
        } else if (pipe.state == Socks5State.transfer) {
            //
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            EncodeUtil.simpleXorEncrypt(data, 0, data.length);
            pipe.getLocalOutBuffer().put(data);
            pipe.getLocalOutBuffer().flip();
            tryFlushWrite(pipe, pipe.getLocalChannel());
            System.currentTimeMillis();
        }
        System.currentTimeMillis();
    }

    @SneakyThrows
    private boolean tryFlushWrite(ClientPipe pipe, SocketChannel channel) {
        ByteBuffer buffer;
        if (channel == pipe.getLocalChannel()) {
            buffer = pipe.getLocalOutBuffer();
        } else {
            buffer = pipe.getRemoteOutBuffer();
        }
        while (buffer.hasRemaining()) {
            int n = channel.write(buffer);
            log.info("tryFlushWrite write {}", n);
            if (n <= 0) {
                log.warn("write fail");
                //
                SelectionKey key = (SelectionKey) objAttrUtil.getAttr(channel, "key");
                key.interestOps(SelectionKey.OP_WRITE);
                //关闭写来源
                SocketChannel otherChannel = pipe.otherChannel(channel);
                getKey(otherChannel).interestOps(0);
                System.currentTimeMillis();
                buffer.compact();
                //buffer.flip();
                return false;
            }
        }
        buffer.clear();
        return true;
    }

    private SelectionKey getKey(SocketChannel channel) {

        return (SelectionKey) objAttrUtil.getAttr(channel, "key");
    }

    private void doConnect(SocketChannel socketChannel) throws IOException {
        System.out.println("doConnect");
        //
        String type = (String) objAttrUtil.getAttr(socketChannel, "type");
        ClientPipe pipe = (ClientPipe) objAttrUtil.getAttr(socketChannel, "pipe");
        SelectionKey key = (SelectionKey) objAttrUtil.getAttr(socketChannel, "key");
        if (type.equals("remote")) {
            boolean b1 = socketChannel.finishConnect();
            key.interestOps(SelectionKey.OP_READ);
            TunnelRequest request = new TunnelRequest();
            //
            request.setDomain(pipe.getTargetAddr().getHostString());
            request.setType((short) TunnelMsgType.REQ_CONNECT_DOMAIN.getV());
            request.setPort(pipe.getTargetAddr().getPort());
            //
            request.write(pipe.getRemoteOutBuffer());
            pipe.getRemoteOutBuffer().flip();
            tryFlushWrite(pipe, socketChannel);
            //
            System.currentTimeMillis();
        }
    }

    private void doWrite(SocketChannel socketChannel) throws IOException {
        System.out.println("doWrite");
        ClientPipe pipe = (ClientPipe) objAttrUtil.getAttr(socketChannel, "pipe");
        boolean flushed = tryFlushWrite(pipe, socketChannel);
        if (flushed) {
            SocketChannel other = pipe.otherChannel(socketChannel);
            //
            SelectionKey key1 = (SelectionKey) objAttrUtil.getAttr(socketChannel, "key");
            key1.interestOps(SelectionKey.OP_READ);
            //
            SelectionKey key2 = (SelectionKey) objAttrUtil.getAttr(other, "key");
            key2.interestOps(SelectionKey.OP_READ);
        }
    }

    private void closePipe(ClientPipe pipe) {
        objAttrUtil.delObj(pipe.localChannel);
        objAttrUtil.delObj(pipe.remoteChannel);

        if (pipe.getLocalChannel() != null) {
            try {
                pipe.getLocalChannel().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (pipe.getRemoteChannel() != null) {
            try {
                pipe.getRemoteChannel().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void doRead(SocketChannel socketChannel) {
        System.out.println("doRead");
        //
        String type = (String) objAttrUtil.getAttr(socketChannel, "type");
        ClientPipe pipe = (ClientPipe) objAttrUtil.getAttr(socketChannel, "pipe");
        ByteBuffer inBuffer = null;
        if (pipe.state == Socks5State.transfer) {
            System.currentTimeMillis();
        }
        if (type.equals("local")) {
            inBuffer = pipe.getLocalInBuffer();
        } else {
            inBuffer = pipe.getRemoteInBuffer();
        }
        inBuffer.clear();
        //
        if (inBuffer.remaining() <= 0) {
            log.warn("buffer full");
            throw new RuntimeException("buffer full");
        }
        int readCount = 0;
        try {
            readCount = socketChannel.read(inBuffer);
            log.info("readCount {}", readCount);
        } catch (IOException e) {
            closePipe(pipe);
            e.printStackTrace();
            return;
        }
        if (readCount == -1) {
            log.warn("read -1");
            closePipe(pipe);
        } else {
            inBuffer.flip();
            if (type.equals("local")) {
                handleLocalIn(pipe);
            } else {
                handleRemoteIn(pipe);
            }
            System.currentTimeMillis();
        }
    }

    @Override
    public void run() {
        try {
            System.out.println("NIOServer start");
            selector = Selector.open();
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(configDto.getClient(), configDto.getClientPort()));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);


            while (selector.select() > 0) {
                log.info("handle select");
                for (Iterator it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                    SelectionKey key = (SelectionKey) it.next();
                    it.remove();
                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            doAccept((ServerSocketChannel) key.channel());
                        } else if (key.isReadable()) {
                            doRead((SocketChannel) key.channel());
                        } else if (key.isConnectable()) {
                            doConnect((SocketChannel) key.channel());
                            System.currentTimeMillis();
                        } else if (key.isWritable()) {
                            doWrite((SocketChannel) key.channel());
                            System.currentTimeMillis();
                        }
                    }
                }
            }
            serverChannel.close();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

    }
}
