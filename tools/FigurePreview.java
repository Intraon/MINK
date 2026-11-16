// Tegner målebilledet med kunstige data på en almindelig pc, så udseendet kan ses uden en telefon.
// Byg appen først (tools/build.sh). Kør så fra repoets rod:
//   java -cp app/build/tmp/kotlin-classes/release:<kotlin-stdlib.jar> tools/FigurePreview.java ud.png
// kotlin-stdlib.jar ligger i Gradles lib-mappe (~/.gradle/wrapper/dists/gradle-*/*/gradle-*/lib/).
import dk.intraon.mink.waterfall.Figure;
import dk.intraon.mink.waterfall.Pen;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Random;
import javax.imageio.ImageIO;

public class FigurePreview {
    static class AwtPen implements Pen {
        final Graphics2D g;
        AwtPen(BufferedImage img) {
            g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        Font font(float size) { return new Font(Font.SANS_SERIF, Font.PLAIN, 1).deriveFont(size); }
        public void rect(float x, float y, float w, float h, int argb) { g.setColor(new Color(argb, true)); g.fill(new Rectangle2D.Float(x, y, w, h)); }
        public void frame(float x, float y, float w, float h, int argb, float width) {
            g.setColor(new Color(argb, true)); g.setStroke(new BasicStroke(width)); g.draw(new Rectangle2D.Float(x, y, w, h));
        }
        public void line(float x1, float y1, float x2, float y2, int argb, float width, float dash) {
            g.setColor(new Color(argb, true));
            g.setStroke(dash > 0 ? new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{dash, dash}, 0f) : new BasicStroke(width));
            g.draw(new Line2D.Float(x1, y1, x2, y2));
        }
        public void polyline(float[] xs, float[] ys, int n, int argb, float width) {
            Path2D.Float p = new Path2D.Float(); p.moveTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) p.lineTo(xs[i], ys[i]);
            g.setColor(new Color(argb, true)); g.setStroke(new BasicStroke(width)); g.draw(p);
        }
        public void text(String s, float x, float y, float size, int argb, int align, boolean vertical) {
            g.setColor(new Color(argb, true)); g.setFont(font(size));
            float w = g.getFontMetrics().stringWidth(s);
            float dx = align < 0 ? 0 : align > 0 ? -w : -w / 2;
            if (vertical) {
                AffineTransform old = g.getTransform();
                g.rotate(-Math.PI / 2, x, y); g.drawString(s, x + dx, y + size * 0.35f); g.setTransform(old);
            } else g.drawString(s, x + dx, y);
        }
        public float textWidth(String s, float size) { g.setFont(font(size)); return g.getFontMetrics().stringWidth(s); }
        public void image(int[] px, int w, int h, float x, float y, float dw, float dh) {
            BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB); b.setRGB(0, 0, w, h, px, 0, w);
            g.drawImage(b, (int) x, (int) y, (int) dw, (int) dh, null);
        }
    }

    static byte pack(double db) { return (byte) (int) (Math.max(0, Math.min(1, (db + 2.0) / 42.0)) * 255); }

    public static void main(String[] a) throws Exception {
        System.setProperty("java.awt.headless", "true");
        int rows = 750, bins = 740; double lo = 869_380_000.0, binHz = 500.0;
        Random rnd = new Random(7);
        byte[] db = new byte[rows * bins];
        for (int r = 0; r < rows; r++) for (int c = 0; c < bins; c++) {
            double hz = lo + (c + 0.5) * binHz, sec = r * 0.04;
            double v = rnd.nextGaussian() * 1.2;
            if (sec >= 1.8 && (sec - 1.8) % 4.0 < 0.4 && Math.abs(hz - 869_616_000.0) < 5_000) v = 31 + rnd.nextGaussian();
            if (sec >= 3.3 && (sec - 3.3) % 4.0 < 0.4 && Math.abs(hz - 869_470_000.0) < 4_000) v = 18 + rnd.nextGaussian();
            if (sec >= 26.3 && sec <= 26.8 && Math.abs(hz - 869_618_000.0) < 31_250) v = 45;
            db[r * bins + c] = pack(v);
        }
        Figure.Input inp = new Figure.Input(rows, bins, db, lo, binHz, 0.04, 869_618_000.0, 62_500.0,
            869_607_000.0, 869_625_000.0, "målet", "MINK · 16.11.2026 21:45:10 · de seneste 30 s · gain 20,7 dB");
        BufferedImage img = new BufferedImage(Figure.W, Figure.H, BufferedImage.TYPE_INT_ARGB);
        Figure.INSTANCE.draw(new AwtPen(img), inp);
        ImageIO.write(img, "png", new File(a.length > 0 ? a[0] : "figure-preview.png"));
    }
}
