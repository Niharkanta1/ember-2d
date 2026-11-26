package dev.ember2d.devtools;

import dev.ember2d.assets.AssetWatcher;
import dev.ember2d.core.Log;
import dev.ember2d.core.PublicApi;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Hot reload watcher for desktop dev builds (F-008, I-150): watches a folder and its subfolders with the JDK's
 * {@link WatchService} on a daemon thread. Changes are handed over only after a quiet period (default 200 ms) without
 * further changes, so an editor that saves a file in several writes, or Tiled saving a map and its tilesets, gives one
 * batch. Set it as {@code EngineConfig.assetWatcher} together with {@code EngineConfig.assetsDir}.
 */
@PublicApi
public final class DirectoryWatcher implements AssetWatcher {

    /** Default quiet period in milliseconds. */
    public static final long DEFAULT_QUIET_MILLIS = 200;

    private static final Log LOG = Log.get("hotreload");

    private final Path root;
    private final long quietNanos;
    private final WatchService service;
    private final Map<WatchKey, Path> folders = new HashMap<>();
    private final Thread thread;
    private final Object lock = new Object();
    private final Set<String> pending = new LinkedHashSet<>();   // guarded by lock
    private long lastChange;                                     // guarded by lock
    private volatile boolean closed;

    public DirectoryWatcher(Path root) {
        this(root, DEFAULT_QUIET_MILLIS);
    }

    /**
     * Starts watching.
     *
     * @throws IllegalArgumentException if {@code root} is not a folder
     * @throws IllegalStateException if the file system cannot be watched
     */
    public DirectoryWatcher(Path root, long quietMillis) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        if (!Files.isDirectory(this.root)) {
            throw new IllegalArgumentException("Hot reload: " + this.root + " is not a folder");
        }
        if (quietMillis < 0) {
            throw new IllegalArgumentException("quietMillis must be >= 0 (was " + quietMillis + ")");
        }
        this.quietNanos = TimeUnit.MILLISECONDS.toNanos(quietMillis);
        try {
            service = FileSystems.getDefault().newWatchService();
            registerTree(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Hot reload: cannot watch " + this.root, e);
        }
        thread = new Thread(this::run, "ember-hot-reload");
        thread.setDaemon(true);
        thread.start();
    }

    /** The watched folder (absolute). */
    public Path root() {
        return root;
    }

    @Override
    public void drainChanges(Consumer<String> changedPath) {
        List<String> ready;
        synchronized (lock) {
            if (pending.isEmpty() || System.nanoTime() - lastChange < quietNanos) {
                return;
            }
            ready = new ArrayList<>(pending);
            pending.clear();
        }
        for (String path : ready) {
            changedPath.accept(path);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            service.close();
        } catch (IOException e) {
            LOG.warn("Hot reload: closing the watcher failed: " + e.getMessage());
        }
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Whether the watcher thread is still running (tests). */
    boolean isRunning() {
        return thread.isAlive();
    }

    private void run() {
        while (!closed) {
            WatchKey key;
            try {
                key = service.take();
            } catch (ClosedWatchServiceException | InterruptedException e) {
                return;
            }
            Path folder = folders.get(key);
            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    LOG.warn("Hot reload: too many changes at once in " + folder + "; some were missed");
                    continue;
                }
                if (folder == null) {
                    continue;
                }
                Path changed = folder.resolve((Path) event.context());
                if (Files.isDirectory(changed)) {
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
                        try {
                            registerTree(changed);   // a new subfolder: watch it too
                            reportFiles(changed);    // files written before it was watched would be missed
                        } catch (IOException | ClosedWatchServiceException e) {
                            LOG.warn("Hot reload: cannot watch new folder " + changed + ": " + e.getMessage());
                        }
                    }
                    continue;
                }
                if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                    LOG.debug("Hot reload: " + changed + " was deleted; ignored");
                    continue;
                }
                String relative = root.relativize(changed).toString().replace('\\', '/');
                synchronized (lock) {
                    pending.add(relative);
                    lastChange = System.nanoTime();
                }
            }
            if (!key.reset()) {
                folders.remove(key);   // the folder was deleted
            }
        }
    }

    /** Reports every file under a folder as changed. */
    private void reportFiles(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                synchronized (lock) {
                    pending.add(relative);
                    lastChange = System.nanoTime();
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerTree(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                WatchKey key = dir.register(service, StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
                folders.put(key, dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
