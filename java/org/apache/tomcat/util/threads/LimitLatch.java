/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.util.threads;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * 连接控制器，负责控制最大连接数。整体看起来很像semaphore
 * NIO下默认10000，达到阈值后，连接请求被拒绝
 * Shared latch that allows the latch to be acquired a limited number of times
 * after which all subsequent requests to acquire the latch will be placed in a
 * FIFO queue until one of the shares is returned.
 */
public class LimitLatch {

    private static final Log log = LogFactory.getLog(LimitLatch.class);

    /**
     * 一个基于AQS的自定义实现
     */
    private class Sync extends AbstractQueuedSynchronizer {
        private static final long serialVersionUID = 1L;

        Sync() {
        }

        @Override
        protected int tryAcquireShared(int ignored) {
            long newCount = count.incrementAndGet();
            if (!released && newCount > limit) {
                // Limit exceeded
                count.decrementAndGet();
                return -1;
            } else {
                return 1;
            }
        }

        @Override
        protected boolean tryReleaseShared(int arg) {
            count.decrementAndGet();
            return true;
        }
    }

    private final Sync sync;
    // 使用原子类保证原子性
    private final AtomicLong count;
    // 使用volatile保证可见性
    private volatile long limit;
    private volatile boolean released = false;

    /**
     * Instantiates a LimitLatch object with an initial limit.
     * @param limit - maximum number of concurrent acquisitions of this latch
     */
    public LimitLatch(long limit) {
        this.limit = limit;
        this.count = new AtomicLong(0);
        this.sync = new Sync();
    }

    /**
     * Returns the current count for the latch
     * @return the current count for latch
     */
    public long getCount() {
        return count.get();
    }

    /**
     * Obtain the current limit.
     * @return the limit
     */
    public long getLimit() {
        return limit;
    }


    /**
     * Sets a new limit. If the limit is decreased there may be a period where
     * more shares of the latch are acquired than the limit. In this case no
     * more shares of the latch will be issued until sufficient shares have been
     * returned to reduce the number of acquired shares of the latch to below
     * the new limit. If the limit is increased, threads currently in the queue
     * may not be issued one of the newly available shares until the next
     * request is made for a latch.
     *
     * @param limit The new limit
     */
    public void setLimit(long limit) {
        this.limit = limit;
    }


    /**
     * 线程调用该方法获得接收新连接的许可，线程可能被阻塞
     * Acquires a shared latch if one is available or waits for one if no shared
     * latch is current available.
     * @throws InterruptedException If the current thread is interrupted
     */
    public void countUpOrAwait() throws InterruptedException {
        if (log.isDebugEnabled()) {
            log.debug("Counting up["+Thread.currentThread().getName()+"] latch="+getCount());
        }
        sync.acquireSharedInterruptibly(1);
    }

    /**
     * 调用该方法来释放一个连接许可，前面阻塞的线程可能会被唤醒
     * Releases a shared latch, making it available for another thread to use.
     * @return the previous counter value
     */
    public long countDown() {
        sync.releaseShared(0);
        long result = getCount();
        if (log.isDebugEnabled()) {
            log.debug("Counting down["+Thread.currentThread().getName()+"] latch="+result);
        }
        return result;
    }

    /**
     * Releases all waiting threads and causes the {@link #limit} to be ignored
     * until {@link #reset()} is called.
     * @return <code>true</code> if release was done
     */
    public boolean releaseAll() {
        released = true;
        return sync.releaseShared(0);
    }

    /**
     * Resets the latch and initializes the shared acquisition counter to zero.
     * @see #releaseAll()
     */
    public void reset() {
        this.count.set(0);
        released = false;
    }

    /**
     * Returns <code>true</code> if there is at least one thread waiting to
     * acquire the shared lock, otherwise returns <code>false</code>.
     * @return <code>true</code> if threads are waiting
     */
    public boolean hasQueuedThreads() {
        return sync.hasQueuedThreads();
    }

    /**
     * Provide access to the list of threads waiting to acquire this limited
     * shared latch.
     * @return a collection of threads
     */
    public Collection<Thread> getQueuedThreads() {
        return sync.getQueuedThreads();
    }
}
