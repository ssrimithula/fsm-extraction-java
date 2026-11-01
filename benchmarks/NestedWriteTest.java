/** Sanity test: a NESTED class writes the tracked static field of its outer class directly. */
public class NestedWriteTest {
    static int v = 0;
    static class Worker extends Thread { public void run() { for (int i = 1; i <= 3; i++) v = i; } }
    public static void main(String[] a) throws Exception { Worker w = new Worker(); w.start(); w.join(); v = 0; }
}
