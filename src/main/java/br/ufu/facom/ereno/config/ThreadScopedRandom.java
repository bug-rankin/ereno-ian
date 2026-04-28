package br.ufu.facom.ereno.config;

import java.util.Random;

/**
 * A {@link Random} subclass whose state is scoped to the current thread.
 *
 * <p>All mutating and query operations delegate to a per-thread {@link Random}
 * instance. Calling {@link #setSeed(long)} reseeds only the current thread's
 * RNG; other threads keep their own independent streams.</p>
 *
 * <p>This lets callers continue to read a single shared {@code RNG} field
 * (e.g. {@code ConfigLoader.RNG.nextInt(n)}) while the runtime parallel
 * orchestrator runs many jobs concurrently with deterministic, isolated
 * per-thread seeds.</p>
 */
public final class ThreadScopedRandom extends Random {

    private static final long serialVersionUID = 1L;

    private final ThreadLocal<Random> perThread = ThreadLocal.withInitial(
            () -> new Random(System.nanoTime() ^ Thread.currentThread().getId()));

    /** Replace the current thread's RNG with a freshly-seeded one. */
    @Override
    public void setSeed(long seed) {
        // The superclass constructor calls setSeed before our field is initialized.
        // Guard against the NPE that would cause.
        if (perThread != null) {
            perThread.set(new Random(seed));
        }
    }

    /** Remove the current thread's RNG (frees the ThreadLocal entry). */
    public void clearCurrentThread() {
        perThread.remove();
    }

    /** Returns the per-thread {@link Random}, creating it if necessary. */
    private Random current() {
        return perThread.get();
    }

    @Override
    protected int next(int bits) {
        // next(int) is protected on Random, so we cannot delegate to
        // current().next(bits) directly. Synthesize it from nextInt() —
        // this is the same uniform-bits trick the JDK uses internally.
        return current().nextInt() >>> (32 - bits);
    }

    @Override
    public int nextInt() {
        return current().nextInt();
    }

    @Override
    public int nextInt(int bound) {
        return current().nextInt(bound);
    }

    @Override
    public long nextLong() {
        return current().nextLong();
    }

    @Override
    public boolean nextBoolean() {
        return current().nextBoolean();
    }

    @Override
    public double nextDouble() {
        return current().nextDouble();
    }

    @Override
    public float nextFloat() {
        return current().nextFloat();
    }

    @Override
    public double nextGaussian() {
        return current().nextGaussian();
    }

    @Override
    public void nextBytes(byte[] bytes) {
        current().nextBytes(bytes);
    }
}
