package com.writely.syntax_analyzer.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AppTest {

    @Test
    @DisplayName("App constants match specifications")
    void testAppConstants() {
        assertEquals("writely", App.APPLICATION_TITLE);
        assertEquals(1024, App.MIN_WINDOW_WIDTH);
        assertEquals(680, App.MIN_WINDOW_HEIGHT);
        assertEquals(1200, App.DEFAULT_WINDOW_WIDTH);
        assertEquals(750, App.DEFAULT_WINDOW_HEIGHT);
    }

    @Test
    @DisplayName("App class can be instantiated")
    void testAppInstantiation() {
        App app = new App();
        assertNotNull(app);
    }
}
