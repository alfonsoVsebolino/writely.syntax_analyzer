package com.example.service;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import static java.lang.Math.max;

/**
 * Valid complete Java program fixture.
 */
public class CompleteProgram {

    private final String serviceName;
    private int counter = 0;

    public CompleteProgram(String serviceName) {
        this.serviceName = serviceName;
    }

    public synchronized int incrementAndGet(int delta) {
        counter += delta;
        return counter;
    }

    public List<String> processItems(List<String> items) {
        List<String> results = new ArrayList<>();
        for (String item : items) {
            if (item != null && !item.isEmpty()) {
                results.add(item.trim());
            }
        }
        return results;
    }
}
