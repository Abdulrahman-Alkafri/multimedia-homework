package multimedia;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class PixelLabFrame extends JFrame {

    private BufferedImage originalImage;
    private BufferedImage currentImage;

    private ImagePanel imagePanel;
    private ChannelViewPanel channelPanel;
    private ColorSpaceVisualizer colorViz;
    private ColorPickerPanel colorPicker;

    private JComboBox<String> csCombo; // اختيار النظام اللوني
    private JComboBox<Integer> colorCountCombo;

    // شريط معلومات اللون
    private JLabel colorLabel;
    private JPanel colorBox;

    // معلومات الصورة
    private JLabel lblName, lblFormat, lblSize, lblDepth, lblFileSize;
    private String fileName = "";
    private String fileFormat = "";
    private long fileSizeBytes = 0;

    public PixelLabFrame() {
        setTitle("PixelLab - مختبر الصور");
        setSize(1150, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // القوائم
        JMenuBar mb = new JMenuBar();
        JMenu fileMenu = new JMenu("File");
        JMenuItem openItem = new JMenuItem("Open");
        openItem.addActionListener(e -> openImage());
        JMenuItem saveItem = new JMenuItem("Save");
        saveItem.addActionListener(e -> saveImage());
        JMenuItem resetItem = new JMenuItem("Reset");
        resetItem.addActionListener(e -> resetImage());
        fileMenu.add(openItem);
        fileMenu.add(saveItem);
        fileMenu.addSeparator();
        fileMenu.add(resetItem);
        mb.add(fileMenu);
        setJMenuBar(mb);

        // شريط الادوات
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        toolbar.add(new JLabel("Color Space:"));
        csCombo = new JComboBox<>(new String[]{"RGB", "CMYK", "HSV", "YUV", "LAB", "YCbCr"});
        csCombo.addActionListener(e -> onCsChanged());
        toolbar.add(csCombo);
        toolbar.add(new JLabel("   Colors:"));
        colorCountCombo = new JComboBox<>(new Integer[]{2, 4, 8, 16, 32, 64, 128, 256});
        colorCountCombo.setSelectedItem(256);
        colorCountCombo.addActionListener(e -> updateImage());
        toolbar.add(colorCountCombo);
        JButton btnReset = new JButton("Reset");
        btnReset.addActionListener(e -> resetImage());
        toolbar.add(btnReset);
        JButton btnSave = new JButton("Save");
        btnSave.addActionListener(e -> saveImage());
        toolbar.add(btnSave);
        add(toolbar, BorderLayout.NORTH);

        // الجزء الرئيسي
        imagePanel = new ImagePanel();
        imagePanel.setPixelClickListener((r, g, b, x, y) -> showColorInfo(r, g, b));
        imagePanel.addPropertyChangeListener("fileDropped", e -> loadFile((File) e.getNewValue()));

        JTabbedPane tabs = new JTabbedPane();
        channelPanel = new ChannelViewPanel();
        channelPanel.setOnChangeCallback(this::updateImage);
        tabs.addTab("القنوات", channelPanel);

        colorViz = new ColorSpaceVisualizer();
        colorViz.setColorPickListener((r, g, b) -> showColorInfo(r, g, b));
        tabs.addTab("الفضاء اللوني", colorViz);

        colorPicker = new ColorPickerPanel();
        colorPicker.setColorPickListener((r, g, b) -> showColorInfo(r, g, b));
        tabs.addTab("منتقي الألوان", colorPicker);

        tabs.addTab("معلومات", buildInfoPanel());

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, imagePanel, tabs);
        split.setDividerLocation(520);
        add(split, BorderLayout.CENTER);

        // شريط الالوان في الأسفل
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        colorBox = new JPanel();
        colorBox.setPreferredSize(new Dimension(20, 20));
        colorBox.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        bottom.add(colorBox);
        colorLabel = new JLabel("  اضغط على الصورة لاختيار لون");
        bottom.add(colorLabel);
        add(bottom, BorderLayout.SOUTH);
    }

    private JPanel buildInfoPanel() {
        JPanel p = new JPanel(new GridLayout(5, 2, 8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        lblName = new JLabel("-");
        lblFormat = new JLabel("-");
        lblSize = new JLabel("-");
        lblDepth = new JLabel("-");
        lblFileSize = new JLabel("-");
        p.add(new JLabel("اسم الملف:")); p.add(lblName);
        p.add(new JLabel("الصيغة:"));     p.add(lblFormat);
        p.add(new JLabel("الأبعاد:"));    p.add(lblSize);
        p.add(new JLabel("العمق:"));      p.add(lblDepth);
        p.add(new JLabel("حجم الملف:")); p.add(lblFileSize);
        return p;
    }

    // فتح صورة
    private void openImage() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Images", "jpg", "jpeg", "png", "bmp", "gif"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            loadFile(fc.getSelectedFile());
        }
    }

    private void loadFile(File f) {
        try {
            BufferedImage img = ImageIO.read(f);
            if (img == null) {
                JOptionPane.showMessageDialog(this, "لا يمكن قراءة الملف");
                return;
            }
            // نحول الى RGB
            if (img.getType() != BufferedImage.TYPE_INT_RGB) {
                BufferedImage tmp = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
                tmp.getGraphics().drawImage(img, 0, 0, null);
                img = tmp;
            }
            originalImage = img;
            currentImage = copyImg(originalImage);

            fileName = f.getName();
            fileSizeBytes = f.length();
            int dot = fileName.lastIndexOf('.');
            fileFormat = dot > 0 ? fileName.substring(dot + 1).toUpperCase() : "?";

            csCombo.setSelectedItem("RGB");
            colorCountCombo.setSelectedItem(256);
            channelPanel.resetControls();
            updateInfoLabels();
            refreshAll();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "خطأ: " + ex.getMessage());
        }
    }

    // حفظ الصورة
    private void saveImage() {
        if (currentImage == null) { JOptionPane.showMessageDialog(this, "لا توجد صورة"); return; }
        JFileChooser fc = new JFileChooser();
        fc.addChoosableFileFilter(new FileNameExtensionFilter("PNG", "png"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("JPEG", "jpg"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("BMP", "bmp"));
        fc.setAcceptAllFileFilterUsed(false);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            String fmt = "png";
            String desc = fc.getFileFilter().getDescription().toLowerCase();
            if (desc.contains("jpeg") || desc.contains("jpg")) fmt = "jpg";
            else if (desc.contains("bmp")) fmt = "bmp";
            if (!f.getName().contains(".")) f = new File(f.getPath() + "." + fmt);
            try {
                ImageIO.write(currentImage, fmt, f);
                JOptionPane.showMessageDialog(this, "تم الحفظ: " + f.getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "خطأ بالحفظ: " + ex.getMessage());
            }
        }
    }

    private void resetImage() {
        if (originalImage == null) return;
        currentImage = copyImg(originalImage);
        channelPanel.resetControls();
        csCombo.setSelectedItem("RGB");
        colorCountCombo.setSelectedItem(256);
        refreshAll();
    }

    private void onCsChanged() {
        if (originalImage == null) return;
        channelPanel.update(originalImage, (String)csCombo.getSelectedItem());
        updateImage();
    }

    private void updateImage() {
        if (originalImage == null) return;
        String cs = (String) csCombo.getSelectedItem();
        BufferedImage modified = channelPanel.applyModifications(originalImage, cs);
        int nColors = (Integer) colorCountCombo.getSelectedItem();
        if (nColors < 256) {
            modified = ImageProcessor.quantize(modified, nColors);
        }
        currentImage = modified;
        imagePanel.setImage(currentImage);
        colorViz.updateImage(currentImage, cs);
    }

    private void refreshAll() {
        String cs = (String) csCombo.getSelectedItem();
        imagePanel.setImage(currentImage);
        channelPanel.update(originalImage, cs);
        colorViz.updateImage(currentImage, cs);
    }

    private void updateInfoLabels() {
        lblName.setText(fileName);
        lblFormat.setText(fileFormat);
        lblSize.setText(originalImage.getWidth() + " x " + originalImage.getHeight());
        lblDepth.setText(originalImage.getColorModel().getPixelSize() + " bit");
        if (fileSizeBytes < 1024)
            lblFileSize.setText(fileSizeBytes + " B");
        else
            lblFileSize.setText(String.format("%.1f KB", fileSizeBytes / 1024.0));
    }

    // عرض معلومات اللون في كل الأنظمة مع مزامنة منتقي الألوان
    private void showColorInfo(int r, int g, int b) {
        colorBox.setBackground(new Color(r, g, b));
        colorPicker.setColor(r, g, b);

        double[] hsv = ColorConverter.rgbToHsv(r, g, b);
        double[] cmyk = ColorConverter.rgbToCmyk(r, g, b);
        double[] yuv = ColorConverter.rgbToYuv(r, g, b);
        double[] lab = ColorConverter.rgbToLab(r, g, b);
        double[] ycbcr = ColorConverter.rgbToYCbCr(r, g, b);

        colorLabel.setText(String.format(
            "RGB(%d,%d,%d) | HSV(%.0f,%.0f%%,%.0f%%) | CMYK(%.0f,%.0f,%.0f,%.0f) | YUV(%.0f,%.0f,%.0f) | LAB(%.1f,%.1f,%.1f) | YCbCr(%.0f,%.0f,%.0f)",
            r, g, b, hsv[0], hsv[1], hsv[2],
            cmyk[0], cmyk[1], cmyk[2], cmyk[3],
            yuv[0], yuv[1], yuv[2],
            lab[0], lab[1], lab[2],
            ycbcr[0], ycbcr[1], ycbcr[2]
        ));
    }

    private static BufferedImage copyImg(BufferedImage src) {
        BufferedImage c = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        c.getGraphics().drawImage(src, 0, 0, null);
        return c;
    }
}
