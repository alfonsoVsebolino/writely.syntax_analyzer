package com.example.flow;

public class ControlFlowAndExpressions {

    public int evaluate(int a, int b, int mode) {
        int result = 0;

        // Conditionals
        if (a > b) {
            result = a - b;
        } else if (a == b) {
            result = a * 2;
        } else {
            result = b - a;
        }

        // Switch statement
        switch (mode) {
            case 1:
                result += 10;
                break;
            case 2:
                result *= 2;
                break;
            default:
                result -= 1;
                break;
        }

        // Loops
        for (int i = 0; i < 5; i++) {
            result += i;
        }

        int count = 3;
        while (count > 0) {
            result++;
            count--;
        }

        do {
            result--;
        } while (result > 100);

        return result;
    }
}
