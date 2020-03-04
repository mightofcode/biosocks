package com.mocyx.mocnetty.future;

/**
 * @author Administrator
 */
public interface MocGenericFutureListener<F extends MocFuture<?>> {
    void operationComplete(F future) throws Exception;
}
