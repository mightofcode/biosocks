package com.mocyx.mocnetty.channel;


import com.mocyx.mocnetty.future.MocPromise;

import java.nio.channels.SocketChannel;

/**
 * @author Administrator
 */
public interface MocChannelPromise extends MocChannelFuture, MocPromise<Void> {

    @Override
    MocChannel channel();

}
