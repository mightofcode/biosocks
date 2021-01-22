package com.mocyx.biosocks.nio.handler;


import com.mocyx.biosocks.ConfigDto;
import com.mocyx.biosocks.bio.protocol.TunnelMsgType;
import com.mocyx.biosocks.nio.NioUtil;
import com.mocyx.biosocks.nio.TunnelDto;
import com.mocyx.biosocks.nio.tunnel.*;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandRequest;
import io.netty.handler.codec.socksx.v5.Socks5CommandType;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Administrator
 */
@Slf4j
public class Socks5CommandRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5CommandRequest> {

    EventLoopGroup bossGroup;

    private ConfigDto configDto;
    public Socks5CommandRequestHandler(EventLoopGroup bossGroup,ConfigDto configDto) {
        this.bossGroup = bossGroup;
        this.configDto=configDto;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5CommandRequest msg) throws Exception {
        if (msg.type().equals(Socks5CommandType.CONNECT)) {
            System.currentTimeMillis();

            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(bossGroup)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            //ch.pipeline().addLast(new LoggingHandler());//in out
                            //将目标服务器信息转发给客户端
                            ch.pipeline().addLast(new TunnelMsgDecoder());
                            ch.pipeline().addLast(new TunnelResponseHandler());
                            ch.pipeline().addLast(new TunnelMsgEncoder());

                        }
                    });
            TunnelDto tunnelDto = ctx.channel().attr(NioUtil.TUNNEL_KEY).get();
            tunnelDto.setRemoteAddr(msg.dstAddr());
            tunnelDto.setRemotePort(msg.dstPort());

            log.debug("连接目标服务器");
            ChannelFuture future = bootstrap.connect(this.configDto.getServer(), this.configDto.getServerPort());
            future.addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture future) throws Exception {
                    if (future.isSuccess()) {
                        log.info("connect tunnel success");
                        TunnelRequest request = new TunnelRequest();
                        request.setType((short) TunnelMsgType.REQ_CONNECT_DOMAIN.getV());
                        request.setDomain(msg.dstAddr());
                        request.setPort(msg.dstPort());
                        future.channel().writeAndFlush(request).sync();

                        SocketChannel socketChannel = (SocketChannel) future.channel();

                        ctx.channel().pipeline().addLast(new DataTransferHandler(socketChannel));
                        socketChannel.pipeline().addLast(new DataTransferHandler((SocketChannel) ctx.channel()));

                        socketChannel.attr(NioUtil.TUNNEL_KEY).set(ctx.channel().attr(NioUtil.TUNNEL_KEY).get());
                        tunnelDto.setRemote(socketChannel);

//                        ctx.pipeline().addLast(new Client2DestHandler(future));
//                        Socks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS, Socks5AddressType.IPv4);
//                        ctx.writeAndFlush(commandResponse);
                    } else {
                        log.error("无法连接至远程服务器 {} {}", future.channel().remoteAddress());
                        ctx.close().sync();
                    }
                }
            });
        } else {
            ctx.fireChannelRead(msg);
        }
    }
}

