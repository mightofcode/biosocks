package com.mocyx.mocnetty.future;

import com.mocyx.mocnetty.event.MocEventExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Administrator
 */
@Slf4j
public class MocDefaultPromise<V> implements MocPromise<V> {


    private final String ST_RUNNING = "running";
    private final String ST_CANCEL = "cancel";
    private final String ST_SUCCESS = "success";
    private final String ST_FAIL = "fail";

    private V result;
    private Throwable cause;
    private String state = ST_RUNNING;
    private MocEventExecutor executor;

    private List<MocGenericFutureListener<? extends MocFuture<? super V>>> listeners = new ArrayList<>();

    public MocDefaultPromise(MocEventExecutor executor) {
        this.executor = executor;
    }

    @Override
    public synchronized boolean cancel(boolean mayInterruptIfRunning) {
        if (state.equals(ST_RUNNING)) {
            state = ST_CANCEL;
            this.notifyAll();
            this.notifyListener();
            return true;
        } else {
            return false;
        }

    }

    @Override
    public synchronized boolean isCancelled() {
        return state.equals(ST_CANCEL);
    }

    @Override
    public synchronized boolean isDone() {
        return state.equals(ST_SUCCESS) || state.equals(ST_FAIL) || state.equals(ST_CANCEL);
    }

    @Override
    public synchronized V get() throws InterruptedException, ExecutionException {

        if (!isDone()) {
            this.wait();
        }

        if (state.equals(ST_CANCEL)) {
            throw new CancellationException();
        } else if (state.equals(ST_SUCCESS)) {
            return result;
        } else if (state.equals(ST_FAIL)) {
            throw new ExecutionException(cause);
        } else if (state.equals(ST_RUNNING)) {
            throw new RuntimeException();
        }
        throw new RuntimeException();
    }

    @Override
    public synchronized V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        if (!isDone()) {
            this.wait(timeout);
        }

        if (state.equals(ST_CANCEL)) {
            throw new CancellationException();
        } else if (state.equals(ST_SUCCESS)) {
            return result;
        } else if (state.equals(ST_FAIL)) {
            throw new ExecutionException(cause);
        } else if (state.equals(ST_RUNNING)) {
            throw new RuntimeException();
        }

        throw new RuntimeException();
    }

    @Override
    public synchronized void addListener(MocGenericFutureListener<? extends MocFuture<? super V>> listener) {
        this.listeners.add(listener);

        if (isDone()) {
            this.notifyListener();
        }

    }

    @Override
    public synchronized void removeListener(MocGenericFutureListener<? extends MocFuture<? super V>> listener) {
        for (int i = 0; i < listeners.size(); i++) {
            if (listener == listeners.get(i)) {
                listeners.remove(i);
                break;
            }
        }
    }

    @Override
    public synchronized void sync() throws InterruptedException {
        syncUninterruptibly();
    }

    @Override
    public synchronized void syncUninterruptibly() {
        while (true) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (cause != null) {
                throw new RuntimeException(cause);
            } else {
                break;
            }
        }
    }

    @Override
    public boolean isSuccess() {
        return state.equals(ST_SUCCESS);
    }

    private synchronized void notifyListenerNow() {
        for (MocGenericFutureListener<? extends MocFuture<? super V>> listener : listeners) {
            try {
                MocGenericFutureListener ls = listener;
                ls.operationComplete(this);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            }
        }
        listeners.clear();
    }

    private void notifyListener() {
        this.executor.execute(new Runnable() {
            @Override
            public void run() {
                notifyListenerNow();
            }
        });
    }


    @Override
    public synchronized void setSuccess(V result) {
        this.result = result;
        this.state = ST_SUCCESS;
        this.notifyAll();
        this.notifyListener();
    }

    @Override
    public synchronized void setFailure(Throwable cause) {
        this.cause = cause;
        this.state = ST_FAIL;
        this.notifyAll();
        this.notifyListener();
    }
}
