package com.writely.syntax_analyzer.app;

import javafx.application.Platform;
import javafx.scene.image.Image;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SvgImageLoaderTest {

    @BeforeAll
    static void initJavaFx() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                latch.countDown();
            });
        } catch (IllegalStateException e) {
            latch.countDown();
        }
        latch.await();
    }

    @Test
    void loadsWritelyRuntimeLogoSuccessfully() {
        Image image = SvgImageLoader.load("/images/writely_runtime_logo.svg", 32, 32);
        assertNotNull(image);
        assertFalse(image.isError());
        assertEquals(32, (int) image.getWidth());
        assertEquals(32, (int) image.getHeight());
    }

    @Test
    void returnsNullOnMissingResource() {
        Image image = SvgImageLoader.load("/images/non_existent.svg", 32, 32);
        assertNull(image);
    }
}
