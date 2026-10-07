package com.example.modern;

import java.util.List;
import java.util.function.Function;

public class ModernJavaConstructs {

    public record UserRecord(String id, String email, int age) {
        public UserRecord {
            if (email == null) {
                throw new IllegalArgumentException("email required");
            }
        }
    }

    public interface Formatter<T> {
        String format(T item);

        default void print(T item) {
            System.out.println(format(item));
        }
    }

    public String processStatus(int code) {
        return switch (code) {
            case 200 -> "OK";
            case 404 -> "Not Found";
            default -> "Unknown";
        };
    }

    public void runLambdas(List<String> list) {
        Function<String, Integer> mapper = s -> s.length();
        list.stream().map(mapper).forEach(System.out::println);
    }
}
