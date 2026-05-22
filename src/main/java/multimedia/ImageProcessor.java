package multimedia;

import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.javacpp.indexer.*;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

import java.awt.image.BufferedImage;
import java.util.stream.IntStream;

// معالجة الصور باستخدام OpenCV للأداء العالي
public class ImageProcessor {

    // =========== تقليل الألوان باستخدام K-Means من OpenCV ===========

    public static BufferedImage quantize(BufferedImage src, int k) {
        int w = src.getWidth(), h = src.getHeight();
        int total = w * h;
        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);

        // بناء مصفوفة Nx3 من نوع float لخوارزمية K-Means
        Mat samples = new Mat(total, 3, CV_32F);
        FloatIndexer si = samples.createIndexer();
        for (int i = 0; i < total; i++) {
            si.put(i, 0, (float) ((pixels[i] >> 16) & 0xFF));
            si.put(i, 1, (float) ((pixels[i] >> 8) & 0xFF));
            si.put(i, 2, (float) (pixels[i] & 0xFF));
        }
        si.release();

        Mat labels = new Mat();
        Mat centers = new Mat();
        TermCriteria tc = new TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 15, 1.0);
        kmeans(samples, k, labels, tc, 3, KMEANS_PP_CENTERS, centers);

        // بناء الصورة الناتجة من المراكز
        int[] outPixels = new int[total];
        IntIndexer li = labels.createIndexer();
        FloatIndexer ci = centers.createIndexer();
        for (int i = 0; i < total; i++) {
            int c = li.get(i);
            int r = clamp(Math.round(ci.get(c, 0)));
            int g = clamp(Math.round(ci.get(c, 1)));
            int b = clamp(Math.round(ci.get(c, 2)));
            outPixels[i] = (r << 16) | (g << 8) | b;
        }
        li.release();
        ci.release();
        samples.close();
        labels.close();
        centers.close();

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, outPixels, 0, w);
        return out;
    }

    // =========== تعديل القنوات اللونية ===========

    public static BufferedImage applyChannelMods(BufferedImage src, String cs,
            int[] offsets, boolean[] enabled, double[][] ranges) {
        int cvtFwd = getCvtCode(cs, true);
        if (cvtFwd >= 0 && offsets.length <= 3) {
            return applyOpenCV(src, cs, cvtFwd, getCvtCode(cs, false), offsets, enabled, ranges);
        }
        return applyParallel(src, cs, offsets, enabled, ranges);
    }

    // تعديل القنوات باستخدام OpenCV (للأنظمة المدعومة: HSV, YUV, YCbCr, LAB)
    private static BufferedImage applyOpenCV(BufferedImage src, String cs,
            int cvtFwd, int cvtInv, int[] offsets, boolean[] enabled, double[][] ranges) {
        int w = src.getWidth(), h = src.getHeight();
        int total = w * h;
        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);

        // بناء Mat بصيغة RGB
        Mat rgb = new Mat(h, w, CV_8UC3);
        UByteIndexer ri = rgb.createIndexer();
        for (int i = 0; i < total; i++) {
            int y = i / w, x = i % w;
            ri.put(y, x, 0, (pixels[i] >> 16) & 0xFF);
            ri.put(y, x, 1, (pixels[i] >> 8) & 0xFF);
            ri.put(y, x, 2, pixels[i] & 0xFF);
        }
        ri.release();

        // تحويل الى الفضاء اللوني المطلوب
        Mat converted = new Mat();
        cvtColor(rgb, converted, cvtFwd);
        rgb.close();

        // فصل القنوات
        MatVector chs = new MatVector();
        split(converted, chs);

        double[][] mapping = getMapping(cs);
        int[] chOrder = getChOrder(cs);

        for (int c = 0; c < offsets.length; c++) {
            int oc = chOrder[c];
            Mat ch = chs.get(oc);
            double scale = mapping[c][0];
            double shift = mapping[c][1];

            if (!enabled[c]) {
                // تعطيل القناة - ضبط على القيمة الدنيا
                double appVal = Math.max(0, ranges[c][0]);
                double cvVal = appVal * scale + shift;
                // dst = 0 * src + cvVal
                ch.convertTo(ch, ch.type(), 0.0, cvVal);
            } else if (offsets[c] != 0) {
                // إضافة الإزاحة
                double cvOff = offsets[c] * scale;
                // dst = 1.0 * src + cvOff (مع تشبع تلقائي)
                ch.convertTo(ch, ch.type(), 1.0, cvOff);
            }
        }

        // دمج القنوات وإعادة التحويل الى RGB
        merge(chs, converted);
        Mat result = new Mat();
        cvtColor(converted, result, cvtInv);

        // استخراج البكسلات
        int[] outPixels = new int[total];
        UByteIndexer oi = result.createIndexer();
        for (int i = 0; i < total; i++) {
            int y = i / w, x = i % w;
            outPixels[i] = (oi.get(y, x, 0) << 16) | (oi.get(y, x, 1) << 8) | oi.get(y, x, 2);
        }
        oi.release();
        converted.close();
        result.close();

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, outPixels, 0, w);
        return out;
    }

    // تعديل القنوات بالتوازي (لـ CMYK و RGB غير المدعومين في OpenCV)
    private static BufferedImage applyParallel(BufferedImage src, String cs,
            int[] offsets, boolean[] enabled, double[][] ranges) {
        int w = src.getWidth(), h = src.getHeight();
        int total = w * h;
        int nCh = offsets.length;
        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);
        int[] outPixels = new int[total];

        IntStream.range(0, total).parallel().forEach(i -> {
            int rgb = pixels[i];
            int rr = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, bb = rgb & 0xFF;
            double[] vals = ColorConverter.fromRgb(rr, gg, bb, cs);
            for (int c = 0; c < nCh; c++) {
                if (!enabled[c]) {
                    vals[c] = Math.max(0, ranges[c][0]);
                } else {
                    vals[c] += offsets[c];
                    vals[c] = Math.max(ranges[c][0], Math.min(ranges[c][1], vals[c]));
                }
            }
            int[] newRgb = ColorConverter.toRgb(vals, cs);
            outPixels[i] = (newRgb[0] << 16) | (newRgb[1] << 8) | newRgb[2];
        });

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, w, h, outPixels, 0, w);
        return out;
    }

    // =========== دوال مساعدة ===========

    // أكواد تحويل OpenCV لكل نظام لوني
    private static int getCvtCode(String cs, boolean fwd) {
        return switch (cs) {
            case "HSV"   -> fwd ? COLOR_RGB2HSV_FULL : COLOR_HSV2RGB_FULL;
            case "YUV"   -> fwd ? COLOR_RGB2YUV      : COLOR_YUV2RGB;
            case "YCbCr" -> fwd ? COLOR_RGB2YCrCb    : COLOR_YCrCb2RGB;
            case "LAB"   -> fwd ? COLOR_RGB2Lab       : COLOR_Lab2RGB;
            default -> -1;
        };
    }

    // مقياس التحويل بين قيم التطبيق وقيم OpenCV
    // opencv_value = app_value * scale + shift
    private static double[][] getMapping(String cs) {
        return switch (cs) {
            case "HSV" -> new double[][]{
                {255.0 / 360.0, 0},  // H: 0-360 → 0-255
                {255.0 / 100.0, 0},  // S: 0-100 → 0-255
                {255.0 / 100.0, 0}   // V: 0-100 → 0-255
            };
            case "YUV" -> new double[][]{
                {1, 0}, {1, 0}, {1, 0}
            };
            case "YCbCr" -> new double[][]{
                {1, 0}, {1, 0}, {1, 0}
            };
            case "LAB" -> new double[][]{
                {255.0 / 100.0, 0},  // L: 0-100 → 0-255
                {1, 128},            // a: -128..127 → 0-255
                {1, 128}             // b: -128..127 → 0-255
            };
            default -> new double[][]{{1, 0}, {1, 0}, {1, 0}};
        };
    }

    // ترتيب القنوات (OpenCV يستخدم YCrCb بدل YCbCr)
    private static int[] getChOrder(String cs) {
        if ("YCbCr".equals(cs)) return new int[]{0, 2, 1};
        return new int[]{0, 1, 2};
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
