package com.mocyx.biosocks.mocnetty;

import com.mocyx.mocnetty.event.MocNioEventExecutor;
import com.mocyx.mocnetty.future.MocFuture;
import com.mocyx.mocnetty.future.MocGenericFutureListener;
import com.mocyx.mocnetty.future.MocPromise;
import org.junit.jupiter.api.Test;

public class MocSingleThreadEventExecutorTest {


    @Test
    void foo() {

        MocNioEventExecutor executor = new MocNioEventExecutor();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                System.out.printf("task 1\n");

            }
        });

        MocPromise<String> promise = executor.newPromise();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                promise.setSuccess("success");
            }
        });

        promise.syncUninterruptibly();

        System.currentTimeMillis();
    }

    @Test
    void testAddListener() {

        MocNioEventExecutor executor = new MocNioEventExecutor();


        MocPromise<String> promise = executor.newPromise();
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                promise.setSuccess("success");
            }
        });

        promise.addListener(new MocGenericFutureListener<MocFuture<? super String>>() {
            @Override
            public void operationComplete(MocFuture<? super String> future) throws Exception {
                System.out.printf("operationComplete\n");
            }
        });
        promise.syncUninterruptibly();
        promise.addListener(new MocGenericFutureListener<MocFuture<? super String>>() {
            @Override
            public void operationComplete(MocFuture<? super String> future) throws Exception {
                System.out.printf("operationComplete2\n");
            }
        });
        System.currentTimeMillis();
    }


}
