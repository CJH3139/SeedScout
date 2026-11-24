package com.seedscout.map;

import com.seedscout.SeedScoutClient;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class MapWorker {
    private static final AtomicLong SEQUENCE = new AtomicLong();

    private record Job(double priority, long sequence, Runnable task, boolean essential) implements Runnable, Comparable<Job> {
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
        EXECUTOR.execute(new Job(priority, SEQUENCE.incrementAndGet(), task, false));
    }

    /**
     * Runs before every queued job, and still runs, on the thread calling {@link #shutdown}, if it has not started by
     * then. For short jobs that must not be lost, like writing caches to disk.
     */
    public static void submitEssential(Runnable task) {
        Job job = new Job(-Double.MAX_VALUE, SEQUENCE.incrementAndGet(), task, true);
        try {
            EXECUTOR.execute(job);
        } catch (RejectedExecutionException e) {
            job.run();
        }
    }

    public static int pendingCount() {
        return EXECUTOR.getQueue().size() + EXECUTOR.getActiveCount();
    }

    public static void shutdown() {
        List<Runnable> dropped = EXECUTOR.shutdownNow();
        for (Runnable runnable : dropped) {
            if (runnable instanceof Job job && job.essential()) job.run();
        }
    }
}
