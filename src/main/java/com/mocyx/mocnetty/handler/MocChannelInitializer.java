package com.mocyx.mocnetty.handler;

import com.mocyx.mocnetty.channel.MocChannel;

/**
 * @author Administrator
 */
public abstract class MocChannelInitializer implements MocChannelHandler {


    @Override
    public void handlerAdded(MocChannelHandlerContext ctx) throws Exception {
        MocChannel channel = ctx.channel();
        this.initChannel(channel);
    }


    abstract void initChannel(MocChannel ch) throws Exception;
}
