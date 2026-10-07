package com.writely.syntax_analyzer;

import com.writely.syntax_analyzer.app.Launcher;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class FoundationSmokeTest {
    @Test
    void applicationLauncherExecutesCleanly() {
        assertDoesNotThrow(() -> Launcher.main(new String[0]));
    }
}
