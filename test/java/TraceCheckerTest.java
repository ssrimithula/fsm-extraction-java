import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/** The property checker must accept good traces AND reject bad ones (a checker that never fails is worthless). */
class TraceCheckerTest {
    @TempDir Path tmp;
    String check(String prop, String trace) throws Exception {
        Path t = tmp.resolve("t.txt"); Files.writeString(t, trace);
        return TestSupport.runClass(tmp, "TraceChecker", prop, t.toString());
    }
    @Test void dpAdjacentEatingIsRejected() throws Exception {
        assertTrue(check("dp", "E:state0=EATING,state1=EATING,state2=THINKING,state3=THINKING,state4=THINKING\n").contains("VIOLATED"));
    }
    @Test void dpNonAdjacentEatingIsAccepted() throws Exception {
        assertTrue(check("dp", "E:state0=EATING,state1=THINKING,state2=EATING,state3=THINKING,state4=THINKING\n").contains("HOLDS"));
    }
    @Test void dpWrapAroundAdjacencyIsChecked() throws Exception {   // philosopher 4 is next to philosopher 0
        assertTrue(check("dp", "E:state0=EATING,state1=THINKING,state2=THINKING,state3=THINKING,state4=EATING\n").contains("VIOLATED"));
    }
    @Test void rwReaderAndWriterTogetherIsRejected() throws Exception {
        assertTrue(check("rw", "E:reader0State=READING,reader1State=IDLE,writer0State=WRITING\n").contains("VIOLATED"));
    }
    @Test void rwManyReadersAreAccepted() throws Exception {
        assertTrue(check("rw", "E:reader0State=READING,reader1State=READING,reader2State=READING,writer0State=WAITING\n").contains("HOLDS"));
    }
    @Test void elevatorWrongWayIsRejected() throws Exception {
        assertTrue(check("elevator", "E:d=up,f=3\nE:d=up,f=2\n").contains("VIOLATED"));
    }
    @Test void elevatorDirectionChangeOnSameFloorIsAccepted() throws Exception {
        assertTrue(check("elevator", "E:d=up,f=8\nE:d=up,f=9\nE:d=down,f=9\nE:d=down,f=8\n").contains("HOLDS"));
    }
}
