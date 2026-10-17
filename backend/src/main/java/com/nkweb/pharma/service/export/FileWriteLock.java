package com.nkweb.pharma.service.export;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Mutual exclusion for append-style updates of a single file.
 *
 * <p>Two different locks are needed, and both are required for correctness:
 * <ol>
 *   <li><b>Intra-JVM</b> – a {@link ReentrantLock} per canonical path. Without it, two threads of the
 *       same JVM would both read the workbook, both append and the second write would silently drop
 *       the other's rows. ({@code FileLock} cannot be used for this: an exclusive
 *       {@code FileLock} on a region already locked by the same JVM throws
 *       {@link OverlappingFileLockException} instead of blocking.)</li>
 *   <li><b>Inter-process</b> – a {@link FileLock} on a sibling {@code <file>.lock}, obtained with
 *       {@code tryLock} in a bounded loop, which is what makes several instances (or a cron based
 *       exporter next to the service) safe against each other.</li>
 * </ol>
 *
 * <p>The lock is acquired in a deterministic order (JVM lock first, then the file lock) so two
 * processes cannot deadlock. A timeout produces {@link ExportLockTimeoutException}, which the worker
 * treats as a transient failure and retries later – that is exactly what should happen when an
 * operator has the workbook open in Excel on Windows.
 */
public final class FileWriteLock {

    private static final ConcurrentHashMap<String, ReentrantLock> JVM_LOCKS = new ConcurrentHashMap<>();
    private static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(150);

    private final long timeoutMillis;
    private final long retryDelayMillis;

    public FileWriteLock(Duration timeout) {
        this(timeout, DEFAULT_RETRY_DELAY);
    }

    public FileWriteLock(Duration timeout, Duration retryDelay) {
        this.timeoutMillis = Math.max(1, timeout.toMillis());
        this.retryDelayMillis = Math.max(1, retryDelay.toMillis());
    }

    /**
     * Runs {@code action} while holding both locks for {@code target}.
     *
     * @throws ExportLockTimeoutException if no lock could be obtained within the timeout.
     * @throws ExportException            if the lock file itself cannot be created.
     */
    public <T> T runExclusive(Path target, Supplier<T> action) {
        Path normalized = target.toAbsolutePath().normalize();
        ReentrantLock jvmLock = JVM_LOCKS.computeIfAbsent(normalized.toString(),
                ignored -> new ReentrantLock(true));

        if (!acquireJvmLock(jvmLock, normalized)) {
            throw new ExportLockTimeoutException(
                    "Timed out after " + timeoutMillis + " ms waiting for the in-process write lock on "
                            + normalized.getFileName());
        }
        try {
            Path lockFile = lockFileFor(normalized);
            try (FileChannel channel = FileChannel.open(lockFile,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock fileLock = acquireFileLock(channel, normalized);
                try {
                    return action.get();
                } finally {
                    releaseQuietly(fileLock);
                }
            } catch (IOException e) {
                throw new ExportException(
                        "Could not acquire the cross-process lock for " + normalized.getFileName(), e);
            }
        } finally {
            jvmLock.unlock();
        }
    }

    static Path lockFileFor(Path target) {
        return target.resolveSibling(target.getFileName() + ".lock");
    }

    private boolean acquireJvmLock(ReentrantLock lock, Path target) {
        try {
            if (lock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)) {
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExportException("Interrupted while waiting for the write lock on "
                    + target.getFileName(), e);
        }
        return false;
    }

    private FileLock acquireFileLock(FileChannel channel, Path target) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (true) {
            try {
                FileLock fileLock = channel.tryLock();
                if (fileLock != null) {
                    return fileLock;
                }
            } catch (OverlappingFileLockException e) {
                // Another thread of this JVM holds the lock: keep waiting, the timeout decides.
            }
            if (System.nanoTime() >= deadline) {
                throw new ExportLockTimeoutException(
                        "Timed out after " + timeoutMillis + " ms waiting for the file lock on "
                                + target.getFileName()
                                + " (is the workbook open in a spreadsheet application?)");
            }
            sleepQuietly();
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExportException("Interrupted while waiting for the file lock");
        }
    }

    private static void releaseQuietly(FileLock fileLock) {
        try {
            if (fileLock.isValid()) {
                fileLock.release();
            }
        } catch (IOException ignored) {
            // Closing the channel releases the lock anyway.
        }
    }

    /** Number of tracked lock objects, exposed for tests/monitoring. */
    static int trackedLocks() {
        return JVM_LOCKS.size();
    }
}
