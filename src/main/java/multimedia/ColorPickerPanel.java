package multimedia;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

public class ColorPickerPanel extends JPanel {

    private double hue = 0;
    private double saturation = 1;
    private double value = 1;

    private BufferedImage svImage;
    private BufferedImage hueStrip;

    private static final int SV_SIZE = 256;
    private static final int HUE_WIDTH = 20;
    private static final int GAP = 12;
    private static final int MARGIN = 15;

    private boolean draggingSV = false;
    private boolean draggingHue = false;

    public interface ColorPickListener {
        void onColorPicked(int r, int g, int b);
    }
    private ColorPickListener listener;

    public ColorPickerPanel() {
        setBackground(new Color(245, 245, 245));
        rebuildSvImage();
        rebuildHueStrip();

        MouseAdapter ma = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (inSvArea(e.getX(), e.getY())) {
                    draggingSV = true;
                    updateSV(e.getX(), e.getY());
                } else if (inHueArea(e.getX(), e.getY())) {
                    draggingHue = true;
                    updateHue(e.getY());
                }
            }
            public void mouseDragged(MouseEvent e) {
                if (draggingSV) updateSV(e.getX(), e.getY());
                else if (draggingHue) updateHue(e.getY());
            }
            public void mouseReleased(MouseEvent e) {
                draggingSV = false;
                draggingHue = false;
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
    }

    public void setColorPickListener(ColorPickListener l) { this.listener = l; }

    public void setColor(int r, int g, int b) {
        double[] hsv = ColorConverter.rgbToHsv(r, g, b);
        boolean hueChanged = Math.abs(this.hue - hsv[0]) > 0.5;
        this.hue = hsv[0];
        this.saturation = hsv[1] / 100.0;
        this.value = hsv[2] / 100.0;
        if (hueChanged) rebuildSvImage();
        repaint();
    }

    private boolean inSvArea(int x, int y) {
        return x >= MARGIN && x < MARGIN + SV_SIZE && y >= MARGIN && y < MARGIN + SV_SIZE;
    }

    private boolean inHueArea(int x, int y) {
        int hx = MARGIN + SV_SIZE + GAP;
        return x >= hx && x < hx + HUE_WIDTH && y >= MARGIN && y < MARGIN + SV_SIZE;
    }

    private void updateSV(int mx, int my) {
        saturation = Math.max(0, Math.min(1, (double)(mx - MARGIN) / SV_SIZE));
        value = Math.max(0, Math.min(1, 1.0 - (double)(my - MARGIN) / SV_SIZE));
        notifyListener();
        repaint();
    }

    private void updateHue(int my) {
        hue = Math.max(0, Math.min(360, (double)(my - MARGIN) / SV_SIZE * 360));
        rebuildSvImage();
        notifyListener();
        repaint();
    }

    private void notifyListener() {
        if (listener != null) {
            int[] rgb = ColorConverter.hsvToRgb(hue, saturation * 100, value * 100);
            listener.onColorPicked(rgb[0], rgb[1], rgb[2]);
        }
    }

    private void rebuildSvImage() {
        svImage = new BufferedImage(SV_SIZE, SV_SIZE, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[SV_SIZE * SV_SIZE];
        for (int y = 0; y < SV_SIZE; y++) {
            double v = 1.0 - (double) y / (SV_SIZE - 1);
            for (int x = 0; x < SV_SIZE; x++) {
                double s = (double) x / (SV_SIZE - 1);
                int[] rgb = ColorConverter.hsvToRgb(hue, s * 100, v * 100);
                pixels[y * SV_SIZE + x] = (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
            }
        }
        svImage.setRGB(0, 0, SV_SIZE, SV_SIZE, pixels, 0, SV_SIZE);
    }

    private void rebuildHueStrip() {
        hueStrip = new BufferedImage(HUE_WIDTH, SV_SIZE, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[HUE_WIDTH * SV_SIZE];
        for (int y = 0; y < SV_SIZE; y++) {
            double h = (double) y / (SV_SIZE - 1) * 360;
            int[] rgb = ColorConverter.hsvToRgb(h, 100, 100);
            int color = (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
            for (int x = 0; x < HUE_WIDTH; x++) {
                pixels[y * HUE_WIDTH + x] = color;
            }
        }
        hueStrip.setRGB(0, 0, HUE_WIDTH, SV_SIZE, pixels, 0, HUE_WIDTH);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.drawImage(svImage, MARGIN, MARGIN, null);
        g2.setColor(Color.GRAY);
        g2.drawRect(MARGIN - 1, MARGIN - 1, SV_SIZE + 1, SV_SIZE + 1);

        int px = MARGIN + (int)(saturation * (SV_SIZE - 1));
        int py = MARGIN + (int)((1 - value) * (SV_SIZE - 1));
        g2.setStroke(new BasicStroke(2));
        g2.setColor(Color.WHITE);
        g2.drawOval(px - 7, py - 7, 14, 14);
        g2.setStroke(new BasicStroke(1));
        g2.setColor(Color.BLACK);
        g2.drawOval(px - 8, py - 8, 16, 16);

        int hueX = MARGIN + SV_SIZE + GAP;
        g2.drawImage(hueStrip, hueX, MARGIN, null);
        g2.setColor(Color.GRAY);
        g2.drawRect(hueX - 1, MARGIN - 1, HUE_WIDTH + 1, SV_SIZE + 1);

        int hy = MARGIN + (int)(hue / 360.0 * (SV_SIZE - 1));
        g2.setStroke(new BasicStroke(2));
        g2.setColor(Color.WHITE);
        g2.fillRoundRect(hueX - 3, hy - 4, HUE_WIDTH + 6, 8, 4, 4);
        g2.setColor(Color.DARK_GRAY);
        g2.setStroke(new BasicStroke(1));
        g2.drawRoundRect(hueX - 3, hy - 4, HUE_WIDTH + 6, 8, 4, 4);

        int[] rgb = ColorConverter.hsvToRgb(hue, saturation * 100, value * 100);
        int previewY = MARGIN + SV_SIZE + 15;

        g2.setColor(new Color(rgb[0], rgb[1], rgb[2]));
        g2.fillRoundRect(MARGIN, previewY, 50, 50, 8, 8);
        g2.setColor(Color.GRAY);
        g2.drawRoundRect(MARGIN, previewY, 50, 50, 8, 8);

        g2.setColor(Color.BLACK);
        g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
        int tx = MARGIN + 60, ty = previewY + 4;

        double[] hsv = {hue, saturation * 100, value * 100};
        double[] cmyk = ColorConverter.rgbToCmyk(rgb[0], rgb[1], rgb[2]);
        double[] yuv = ColorConverter.rgbToYuv(rgb[0], rgb[1], rgb[2]);
        double[] lab = ColorConverter.rgbToLab(rgb[0], rgb[1], rgb[2]);
        double[] ycbcr = ColorConverter.rgbToYCbCr(rgb[0], rgb[1], rgb[2]);

        g2.drawString(String.format("RGB(%d, %d, %d)", rgb[0], rgb[1], rgb[2]), tx, ty += 13);
        g2.drawString(String.format("HSV(%.0f, %.0f%%, %.0f%%)", hsv[0], hsv[1], hsv[2]), tx, ty += 15);
        g2.drawString(String.format("CMYK(%.0f, %.0f, %.0f, %.0f)", cmyk[0], cmyk[1], cmyk[2], cmyk[3]), tx, ty += 15);

        tx = MARGIN;
        ty = previewY + 70;
        g2.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g2.drawString(String.format("YUV(%.0f, %.0f, %.0f)  LAB(%.1f, %.1f, %.1f)  YCbCr(%.0f, %.0f, %.0f)",
            yuv[0], yuv[1], yuv[2], lab[0], lab[1], lab[2], ycbcr[0], ycbcr[1], ycbcr[2]),
            tx, ty);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(MARGIN * 2 + SV_SIZE + GAP + HUE_WIDTH, MARGIN + SV_SIZE + 110);
    }
}
