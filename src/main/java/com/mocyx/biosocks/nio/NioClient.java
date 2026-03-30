package com.mocyx.biosocks.nio;

import com.alibaba.fastjson.JSON;
import com.mocyx.biosocks.protocol.SocksProtocol.Socks5State;
import com.mocyx.biosocks.protocol.SocksProtocol.SocksConnectRequestDto;
import com.mocyx.biosocks.protocol.SocksProtocol.SocksConnectResponseDto;
import com.mocyx.biosocks.protocol.SocksProtocol.SocksShakeRequestDto;
import com.mocyx.biosocks.protocol.TunnelMsgType;
import com.mocyx.biosocks.protocol.TunnelProtocol.TunnelRequest;
import com.mocyx.biosocks.protocol.TunnelProtocol.TunnelResponse;
import com.mocyx.biosocks.util.ConfigDto;
import com.mocyx.biosocks.util.EncodeUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class NioClient implements Runnable {

    private static final int IN_BUFFER_SIZE = 4 * 1024;
    private static final int OUT_BUFFER_SIZE = 32 * 1024;
    private static final int BACKPRESSURE_THRESHOLD_NUMERATOR = 3;
    private static final int BACKPRESSURE_THRESHOLD_DENOMINATOR = 4;

    private final ConfigDto configDto;
    private final Set<Pipe> pipeList = new HashSet<>();
    private Selector selector;

    public AtomicBoolean closeFlag = new AtomicBoolean(false);

    public NioClient(ConfigDto configDto) {
        this.configDto = configDto;
    }

    @Getter
    @Setter
    static class Channel {
        private String type;
        private Pipe pipe;
        private SelectionKey key;
        private SocketChannel socketChannel;
        private ByteBuffer inBuffer = ByteBuffer.allocate(IN_BUFFER_SIZE);
        private ByteBuffer outBuffer = ByteBuffer.allocate(OUT_BUFFER_SIZE);
        private boolean inputClosed;
        private boolean outputShutdownPending;
    }

    @Getter
    @Setter
    static class Pipe {
        private Channel localChannel;
        private Channel remoteChannel;
        private InetSocketAddress targetAddr;
        private Socks5State state = Socks5State.shake;
        private long lastActiveTime = System.currentTimeMillis();
        private boolean closed;

        private Channel otherChannel(Channel channel) {
            if (channel == localChannel) {
                return remoteChannel;
            }
            return localChannel;
        }
    }

    private void touch(Channel channel) {
        if (channel != null && channel.getPipe() != null) {
            channel.getPipe().setLastActiveTime(System.currentTimeMillis());
        }
    }

    private Channel getChannel(SelectionKey key) {
        if (key == null) {
            return null;
        }
        Channel channel = (Channel) key.attachment();
        touch(channel);
        return channel;
    }

    private void doAccept(ServerSocketChannel serverChannel) throws IOException {
        SocketChannel socketChannel = serverChannel.accept();
        if (socketChannel == null) {
            return;
        }
        socketChannel.configureBlocking(false);

        Pipe pipe = new Pipe();
        pipeList.add(pipe);

        Channel channel = new Channel();
        channel.setType("local");
        channel.setPipe(pipe);
        channel.setSocketChannel(socketChannel);

        SelectionKey key = socketChannel.register(selector, SelectionKey.OP_READ, channel);
        channel.setKey(key);
        pipe.setLocalChannel(channel);
    }

    private void setReadInterest(Channel channel, boolean enabled) {
        if (channel == null || channel.getKey() == null || !channel.getKey().isValid()) {
            return;
        }
        int ops = channel.getKey().interestOps();
        if ((ops & SelectionKey.OP_CONNECT) != 0) {
            return;
        }
        if (enabled && !channel.isInputClosed()) {
            channel.getKey().interestOps(ops | SelectionKey.OP_READ);
        } else {
            channel.getKey().interestOps(ops & ~SelectionKey.OP_READ);
        }
    }

    private void setWriteInterest(Channel channel, boolean enabled) {
        if (channel == null || channel.getKey() == null || !channel.getKey().isValid()) {
            return;
        }
        int ops = channel.getKey().interestOps();
        if (enabled) {
            channel.getKey().interestOps(ops | SelectionKey.OP_WRITE);
        } else {
            channel.getKey().interestOps(ops & ~SelectionKey.OP_WRITE);
        }
    }

    private void ensureOutCapacity(Channel channel, int additionalBytes) {
        ByteBuffer buffer = channel.getOutBuffer();
        if (buffer.remaining() >= additionalBytes) {
            return;
        }

        int required = buffer.position() + additionalBytes;
        int newCapacity = buffer.capacity();
        while (newCapacity < required) {
            newCapacity <<= 1;
        }

        ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
        buffer.flip();
        newBuffer.put(buffer);
        channel.setOutBuffer(newBuffer);
    }

    private void updatePeerReadInterest(Channel destination) {
        if (destination == null || destination.getPipe() == null) {
            return;
        }
        Channel source = destination.getPipe().otherChannel(destination);
        if (source == null) {
            return;
        }
        int threshold = destination.getOutBuffer().capacity() * BACKPRESSURE_THRESHOLD_NUMERATOR
                / BACKPRESSURE_THRESHOLD_DENOMINATOR;
        setReadInterest(source, destination.getOutBuffer().position() < threshold);
    }

    @SneakyThrows
    private boolean flushPendingWrites(Channel channel) {
        if (channel == null || channel.getSocketChannel() == null) {
            return true;
        }

        ByteBuffer buffer = channel.getOutBuffer();
        buffer.flip();
        while (buffer.hasRemaining()) {
            int written = channel.getSocketChannel().write(buffer);
            if (written < 0) {
                buffer.compact();
                closePipe(channel.getPipe());
                return false;
            }
            if (written == 0) {
                break;
            }
        }
        buffer.compact();

        boolean pending = buffer.position() > 0;
        setWriteInterest(channel, pending);
        updatePeerReadInterest(channel);

        if (!pending && channel.isOutputShutdownPending()) {
            try {
                if (channel.getSocketChannel().isConnected()) {
                    channel.getSocketChannel().shutdownOutput();
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
            channel.setOutputShutdownPending(false);
            tryClosePipe(channel.getPipe());
        }
        return !pending;
    }

    @SneakyThrows
    private void appendAndFlush(Channel destination, ByteBuffer sourceBuffer) {
        if (destination == null || sourceBuffer == null || !sourceBuffer.hasRemaining()) {
            return;
        }
        ensureOutCapacity(destination, sourceBuffer.remaining());
        destination.getOutBuffer().put(sourceBuffer);
        updatePeerReadInterest(destination);
        flushPendingWrites(destination);
    }

    private void transferToPeer(Channel source, Channel destination) {
        if (destination == null) {
            closePipe(source.getPipe());
            return;
        }
        ByteBuffer buffer = source.getInBuffer();
        if (!buffer.hasRemaining()) {
            return;
        }
        EncodeUtil.simpleXorEncrypt(buffer.array(), buffer.position(), buffer.remaining());
        appendAndFlush(destination, buffer);
    }

    private void finishRemoteConnect(Channel remote) {
        if (remote == null || remote.getPipe() == null || remote.getPipe().isClosed()) {
            return;
        }
        Pipe pipe = remote.getPipe();
        log.info("connect {}", pipe.getTargetAddr());

        if (remote.getKey() != null && remote.getKey().isValid()) {
            remote.getKey().interestOps(SelectionKey.OP_READ);
        }

        TunnelRequest request = new TunnelRequest();
        request.setDomain(pipe.getTargetAddr().getHostString());
        request.setType((short) TunnelMsgType.REQ_CONNECT_DOMAIN.getV());
        request.setPort(pipe.getTargetAddr().getPort());

        ensureOutCapacity(remote, 16 + request.getDomain().length() * 4);
        request.write(remote.getOutBuffer());
        flushPendingWrites(remote);
    }

    @SneakyThrows
    private void handleLocalIn(Channel local) {
        Pipe pipe = local.getPipe();

        if (pipe.getState() == Socks5State.shake) {
            SocksShakeRequestDto requestDto = SocksShakeRequestDto.tryRead(local.getInBuffer());
            if (requestDto == null) {
                return;
            }
            ensureOutCapacity(local, 2);
            local.getOutBuffer().put((byte) 0x05);
            local.getOutBuffer().put((byte) 0x00);
            flushPendingWrites(local);
            pipe.setState(Socks5State.connect);
            return;
        }

        if (pipe.getState() == Socks5State.connect) {
            SocksConnectRequestDto connectRequestDto = SocksConnectRequestDto.tryRead(local.getInBuffer());
            if (connectRequestDto == null) {
                return;
            }

            String host = null;
            if (!StringUtils.isEmpty(connectRequestDto.getDomain())) {
                host = connectRequestDto.getDomain();
            } else if (connectRequestDto.getAddr() != null) {
                host = connectRequestDto.getAddr().getHostAddress();
            }
            if (StringUtils.isEmpty(host)) {
                log.warn("host is empty {}", JSON.toJSONString(connectRequestDto));
                closePipe(pipe);
                return;
            }

            pipe.setTargetAddr(new InetSocketAddress(host, connectRequestDto.getPort()));

            SocketChannel remoteSocket = SocketChannel.open();
            remoteSocket.configureBlocking(false);

            Channel remoteChannel = new Channel();
            remoteChannel.setType("remote");
            remoteChannel.setPipe(pipe);
            remoteChannel.setSocketChannel(remoteSocket);

            SelectionKey key = remoteSocket.register(selector, SelectionKey.OP_CONNECT, remoteChannel);
            remoteChannel.setKey(key);
            pipe.setRemoteChannel(remoteChannel);

            InetSocketAddress address = new InetSocketAddress(configDto.getServer(), configDto.getServerPort());
            if (remoteSocket.connect(address)) {
                finishRemoteConnect(remoteChannel);
            }
            return;
        }

        if (pipe.getState() == Socks5State.transfer) {
            transferToPeer(local, pipe.otherChannel(local));
        }
    }

    @SneakyThrows
    private void handleRemoteIn(Channel remote) {
        Pipe pipe = remote.getPipe();
        ByteBuffer buffer = remote.getInBuffer();

        if (pipe.getState() == Socks5State.connect) {
            TunnelResponse response = TunnelResponse.tryRead(buffer);
            if (response == null) {
                return;
            }

            Channel local = pipe.otherChannel(remote);
            SocksConnectResponseDto res = new SocksConnectResponseDto();
            res.setVer((byte) 0x05);
            res.setCmd(response.getType() == TunnelMsgType.RES_CONNECT_SUCCESS.getV() ? (byte) 0x00 : (byte) 0x04);
            res.setRsv((byte) 0x00);
            res.setAtyp((byte) 0x01);
            res.setAddr(Inet4Address.getByName("0.0.0.0"));
            res.setPort((short) 0x0843);

            ensureOutCapacity(local, 32);
            res.write(local.getOutBuffer());
            flushPendingWrites(local);

            if (response.getType() == TunnelMsgType.RES_CONNECT_SUCCESS.getV()) {
                pipe.setState(Socks5State.transfer);
            } else {
                log.warn("cloud connect remote fail");
            }
            return;
        }

        if (pipe.getState() == Socks5State.transfer) {
            transferToPeer(remote, pipe.otherChannel(remote));
        }
    }

    private void doConnect(SelectionKey key) {
        Channel remote = getChannel(key);
        if (remote == null) {
            return;
        }
        try {
            if (!remote.getSocketChannel().finishConnect()) {
                return;
            }
        } catch (IOException e) {
            log.warn("connect cloud fail", e);
            closePipe(remote.getPipe());
            return;
        }
        finishRemoteConnect(remote);
    }

    private void doWrite(SelectionKey key) throws IOException {
        Channel channel = getChannel(key);
        if (channel == null) {
            return;
        }
        flushPendingWrites(channel);
    }

    private void tryClosePipe(Pipe pipe) {
        if (pipe == null || pipe.isClosed()) {
            return;
        }
        Channel local = pipe.getLocalChannel();
        Channel remote = pipe.getRemoteChannel();
        if (local != null && remote != null
                && local.isInputClosed()
                && remote.isInputClosed()
                && local.getOutBuffer().position() == 0
                && remote.getOutBuffer().position() == 0
                && !local.isOutputShutdownPending()
                && !remote.isOutputShutdownPending()) {
            closePipe(pipe);
        }
    }

    private void closePipe(Pipe pipe) {
        if (pipe == null || pipe.isClosed()) {
            return;
        }
        pipe.setClosed(true);
        pipeList.remove(pipe);
        log.info("close {}", pipe.getTargetAddr());
        closeChannel(pipe.getLocalChannel());
        closeChannel(pipe.getRemoteChannel());
    }

    private void closeChannel(Channel channel) {
        if (channel == null) {
            return;
        }
        try {
            if (channel.getKey() != null) {
                channel.getKey().cancel();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        try {
            if (channel.getSocketChannel() != null) {
                channel.getSocketChannel().close();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void closeChannelInput(Channel channel) {
        if (channel == null) {
            return;
        }
        channel.setInputClosed(true);
        try {
            if (channel.getSocketChannel() != null) {
                channel.getSocketChannel().shutdownInput();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        setReadInterest(channel, false);

        Pipe pipe = channel.getPipe();
        if (pipe == null) {
            return;
        }
        Channel other = pipe.otherChannel(channel);
        if (other != null && other.getSocketChannel() != null) {
            if (other.getOutBuffer().position() == 0) {
                try {
                    if (other.getSocketChannel().isConnected()) {
                        other.getSocketChannel().shutdownOutput();
                    }
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            } else {
                other.setOutputShutdownPending(true);
                setWriteInterest(other, true);
            }
        }
        tryClosePipe(pipe);
    }

    private void doRead(SelectionKey key) {
        Channel channel = getChannel(key);
        if (channel == null) {
            return;
        }
        Pipe pipe = channel.getPipe();
        ByteBuffer inBuffer = channel.getInBuffer();
        inBuffer.clear();

        int readCount;
        try {
            readCount = channel.getSocketChannel().read(inBuffer);
        } catch (IOException e) {
            closePipe(pipe);
            log.warn(e.getMessage(), e);
            return;
        }

        if (readCount == -1) {
            closeChannelInput(channel);
            return;
        }
        if (readCount == 0) {
            return;
        }

        inBuffer.flip();
        if ("local".equals(channel.getType())) {
            handleLocalIn(channel);
        } else {
            handleRemoteIn(channel);
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

            while (true) {
                int selectCount = selector.select(500);
                if (selectCount > 0) {
                    for (Iterator<SelectionKey> it = selector.selectedKeys().iterator(); it.hasNext(); ) {
                        SelectionKey key = it.next();
                        it.remove();
                        if (!key.isValid()) {
                            continue;
                        }
                        try {
                            if (key.isAcceptable()) {
                                doAccept((ServerSocketChannel) key.channel());
                            }
                            if (key.isReadable()) {
                                doRead(key);
                            }
                            if (key.isConnectable()) {
                                doConnect(key);
                            }
                            if (key.isWritable()) {
                                doWrite(key);
                            }
                        } catch (Exception e) {
                            log.warn(e.getMessage(), e);
                            Channel channel = (Channel) key.attachment();
                            if (channel != null) {
                                closePipe(channel.getPipe());
                            }
                        }
                    }
                }
                if (closeFlag.get()) {
                    break;
                }
            }

            serverChannel.close();
            selector.close();

            for (Pipe pipe : new ArrayList<>(pipeList)) {
                closePipe(pipe);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
