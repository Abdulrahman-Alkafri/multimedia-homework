package multimedia;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

// لوحة عرض القنوات اللونية والتحكم بها
public class ChannelViewPanel extends JPanel {

    private JPanel container;
    private JSlider[] sliders;
    private JCheckBox[] checkboxes;
    private JLabel[] imgLabels;
    private BufferedImage srcImage;
    private String colorSpace = "RGB";
    private Runnable onChange;

    public ChannelViewPanel() {
        setLayout(new BorderLayout());
        container = new JPanel();
        add(new JScrollPane(container), BorderLayout.CENTER);
    }

    public void setOnChangeCallback(Runnable cb) { this.onChange = cb; }

    public void update(BufferedImage img, String cs) {
        this.srcImage = img;
        this.colorSpace = cs;
        buildPanels();
    }

    public void resetControls() {
        if (sliders != null)
            for (JSlider s : sliders) s.setValue(0);
        if (checkboxes != null)
            for (JCheckBox c : checkboxes) c.setSelected(true);
    }

    private void buildPanels() {
        container.removeAll();
        int nCh = ColorConverter.getChannelCount(colorSpace);
        String[] names = ColorConverter.getChannelNames(colorSpace);
        double[][] ranges = ColorConverter.getChannelRanges(colorSpace);

        container.setLayout(new GridLayout(1, nCh, 5, 5));
        sliders = new JSlider[nCh];
        checkboxes = new JCheckBox[nCh];
        imgLabels = new JLabel[nCh];

        for (int i = 0; i < nCh; i++) {
            JPanel p = new JPanel();
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBorder(BorderFactory.createTitledBorder(names[i]));

            // صورة القناة
            JLabel lbl = new JLabel();
            lbl.setHorizontalAlignment(SwingConstants.CENTER);
            lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
            lbl.setPreferredSize(new Dimension(130, 130));
            imgLabels[i] = lbl;
            p.add(lbl);

            // سلايدر للتعديل
            int half = (int)((ranges[i][1] - ranges[i][0]) / 2);
            if (half < 1) half = 1;
            JSlider sl = new JSlider(-half, half, 0);
            sl.setAlignmentX(Component.CENTER_ALIGNMENT);
            sl.setMaximumSize(new Dimension(170, 35));
            sl.addChangeListener(e -> {
                refreshPreviews();
                if (onChange != null) onChange.run();
            });
            sliders[i] = sl;
            p.add(sl);

            // تفعيل/تعطيل القناة
            JCheckBox cb = new JCheckBox("مفعلة", true);
            cb.setAlignmentX(Component.CENTER_ALIGNMENT);
            cb.addActionListener(e -> {
                refreshPreviews();
                if (onChange != null) onChange.run();
            });
            checkboxes[i] = cb;
            p.add(cb);

            container.add(p);
        }
        refreshPreviews();
        revalidate();
        repaint();
    }

    // تحديث صور القنوات
    private void refreshPreviews() {
        if (srcImage == null || imgLabels == null) return;
        int nCh = ColorConverter.getChannelCount(colorSpace);
        double[][] ranges = ColorConverter.getChannelRanges(colorSpace);

        // نستخدم نسخة مصغرة للسرعة
        int pw = srcImage.getWidth(), ph = srcImage.getHeight();
        if (pw > 150 || ph > 150) {
            double r = Math.min(150.0 / pw, 150.0 / ph);
            pw = (int)(pw * r); ph = (int)(ph * r);
        }
        BufferedImage small = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        small.getGraphics().drawImage(srcImage, 0, 0, pw, ph, null);

        for (int ch = 0; ch < nCh; ch++) {
            BufferedImage chImg = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
            double cmin = ranges[ch][0], cmax = ranges[ch][1];
            double span = cmax - cmin;
            if (span == 0) span = 1;
            int offset = sliders[ch].getValue();
            boolean on = checkboxes[ch].isSelected();

            for (int y = 0; y < ph; y++) {
                for (int x = 0; x < pw; x++) {
                    int rgb = small.getRGB(x, y);
                    int rr = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, bb = rgb & 0xFF;
                    double[] vals = ColorConverter.fromRgb(rr, gg, bb, colorSpace);

                    double v = vals[ch] + offset;
                    v = Math.max(cmin, Math.min(cmax, v));
                    int gray = (int)((v - cmin) / span * 255);
                    if (gray < 0) gray = 0;
                    if (gray > 255) gray = 255;
                    if (!on) gray /= 4;

                    chImg.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
                }
            }
            imgLabels[ch].setIcon(new ImageIcon(chImg));
        }
    }

    // تطبيق التعديلات على الصورة الكاملة
    public BufferedImage applyModifications(BufferedImage original, String cs) {
        if (sliders == null || original == null) return original;
        int nCh = ColorConverter.getChannelCount(cs);
        double[][] ranges = ColorConverter.getChannelRanges(cs);

        // نتحقق اذا في تعديلات
        boolean anyChange = false;
        for (int c = 0; c < nCh; c++) {
            if (sliders[c].getValue() != 0 || !checkboxes[c].isSelected()) {
                anyChange = true;
                break;
            }
        }
        if (!anyChange) return original;

        int w = original.getWidth(), h = original.getHeight();
        BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = original.getRGB(x, y);
                int rr = (rgb >> 16) & 0xFF;
                int gg = (rgb >> 8) & 0xFF;
                int bb = rgb & 0xFF;

                double[] vals = ColorConverter.fromRgb(rr, gg, bb, cs);
                for (int c = 0; c < nCh; c++) {
                    if (!checkboxes[c].isSelected()) {
                        vals[c] = Math.max(0, ranges[c][0]);
                    } else {
                        vals[c] += sliders[c].getValue();
                        vals[c] = Math.max(ranges[c][0], Math.min(ranges[c][1], vals[c]));
                    }
                }
                int[] newRgb = ColorConverter.toRgb(vals, cs);
                result.setRGB(x, y, (newRgb[0] << 16) | (newRgb[1] << 8) | newRgb[2]);
            }
        }
        return result;
    }
}
