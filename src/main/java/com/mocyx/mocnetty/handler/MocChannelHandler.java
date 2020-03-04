package com.mocyx.mocnetty.handler;

/**
 * @author Administrator
 */
public interface MocChannelHandler {
    void handlerAdded(MocChannelHandlerContext ctx) throws Exception;

    void handlerRemoved(MocChannelHandlerContext ctx) throws Exception;
}
