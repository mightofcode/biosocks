package com.mocyx.biosocks.nio;

import com.mocyx.biosocks.ConfigDto;
import com.mocyx.biosocks.Global;
import com.mocyx.biosocks.nio.tunnel.TunnelMsgDecoder;
import com.mocyx.biosocks.nio.tunnel.TunnelMsgEncoder;
import com.mocyx.biosocks.nio.tunnel.TunnelRequestHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author Administrator
 */
@Slf4j
public class ProxyServer implements Runnable {

    private ConfigDto configDto;

    public ProxyServer(ConfigDto configDto) {
        this.configDto = configDto;
    }

    @Override
    public void run() {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup worker = new NioEventLoopGroup(1);
        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(boss, worker)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {

                            ch.pipeline().addLast(new TunnelMsgDecoder());

                            ch.pipeline().addLast(new TunnelRequestHandler(worker));

                            ch.pipeline().addLast(new TunnelMsgEncoder());

                            TunnelDto tunnelDto = new TunnelDto();
                            tunnelDto.setLocal(ch);

                            ch.attr(NioUtil.TUNNEL_KEY).set(tunnelDto);

                        }
                    });
            ChannelFuture future = bootstrap.bind(configDto.getServer(), configDto.getServerPort());

            future.addListener(new GenericFutureListener<Future<? super Void>>() {
                @Override
                public void operationComplete(Future<? super Void> future) throws Exception {
                    System.currentTimeMillis();
                }
            });

            future = future.sync();
            log.debug("bind port {} {} ", configDto.getServer(), configDto.getServerPort());
            future.channel().closeFuture().sync();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            boss.shutdownGracefully();
            worker.shutdownGracefully();
        }
    }
}
