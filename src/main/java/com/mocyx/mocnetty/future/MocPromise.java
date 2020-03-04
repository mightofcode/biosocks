package com.mocyx.mocnetty.future;


/**
 * @author Administrator
 */
public interface MocPromise<T> extends MocFuture<T> {


    void setSuccess(T result);

    void setFailure(Throwable cause);
}
