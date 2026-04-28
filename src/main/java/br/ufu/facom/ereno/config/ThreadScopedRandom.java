package br.ufu.facom.ereno.config;

import java.util.Random;

public final class ThreadScopedRandom extends Random {

    private static final long serialVersionUID = 1L;

    private final ThreadLocal<Random> perThread = ThreadLocal.withInitial(
            () -> new Random(System.nanoTime() ^ Thread.currentThread().getId()));

    @Override
    public void setSeed(long seed) {

        if (perThread != null) {
            perThread.set(new Random(seed));
        }
    }

    public void clearCurrentThread() {
        perThread.remove();
    }

    private Random current() {
        return perThread.get();
    }

    @Override
    protected int next(int bits) {

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
