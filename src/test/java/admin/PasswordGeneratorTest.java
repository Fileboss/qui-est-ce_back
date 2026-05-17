package admin;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordGeneratorTest {

    @Test
    void generate_meetsComplexityOver50Runs() {
        for (int i = 0; i < 50; i++) {
            String pw = PasswordGenerator.generate();
            assertTrue(pw.length() >= 16, "length too short: " + pw);
            assertTrue(pw.matches(".*[a-z].*"), "no lowercase: " + pw);
            assertTrue(pw.matches(".*[A-Z].*"), "no uppercase: " + pw);
            assertTrue(pw.matches(".*\\d.*"), "no digit: " + pw);
            assertTrue(pw.matches(".*[!@#$%^&*\\-_+=].*"), "no symbol: " + pw);
        }
    }

    @Test
    void generate_producesDistinctPasswords() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(PasswordGenerator.generate());
        }
        assertTrue(seen.size() == 100, "duplicate passwords generated: " + (100 - seen.size()));
    }
}
