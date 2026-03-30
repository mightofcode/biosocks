package com.mocyx.biosocks.nio;

import com.alibaba.fastjson.JSON;
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

@Slf4j
public class NioServer implements Runnable {

    private static final int IN_BUFFER_SIZE = 4 * 1024;
    private static final int OUT_BUFFER_SIZE = 32 * 1024;
    private static final int BACKPRESSURE_THRESHOLD_NUMERATOR = 3;
    private static final int BACKPRESSURE_THRESHOLD_DENOMINATOR = 4;
    private static final long IDLE_TIMEOUT_MS = 120_000L;

    private final ConfigDto configDto;
    private final Set<Pipe> pipes = new HashSet<>();
    private Selector selector;

    public NioServer(ConfigDto configDto) {
        this.configDto = configDto;
    }

    @Getter
    @Setter
    static class Pipe {
        private Channel localChannel;
        private Channel remoteChannel;
        private InetSocketAddress targetAddr;
        private long lastActiveTime = System.currentTimeMillis();
        private boolean closed;

        private Channel otherChannel(Channel channel) {
            if (channel == localChannel) {
                return remoteChannel;
            }
            return localChannel;
        }
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
        log.info("accept {} {} {}", socketChannel.getLocalAddress(), socketChannel.getRemoteAddress(), pipes.size());
        socketChannel.configureBlocking(false);

        Pipe pipe = new Pipe();
        pipes.add(pipe);

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

    private void finishRemoteConnect(Channel channel) {
        if (channel == null || channel.getPipe() == null || channel.getPipe().isClosed()) {
            return;
        }
        Pipe pipe = channel.getPipe();
        Channel other = pipe.otherChannel(channel);

        log.info("connect {}", pipe.getTargetAddr());

        if (channel.getKey() != null && channel.getKey().isValid()) {
            channel.getKey().interestOps(SelectionKey.OP_READ);
        }

        TunnelResponse response = new TunnelResponse();
        response.setType((short) TunnelMsgType.RES_CONNECT_SUCCESS.getV());
        ensureOutCapacity(other, 8);
        response.write(other.getOutBuffer());
        flushPendingWrites(other);
    }

    private void handleLocalConnectRequest(Channel channel, TunnelRequest request) throws IOException {
        if (request == null) {
            return;
        }
        if (request.getType() != TunnelMsgType.REQ_CONNECT_DOMAIN.getV()) {
            closePipe(channel.getPipe());
            return;
        }

        if (StringUtils.isEmpty(request.getDomain())) {
            log.warn("host is empty {}", JSON.toJSONString(request));
            closePipe(channel.getPipe());
            return;
        }

        SocketChannel remote = SocketChannel.open();
        remote.configureBlocking(false);

        Channel remoteChannel = new Channel();
        remoteChannel.setType("remote");
        remoteChannel.setPipe(channel.getPipe());
        remoteChannel.setSocketChannel(remote);
        channel.getPipe().setRemoteChannel(remoteChannel);

        InetSocketAddress targetAddr = new InetSocketAddress(request.getDomain(), request.getPort());
        channel.getPipe().setTargetAddr(targetAddr);

        SelectionKey key = remote.register(selector, SelectionKey.OP_CONNECT, remoteChannel);
        remoteChannel.setKey(key);

        try {
            if (remote.connect(targetAddr)) {
                finishRemoteConnect(remoteChannel);
            }
        } catch (Exception e) {
            TunnelResponse response = new TunnelResponse();
            response.setType((short) TunnelMsgType.RES_CONNECT_FAIL.getV());
            ensureOutCapacity(channel, 8);
            response.write(channel.getOutBuffer());
            flushPendingWrites(channel);
            closePipe(channel.getPipe());
        }
    }

    @SneakyThrows
    private void handleLocalIn(Channel channel) {
        if (channel.getPipe().getRemoteChannel() == null) {
            TunnelRequest request = TunnelRequest.tryRead(channel.getInBuffer());
            handleLocalConnectRequest(channel, request);
            return;
        }
        transferToPeer(channel, channel.getPipe().otherChannel(channel));
    }

    @SneakyThrows
    private void handleRemoteIn(Channel remote) {
        transferToPeer(remote, remote.getPipe().otherChannel(remote));
    }

    private void doConnect(SelectionKey key) {
        Channel channel = getChannel(key);
        if (channel == null) {
            return;
        }
        try {
            if (!channel.getSocketChannel().finishConnect()) {
                return;
            }
        } catch (IOException e) {
            log.warn("connect cloud fail {}", channel.getPipe().getTargetAddr(), e);
            closePipe(channel.getPipe());
            return;
        }
        finishRemoteConnect(channel);
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
        pipes.remove(pipe);
        log.info("close {} idle {}", pipe.getTargetAddr(), System.currentTimeMillis() - pipe.getLastActiveTime());
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

        ByteBuffer inBuffer = channel.getInBuffer();
        inBuffer.clear();

        int readCount;
        try {
            readCount = channel.getSocketChannel().read(inBuffer);
        } catch (IOException e) {
            log.error("read error {}", e.getMessage(), e);
            closePipe(channel.getPipe());
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

    private void deleteIdlePipe() {
        long now = System.currentTimeMillis();
        for (Pipe pipe : new ArrayList<>(pipes)) {
            if (now - pipe.getLastActiveTime() > IDLE_TIMEOUT_MS) {
                closePipe(pipe);
            }
        }
    }

    @Override
    public void run() {
        try {
            selector = Selector.open();
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(configDto.getServer(), configDto.getServerPort()));
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);

            long tick = 0;
            while (true) {
                int ready = selector.select();
                if (ready == 0) {
                    continue;
                }
                tick += 1;
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
                if (tick % 100 == 0) {
                    deleteIdlePipe();
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }
}
