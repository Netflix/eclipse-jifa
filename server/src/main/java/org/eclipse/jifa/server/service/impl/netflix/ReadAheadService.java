package org.eclipse.jifa.server.service.impl.netflix;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jifa.common.util.ExecutorFactory;

@Component
@Slf4j
public class ReadAheadService {
    final static int CHUNK_SIZE = 8*1024*1024;
    final static int READ_THREADS = 16;
    final static int READ_THREADS_QUEUE_CAPACITY = 16;

    final static int MAX_AGE_SECONDS = 300;
    final Executor readers = ExecutorFactory.mdcAware(ExecutorFactory.newExecutor("ReadAheader", READ_THREADS, READ_THREADS_QUEUE_CAPACITY));

    final ConcurrentMap<String, Instant> readAheadRequests = new ConcurrentHashMap<>();

    static final String[] INDEXES = new String[]{
        // this assumes the files are all based on heapdump.hprof

        // any order
        "heapdump.i2sv2.index", // retained size cache (histogram & leak suspects)
        "heapdump.domOut.index", // dominator tree outbound
        "heapdump.o2ret.index", // retained size for object
        "heapdump.o2hprof.index", // file pointer
        "heapdump.o2c.index", // class id for object
        "heapdump.a2s.index", // array to size
        "heapdump.inbound.index", // inbound pointers for an object
        "heapdump.outbound.index", // outbound pointers for object
        "heapdump.domIn.index", // dominator tree inbound

        // last
        // "heapdump.idx.index",
        // "heapdump.index",

        // of course, also the hprof
        // "heapdump.hprof"
    };

    // the readahead request is likely to come through concurrently, so maybe
    // it will occur again before completing
    // note that because we use standard CompletableFuture it will run on fj pool

    public ReadAheadService() {
        log.info("init(): setting up readahead, read_threads = {}, chunk_size = {}, max_age_seconds = {}",
            READ_THREADS, CHUNK_SIZE, MAX_AGE_SECONDS);
    }

    public synchronized void issueForPath(Path hprofFile) {
        Path path = hprofFile.getParent();
        for (String index : INDEXES) {
            issueReadFile(path.resolve(index));
        }
    }

    void issueReadFile(final Path file) {
        String filename = file.toString();
        Instant existingRequest = readAheadRequests.get(filename);
        if (needsLoad(existingRequest)) {
            // put before, so that nobody else attempts; small chance of a race here but meh
            readAheadRequests.put(filename, Instant.now());
            log.info("issueReadFile(): issuing for {}", filename);
            try {
                readers.execute(() -> {
                    readAheadRequests.put(filename, Instant.now());

                    log.info("issueReadFile(): issued for {}", filename);
                    try (FileInputStream fis = new FileInputStream(filename)) {
                        byte[] chunk = new byte[CHUNK_SIZE];
                        while(fis.read(chunk) > 0) {
                            // read again
                        }
                        log.info("issueReadFile(): completed for {}", filename);
                    } catch (IOException e) {
                        log.info("issueReadFile(): failed for {}, {}", filename, e);
                    }

                    readAheadRequests.put(filename, Instant.now());
                });
            } catch (RejectedExecutionException e) {
                log.warn("issueReadFile(): read-ahead queue full, abandoning read-ahead for {} (active threads: {}, queue capacity: {})",
                    filename, READ_THREADS, READ_THREADS_QUEUE_CAPACITY);
                // Remove the timestamp we just added since we're not actually processing this file
                readAheadRequests.remove(filename);
            }
        }
    }

    boolean needsLoad(Instant instant) {
        if (instant == null) {
            return true;
        }

        Instant OLDEST_OK_AGE = Instant.now().minusSeconds(MAX_AGE_SECONDS);
        if (instant.isBefore(OLDEST_OK_AGE)) {
            return true;
        }

        return false;
    }
}
