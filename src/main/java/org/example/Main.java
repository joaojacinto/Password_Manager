package org.example;

import crypto.CryptoService;
import generator.PasswordGenerator;

import javax.crypto.SecretKey;

public class Main {
    static void main() {

        CryptoService crypto = new CryptoService();
        SecretKey key = crypto.deriveKey("hello".toCharArray(), crypto.generateSalt());
        byte[] data = "same text".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] a = crypto.encrypt(key, data);
        byte[] b = crypto.encrypt(key, data);

        java.util.HexFormat hex = java.util.HexFormat.of();
        System.out.println(hex.formatHex(a));
        System.out.println(hex.formatHex(b));
    }
}
