package com.mocyx.mocnetty.channel;

import com.mocyx.mocnetty.event.MocEventExecutor;
import com.mocyx.mocnetty.future.MocDefaultPromise;

/**
 * @author Administrator
 */
public class MocDefaultChannelPromise extends MocDefaultPromise<Void> implements MocChannelPromise {

    private MocChannel channel;

    public MocDefaultChannelPromise(MocChannel channel) {
        super(channel.getEventExecutor());
        this.channel = channel;
    }

    @Override
    public MocChannel channel() {
        return channel;
    }

}
