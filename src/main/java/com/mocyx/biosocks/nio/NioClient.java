package com.mocyx.biosocks.nio;

import com.alibaba.fastjson.JSON;
import com.mocyx.biosocks.util.ConfigDto;
import com.mocyx.biosocks.protocol.SocksProtocol.*;
import com.mocyx.biosocks.protocol.TunnelMsgType;
import com.mocyx.biosocks.protocol.TunnelProtocol.TunnelRequest;
import com.mocyx.biosocks.protocol.TunnelProtocol.TunnelResponse;
import com.mocyx.biosocks.util.EncodeUtil;
import com.mocyx.biosocks.util.ObjAttrUtil;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

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


    @Getter
    @Setter
    static class Channel{
        //local remote
        private String type;
        private Pipe pipe;
        private SelectionKey key;
        private SocketChannel socketChannel;
        private ByteBuffer inBuffer = ByteBuffer.allocate(4 * 1024);
        private ByteBuffer outBuffer = ByteBuffer.allocate(8 * 1024);
        private boolean outputOpen=true;
    }

    @Getter
    @Setter
    static class Pipe {
        private Channel localChannel;
        private Channel remoteChannel;
        //
        private InetSocketAddress targetAddr;
        private Socks5State state = Socks5State.shake;
        private long lastActiveTime=System.currentTimeMillis();
        //
        private Channel otherChannel(Channel channel) {
            if (channel == localChannel) {
                return remoteChannel;
            } else {
                return localChannel;
            }
        }
    }

//
//    @Data
//    static class ClientPipe {
//        private ByteBuffer localInBuffer = ByteBuffer.allocate(4 * 1024);
//        private ByteBuffer localOutBuffer = ByteBuffer.allocate(8 * 1024);
//        private ByteBuffer remoteInBuffer = ByteBuffer.allocate(4 * 1024);
//        private ByteBuffer remoteOutBuffer = ByteBuffer.allocate(8 * 1024);
//        //
//        private SocketChannel localChannel;
//        private SocketChannel remoteChannel;
//        private Socks5State state = Socks5State.shake;
//        private InetSocketAddress targetAddr;
//
//        private SocketChannel otherChannel(SocketChannel channel) {
//            if (channel == localChannel) {
//                return remoteChannel;
//            } else {
//                return localChannel;
//            }
//        }
//    }

    private void doAccept(ServerSocketChannel serverChannel) throws IOException {
        SocketChannel channel = serverChannel.accept();
        Channel c=new Channel();
        c.setType("local");


        c.setSocketChannel(channel);
        channel.configureBlocking(false);
        SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
        c.setKey(key);
        Pipe clientPipe = new Pipe();
        c.setPipe(clientPipe);
        clientPipe.setLocalChannel(c);

        objAttrUtil.setAttr(channel, "channel", c);
        System.currentTimeMillis();
    }

    private Channel getChannelFromSocketChannel(SocketChannel socketChannel){
       Channel channel= (Channel) objAttrUtil.getAttr(socketChannel, "channel");
        Pipe pipe=channel.getPipe();
        if(pipe!=null){
            pipe.lastActiveTime=System.currentTimeMillis();
        }
        return channel;
    }

    @SneakyThrows
    private void handleLocalIn(Channel local) {
        System.currentTimeMillis();

        if (local.getPipe().state == Socks5State.shake) {
            SocksShakeRequestDto requestDto = SocksShakeRequestDto.tryRead(local.getInBuffer());
            SocksShakeResponseDto responseDto = new SocksShakeResponseDto();
            responseDto.setVer((byte) 0x05);
            responseDto.setMethod((byte) 0x00);
            ByteBuffer tmpBuffer = ByteBuffer.allocate(16);
            responseDto.write(tmpBuffer);
            tmpBuffer.flip();
            local.getSocketChannel().write(tmpBuffer);
            //
            local.getPipe().state = Socks5State.connect;
            System.currentTimeMillis();
        } else if (local.getPipe().state == Socks5State.connect) {
            SocksConnectRequestDto connectRequestDto = SocksConnectRequestDto.tryRead(local.getInBuffer());
            SocketChannel remote = SocketChannel.open();
            Channel remoteChannel=new Channel();
            remoteChannel.setSocketChannel(remote);
            local.getPipe().setRemoteChannel(remoteChannel);
            remote.configureBlocking(false);
            //
            String host = null;
            if (!StringUtils.isEmpty(connectRequestDto.getDomain())) {
                host = connectRequestDto.getDomain();
            } else if (connectRequestDto.getAddr() != null) {
                host = connectRequestDto.getAddr().getHostAddress();
            }
            if (StringUtils.isEmpty(host)) {
                log.warn("host is empty {}", JSON.toJSONString(connectRequestDto));
                closePipe(local.getPipe());
                return;
            }
            InetSocketAddress targetAddr = new InetSocketAddress(host,
                    connectRequestDto.getPort());
            local.getPipe().setTargetAddr(targetAddr);
            //
            InetSocketAddress address = new InetSocketAddress(configDto.getServer(),
                    configDto.getServerPort());
            SelectionKey key = remote.register(selector, SelectionKey.OP_CONNECT);
            remoteChannel.setKey(key);
            remoteChannel.setType("remote");
            remoteChannel.setPipe(local.getPipe());
            objAttrUtil.setAttr(remote, "channel", remoteChannel);
            boolean b1 = remote.connect(address);
            System.currentTimeMillis();
        } else if (local.getPipe().state == Socks5State.transfer) {
            Channel remote=local.getPipe().otherChannel(local);
            ByteBuffer buffer = local.getInBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            EncodeUtil.simpleXorEncrypt(data, 0, data.length);
            remote.getOutBuffer().put(data);
            remote.getOutBuffer().flip();
            tryFlushWrite(remote);
            System.currentTimeMillis();
        }
        System.currentTimeMillis();
    }

    @SneakyThrows
    private void handleRemoteIn(Channel remote) {
        System.currentTimeMillis();
        ByteBuffer buffer = remote.getInBuffer();

        if (remote.getPipe().state == Socks5State.connect) {
            Channel local=remote.getPipe().otherChannel(remote);
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
                res.write(local.getOutBuffer());
                local.getOutBuffer().flip();
                tryFlushWrite(local);
                System.currentTimeMillis();
                remote.getPipe().state = Socks5State.transfer;
            } else if (response.getType() == TunnelMsgType.RES_CONNECT_FAIL.getV()) {
                res.setCmd((byte) 0x04);
                res.write(local.getOutBuffer());
                local.getOutBuffer().flip();
                tryFlushWrite(local);
                log.warn("cloud connect remote fail");
            }
        } else if (remote.getPipe().state == Socks5State.transfer) {
            //
            Channel local=remote.getPipe().otherChannel(remote);
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            EncodeUtil.simpleXorEncrypt(data, 0, data.length);
            local.getOutBuffer().put(data);
            local.getOutBuffer().flip();
            tryFlushWrite(local);
            System.currentTimeMillis();
        }
        System.currentTimeMillis();
    }

    @SneakyThrows
    private boolean tryFlushWrite(Channel channel) {
        ByteBuffer buffer=channel.getOutBuffer();
        Pipe pipe=channel.getPipe();
        while (buffer.hasRemaining()) {
            int n = 0;
            n = channel.getSocketChannel().write(buffer);

            log.debug("tryFlushWrite write {}", n);
            if (n <= 0) {
                log.warn("write fail {} {} {} {}",buffer.remaining(),n,channel.getSocketChannel().getRemoteAddress(),pipe.getTargetAddr());
                //
                channel.getKey().interestOps(SelectionKey.OP_WRITE);
                //关闭写来源
                Channel otherChannel = pipe.otherChannel(channel);
                otherChannel.getKey().interestOps(0);
                System.currentTimeMillis();
                buffer.compact();
                buffer.flip();
                return false;
            }
        }
        buffer.clear();
        return true;
    }


    private void doConnect(SocketChannel socketChannel) {

        Channel remote=getChannelFromSocketChannel(socketChannel);
        String type = remote.getType();
        Pipe pipe = remote.getPipe();
        Channel other=pipe.otherChannel(remote);
        //
        if (type.equals("remote")) {
            try {
                boolean b1 = socketChannel.finishConnect();
            } catch (IOException e) {
                closePipe((pipe));
                log.warn("connect cloud fail");
                return;
            }
            log.info("connect {}", pipe.targetAddr);
            remote.getKey().interestOps(SelectionKey.OP_READ);
            TunnelRequest request = new TunnelRequest();
            //
            request.setDomain(pipe.getTargetAddr().getHostString());
            request.setType((short) TunnelMsgType.REQ_CONNECT_DOMAIN.getV());
            request.setPort(pipe.getTargetAddr().getPort());
            //
            request.write(remote.getOutBuffer());
            remote.getOutBuffer().flip();
            tryFlushWrite(remote);
            //
            System.currentTimeMillis();
        }
    }

    private void doWrite(SocketChannel socketChannel) throws IOException {
        Channel channel=getChannelFromSocketChannel(socketChannel);
        boolean flushed = tryFlushWrite(channel);
        if (flushed) {
            Channel other = channel.getPipe().otherChannel(channel);
            channel.getKey().interestOps(SelectionKey.OP_READ);
            other.getKey().interestOps(SelectionKey.OP_READ);
        }
    }

    private void closePipe(Pipe pipe) {
        objAttrUtil.delObj(pipe.localChannel);
        objAttrUtil.delObj(pipe.remoteChannel);
        log.info("close {}", pipe.targetAddr);
        if (pipe.getLocalChannel() != null) {
            try {
                pipe.getLocalChannel().getSocketChannel().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (pipe.getRemoteChannel() != null) {
            try {
                pipe.getRemoteChannel().getSocketChannel().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void tryDisableOutput(Channel channel){
        if(channel!=null&&channel.getSocketChannel()!=null&&channel.getOutBuffer().position()==0){
            try {
                channel.getSocketChannel().shutdownOutput();
            }catch (Exception e){
                log.error(e.getMessage(),e);
            }
        }
    }
    private void closeChannelInput(Channel channel){
        try {
            Pipe pipe=channel.getPipe();
            if(channel.getSocketChannel()!=null){
                channel.getSocketChannel().shutdownInput();
                channel.getKey().interestOps(0);
            }
            Channel other=pipe.otherChannel(channel);
            if(other!=null&&other.getSocketChannel()!=null){
                tryDisableOutput(other);
            }
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
    }
    private void doRead(SocketChannel socketChannel) {

        Channel channel=getChannelFromSocketChannel(socketChannel);
        Pipe pipe=channel.getPipe();
        ByteBuffer inBuffer = channel.getInBuffer();
        inBuffer.clear();
        //
        if (inBuffer.remaining() <= 0) {
            log.warn("buffer full");
            throw new RuntimeException("buffer full");
        }
        int readCount = 0;
        try {
            readCount = socketChannel.read(inBuffer);
            log.debug("readCount {}", readCount);
        } catch (IOException e) {
            closePipe(pipe);
            e.printStackTrace();
            return;
        }
        if (readCount == -1) {
            log.debug("read -1");
            closeChannelInput(channel);
        } else {
            inBuffer.flip();
            if (channel.getType().equals("local")) {
                handleLocalIn(channel);
            } else {
                handleRemoteIn(channel);
            }
            System.currentTimeMillis();
        }
    }

    @Override
    public void run() {
        try {
            selector = Selector.open();
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(configDto.getClient(), configDto.getClientPort()));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            while (selector.select() > 0) {
                log.debug("handle select");
                for (Iterator it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                    SelectionKey key = (SelectionKey) it.next();
                    it.remove();
                    if (key.isValid()) {
                        try {
                            if (key.isValid()&&key.isAcceptable()) {
                                doAccept((ServerSocketChannel) key.channel());
                            }
                            if (key.isValid()&&key.isReadable()) {
                                doRead((SocketChannel) key.channel());
                            }
                            if (key.isValid()&&key.isConnectable()) {
                                doConnect((SocketChannel) key.channel());
                            }
                            if (key.isValid()&&key.isWritable()) {
                                doWrite((SocketChannel) key.channel());
                            }
                        } catch (Exception e) {
                            log.warn(e.getMessage(), e);
                            Channel channel=getChannelFromSocketChannel((SocketChannel) key.channel());
                            if (channel!=null&&channel.getPipe() != null) {
                                closePipe(channel.getPipe());
                            }
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
