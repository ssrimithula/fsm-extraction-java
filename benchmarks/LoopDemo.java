/**
 * The loop examples of the base paper (Sec 4.2, Figures 5 and 6), as a runnable program.
 * Key variable: x (a static field). Algorithm 1 merges equal values and invents cycles that are not
 * in the source; Algorithm 2 (loop-aware) keeps the loop id as a hidden part of the state.
 */
public class LoopDemo {
    static int x = 0;
    public static void main(String[] args) {
        x = 1; x = 2;
        for (int i = 0; i < 3; i++) { x = 3; x = 2; x = 4; }      // Fig 5: single loop, x=2 appears inside AND before it
        for (int i = 0; i < 2; i++) { x = 5; x = 6; }              // Fig 6(B): a sequence of loops ...
        for (int i = 0; i < 2; i++) { x = 7; x = 8; }
        for (int a = 0; a < 2; a++) {                              // Fig 6(C): nested loops
            x = 9;
            for (int b = 0; b < 3; b++) { x = 10; x = 11; }
            x = 12;
        }
        x = 0;
        System.out.println("LoopDemo finished, x=" + x);
    }
}
