package com.mocyx.mocnetty.channel;

import com.mocyx.mocnetty.event.MocEventExecutor;
import com.mocyx.mocnetty.handler.MocChannelHandler;
import com.sun.xml.internal.ws.server.ServerSchemaValidationTube;
import lombok.Getter;
import lombok.Setter;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Administrator
 */
public class MocChannel {

    @Getter
    private MocEventExecutor eventExecutor;
//
//    private SocketChannel socketChannel;
//    private ServerSocketChannel serverSocketChannel;

    private SelectableChannel selectableChannel;

    private SelectionKey selectionKey;

    private MocChannelPromise closeFuture;
    private MocChannelPromise connectPromise;

    private List<ByteBuffer> writeBuffers = new ArrayList<>();

    private List<MocChannelHandler> pipeline = new ArrayList<>();

    public MocChannel(MocEventExecutor eventExecutor, boolean server) {
        try {
            if (server) {
                selectableChannel = ServerSocketChannel.open();
            } else {
                selectableChannel = SocketChannel.open();
            }

            selectableChannel.configureBlocking(false);
            this.eventExecutor = eventExecutor;
            this.selectionKey = (SelectionKey) this.eventExecutor.register(this);
            closeFuture = new MocDefaultChannelPromise(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public ByteBuffer getWriteBuffer() {
        if (writeBuffers.size() == 0) {
            return null;
        } else {
            return writeBuffers.get(0);
        }
    }

    public void removeFirstWriteBuffer() {
        if (writeBuffers.size() == 0) {
            throw new RuntimeException();
        } else {
            writeBuffers.remove(0);
        }
    }

    public SocketChannel socketChannel(){
        return (SocketChannel)selectableChannel;
    }
    public ServerSocketChannel serverSocketChannel(){
        return (ServerSocketChannel)selectableChannel;
    }
    public void bind(InetSocketAddress socketAddress) {
        try {
            socketChannel().bind(socketAddress);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public MocChannelFuture closeFuture() {
        return this.closeFuture;
    }

    public MocChannelFuture connectPromise() {
        return this.connectPromise;
    }
    public void onWrite() {

        System.currentTimeMillis();
    }
    public void onRead() {

        System.currentTimeMillis();
    }



    public void connect(InetSocketAddress remote, MocChannelPromise promise) {
        try {
            boolean success = socketChannel().connect(remote);
            if (success) {
                selectionKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_ACCEPT);
                promise.setSuccess(null);
            } else {
                selectionKey.interestOps(SelectionKey.OP_CONNECT);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
