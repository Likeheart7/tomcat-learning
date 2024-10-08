/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.threads;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.util.res.StringManager;

/**
 * As task queue specifically designed to run with a thread pool executor. The
 * task queue is optimised to properly utilize threads within a thread pool
 * executor. If you use a normal queue, the executor will spawn threads when
 * there are idle threads and you won't be able to force items onto the queue
 * itself.
 */
public class TaskQueue extends LinkedBlockingQueue<Runnable> {

    private static final long serialVersionUID = 1L;
    protected static final StringManager sm = StringManager.getManager(TaskQueue.class);

    private transient volatile ThreadPoolExecutor parent = null;

    public TaskQueue() {
        super();
    }

    /**
     * 因为父类LinkedBlockingQueue默认最大长度是Integer.MAXVALUE
     * 使用capacity限制他的最大长度
     */
    public TaskQueue(int capacity) {
        super(capacity);
    }

    public TaskQueue(Collection<? extends Runnable> c) {
        super(c);
    }

    public void setParent(ThreadPoolExecutor tp) {
        parent = tp;
    }


    /**
     * Used to add a task to the queue if the task has been rejected by the Executor.
     *
     * @param o         The task to add to the queue
     *
     * @return          {@code true} if the task was added to the queue,
     *                      otherwise {@code false}
     */
    public boolean force(Runnable o) {
        if (parent == null || parent.isShutdown()) {
            throw new RejectedExecutionException(sm.getString("taskQueue.notRunning"));
        }
        return super.offer(o); //forces the item onto the queue, to be used if the task is rejected
    }


    /**
     * Used to add a task to the queue if the task has been rejected by the Executor.
     *
     * @param o         The task to add to the queue
     * @param timeout   The timeout to use when adding the task
     * @param unit      The units in which the timeout is expressed
     *
     * @return          {@code true} if the task was added to the queue,
     *                      otherwise {@code false}
     *
     * @throws InterruptedException If the call is interrupted before the
     *                              timeout expires
     *
     * @deprecated Unused. Will be removed in Tomcat 10.1.x.
     */
    @Deprecated
    public boolean force(Runnable o, long timeout, TimeUnit unit) throws InterruptedException {
        if (parent == null || parent.isShutdown()) {
            throw new RejectedExecutionException(sm.getString("taskQueue.notRunning"));
        }
        return super.offer(o,timeout,unit); //forces the item onto the queue, to be used if the task is rejected
    }


    /*
    Tomcat默认设置的TaskQueue的长度还是Integer.MAXVALUE
    当该方法被调用时,已经达到核心线程数了
    这样做的目的就是为了当tomcat的线程池队列长度是过大时,
    让线程池有机会创建新的线程,而不是在最大线程满了之后一直往队列里添加.
     */
    @Override
    public boolean offer(Runnable o) {
      //we can't do any checks
        if (parent==null) {
            return super.offer(o);
        }
        //we are maxed out on threads, simply queue the object
        // 如果线程数达到最大值,添加任务到队列中
        if (parent.getPoolSizeNoLock() == parent.getMaximumPoolSize()) {
            return super.offer(o);
        }
        //we have idle threads, just add it to the queue
        // 到达这里时,当前线程数一定大于核心线程数,并且小于最大线程数
        // 说明可以创建新线程,根据情况决定是否创建新线程

        // 如果已提交任务数量小于等于当前线程池中线程数,说明有线程空闲,放进队列就行,不用创建新线程
        if (parent.getSubmittedCount() <= parent.getPoolSizeNoLock()) {
            return super.offer(o);
        }
        //if we have less threads than maximum force creation of a new thread
        // 走到这里说明已提交任务大于当前线程数
        // 此时判断如果还没达到最大线程数,返回false让其尝试去创建线程.
        // 在并发的情况下,这一步可能导致返回false的线程去尝试创建新线程时线程池达到了最大线程数(其他线程干的),从而被拒绝策略处理
        // 这一步也是为什么Tomcat需要在ThreadPoolExecutor#execute内,任务被拒绝后还要用force尝试将其放入队列的原因.
        if (parent.getPoolSizeNoLock() < parent.getMaximumPoolSize()) {
            return false;
        }
        // 默认情况下将其添加到任务队列.
        //if we reached here, we need to add it to the queue
        return super.offer(o);
    }


    @Override
    public Runnable poll(long timeout, TimeUnit unit)
            throws InterruptedException {
        Runnable runnable = super.poll(timeout, unit);
        if (runnable == null && parent != null) {
            // the poll timed out, it gives an opportunity to stop the current
            // thread if needed to avoid memory leaks.
            parent.stopCurrentThreadIfNeeded();
        }
        return runnable;
    }

    @Override
    public Runnable take() throws InterruptedException {
        if (parent != null && parent.currentThreadShouldBeStopped()) {
            return poll(parent.getKeepAliveTime(TimeUnit.MILLISECONDS),
                    TimeUnit.MILLISECONDS);
            // yes, this may return null (in case of timeout) which normally
            // does not occur with take()
            // but the ThreadPoolExecutor implementation allows this
        }
        return super.take();
    }
}
