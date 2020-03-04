package com.mocyx.mocnetty.channel;

import com.mocyx.mocnetty.future.MocFuture;

/**
 * @author Administrator
 */
public interface MocChannelFuture extends MocFuture<Void> {
    MocChannel channel();
}
