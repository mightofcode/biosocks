package com.mocyx.mocnetty.event;

import com.mocyx.mocnetty.channel.MocChannel;
import com.mocyx.mocnetty.channel.MocChannelFuture;
import com.mocyx.mocnetty.channel.MocChannelPromise;
import com.mocyx.mocnetty.future.MocPromise;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author Administrator
 */
public interface MocEventExecutor {

    Object register(MocChannel channel);

    boolean inEventLoop();

    boolean inEventLoop(Thread thread);

    <V> MocPromise<V> newPromise();

    <T> Future<T> submit(Callable<T> task);

    void execute(Runnable command);

    void shutdown();

}
