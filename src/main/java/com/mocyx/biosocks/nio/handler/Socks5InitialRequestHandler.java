package com.mocyx.biosocks.nio.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.SocksVersion;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialRequest;
import io.netty.handler.codec.socksx.v5.DefaultSocks5InitialResponse;
import io.netty.handler.codec.socksx.v5.Socks5AuthMethod;
import io.netty.handler.codec.socksx.v5.Socks5InitialResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Administrator
 */
@Slf4j
public class Socks5InitialRequestHandler extends SimpleChannelInboundHandler<DefaultSocks5InitialRequest> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DefaultSocks5InitialRequest msg) throws Exception {
        System.currentTimeMillis();
        if (msg.decoderResult().isFailure()&&false) {
            //TODO
            log.warn("not socks5");
            ctx.fireChannelRead(msg);
        } else {
            if (msg.version().equals(SocksVersion.SOCKS5)) {
                Socks5InitialResponse initialResponse = new DefaultSocks5InitialResponse(Socks5AuthMethod.NO_AUTH);
                ctx.writeAndFlush(initialResponse);
            }
        }
    }
}
