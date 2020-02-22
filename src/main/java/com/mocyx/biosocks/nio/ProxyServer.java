package com.mocyx.biosocks.nio;


import com.mocyx.biosocks.ConfigDto;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
//
///**
// * @author Administrator
// */
//@Slf4j
//@Component
//public class ProxyServer implements Runnable {
//
//
//    @Override
//    public void run() {
//        config = Util.getConfigFromFile("./data/server.json", ConfigDto.class);
//        Util.setConfig(config);
//        EventLoopGroup boss = new NioEventLoopGroup(1);
//        EventLoopGroup worker = new NioEventLoopGroup();
//        try {
//            ServerBootstrap bootstrap = new ServerBootstrap();
//            bootstrap.group(boss, worker)
//                    .channel(NioServerSocketChannel.class)
//                    .option(ChannelOption.SO_BACKLOG, 1024)
//                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
//                    .childHandler(new ChannelInitializer<SocketChannel>() {
//                        @Override
//                        protected void initChannel(SocketChannel ch) throws Exception {
//                            System.currentTimeMillis();
//
//                            ch.pipeline().addLast(new TunnelMsgDecoder());
//
//                            ch.pipeline().addLast(new TunnelRequestHandler(boss));
//
//                            ch.pipeline().addLast(new TunnelMsgEncoder());
//
//                            TunnelDto tunnelDto = new TunnelDto();
//                            tunnelDto.setLocal(ch);
//                            ch.attr(Util.TUNNEL_KEY).set(tunnelDto);
//
//                        }
//                    });
//            ChannelFuture future = bootstrap.bind(config.getServer(), config.getServerPort()).sync();
//            log.debug("bind port {} {} ", config.getClient(), config.getClientPort());
//            future.channel().closeFuture().sync();
//        } catch (Exception e) {
//            log.error(e.getMessage(), e);
//            boss.shutdownGracefully();
//            worker.shutdownGracefully();
//        }
//    }
//}
