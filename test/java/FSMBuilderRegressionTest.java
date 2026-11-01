import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** Phase-1 behaviour must be unchanged: 5 traces x 2 key files give byte-identical .dot files. */
class FSMBuilderRegressionTest {
    @TempDir Path tmp;
    static final Path RES = TestSupport.ROOT.resolve("test/resources");

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
        "trace,keys", "trace,keys_x_only", "trace2,keys", "trace2,keys_x_only", "trace_positive,keys", "trace_positive,keys_x_only",
        "trace_negative,keys", "trace_negative,keys_x_only", "trace_loop,keys", "trace_loop,keys_x_only" })
    void phase1OutputIsByteIdentical(String trace, String keys) throws Exception {
        TestSupport.runClass(tmp, "FSMBuilder", RES.resolve("traces/" + trace + ".txt").toString(), RES.resolve("traces/" + keys + ".txt").toString());
        assertEquals(Files.readString(RES.resolve("golden/" + trace + "_" + keys + ".dot")), Files.readString(tmp.resolve("fsm.dot")));
    }

    @Test void rawIntsKeepsConcreteValues() throws Exception {
        TestSupport.runClass(tmp, "FSMBuilder", RES.resolve("traces/trace_positive.txt").toString(), RES.resolve("traces/keys.txt").toString(), "--raw-ints");
        String dot = Files.readString(tmp.resolve("fsm.dot"));
        assertTrue(dot.contains("x=2 | y=1"), dot);
        assertFalse(dot.contains("x=POSITIVE"));
    }

    @Test void subgraphKeepsOnlyMatchingStates() throws Exception {
        Path t = tmp.resolve("t.txt"); Files.writeString(t, "E1:d=up,f=1\nE2:d=up,f=2\nE3:d=down,f=2\nE4:d=down,f=1\n");
        Path k = tmp.resolve("k.txt"); Files.writeString(k, "f,d\n");
        TestSupport.runClass(tmp, "FSMBuilder", t.toString(), k.toString(), "--raw-ints", "--subgraph", "d=down");
        String dot = Files.readString(tmp.resolve("fsm.dot"));
        assertTrue(dot.contains("\"d=down | f=2\" -> \"d=down | f=1\""), dot);
        assertFalse(dot.contains("d=up | f=2\" ->"), dot);
    }
}
