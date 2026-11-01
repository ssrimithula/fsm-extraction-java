import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Classic Readers-Writers problem, adapted for FSMAgent the same way
 * DiningPhilosophers.java was:
 *
 * 1. Each thread's state lives in its own static String field (readerNState
 *    / writerNState), not an array - FSMAgent only sees PUTFIELD/PUTSTATIC,
 *    never AASTORE (array element writes).
 * 2. States are Strings (IDLE/WAITING/READING/WRITING), not ints, so
 *    FSMBuilder's numeric sign-abstraction doesn't collapse distinct
 *    states together.
 *
 * DELIBERATELY DIFFERENT choice from DiningPhilosophers.java: this uses
 * java.util.concurrent.locks.ReentrantReadWriteLock - a standard, trusted
 * JDK synchronization primitive - instead of hand-rolled locking. That
 * keeps this benchmark focused on validating the TRACING pipeline against
 * a second concurrency pattern, rather than risking another multi-hour
 * detour debugging custom synchronization logic (as happened with the
 * Dining Philosophers fork-ordering investigation).
 *
 * Ground-truth check: independent of FSMAgent (activeReaders/activeWriters
 * are NOT in the instrumentation config), asserting the core Readers-
 * Writers invariant - a writer is never active at the same time as any
 * reader or any other writer.
 */
public class ReadersWriters {

    static final int NUM_READERS = 3;
    static final int NUM_WRITERS = 2;
    static int ITERATIONS = 2;

    static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    // One field per thread - see class-level note above for why this isn't
    // a String[] array.
    static volatile String reader0State = "IDLE";
    static volatile String reader1State = "IDLE";
    static volatile String reader2State = "IDLE";
    static volatile String writer0State = "IDLE";
    static volatile String writer1State = "IDLE";

    // Shared "resource" - a simple counter writers increment and readers observe.
    static int sharedValue = 0;

    // Ground-truth invariant check - independent of the agent.
    static final AtomicInteger activeReaders = new AtomicInteger(0);
    static final AtomicInteger activeWriters = new AtomicInteger(0);

    static synchronized void setReaderState(int id, String value) {
        switch (id) {
            case 0: reader0State = value; break;
            case 1: reader1State = value; break;
            case 2: reader2State = value; break;
            default: throw new IllegalArgumentException("bad reader id: " + id);
        }
    }

    static synchronized void setWriterState(int id, String value) {
        switch (id) {
            case 0: writer0State = value; break;
            case 1: writer1State = value; break;
            default: throw new IllegalArgumentException("bad writer id: " + id);
        }
    }

    static void enterRead() {
        int w = activeWriters.get();
        if (w != 0) {
            System.out.println("*** GROUND-TRUTH VIOLATION: reader entered while " + w + " writer(s) active");
        }
        activeReaders.incrementAndGet();
    }

    static void exitRead() {
        activeReaders.decrementAndGet();
    }

    static void enterWrite() {
        int prevWriters = activeWriters.getAndIncrement();
        int readers = activeReaders.get();
        if (prevWriters != 0) {
            System.out.println("*** GROUND-TRUTH VIOLATION: writer entered while another writer was active");
        }
        if (readers != 0) {
            System.out.println("*** GROUND-TRUTH VIOLATION: writer entered while " + readers + " reader(s) active");
        }
    }

    static void exitWrite() {
        activeWriters.decrementAndGet();
    }

    static class Reader extends Thread {
        final int id;
        Reader(int id) { super("Reader-" + id); this.id = id; }

        @Override
        public void run() {
            try {
                for (int i = 0; i < ITERATIONS; i++) {
                    setReaderState(id, "WAITING");
                    Thread.sleep(5 + (int) (Math.random() * 20));

                    lock.readLock().lock();
                    try {
                        enterRead();
                        setReaderState(id, "READING");
                        Thread.sleep(10 + (int) (Math.random() * 20)); // simulate reading sharedValue
                        // FIX: leave READING *before* the lock is released (same race as DP).
                        setReaderState(id, "IDLE");
                        exitRead();
                    } finally {
                        lock.readLock().unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static class Writer extends Thread {
        final int id;
        Writer(int id) { super("Writer-" + id); this.id = id; }

        @Override
        public void run() {
            try {
                for (int i = 0; i < ITERATIONS; i++) {
                    setWriterState(id, "WAITING");
                    Thread.sleep(5 + (int) (Math.random() * 20));

                    lock.writeLock().lock();
                    try {
                        enterWrite();
                        setWriterState(id, "WRITING");
                        sharedValue++; // the actual write being protected
                        Thread.sleep(10 + (int) (Math.random() * 20));
                        setWriterState(id, "IDLE");
                        exitWrite();
                    } finally {
                        lock.writeLock().unlock();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0) {
            ITERATIONS = Integer.parseInt(args[0]);
        }
        long startTime = System.nanoTime();

        Reader[] readers = new Reader[NUM_READERS];
        Writer[] writers = new Writer[NUM_WRITERS];
        for (int i = 0; i < NUM_READERS; i++) readers[i] = new Reader(i);
        for (int i = 0; i < NUM_WRITERS; i++) writers[i] = new Writer(i);

        for (Reader r : readers) r.start();
        for (Writer w : writers) w.start();
        for (Reader r : readers) r.join();
        for (Writer w : writers) w.join();

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        System.out.println("All readers/writers finished. iterations=" + ITERATIONS
                + " elapsed_ms=" + elapsedMs + " final_sharedValue=" + sharedValue);
    }
}
