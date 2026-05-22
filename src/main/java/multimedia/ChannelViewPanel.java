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

    // تحديث صور القنوات (محسّن باستخدام مصفوفات بكسل مجمعة)
    private void refreshPreviews() {
        if (srcImage == null || imgLabels == null) return;
        int nCh = ColorConverter.getChannelCount(colorSpace);
        double[][] ranges = ColorConverter.getChannelRanges(colorSpace);

        // نسخة مصغرة للسرعة
        int pw = srcImage.getWidth(), ph = srcImage.getHeight();
        if (pw > 150 || ph > 150) {
            double r = Math.min(150.0 / pw, 150.0 / ph);
            pw = (int)(pw * r); ph = (int)(ph * r);
        }
        BufferedImage small = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
        small.getGraphics().drawImage(srcImage, 0, 0, pw, ph, null);

        int total = pw * ph;
        int[] pixels = small.getRGB(0, 0, pw, ph, null, 0, pw);

        // حساب قيم القنوات مرة واحدة لكل البكسلات
        double[][] allVals = new double[total][];
        for (int i = 0; i < total; i++) {
            int rgb = pixels[i];
            allVals[i] = ColorConverter.fromRgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, colorSpace);
        }

        for (int ch = 0; ch < nCh; ch++) {
            int[] chPixels = new int[total];
            double cmin = ranges[ch][0], cmax = ranges[ch][1];
            double span = (cmax - cmin == 0) ? 1 : cmax - cmin;
            int offset = sliders[ch].getValue();
            boolean on = checkboxes[ch].isSelected();

            for (int i = 0; i < total; i++) {
                double v = Math.max(cmin, Math.min(cmax, allVals[i][ch] + offset));
                int gray = Math.max(0, Math.min(255, (int)((v - cmin) / span * 255)));
                if (!on) gray /= 4;
                chPixels[i] = (gray << 16) | (gray << 8) | gray;
            }
            BufferedImage chImg = new BufferedImage(pw, ph, BufferedImage.TYPE_INT_RGB);
            chImg.setRGB(0, 0, pw, ph, chPixels, 0, pw);
            imgLabels[ch].setIcon(new ImageIcon(chImg));
        }
    }

    // تطبيق التعديلات على الصورة الكاملة (محسّن باستخدام OpenCV والمعالجة المتوازية)
    public BufferedImage applyModifications(BufferedImage original, String cs) {
        if (sliders == null || original == null) return original;
        int nCh = ColorConverter.getChannelCount(cs);
        double[][] ranges = ColorConverter.getChannelRanges(cs);

        // تجميع حالة السلايدرات
        int[] offsets = new int[nCh];
        boolean[] enabled = new boolean[nCh];
        boolean anyChange = false;
        for (int c = 0; c < nCh; c++) {
            offsets[c] = sliders[c].getValue();
            enabled[c] = checkboxes[c].isSelected();
            if (offsets[c] != 0 || !enabled[c]) anyChange = true;
        }
        if (!anyChange) return original;

        return ImageProcessor.applyChannelMods(original, cs, offsets, enabled, ranges);
    }
}
