package com.mocyx.biosocks.nio.tunnel;


import com.alibaba.fastjson.JSON;
import com.mocyx.biosocks.TunnelMsgType;
import com.mocyx.biosocks.nio.NioUtil;
import com.mocyx.biosocks.nio.handler.DataTransferHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Administrator
 */
@Slf4j
public class TunnelRequestHandler extends SimpleChannelInboundHandler<TunnelRequest> {

    private EventLoopGroup bossGroup;

    public TunnelRequestHandler(EventLoopGroup bossGroup) {
        this.bossGroup = bossGroup;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TunnelRequest msg) throws Exception {

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(bossGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        //ch.pipeline().addLast(new LoggingHandler());//in out
                        //将目标服务器信息转发给客户端
                        ch.pipeline().addLast(new DataTransferHandler((SocketChannel) ctx.channel()));
                    }
                });
        log.info("tunnel request {} {}", ctx.channel().remoteAddress(), JSON.toJSONString(msg));
        bootstrap.connect(msg.domain, msg.port).addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                log.info("remote connect {} {}", future.isSuccess(), future.channel().remoteAddress());
                if (future.isSuccess()) {
                    SocketChannel socketChannel = (SocketChannel) future.channel();
                    socketChannel.attr(NioUtil.TUNNEL_KEY).set(ctx.channel().attr(NioUtil.TUNNEL_KEY).get());
                    ctx.channel().attr(NioUtil.TUNNEL_KEY).get().setRemote(socketChannel);

                    TunnelResponse response = new TunnelResponse();
                    response.setType((short) TunnelMsgType.RES_CONNECT_SUCCESS.getV());
                    ctx.channel().writeAndFlush(response).sync();

                    ctx.channel().pipeline().addLast(new DataTransferHandler(socketChannel));
                    socketChannel.pipeline().addLast(new DataTransferHandler((SocketChannel) ctx.channel()));
                    ctx.channel().pipeline().remove(TunnelMsgDecoder.class);
                    ctx.channel().pipeline().remove(TunnelMsgEncoder.class);
                    ctx.channel().pipeline().remove(TunnelRequestHandler.class);

                } else {
                    TunnelResponse response = new TunnelResponse();
                    response.setType((short) TunnelMsgType.RES_CONNECT_FAIL.getV());
                    ctx.channel().writeAndFlush(response).sync();
                    ctx.channel().close();
                }
            }
        });

    }
}
