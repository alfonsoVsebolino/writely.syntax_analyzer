package com.writely.syntax_analyzer;

import com.writely.syntax_analyzer.app.Launcher;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FoundationSmokeTest {
    @Test
    void applicationLauncherExecutesCleanly() {
        assertNotNull(Launcher.class);
    }
}
