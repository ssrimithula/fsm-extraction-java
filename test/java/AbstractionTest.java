import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Algorithm 3: binary and multivalued abstraction functions. */
class AbstractionTest {
    @Test void multivaluedRegions() {
        String[] s = "f:multi:3:6".split(":");
        assertEquals("<3", ModelAbstractor.apply(s, "1"));
        assertEquals("<3", ModelAbstractor.apply(s, "2"));
        assertEquals("3-6", ModelAbstractor.apply(s, "3"));
        assertEquals("3-6", ModelAbstractor.apply(s, "6"));
        assertEquals(">6", ModelAbstractor.apply(s, "7"));
    }
    @Test void multivaluedWithThreeThresholds() {
        String[] s = "x:multi:0:10:20".split(":");
        assertEquals("<0", ModelAbstractor.apply(s, "-1"));
        assertEquals("0-10", ModelAbstractor.apply(s, "5"));
        assertEquals("10-20", ModelAbstractor.apply(s, "15"));
        assertEquals(">20", ModelAbstractor.apply(s, "21"));
    }
    @Test void binaryPredicates() {
        String[] eq = "s:bin:=:EATING:E:~E".split(":");
        assertEquals("E", ModelAbstractor.apply(eq, "EATING"));
        assertEquals("~E", ModelAbstractor.apply(eq, "HUNGRY"));
        String[] gt = "f:bin:>:5:high:low".split(":");
        assertEquals("high", ModelAbstractor.apply(gt, "6"));
        assertEquals("low", ModelAbstractor.apply(gt, "5"));
    }
}
