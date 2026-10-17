package org.example;

import crypto.CryptoService;
import generator.PasswordGenerator;

public class Main {
    static void main() {

        CryptoService crypto = new CryptoService();
        byte[] salt = crypto.generateSalt();

        byte[] a = crypto.deriveKey("hello".toCharArray(), salt).getEncoded();
        byte[] b = crypto.deriveKey("hello".toCharArray(), salt).getEncoded();
        byte[] c = crypto.deriveKey("hellp".toCharArray(), salt).getEncoded();

        System.out.println(java.util.HexFormat.of().formatHex(a));
        System.out.println(java.util.HexFormat.of().formatHex(b));
        System.out.println(java.util.HexFormat.of().formatHex(c));
    }
}
