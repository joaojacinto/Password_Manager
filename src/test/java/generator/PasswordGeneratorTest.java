package generator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordGeneratorTest {
    private PasswordGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new PasswordGenerator();
    }

    @Test
    void generatesPasswordWithRequestedLength() {
        String password = generator.generate(20, true, true, true, true);

        assertEquals(20, password.length());
    }

    @RepeatedTest(100)
    void containsAtLeastOneCharacterFromEachSelectedPool() {
        String password = generator.generate(16, true, true, true, true);

        assertTrue(password.matches(".*[a-z].*"), "missing lowercase");
        assertTrue(password.matches(".*[A-Z].*"), "missing uppercase");
        assertTrue(password.matches(".*[0-9].*"), "missing digit");
        assertTrue(password.matches(".*[^a-zA-Z0-9].*"), "missing symbol");
    }

    @RepeatedTest(100)
    void minimumLengthStillContainsEveryPool() {
        // edge case: length equals the number of pools
        String password = generator.generate(4, true, true, true, true);

        assertEquals(4, password.length());
        assertTrue(password.matches(".*[a-z].*"));
        assertTrue(password.matches(".*[A-Z].*"));
        assertTrue(password.matches(".*[0-9].*"));
        assertTrue(password.matches(".*[^a-zA-Z0-9].*"));
    }

    @RepeatedTest(100)
    void neverUsesUnselectedPools() {
        String lettersOnly = generator.generate(30, true, true, false, false);
        String digitsOnly  = generator.generate(30, false, false, true, false);

        assertTrue(lettersOnly.matches("[a-zA-Z]+"));
        assertTrue(digitsOnly.matches("[0-9]+"));
    }

    @Test
    void throwsWhenNoPoolIsSelected() {
        assertThrows(IllegalArgumentException.class,
                () -> generator.generate(16, false, false, false, false));
    }

    @Test
    void throwsWhenLengthIsSmallerThanNumberOfPools() {
        assertThrows(IllegalArgumentException.class,
                () -> generator.generate(3, true, true, true, true));
    }
}