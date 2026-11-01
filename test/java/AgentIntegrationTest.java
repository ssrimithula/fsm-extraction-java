import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

/** End-to-end: the real agent on the real benchmark programs. */
class AgentIntegrationTest {
    @TempDir Path tmp;

    @Test void testProgramTraceIsAutomaticAndFull() throws Exception {
        Path tr = tmp.resolve("t.txt");
        TestSupport.runWithAgent(tmp, "testprogram.instr", tr, false, "7\n", "TestProgram");
        String all = Files.readString(tr);
        assertTrue(all.contains("TestProgram.x_WRITE:x=1,y=0"), all);       // full state on every line
        assertTrue(all.contains("TestProgram.y_WRITE:x=2,y=1"), all);
        assertFalse(TestSupport.lines(tr).get(0).equals("TestProgram.x_WRITE:x=0"), "partial first state must be suppressed");
    }

    @Test void agentDoesNotChangeProgramOutput() throws Exception {
        String plain = TestSupport.run(tmp, "7\n", "-cp", TestSupport.CLASSES.toString(), "TestProgram");
        String agent = TestSupport.runWithAgent(tmp, "testprogram.instr", tmp.resolve("t.txt"), false, "7\n", "TestProgram")
                .replaceAll("(?m)^\\[FSMAgent\\].*\\R", "");
        assertEquals(plain, agent);
    }

    @Test void nestedClassWritesAreCaptured() throws Exception {
        Path tr = tmp.resolve("t.txt");
        TestSupport.runWithAgent(tmp, "nested.instr", tr, false, null, "NestedWriteTest");
        List<String> l = TestSupport.lines(tr);
        assertEquals(5, l.size(), l.toString());           // v=0, 1, 2, 3, 0
    }

    @Test void diningPhilosophersHoldsSafetyAndGivesPaperStates() throws Exception {
        Set<String> abstractStates = new TreeSet<>();
        for (int run = 0; run < 5; run++) {
            Path tr = tmp.resolve("dp" + run + ".txt");
            String out = TestSupport.runWithAgent(tmp, "dp.instr", tr, false, null, "DiningPhilosophers", "3");
            assertFalse(out.contains("GROUND-TRUTH VIOLATION"), out);
            assertTrue(TestSupport.runClass(tmp, "TraceChecker", "dp", tr.toString()).contains("HOLDS"));
            Path bin = tmp.resolve("bin" + run + ".txt");
            TestSupport.runClass(tmp, "PredicateAbstractor", tr.toString(), bin.toString(), "state0,state1,state2,state3,state4", "EATING", "E", "~E");
            TestSupport.lines(bin).forEach(x -> abstractStates.add(x.substring(x.indexOf(':') + 1)));
        }
        assertTrue(abstractStates.size() <= 11, "paper's maximum is 11 abstract states, got " + abstractStates.size());
    }

    @Test void readersWritersHoldsSafety() throws Exception {
        for (int run = 0; run < 5; run++) {
            Path tr = tmp.resolve("rw" + run + ".txt");
            TestSupport.runWithAgent(tmp, "rw.instr", tr, false, null, "ReadersWriters", "3");
            assertTrue(TestSupport.runClass(tmp, "TraceChecker", "rw", tr.toString()).contains("HOLDS"));
        }
    }

    @Test void elevatorHoldsPaperPropertyAndDeliversEveryone() throws Exception {
        Path tr = tmp.resolve("el.txt");
        String out = TestSupport.runWithAgent(tmp, "elevator.instr", tr, false, null, "ElevatorController", "10", "4");
        assertTrue(out.contains("ground_truth_violations=0"), out);
        assertTrue(TestSupport.runClass(tmp, "TraceChecker", "elevator", tr.toString()).contains("HOLDS"));
    }

    // ---- Algorithm 2 -------------------------------------------------------------------------------
    @Test void loopIdsAreStampedAndSameValueInsideAndOutsideLoopIsDistinguished() throws Exception {
        Path tr = tmp.resolve("ld.txt");
        TestSupport.runWithAgent(tmp, "loopdemo.instr", tr, true, null, "LoopDemo");
        List<String> l = TestSupport.lines(tr);
        assertTrue(l.stream().anyMatch(s -> s.contains("loop=L0,x=2")), "x=2 before the loop");
        assertTrue(l.stream().anyMatch(s -> s.contains("loop=L1,x=2")), "x=2 inside the loop");
        assertTrue(Files.exists(Paths.get(tr + ".loops")));
        assertEquals(5, TestSupport.lines(Paths.get(tr + ".loops")).size(), "LoopDemo has 5 loops");
    }

    @Test void loopAwareBuilderSeparatesWhatAlgorithm1Merges() throws Exception {
        Path tr = tmp.resolve("ld.txt");
        TestSupport.runWithAgent(tmp, "loopdemo.instr", tr, true, null, "LoopDemo");
        Path keys = TestSupport.ROOT.resolve("configs/loopdemo.keys");
        // Algorithm 2: 14 states (x=2 appears twice: before the loop and inside it)
        String out = TestSupport.runClass(tmp, "LoopAwareFSMBuilder", tr.toString(), keys.toString(), tmp.resolve("a2.dot").toString(), "--raw-ints");
        assertTrue(out.contains("14 states"), out);
        // Algorithm 1: 13 states (x=0..12; both x=2 merged) -> counts distinct node labels
        TestSupport.runClass(tmp, "FSMBuilder", tr.toString(), keys.toString(), "--raw-ints", "--clean");
        long a1 = Files.readAllLines(tmp.resolve("fsm.dot")).stream().filter(s -> s.contains("->") && !s.contains("START")).count();
        assertTrue(a1 > 0);
        assertTrue(Files.readString(tmp.resolve("fsm.dot")).contains("\"x=3\" -> \"x=2\""),
                "Algorithm 1 invents the loop edge x=3 -> x=2 that Algorithm 2 avoids");
    }

    @Test void loopModeDoesNotBreakRealPrograms() throws Exception {
        Path tr = tmp.resolve("dp.txt");
        String out = TestSupport.runWithAgent(tmp, "dp.instr", tr, true, null, "DiningPhilosophers", "2");
        assertFalse(out.contains("GROUND-TRUTH VIOLATION"), out);
        assertTrue(TestSupport.runClass(tmp, "TraceChecker", "dp", tr.toString()).contains("HOLDS"));
        assertTrue(Files.readString(tr).contains("loop=L"));
    }
}
