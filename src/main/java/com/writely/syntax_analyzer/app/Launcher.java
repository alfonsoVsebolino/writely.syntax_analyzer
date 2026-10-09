package com.writely.syntax_analyzer.app;

import javafx.application.Application;

/**
 * Bootstrap entry point for the writely desktop GUI.
 * Delegates to {@link Application#launch(Class, String...)} to circumvent JavaFX module runtime restrictions.
 */
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
