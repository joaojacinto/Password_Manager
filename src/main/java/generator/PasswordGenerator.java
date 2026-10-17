package generator;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class PasswordGenerator {
    // unchangeable sequence
    private static final String LOWER   = "abcdefghijklmnopqrstuvwxyz";
    private static final String UPPER   = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String DIGITS  = "0123456789";
    private static final String SYMBOLS = "!@#$%^&*()-_=+[]{};:,.<>?";

    // class that provides a cryptographically strong random number generator
    private final SecureRandom random = new SecureRandom();

    // method signature for length and on/off switches. returns string
    public String generate(
            int length, boolean lower, boolean upper,
            boolean digits, boolean symbols) {

        // only adds the "on's" the user switched
        List<String> pools = new ArrayList<>();
        if (lower)   pools.add(LOWER);
        if (upper)   pools.add(UPPER);
        if (digits)  pools.add(DIGITS);
        if (symbols) pools.add(SYMBOLS);


        // exceptions for empty list or length too short for the user input
        if (pools.isEmpty()) {
            throw new IllegalArgumentException("Select at least one character type.");
        }
        if (length < pools.size()) {
            throw new IllegalArgumentException("Length too short for the selected options.");
        }


        // adds the pools selected to the password array
        // Character = object char
        List<Character> password = new ArrayList<>();
        for (String pool : pools) {
            password.add(randomChar(pool));
        }

        // creates the combined string from the selected pool
        String combined = String.join("", pools);
        // while the password is shorter than the length from the user input, add one more random character to the password list
        while (password.size() < length) {
            password.add(randomChar(combined));
        }

        // shuffle the password with collections method shuffle
        // by passing random argument makes the shuffle use the secure generator instead of a default one.
        Collections.shuffle(password, random);

        // converting the list to a single string
        // for each char in password char list, add each char to stringbuilder
        StringBuilder result = new StringBuilder();
        for (char c : password) {
            result.append(c);
        }

        // toString to return the string
        return result.toString();
    }

    // helper method
    private char randomChar(String pool) {
        return pool.charAt(random.nextInt(pool.length()));
    }
}
