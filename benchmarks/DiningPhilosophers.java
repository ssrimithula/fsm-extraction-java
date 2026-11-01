import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Classic Dining Philosophers, adapted so that each philosopher's state is
 * observable by FSMAgent's bytecode instrumentation (Objective O4).
 *
 * Two design choices exist ONLY because of how the BCI agent works, and are
 * worth being able to explain in review:
 *
 * 1. State is five separate static String fields (state0..state4), NOT a
 *    String[] array. FSMAgent instruments PUTFIELD/PUTSTATIC - a genuine
 *    field write. An array element write compiles to AASTORE, a completely
 *    different bytecode instruction the agent does not (and, as currently
 *    scoped, cannot) intercept. Using an array would make the philosophers'
 *    states invisible to the agent entirely.
 *
 * 2. States are Strings ("THINKING"/"HUNGRY"/"EATING"), not ints. FSMBuilder
 *    (unchanged from Phase 1) abstracts any value it can parse as an int
 *    into POSITIVE/NEGATIVE/ZERO. If states were encoded as 0/1/2, HUNGRY
 *    and EATING would both abstract to POSITIVE and become indistinguishable
 *    in the resulting FSM. Non-numeric strings pass through FSMBuilder's
 *    abstractValue() unchanged, which is what we want here.
 *
 * Deadlock avoidance: every philosopher acquires the LOWER-indexed fork
 * first, regardless of which side it's physically on. This breaks the
 * circular-wait condition that causes the textbook deadlock.
 */
public class DiningPhilosophers {

    static final int N = 5;
    static int MEALS_PER_PHILOSOPHER = 2;

    // One field per philosopher - see class-level note above for why this
    // isn't a String[] array.
    static volatile String state0 = "THINKING";
    static volatile String state1 = "THINKING";
    static volatile String state2 = "THINKING";
    static volatile String state3 = "THINKING";
    static volatile String state4 = "THINKING";

    static final Lock[] forks = new Lock[N];

    // Ground-truth check, completely independent of FSMAgent/TraceGenerator
    // (forkOwner is NOT in instrumentation_dp.txt, so the agent never
    // touches it). Used only to verify whether the trace's picture of
    // concurrency matches what actually happened.
    static final AtomicIntegerArray forkOwner = new AtomicIntegerArray(N);
    static void claim(int forkIndex, int philosopherId) {
        int prev = forkOwner.getAndSet(forkIndex, philosopherId);
        if (prev != -1) {
            System.out.println("*** GROUND-TRUTH VIOLATION: fork " + forkIndex
                    + " claimed by P" + philosopherId + " but was already held by P" + prev);
        }
    }
    static void release(int forkIndex, int philosopherId) {
        boolean ok = forkOwner.compareAndSet(forkIndex, philosopherId, -1);
        if (!ok) {
            System.out.println("*** GROUND-TRUTH VIOLATION: P" + philosopherId
                    + " tried to release fork " + forkIndex + " it didn't own");
        }
    }

    /** Central setter so every state change goes through one instrumented method. */
    static synchronized void setState(int id, String value) {
        switch (id) {
            case 0: state0 = value; break;
            case 1: state1 = value; break;
            case 2: state2 = value; break;
            case 3: state3 = value; break;
            case 4: state4 = value; break;
            default: throw new IllegalArgumentException("bad philosopher id: " + id);
        }
    }

    static class Philosopher extends Thread {
        final int id;

        Philosopher(int id) {
            super("Philosopher-" + id);
            this.id = id;
        }

        @Override
        public void run() {
            int left = id;
            int right = (id + 1) % N;
            int first = Math.min(left, right);   // always lock the lower index first
            int second = Math.max(left, right);  // ...to avoid circular wait

            try {
                for (int meal = 0; meal < MEALS_PER_PHILOSOPHER; meal++) {
                    setState(id, "THINKING");
                    Thread.sleep(10 + (int) (Math.random() * 30));

                    setState(id, "HUNGRY");
                    forks[first].lock();
                    try {
                        forks[second].lock();
                        try {
                            claim(first, id);
                            claim(second, id);
                            setState(id, "EATING");
                            Thread.sleep(10 + (int) (Math.random() * 30));
                            // FIX (found via the 40-run experiment): the philosopher must stop
                            // being EATING *before* the forks are released. Otherwise a neighbour
                            // can grab a fork, set EATING, and the model briefly shows two
                            // adjacent philosophers eating - a real update race in the program,
                            // not a tracing artifact.
                            setState(id, "THINKING");
                            release(first, id);
                            release(second, id);
                        } finally {
                            forks[second].unlock();
                        }
                    } finally {
                        forks[first].unlock();
                    }
                }
                setState(id, "THINKING"); // final state after all meals
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0) {
            MEALS_PER_PHILOSOPHER = Integer.parseInt(args[0]);
        }
        long startTime = System.nanoTime();

        for (int i = 0; i < N; i++) {
            forks[i] = new ReentrantLock();
            forkOwner.set(i, -1); // sentinel for "free" - must not be a valid philosopher id (0..N-1)
        }

        Philosopher[] philosophers = new Philosopher[N];
        for (int i = 0; i < N; i++) {
            philosophers[i] = new Philosopher(i);
        }
        for (Philosopher p : philosophers) {
            p.start();
        }
        for (Philosopher p : philosophers) {
            p.join();
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        System.out.println("All philosophers finished eating. meals=" + MEALS_PER_PHILOSOPHER + " elapsed_ms=" + elapsedMs);
    }
}
