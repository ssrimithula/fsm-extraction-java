import java.util.Random;

/**
 * Elevator Controller benchmark (the use-case scenario the guide suggested; models the
 * system of the base paper's Section 6.1 / Figure 10): ONE elevator serving nine floors
 * and several passengers modelled as concurrent threads that repeatedly ride between
 * random floors. The elevator uses a SCAN policy: keep moving in the current direction
 * while there is a request ahead, otherwise reverse.
 *
 * Observable key fields (what FSMAgent tracks - see configs/elevator.instr):
 *     f : current floor (int, 1..9)       d : direction ("up" / "down")
 * Both are plain static fields written only through setFloor()/setDirection(); the
 * program contains NO tracing code. The agent finds those writes in the bytecode.
 *
 * Agent-driven design choices (same reasons as the other benchmarks):
 *   - f and d are fields, not locals, and not array elements (PUTSTATIC is what is hooked).
 *   - d is a String so FSMBuilder keeps it as-is; f is an int, shown with --raw-ints.
 *
 * Ground truth (independent of the agent): every passenger must be delivered to the
 * floor it asked for, and the elevator must move exactly one floor per step.
 */
public class ElevatorController {

    static final int FLOORS = 9;
    static int PASSENGERS = 4;
    static int TRIPS = 6;

    // ---- tracked key fields -------------------------------------------------
    static volatile int f = 1;
    static volatile String d = "up";

    // ---- internal bookkeeping (NOT tracked) ---------------------------------
    static final Object lock = new Object();
    static final int[] waiting = new int[FLOORS + 1]; // passengers waiting to board at each floor
    static final int[] inside  = new int[FLOORS + 1]; // riders whose destination is each floor
    static boolean done = false;
    static int violations = 0;

    static void setFloor(int nf) {
        if (Math.abs(nf - f) != 1 || nf < 1 || nf > FLOORS) {
            violations++;
            System.out.println("*** GROUND-TRUTH VIOLATION: illegal floor move " + f + " -> " + nf);
        }
        f = nf;
    }
    static void setDirection(String nd) { d = nd; }

    static boolean requestsAbove() { for (int g = f + 1; g <= FLOORS; g++) if (waiting[g] + inside[g] > 0) return true; return false; }
    static boolean requestsBelow() { for (int g = f - 1; g >= 1; g--) if (waiting[g] + inside[g] > 0) return true; return false; }
    static boolean anyRequest()    { for (int g = 1; g <= FLOORS; g++) if (waiting[g] + inside[g] > 0) return true; return false; }

    static class Elevator extends Thread {
        Elevator() { super("Elevator"); setDaemon(true); }
        @Override public void run() {
            synchronized (lock) {
                try {
                    while (!done) {
                        if (!anyRequest()) { lock.wait(); continue; }
                        // 1. serve the current floor: wait until everyone boards / alights
                        while (waiting[f] + inside[f] > 0) { lock.notifyAll(); lock.wait(); }
                        if (!anyRequest()) continue;
                        // 2. SCAN: continue in the current direction if anything is ahead, else reverse
                        if (d.equals("up")) {
                            if (requestsAbove()) setFloor(f + 1);
                            else { setDirection("down"); }
                        } else {
                            if (requestsBelow()) setFloor(f - 1);
                            else { setDirection("up"); }
                        }
                        lock.wait(2); // travel time per floor
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }
    }

    static class Passenger extends Thread {
        final int id; final Random rnd;
        int at;
        Passenger(int id) { super("Passenger-" + id); this.id = id; this.rnd = new Random(1000 + id); this.at = 1 + rnd.nextInt(FLOORS); }
        @Override public void run() {
            try {
                for (int t = 0; t < TRIPS; t++) {
                    int dest; do { dest = 1 + rnd.nextInt(FLOORS); } while (dest == at);
                    Thread.sleep(1 + rnd.nextInt(10));
                    synchronized (lock) {
                        waiting[at]++; lock.notifyAll();
                        while (f != at || !isServingFloor(at)) lock.wait();
                        waiting[at]--; inside[dest]++; lock.notifyAll();
                        while (f != dest || !isServingFloor(dest)) lock.wait();
                        inside[dest]--; lock.notifyAll();
                        if (f != dest) { violations++; System.out.println("*** GROUND-TRUTH VIOLATION: delivered to wrong floor"); }
                        at = dest;
                    }
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    /** The elevator is "serving" floor g while it is standing at g with unfinished boarding/alighting there. */
    static boolean isServingFloor(int g) { return f == g; }

    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0) TRIPS = Integer.parseInt(args[0]);
        if (args.length > 1) PASSENGERS = Integer.parseInt(args[1]);
        long start = System.nanoTime();
        Elevator e = new Elevator(); e.start();
        Passenger[] ps = new Passenger[PASSENGERS];
        for (int i = 0; i < PASSENGERS; i++) { ps[i] = new Passenger(i); ps[i].start(); }
        for (Passenger p : ps) p.join();
        synchronized (lock) { done = true; lock.notifyAll(); }
        e.join(2000);
        long ms = (System.nanoTime() - start) / 1_000_000;
        System.out.println("All trips completed. trips=" + TRIPS + " passengers=" + PASSENGERS
                + " ground_truth_violations=" + violations + " elapsed_ms=" + ms);
    }
}
