package multimedia;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

// لوحة عرض الصورة مع دعم السحب والإفلات
public class ImagePanel extends JPanel {

    private BufferedImage image;
    private int drawX, drawY, drawW, drawH;

    public interface PixelClickListener {
        void onPixelClick(int r, int g, int b, int imgX, int imgY);
    }
    private PixelClickListener pixelClickListener;

    public ImagePanel() {
        setBackground(Color.LIGHT_GRAY);
        setPreferredSize(new Dimension(500, 400));

        // دعم السحب والإفلات
        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles = (List<File>) evt.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    if (droppedFiles.size() > 0) {
                        firePropertyChange("fileDropped", null, droppedFiles.get(0));
                    }
                    evt.dropComplete(true);
                } catch (Exception e) {
                    System.out.println("drag drop error: " + e.getMessage());
                    evt.dropComplete(false);
                }
            }
        });

        // عند النقر على الصورة نأخذ لون البكسل
        addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (image == null || pixelClickListener == null) return;
                if (drawW == 0 || drawH == 0) return;

                int imgX = (int) ((e.getX() - drawX) * ((double) image.getWidth() / drawW));
                int imgY = (int) ((e.getY() - drawY) * ((double) image.getHeight() / drawH));

                if (imgX < 0 || imgY < 0 || imgX >= image.getWidth() || imgY >= image.getHeight())
                    return;

                int rgb = image.getRGB(imgX, imgY);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                pixelClickListener.onPixelClick(r, g, b, imgX, imgY);
            }
        });
    }

    public void setPixelClickListener(PixelClickListener l) { pixelClickListener = l; }

    public void setImage(BufferedImage img) {
        this.image = img;
        repaint();
    }

    public BufferedImage getImage() { return image; }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (image == null) {
            // رسالة عند عدم وجود صورة
            g.setColor(Color.DARK_GRAY);
            String txt = "اسحب صورة هنا أو استخدم File > Open";
            int txtW = g.getFontMetrics().stringWidth(txt);
            g.drawString(txt, (getWidth() - txtW) / 2, getHeight() / 2);
            return;
        }

        // نرسم الصورة بحيث تتناسب مع حجم اللوحة
        double sx = (double) getWidth() / image.getWidth();
        double sy = (double) getHeight() / image.getHeight();
        double scale = Math.min(sx, sy);
        drawW = (int) (image.getWidth() * scale);
        drawH = (int) (image.getHeight() * scale);
        drawX = (getWidth() - drawW) / 2;
        drawY = (getHeight() - drawH) / 2;

        g.drawImage(image, drawX, drawY, drawW, drawH, null);
    }
}
