package com.mocyx.biosocks.nio.tunnel;

import com.mocyx.biosocks.TunnelMsgType;
import com.mocyx.biosocks.nio.NioUtil;
import com.mocyx.biosocks.nio.TunnelDto;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.socksx.v5.DefaultSocks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import io.netty.handler.codec.socksx.v5.Socks5CommandResponse;
import io.netty.handler.codec.socksx.v5.Socks5CommandStatus;

/**
 * @author Administrator
 */
public class TunnelResponseHandler extends SimpleChannelInboundHandler<TunnelResponse> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TunnelResponse msg) throws Exception {
        TunnelDto tunnelDto = ctx.channel().attr(NioUtil.TUNNEL_KEY).get();

        if (msg.type == TunnelMsgType.RES_CONNECT_SUCCESS.getV()) {
            Socks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.SUCCESS,
                    Socks5AddressType.DOMAIN, tunnelDto.getRemoteAddr(), tunnelDto.getRemotePort());

            tunnelDto.getLocal().writeAndFlush(commandResponse);

            ctx.channel().pipeline().remove(TunnelMsgDecoder.class);
            ctx.channel().pipeline().remove(TunnelMsgEncoder.class);
            ctx.channel().pipeline().remove(TunnelResponseHandler.class);


        } else if (msg.type == TunnelMsgType.RES_CONNECT_FAIL.getV()) {
            Socks5CommandResponse commandResponse = new DefaultSocks5CommandResponse(Socks5CommandStatus.FAILURE, Socks5AddressType.DOMAIN,
                    tunnelDto.getRemoteAddr(), tunnelDto.getRemotePort());
            tunnelDto.getLocal().writeAndFlush(commandResponse).sync();
            tunnelDto.getLocal().close();
        }
        System.currentTimeMillis();
    }

}
