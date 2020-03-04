package com.mocyx.mocnetty.event;

import com.mocyx.mocnetty.channel.MocChannel;
import com.mocyx.mocnetty.channel.MocChannelFuture;
import com.mocyx.mocnetty.channel.MocChannelPromise;
import com.mocyx.mocnetty.future.MocDefaultPromise;
import com.mocyx.mocnetty.future.MocPromise;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.*;

/**
 * @author Administrator
 */
@Slf4j
public class MocNioEventExecutor implements MocEventExecutor {

    private BlockingQueue<Runnable> tasks = new ArrayBlockingQueue<Runnable>(1024);

    private boolean isShutdown = false;

    private Selector selector;

    public MocNioEventExecutor() {
        try {
            thread = new Thread(new Worker(tasks));
            thread.start();

            selector = Selector.open();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }

    private class Worker implements Runnable {

        BlockingQueue<Runnable> tasks;

        Worker(BlockingQueue<Runnable> tasks) {
            this.tasks = tasks;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    MocNioEventExecutor.this.run();
                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

                Runnable task = null;
                try {
                    task = tasks.take();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (task != null) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        log.error("task error " + e.getMessage(), e);
                    }
                }
            }
        }
    }

    private final Thread thread;

    //private void processSelectedKey(SelectionKey k, AbstractNioChannel ch)
    private synchronized void run() {
        try {
            int n = this.selector.select();
            if (n != 0) {
                Set<SelectionKey> selectionKeys = this.selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectionKeys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    MocChannel mocChannel = (MocChannel) key.attachment();
                    int ops = key.readyOps();
                    if ((ops & SelectionKey.OP_CONNECT) != 0) {
                        mocChannel.socketChannel().finishConnect();
                        key.interestOps(key.interestOps() & (~SelectionKey.OP_CONNECT));
                    } else if ((ops & SelectionKey.OP_WRITE) != 0) {
                        mocChannel.onWrite();
                    } else if ((ops & SelectionKey.OP_READ) != 0) {
                        mocChannel.onRead();
                    } else if ((ops & SelectionKey.OP_ACCEPT) != 0) {
                        mocChannel.serverSocketChannel().accept();
                    }
                }
                selectionKeys.clear();

            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Object register(MocChannel channel) {
        try {
//            SelectionKey key = channel.channel().register(selector, 0, channel);
//            return key;
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public synchronized boolean inEventLoop() {
        return inEventLoop(Thread.currentThread());
    }

    @Override
    public synchronized boolean inEventLoop(Thread thread) {
        return thread == Thread.currentThread();
    }

    @Override
    public synchronized <V> MocPromise<V> newPromise() {

        return new MocDefaultPromise<V>(this);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        RunnableFuture<T> future = new FutureTask<>(task);
        execute(future);
        return future;
    }

    @Override
    public synchronized void shutdown() {
        while (true) {
            try {
                thread.join();
                break;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    @Override
    public synchronized void execute(Runnable command) {
        tasks.offer(command);
    }
}
