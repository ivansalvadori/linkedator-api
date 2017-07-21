package br.ufsc.inf.lapesd.linkedator.api.utils;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class FileWatcher extends Thread implements AutoCloseable {
    private static final int DEFAULT_SETUP_WATCHKEY_RETRY = 2000;
    private static final Set<WatchEvent.Kind<?>> interest;

    private boolean cancelled = false;
    private final @Nonnull Path file;
    private final @Nonnull Consumer<Path> listener;
    private int setupWatchKeyRetry = DEFAULT_SETUP_WATCHKEY_RETRY;
    private final @Nonnull WatchService watchService;
    private WatchKey watchKey;

    public FileWatcher(@Nonnull Path file, @Nonnull Consumer<Path> listener) throws IOException {
        this.file = file;
        this.listener = listener;

        Path dir = file.getParent();
        FileSystem fs = dir.getFileSystem();
        watchService = fs.newWatchService();
    }

    @Override
    public void close() throws Exception {
        cancel();
        watchService.close();
    }

    public synchronized void cancel() {
        if (!cancelled) {
            cancelled = true;
            interrupt();
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public int getSetupWatchKeyRetry() {
        return setupWatchKeyRetry;
    }

    public FileWatcher setSetupWatchKeyRetry(int setupWatchKeyRetry) {
        this.setupWatchKeyRetry = setupWatchKeyRetry;
        return this;
    }

    @Override
    public void run() {
        while (!isCancelled()) {
            try {
                try {
                    setupWatchKey();
                } catch (IOException e) {
                    Thread.sleep(setupWatchKeyRetry);
                    continue;
                }
                watch();
            } catch (InterruptedException | ClosedWatchServiceException ignored) { }
        }
    }

    private void setupWatchKey() throws IOException {
        if (watchKey == null || !watchKey.isValid())
            watchKey = file.getParent().register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
    }

    private void watch() throws InterruptedException {
        while (!isCancelled()) {
            WatchKey key = watchService.take();
            if (key != watchKey)
                continue; //ignore
            if (!watchKey.isValid())
                return; //loops on run() until it obtains a valid watchKey
            boolean changed = false;
            for (WatchEvent<?> event : key.pollEvents()) {
                Path resolved = ((Path) watchKey.watchable()).resolve((Path) event.context());
                if (!resolved.equals(file)) continue; //not interested
                if ((changed = interest.contains(event.kind()))) break;
            }
            key.reset(); //enable receipt of new events
            if (changed)
                listener.accept(file); //notify
        }
    }

    static {
        interest = new HashSet<>();
        interest.add(OVERFLOW);
        interest.add(ENTRY_CREATE);
        interest.add(ENTRY_MODIFY);
    }
}
