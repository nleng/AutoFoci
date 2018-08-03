package autoFoci;

import java.awt.Graphics;
import java.awt.Image;
import javax.swing.JPanel;
import javax.swing.ImageIcon;

public class ImagePanel extends JPanel {

    private Image image;

    public ImagePanel(String image_path) {
        image = new ImageIcon(this.getClass().getResource(image_path)).getImage();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.drawImage(image, 0, 0, this.getWidth(), this.getHeight(), null);
    }

}