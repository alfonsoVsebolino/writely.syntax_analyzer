package com.writely.syntax_analyzer.app;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.SVGLoader;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.net.URL;

/**
 * Utility for rendering SVG vector graphics to JavaFX raster Images.
 */
public final class SvgImageLoader {

    private SvgImageLoader() {
    }

    public static Image load(String resourcePath, int width, int height) {
        URL resource = SvgImageLoader.class.getResource(resourcePath);
        if (resource == null) {
            return null;
        }

        SVGLoader loader = new SVGLoader();
        SVGDocument document = loader.load(resource);
        if (document == null) {
            return null;
        }

        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = bufferedImage.createGraphics();
        try {
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            document.render((Component) null, g2d, new ViewBox(0, 0, width, height));
        } finally {
            g2d.dispose();
        }

        int[] pixels = ((DataBufferInt) bufferedImage.getRaster().getDataBuffer()).getData();
        WritableImage writableImage = new WritableImage(width, height);
        writableImage.getPixelWriter().setPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
        return writableImage;
    }
}
