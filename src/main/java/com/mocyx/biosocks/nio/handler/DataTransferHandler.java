package com.mocyx.biosocks.nio.handler;


import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Administrator
 */
@Slf4j
public class DataTransferHandler extends ChannelInboundHandlerAdapter {

    private SocketChannel remoteChannel;

    public DataTransferHandler(SocketChannel channel) {
        this.remoteChannel = channel;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            log.debug("transfer data {}", buf.readableBytes());
        }
        remoteChannel.writeAndFlush(msg);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.debug("客户端断开连接");
        remoteChannel.close();
    }
}