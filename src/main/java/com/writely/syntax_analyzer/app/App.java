package com.writely.syntax_analyzer.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Main JavaFX Application bootstrap for writely syntax analyzer.
 */
public class App extends Application {

    public static final String APPLICATION_TITLE = "writely";
    public static final int MIN_WINDOW_WIDTH = 1024;
    public static final int MIN_WINDOW_HEIGHT = 680;
    public static final int DEFAULT_WINDOW_WIDTH = 1200;
    public static final int DEFAULT_WINDOW_HEIGHT = 750;

    @Override
    public void start(Stage primaryStage) throws IOException {
        URL fxmlLocation = getClass().getResource("/fxml/main-workspace.fxml");
        if (fxmlLocation == null) {
            throw new IllegalStateException("FXML resource not found: /fxml/main-workspace.fxml");
        }

        FXMLLoader loader = new FXMLLoader(fxmlLocation);
        Parent root = loader.load();

        Scene scene = new Scene(root, DEFAULT_WINDOW_WIDTH, DEFAULT_WINDOW_HEIGHT);

        URL cssLocation = getClass().getResource("/css/workspace.css");
        if (cssLocation != null) {
            scene.getStylesheets().add(cssLocation.toExternalForm());
        }

        primaryStage.setTitle(APPLICATION_TITLE);
        primaryStage.setMinWidth(MIN_WINDOW_WIDTH);
        primaryStage.setMinHeight(MIN_WINDOW_HEIGHT);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
