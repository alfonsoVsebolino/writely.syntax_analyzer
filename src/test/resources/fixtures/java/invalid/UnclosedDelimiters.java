package com.example.invalid;

public class UnclosedDelimiters {
    public void test(int x) {
        if (x > 0 {
            System.out.println("Invalid");
        }
    }
}
