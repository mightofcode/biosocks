package com.mocyx.mocnetty;

import com.mocyx.mocnetty.channel.MocChannel;
import com.mocyx.mocnetty.channel.MocChannelPromise;
import com.mocyx.mocnetty.channel.MocDefaultChannelPromise;
import com.mocyx.mocnetty.event.MocEventExecutor;
import com.mocyx.mocnetty.event.MocNioEventExecutor;
import com.mocyx.mocnetty.future.MocFuture;

import java.net.InetSocketAddress;

/**
 *
 */
public class MocBootstrap {

    private MocEventExecutor work;

    private MocChannel channel;

    private MocChannelPromise connectPromise;

    public MocBootstrap(MocEventExecutor work) {
        this.work = work;
        channel = new MocChannel(work);
        connectPromise = new MocDefaultChannelPromise(channel);
    }

    public MocFuture connect(InetSocketAddress remote) {
        channel.connect(remote, connectPromise);
        return connectPromise;
    }
}
