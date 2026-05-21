package multimedia;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

// عرض الفضاء اللوني بشكل ثنائي وثلاثي الأبعاد
public class ColorSpaceVisualizer extends JPanel {

    private final DrawArea drawArea;
    private JRadioButton btn3d, btn2d;
    private JComboBox<String> comboX, comboY;
    private String colorSpace = "RGB";

    public interface ColorPickListener {
        void onColorPicked(int r, int g, int b);
    }
    private ColorPickListener listener;

    public ColorSpaceVisualizer() {
        setLayout(new BorderLayout());

        // عناصر التحكم في الأعلى
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        btn3d = new JRadioButton("3D", true);
        btn2d = new JRadioButton("2D");
        ButtonGroup bg = new ButtonGroup();
        bg.add(btn3d); bg.add(btn2d);
        top.add(btn3d); top.add(btn2d);

        top.add(new JLabel("  X:"));
        comboX = new JComboBox<>(); comboX.setEnabled(false);
        top.add(comboX);
        top.add(new JLabel("  Y:"));
        comboY = new JComboBox<>(); comboY.setEnabled(false);
        top.add(comboY);
        add(top, BorderLayout.NORTH);

        drawArea = new DrawArea();
        add(drawArea, BorderLayout.CENTER);

        btn3d.addActionListener(e -> { comboX.setEnabled(false); comboY.setEnabled(false); drawArea.is3d = true; drawArea.repaint(); });
        btn2d.addActionListener(e -> { comboX.setEnabled(true);  comboY.setEnabled(true);  drawArea.is3d = false; drawArea.repaint(); });
        comboX.addActionListener(e -> { drawArea.ax = comboX.getSelectedIndex(); drawArea.repaint(); });
        comboY.addActionListener(e -> { drawArea.ay = comboY.getSelectedIndex(); drawArea.repaint(); });
    }

    public void setColorPickListener(ColorPickListener l) { this.listener = l; drawArea.listener = l; }

    public void updateImage(BufferedImage img, String cs) {
        this.colorSpace = cs;
        // تحديث اسماء المحاور
        String[] names = ColorConverter.getChannelNames(cs);
        comboX.removeAllItems(); comboY.removeAllItems();
        for (int i = 0; i < Math.min(names.length, 3); i++) {
            comboX.addItem(names[i]); comboY.addItem(names[i]);
        }
        if (names.length >= 2) { comboX.setSelectedIndex(0); comboY.setSelectedIndex(1); }
        drawArea.loadPoints(img, cs);
    }

    // المنطقة الداخلية للرسم
    private static class DrawArea extends JPanel {
        List<float[]> pts = new ArrayList<>(); // nx,ny,nz,r,g,b
        String cs = "RGB";
        boolean is3d = true;
        int ax = 0, ay = 1;
        double yaw = 0.55, pitch = 0.4; // زوايا الدوران
        double zoom = 200;
        int mx0, my0;
        double yaw0, pitch0;
        float[] picked = null;
        ColorPickListener listener;

        DrawArea() {
            setBackground(new Color(240, 240, 240));

            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    mx0 = e.getX(); my0 = e.getY();
                    yaw0 = yaw; pitch0 = pitch;
                    // اختيار لون
                    tryPick(e.getX(), e.getY());
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseDragged(MouseEvent e) {
                    if (!is3d) return;
                    yaw = yaw0 + (e.getX() - mx0) * 0.01;
                    pitch = pitch0 + (e.getY() - my0) * 0.01;
                    if (pitch > 1.5) pitch = 1.5;
                    if (pitch < -1.5) pitch = -1.5;
                    repaint();
                }
            });
            addMouseWheelListener(e -> {
                zoom *= (e.getWheelRotation() < 0) ? 1.1 : 0.9;
                if (zoom < 50) zoom = 50;
                if (zoom > 800) zoom = 800;
                repaint();
            });
        }

        void loadPoints(BufferedImage img, String colorSpace) {
            cs = colorSpace;
            pts.clear();
            picked = null;
            if (img == null) { repaint(); return; }

            int w = img.getWidth(), h = img.getHeight();
            int step = Math.max(1, (int) Math.sqrt((double) w * h / 2500));
            double[][] ranges = ColorConverter.getChannelRanges(cs);
            int nch = Math.min(ColorConverter.getChannelCount(cs), 3);

            for (int y = 0; y < h; y += step) {
                for (int x = 0; x < w; x += step) {
                    int rgb = img.getRGB(x, y);
                    int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                    double[] v = ColorConverter.fromRgb(r, g, b, cs);

                    float nx = norm(v[0], ranges[0]);
                    float ny = nch > 1 ? norm(v[1], ranges[1]) : 0;
                    float nz = nch > 2 ? norm(v[2], ranges[2]) : 0;
                    pts.add(new float[]{nx, ny, nz, r, g, b});
                }
            }
            repaint();
        }

        float norm(double val, double[] range) {
            double s = range[1] - range[0];
            if (s == 0) return 0;
            return (float)((val - range[0]) / s - 0.5);
        }

        void tryPick(int clickX, int clickY) {
            int cx = getWidth()/2, cy = getHeight()/2;
            float best = 12*12;
            float[] found = null;
            for (float[] p : pts) {
                int[] s = is3d ? proj(p[0],p[1],p[2],cx,cy) : new int[]{cx+(int)(getAx(p,ax)*zoom), cy-(int)(getAx(p,ay)*zoom)};
                float d = (clickX-s[0])*(clickX-s[0]) + (clickY-s[1])*(clickY-s[1]);
                if (d < best) { best = d; found = p; }
            }
            if (found != null) {
                picked = found;
                if (listener != null) listener.onColorPicked((int)found[3], (int)found[4], (int)found[5]);
                repaint();
            }
        }

        float getAx(float[] p, int a) {
            if (a == 0) return p[0]; if (a == 1) return p[1]; return p[2];
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D)g;
            int cx = getWidth()/2, cy = getHeight()/2;
            String[] names = ColorConverter.getChannelNames(cs);
            int nc = Math.min(names.length, 3);

            if (is3d) {
                // رسم حواف المكعب
                g2.setColor(new Color(180, 180, 180));
                float s = 0.5f;
                float[][] c = {{-s,-s,-s},{s,-s,-s},{s,s,-s},{-s,s,-s},{-s,-s,s},{s,-s,s},{s,s,s},{-s,s,s}};
                int[][] e = {{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
                for (int[] ed : e) {
                    int[] p1 = proj(c[ed[0]][0], c[ed[0]][1], c[ed[0]][2], cx, cy);
                    int[] p2 = proj(c[ed[1]][0], c[ed[1]][1], c[ed[1]][2], cx, cy);
                    g2.drawLine(p1[0], p1[1], p2[0], p2[1]);
                }

                // رسم المحاور
                int[] o = proj(0,0,0,cx,cy);
                Color[] ac = {Color.RED, new Color(0,150,0), Color.BLUE};
                float[][] dirs = {{0.6f,0,0},{0,0.6f,0},{0,0,0.6f}};
                for (int i = 0; i < nc; i++) {
                    g2.setColor(ac[i]);
                    g2.setStroke(new BasicStroke(2));
                    int[] end = proj(dirs[i][0], dirs[i][1], dirs[i][2], cx, cy);
                    g2.drawLine(o[0], o[1], end[0], end[1]);
                    g2.drawString(names[i], end[0]+3, end[1]-3);
                }

                // رسم النقاط
                g2.setStroke(new BasicStroke(1));
                for (float[] p : pts) {
                    int[] sc = proj(p[0], p[1], p[2], cx, cy);
                    g2.setColor(new Color((int)p[3], (int)p[4], (int)p[5]));
                    int sz = (p == picked) ? 6 : 3;
                    g2.fillRect(sc[0]-sz/2, sc[1]-sz/2, sz, sz);
                }
                // حلقة الاختيار
                if (picked != null) {
                    int[] sc = proj(picked[0], picked[1], picked[2], cx, cy);
                    g2.setColor(Color.BLACK);
                    g2.drawOval(sc[0]-5, sc[1]-5, 10, 10);
                }
            } else {
                // وضع ثنائي الأبعاد
                g2.setColor(Color.GRAY);
                g2.drawLine(cx-(int)(0.55*zoom), cy, cx+(int)(0.55*zoom), cy);
                g2.drawLine(cx, cy+(int)(0.55*zoom), cx, cy-(int)(0.55*zoom));
                // اسماء المحاور
                g2.setColor(Color.RED);
                if (ax < nc) g2.drawString(names[ax], cx+(int)(0.55*zoom)+4, cy-2);
                g2.setColor(new Color(0,150,0));
                if (ay < nc) g2.drawString(names[ay], cx+4, cy-(int)(0.55*zoom)-4);

                for (float[] p : pts) {
                    float vx = getAx(p, ax), vy = getAx(p, ay);
                    int sx = cx + (int)(vx * zoom), sy = cy - (int)(vy * zoom);
                    g2.setColor(new Color((int)p[3], (int)p[4], (int)p[5]));
                    int sz = (p == picked) ? 5 : 3;
                    g2.fillRect(sx-sz/2, sy-sz/2, sz, sz);
                }
                if (picked != null) {
                    float vx = getAx(picked, ax), vy = getAx(picked, ay);
                    int sx = cx + (int)(vx * zoom), sy = cy - (int)(vy * zoom);
                    g2.setColor(Color.BLACK);
                    g2.drawOval(sx-5, sy-5, 10, 10);
                }
            }
        }

        int[] proj(double x, double y, double z, int cx, int cy) {
            double cy2 = Math.cos(yaw), sy2 = Math.sin(yaw);
            double cp = Math.cos(pitch), sp = Math.sin(pitch);
            double rx = x * cy2 + z * sy2;
            double rz = -x * sy2 + z * cy2;
            double ry = y * cp - rz * sp;
            return new int[]{cx + (int)(rx * zoom), cy - (int)(ry * zoom)};
        }
    }
}
