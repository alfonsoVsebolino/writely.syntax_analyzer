package com.writely.syntax_analyzer.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LauncherTest {

    @Test
    @DisplayName("Launcher has private constructor and is final utility class")
    void testLauncherStructure() throws NoSuchMethodException {
        assertTrue(Modifier.isFinal(Launcher.class.getModifiers()));

        Constructor<Launcher> constructor = Launcher.class.getDeclaredConstructor();
        assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        constructor.setAccessible(true);
    }
}
