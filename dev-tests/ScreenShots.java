import com.restaurant.ui.*;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** Developer tool: opens every screen of the REAL app and saves a PNG of each (needs a database and a display / xvfb-run). */
public class ScreenShots {
    public static void main(String[] a) throws Exception {
        String dir = a[0];
        MainFrame[] f = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> { f[0] = new MainFrame(); f[0].setVisible(true); });
        for (Screen s : Screen.values()) {
            SwingUtilities.invokeAndWait(() -> f[0].navigateTo(s));
            Thread.sleep(1800);
            Container root = f[0].getContentPane();
            BufferedImage img = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
            SwingUtilities.invokeAndWait(() -> root.paint(img.getGraphics()));
            javax.imageio.ImageIO.write(img, "png", new File(dir, s.name().toLowerCase() + ".png"));
        }
        System.exit(0);
    }
}
