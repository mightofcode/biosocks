package com.mocyx.mocnetty;

import com.mocyx.mocnetty.channel.MocChannel;
import com.mocyx.mocnetty.channel.MocChannelFuture;
import com.mocyx.mocnetty.channel.MocChannelPromise;
import com.mocyx.mocnetty.channel.MocDefaultChannelPromise;
import com.mocyx.mocnetty.event.MocEventExecutor;
import com.mocyx.mocnetty.event.MocNioEventExecutor;
import com.mocyx.mocnetty.handler.MocChannelHandler;
import io.netty.channel.ChannelFuture;

import java.net.InetSocketAddress;

/**
 * @author Administrator
 */
public class MocServerBootstrap {

    private MocEventExecutor boss = new MocNioEventExecutor();
    private MocEventExecutor work = new MocNioEventExecutor();
    private MocChannel serverChannel;

    public MocServerBootstrap() {
        serverChannel = new MocChannel(boss);

    }

    public MocChannel serverChannel() {
        return serverChannel;
    }

    public void start(InetSocketAddress address) {
        serverChannel.bind(address);
    }

}
