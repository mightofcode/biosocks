package com.mocyx.biosocks.nio;

import com.alibaba.fastjson.JSON;
import com.mocyx.biosocks.util.ConfigDto;
import com.mocyx.biosocks.protocol.TunnelMsgType;
import com.mocyx.biosocks.protocol.TunnelProtocol.TunnelRequest;
import com.mocyx.biosocks.protocol.TunnelProtocol.TunnelResponse;
import com.mocyx.biosocks.util.EncodeUtil;
import com.mocyx.biosocks.util.ObjAttrUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Slf4j
public class NioServer implements Runnable {

    private Selector selector;

    ConfigDto configDto;

    public NioServer(ConfigDto configDto) {
        this.configDto = configDto;
    }


    ObjAttrUtil objAttrUtil=new ObjAttrUtil();

    @Getter
    @Setter
    static class Pipe {
        private Channel localChannel;
        private Channel remoteChannel;
        //
        private InetSocketAddress targetAddr;
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

    private Channel getChannelFromSocketChannel(SocketChannel socketChannel){
        if(socketChannel==null){
            return null;
        }
        Channel channel= (Channel) objAttrUtil.getAttr(socketChannel, "channel");
        if(channel==null){
            return null;
        }
        Pipe pipe=channel.getPipe();
        if(pipe!=null){
            pipe.lastActiveTime=System.currentTimeMillis();
        }
        return channel;
    }

    List<Pipe> pipes=new ArrayList<>();
    private void doAccept(ServerSocketChannel serverChannel) throws IOException {
        SocketChannel channel = serverChannel.accept();
        log.info("accept {} {} {}",channel.getLocalAddress(),channel.getRemoteAddress(),pipes.size());
        channel.configureBlocking(false);
        SelectionKey key = channel.register(selector, SelectionKey.OP_READ);
        Pipe clientPipe = new Pipe();
        pipes.add(clientPipe);
        Channel c=new Channel();
        c.setSocketChannel(channel);
        c.setType("local");
        c.setPipe(clientPipe);
        c.setKey(key);
        clientPipe.setLocalChannel(c);
        objAttrUtil.setAttr(channel, "channel", c);
    }

    private void tryDisableOutput(Channel channel){
        if(channel!=null&&channel.getSocketChannel()!=null&&channel.getOutBuffer().position()==0){
            try {
                if(channel.getSocketChannel().isConnected()){
                    channel.getSocketChannel().shutdownOutput();
                }
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
    private void closePipe(Pipe pipe) {
        if(pipe==null){
            return;
        }
        if(pipe.getLocalChannel()!=null){
            objAttrUtil.delObj(pipe.getLocalChannel().getSocketChannel());
        }
        if(pipe.getRemoteChannel()!=null){
            objAttrUtil.delObj(pipe.getRemoteChannel().getSocketChannel());
        }
        log.info("close {} idle {}", pipe.getTargetAddr(),System.currentTimeMillis()-pipe.lastActiveTime);
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

    private void doRead(SocketChannel socketChannel) throws Exception{
        //
        Channel channel=getChannelFromSocketChannel(socketChannel);
        Pipe pipe=channel.getPipe();
        ByteBuffer inBuffer = channel.getInBuffer();
        inBuffer.clear();
        //
        if (inBuffer.remaining() <= 0) {
            log.info("buffer full");
            throw new RuntimeException("buffer full");
        }
        int readCount = 0;
        try {
            readCount = socketChannel.read(inBuffer);
            log.debug("readCount {}", readCount);
        } catch (IOException e) {
            log.error("read error {} {}",channel.getSocketChannel().getRemoteAddress(),e.getMessage(),e);
            closePipe(pipe);
            return;
        }
        if (readCount == -1) {
            log.debug("read -1");
            closeChannelInput(channel);
        } else {
            inBuffer.flip();
            if (channel.getType().equals("local")) {
                handleLocalIn(pipe.getLocalChannel());
            } else {
                handleRemoteIn(pipe.getRemoteChannel());
            }
            System.currentTimeMillis();
        }
    }

    private void handleLocalConnectRequest(Channel channel,TunnelRequest request)throws Exception{
        if(request!=null&&request.getType()==TunnelMsgType.REQ_CONNECT_DOMAIN.getV()){
            //connect to remote
            SocketChannel remote = SocketChannel.open();
            remote.configureBlocking(false);
            Channel remoteChannel=new Channel();
            remoteChannel.setType("remote");
            remoteChannel.setSocketChannel(remote);
            remoteChannel.setPipe(channel.getPipe());
            remoteChannel.setSocketChannel(remote);
            channel.getPipe().setRemoteChannel(remoteChannel);
            //
            if (StringUtils.isEmpty(request.getDomain())) {
                log.warn("host is empty {}", JSON.toJSONString(request));
                closePipe(channel.getPipe());
                return;
            }
            //
            InetSocketAddress targetAddr = new InetSocketAddress(request.getDomain(),
                    request.getPort());
            channel.getPipe().setTargetAddr(targetAddr);
            objAttrUtil.setAttr(remote, "channel", remoteChannel);
            SelectionKey key = remote.register(selector, SelectionKey.OP_CONNECT);
            remoteChannel.setKey(key);
            try {
                boolean b1 = remote.connect(targetAddr);
            }catch (Exception e){
                TunnelResponse response = new TunnelResponse();
                response.setType((short) TunnelMsgType.RES_CONNECT_FAIL.getV());
                response.write(channel.getOutBuffer());
                channel.getOutBuffer().flip();
                tryFlushWrite(channel);
            }
        }
    }
    @SneakyThrows
    private void handleLocalIn(Channel channel) {
        if(channel.getPipe().getRemoteChannel()==null){
            TunnelRequest request= TunnelRequest.tryRead(channel.getInBuffer());
            handleLocalConnectRequest(channel,request);
        }else {
            //
            System.currentTimeMillis();
            //
            Channel remote=channel.getPipe().otherChannel(channel);
            if(remote!=null){
                ByteBuffer buffer = channel.getInBuffer();
                byte[] data = new byte[buffer.remaining()];
                buffer.get(data);
                EncodeUtil.simpleXorEncrypt(data, 0, data.length);
                remote.getOutBuffer().put(data);
                remote.getOutBuffer().flip();
                tryFlushWrite(remote);
            }
        }
    }

    @SneakyThrows
    private void handleRemoteIn(Channel remote) {
        Channel local=remote.getPipe().otherChannel(remote);
        if(local!=null){
            ByteBuffer buffer = remote.getInBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            EncodeUtil.simpleXorEncrypt(data, 0, data.length);
            local.getOutBuffer().put(data);
            local.getOutBuffer().flip();
            tryFlushWrite(local);
        }
        System.currentTimeMillis();
    }

    private void doConnect(SocketChannel socketChannel) {
        Channel channel=getChannelFromSocketChannel(socketChannel);
        String type = channel.getType();
        Pipe pipe = channel.getPipe();
        Channel other=pipe.otherChannel(channel);
        if (type.equals("remote")) {
            try {
                boolean b1 = socketChannel.finishConnect();
                System.currentTimeMillis();
            } catch (IOException e) {
                log.warn("connect cloud fail {}",pipe.getTargetAddr(),e);
                closePipe(pipe);
                return;
            }
            log.info("connect {}", pipe.targetAddr);
            channel.getKey().interestOps(SelectionKey.OP_READ);
            TunnelResponse request = new TunnelResponse();
            request.setType((short) TunnelMsgType.RES_CONNECT_SUCCESS.getV());
            request.write(other.getOutBuffer());
            other.getOutBuffer().flip();
            tryFlushWrite(other);
            //
            System.currentTimeMillis();
        }
    }

    @SneakyThrows
    private boolean directFlushWrite(Channel channel){

        ByteBuffer buffer=channel.getOutBuffer();
        Pipe pipe=channel.getPipe();
        while (buffer.hasRemaining()) {
            //test it with https://www.youtube.com/watch?v=03-ge__OaIU&list=RDMM&index=11
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
        //
        buffer.clear();
        return true;
    }
    @SneakyThrows
    private boolean tryFlushWrite(Channel channel) {

        if((channel.getKey().interestOps()&SelectionKey.OP_WRITE)!=0){
            return false;
        }
        return  directFlushWrite(channel);

    }

    private void doWrite(SocketChannel socketChannel) throws IOException {
        Channel channel=getChannelFromSocketChannel(socketChannel);
        log.debug("do write");
        boolean flushed = directFlushWrite(channel);
        if (flushed) {
            Channel other = channel.getPipe().otherChannel(channel);
            channel.getKey().interestOps(SelectionKey.OP_READ);
            other.getKey().interestOps(SelectionKey.OP_READ);
        }
    }

    private void deleteIdlePipe(){
        Iterator<Pipe> pipeIterator= pipes.iterator();
        while (pipeIterator.hasNext()){
            Pipe pipe=pipeIterator.next();
            if(System.currentTimeMillis()-pipe.lastActiveTime>1000*120){
                closePipe(pipe);
                pipeIterator.remove();
            }
        }
    }
    @Override
    public void run() {

        try {
            selector = Selector.open();
            //
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(configDto.getServer(), configDto.getServerPort()));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            //
            long tick=0;
            while (selector.select() > 0) {
                log.debug("handle select");
                tick+=1;
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
                                System.currentTimeMillis();
                            }
                            if (key.isValid()&&key.isWritable()) {
                                doWrite((SocketChannel) key.channel());
                                System.currentTimeMillis();
                            }
                        } catch (NullPointerException e) {
                            log.error(e.getMessage(), e);
                            Channel channel=getChannelFromSocketChannel((SocketChannel) key.channel());
                            if (channel!=null&&channel.getPipe() != null) {
                                closePipe(channel.getPipe());
                            }
                        }catch (Exception e) {
                            log.warn(e.getMessage(), e);
                            Channel channel=getChannelFromSocketChannel((SocketChannel) key.channel());
                            if (channel!=null&&channel.getPipe() != null) {
                                closePipe(channel.getPipe());
                            }
                        }
                    }
                }
                if(tick%100==0){
                    deleteIdlePipe();
                }
            }
            serverChannel.close();
        }catch (Exception e){
            log.error(e.getMessage(),e);
        }
    }
}
