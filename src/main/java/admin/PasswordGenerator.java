package admin;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PasswordGenerator {

    private static final int LENGTH = 20;
    private static final char[] LOWER = "abcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final char[] UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final char[] DIGIT = "0123456789".toCharArray();
    private static final char[] SYMBOL = "!@#$%^&*-_+=".toCharArray();
    private static final char[] ALL;
    private static final SecureRandom RANDOM = new SecureRandom();

    static {
        ALL = new char[LOWER.length + UPPER.length + DIGIT.length + SYMBOL.length];
        int i = 0;
        System.arraycopy(LOWER, 0, ALL, i, LOWER.length);
        i += LOWER.length;
        System.arraycopy(UPPER, 0, ALL, i, UPPER.length);
        i += UPPER.length;
        System.arraycopy(DIGIT, 0, ALL, i, DIGIT.length);
        i += DIGIT.length;
        System.arraycopy(SYMBOL, 0, ALL, i, SYMBOL.length);
    }

    private PasswordGenerator() {}

    public static String generate() {
        List<Character> chars = new ArrayList<>(LENGTH);
        chars.add(LOWER[RANDOM.nextInt(LOWER.length)]);
        chars.add(UPPER[RANDOM.nextInt(UPPER.length)]);
        chars.add(DIGIT[RANDOM.nextInt(DIGIT.length)]);
        chars.add(SYMBOL[RANDOM.nextInt(SYMBOL.length)]);
        while (chars.size() < LENGTH) {
            chars.add(ALL[RANDOM.nextInt(ALL.length)]);
        }
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder(LENGTH);
        for (char c : chars) {
            sb.append(c);
        }
        return sb.toString();
    }
}
