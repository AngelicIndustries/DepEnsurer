package industries.angelic.depensurer.api.processor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class VersionRangesTest {

    @Test
    public void acceptsMavenRanges() {
        for (String spec : new String[] { "[1.0]", "[1.0,2.0)", "(1.0,2.0]", "(,1.0]", "[1.0,)", "[1.0,1.5),[2.0,)",
            " [1.0 , 2.0) " }) {
            assertNull(spec, VersionRanges.check(spec));
        }
    }

    @Test
    public void rejectsEverythingElse() {
        for (String spec : new String[] { "", "1.0", "[1.0", "(1.0)", "[1.0)", "[,1.0]", "[1.0,]", "[,]", "(,)",
            "[1,2,3]", "[1.0],", "[1.0]x", "[1.0][2.0]", "[]" }) {
            assertNotNull(spec, VersionRanges.check(spec));
        }
    }
}
