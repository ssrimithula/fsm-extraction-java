import java.util.Scanner;

public class TestProgram {
    // x and y are static fields (not method-local variables) so that
    // FSMAgent's bytecode instrumentation, which hooks PUTFIELD/PUTSTATIC,
    // can observe every write automatically -- no manual println tracing
    // needed once the agent is attached.
    static int x = 0;
    static int y = 0;

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        System.out.print("Enter a number: ");
        int input = sc.nextInt();

        if (input > 0) {
            x = 1; y = 0;
            System.out.println("POSITIVE_INIT:x=" + x + ",y=" + y);

            x = 2; y = 1;
            System.out.println("POSITIVE_INCREMENT:x=" + x + ",y=" + y);
        } else {
            x = -1; y = 0;
            System.out.println("NEGATIVE_INIT:x=" + x + ",y=" + y);

            x = -2; y = -1;
            System.out.println("NEGATIVE_INCREMENT:x=" + x + ",y=" + y);
        }

        x = 0; y = 0;
        System.out.println("FINAL:x=" + x + ",y=" + y);

        sc.close();
    }
}
