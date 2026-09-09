package com.seedscout.map;

import com.seedscout.SeedScoutClient;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class MapWorker {
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private record Job(double priority, long sequence, Runnable task) implements Runnable, Comparable<Job> {
        @Override
        public void run() {
            try {
                task.run();
            } catch (Throwable t) {
                SeedScoutClient.LOGGER.error("SeedScout worker job failed", t);
            }
        }

        @Override
        public int compareTo(Job other) {
            int byPriority = Double.compare(priority, other.priority);
            return byPriority != 0 ? byPriority : Long.compare(sequence, other.sequence);
        }
    }

    private static final int WORKERS = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);

    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS,
            new PriorityBlockingQueue<>(),
            runnable -> {
                Thread thread = new Thread(runnable, "SeedScout-worker");
                thread.setDaemon(true);
                thread.setPriority(Math.max(Thread.MIN_PRIORITY, Thread.NORM_PRIORITY - 1));
                return thread;
            });

    private MapWorker() {}

    public static void submit(Runnable task) {
        submitOrdered(0.0, task);
    }

    public static void submitOrdered(double priority, Runnable task) {
        EXECUTOR.execute(new Job(priority, SEQUENCE.incrementAndGet(), task));
    }

    public static int pendingCount() {
        return EXECUTOR.getQueue().size() + EXECUTOR.getActiveCount();
    }

    public static void shutdown() {
        EXECUTOR.shutdownNow();
    }
}
