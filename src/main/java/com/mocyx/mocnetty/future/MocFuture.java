package com.mocyx.mocnetty.future;

import java.util.concurrent.Future;

/**
 * @author Administrator
 */
public interface MocFuture<V> extends Future<V> {


    void addListener(MocGenericFutureListener<? extends MocFuture<? super V>> listener);

    void removeListener(MocGenericFutureListener<? extends MocFuture<? super V>> listener);


    void sync() throws InterruptedException;

    void syncUninterruptibly();

    boolean isSuccess();
}
