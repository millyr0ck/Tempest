package rip.tempest.utils.render;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

public final class PatternWorker {
    public static final class Frame {
        public final int[] pixels;
        public final int width, height;

        Frame(int[] pixels, int width, int height) {
            this.pixels = pixels;
            this.width = width;
            this.height = height;
        }
    }

    private static final class Job {
        ArrayPattern pattern;
        int width, height;
        double w, h, cx, span, t;
        boolean motion;
        int[] base;
    }

    private final AtomicReference<Job> pending = new AtomicReference<>();
    private final AtomicReference<Frame> done = new AtomicReference<>();
    private volatile Thread thread;

    public void request(ArrayPattern pattern, int width, int height, double w, double h, double cx, double span,
                        double t, boolean motion, int[] base) {
        Job job = new Job();
        job.pattern = pattern;
        job.width = width;
        job.height = height;
        job.w = w;
        job.h = h;
        job.cx = cx;
        job.span = span;
        job.t = t;
        job.motion = motion;
        job.base = base;
        pending.set(job);
        Thread worker = thread;
        if (worker == null) {
            worker = new Thread(this::run, "Tempest Patterns");
            worker.setDaemon(true);
            worker.setPriority(Thread.NORM_PRIORITY - 1);
            thread = worker;
            worker.start();
        }
        LockSupport.unpark(worker);
    }

    public Frame take() {
        return done.getAndSet(null);
    }

    private void run() {
        while (true) {
            Job job = pending.getAndSet(null);
            if (job == null) {
                LockSupport.park(this);
                continue;
            }
            try {
                done.set(new Frame(fill(job), job.width, job.height));
            } catch (Throwable ignored) {
            }
        }
    }

    private static int[] fill(Job job) {
        int tw = job.width, th = job.height;
        int[] pixels = new int[tw * th];
        ArrayPattern pattern = job.pattern;
        ArrayPattern.useBaseTable(job.base);
        try {
            double stepX = job.w / tw, stepY = job.h / th;
            for (int py = 0; py < th; py++) {
                double y = (py + 0.5) * stepY;
                int row = py * tw;
                for (int px = 0; px < tw; px++) {
                    pixels[row + px] = pattern.color((px + 0.5) * stepX, y, job.w, job.h, job.cx, job.span, job.t, job.motion);
                }
            }
        } finally {
            ArrayPattern.useBaseTable(null);
        }
        return pixels;
    }
}
