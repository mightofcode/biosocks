package com.mocyx.mocnetty.event;

import com.mocyx.mocnetty.future.MocDefaultPromise;

import java.util.concurrent.RunnableFuture;

public abstract class MocPromiseTask<V> extends MocDefaultPromise<V> implements RunnableFuture<V> {

    public MocPromiseTask(MocEventExecutor executor) {
        super(executor);
    }
}
