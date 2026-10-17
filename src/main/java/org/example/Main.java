package org.example;

import generator.PasswordGenerator;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    static void main() {
        //TIP Press <shortcut actionId="ShowIntentionActions"/> with your caret at the highlighted text
        // to see how IntelliJ IDEA suggests fixing it.
        PasswordGenerator passwordGenerator = new PasswordGenerator();

        System.out.println(passwordGenerator.generate(16, true, true, true, true));
    }
}
