package multimedia;

// صف التحويل بين الأنظمة اللونية
public class ColorConverter {

    // تحويل من RGB الى HSV
    public static double[] rgbToHsv(int r, int g, int b) {
        double rd = r / 255.0, gd = g / 255.0, bd = b / 255.0;
        double cmax = Math.max(rd, Math.max(gd, bd));
        double cmin = Math.min(rd, Math.min(gd, bd));
        double diff = cmax - cmin;

        double h = 0;
        double s = (cmax == 0) ? 0 : diff / cmax;
        double v = cmax;

        if (diff != 0) {
            if (cmax == rd)
                h = 60.0 * (((gd - bd) / diff) % 6);
            else if (cmax == gd)
                h = 60.0 * (((bd - rd) / diff) + 2);
            else
                h = 60.0 * (((rd - gd) / diff) + 4);
            if (h < 0) h += 360;
        }
        return new double[]{h, s * 100, v * 100};
    }

    public static int[] hsvToRgb(double h, double s, double v) {
        s /= 100.0;
        v /= 100.0;
        double c = v * s;
        double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
        double m = v - c;
        double r1, g1, b1;

        if (h < 60)       { r1 = c; g1 = x; b1 = 0; }
        else if (h < 120) { r1 = x; g1 = c; b1 = 0; }
        else if (h < 180) { r1 = 0; g1 = c; b1 = x; }
        else if (h < 240) { r1 = 0; g1 = x; b1 = c; }
        else if (h < 300) { r1 = x; g1 = 0; b1 = c; }
        else              { r1 = c; g1 = 0; b1 = x; }

        return new int[]{
            clamp((int) Math.round((r1 + m) * 255)),
            clamp((int) Math.round((g1 + m) * 255)),
            clamp((int) Math.round((b1 + m) * 255))
        };
    }

    // تحويل من RGB الى CMYK
    public static double[] rgbToCmyk(int r, int g, int b) {
        double rd = r / 255.0, gd = g / 255.0, bd = b / 255.0;
        double k = 1.0 - Math.max(rd, Math.max(gd, bd));
        if (k >= 1.0) return new double[]{0, 0, 0, 100};

        double c = (1.0 - rd - k) / (1.0 - k);
        double m = (1.0 - gd - k) / (1.0 - k);
        double y = (1.0 - bd - k) / (1.0 - k);
        return new double[]{c * 100, m * 100, y * 100, k * 100};
    }

    public static int[] cmykToRgb(double c, double m, double y, double k) {
        c /= 100.0; m /= 100.0; y /= 100.0; k /= 100.0;
        return new int[]{
            clamp((int) Math.round(255 * (1 - c) * (1 - k))),
            clamp((int) Math.round(255 * (1 - m) * (1 - k))),
            clamp((int) Math.round(255 * (1 - y) * (1 - k)))
        };
    }

    // تحويل RGB الى YUV
    public static double[] rgbToYuv(int r, int g, int b) {
        double Y = 0.299 * r + 0.587 * g + 0.114 * b;
        double U = -0.14713 * r - 0.28886 * g + 0.436 * b + 128;
        double V = 0.615 * r - 0.51499 * g - 0.10001 * b + 128;
        return new double[]{Y, U, V};
    }

    public static int[] yuvToRgb(double y, double u, double v) {
        u -= 128; v -= 128;
        return new int[]{
            clamp((int) Math.round(y + 1.13983 * v)),
            clamp((int) Math.round(y - 0.39465 * u - 0.58060 * v)),
            clamp((int) Math.round(y + 2.03211 * u))
        };
    }

    // تحويل RGB الى YCbCr
    public static double[] rgbToYCbCr(int r, int g, int b) {
        double Y  = 0.299 * r + 0.587 * g + 0.114 * b;
        double Cb = 128 - 0.168736 * r - 0.331264 * g + 0.5 * b;
        double Cr = 128 + 0.5 * r - 0.418688 * g - 0.081312 * b;
        return new double[]{Y, Cb, Cr};
    }

    public static int[] ycbcrToRgb(double y, double cb, double cr) {
        return new int[]{
            clamp((int) Math.round(y + 1.402 * (cr - 128))),
            clamp((int) Math.round(y - 0.344136 * (cb - 128) - 0.714136 * (cr - 128))),
            clamp((int) Math.round(y + 1.772 * (cb - 128)))
        };
    }

    // تحويل RGB الى LAB عبر فضاء XYZ
    public static double[] rgbToLab(int r, int g, int b) {
        // اولا نحول الى linear RGB
        double rr = gammaToLinear(r / 255.0);
        double gg = gammaToLinear(g / 255.0);
        double bb = gammaToLinear(b / 255.0);

        // ثم الى XYZ باستخدام مصفوفة التحويل
        double x = 0.4124564 * rr + 0.3575761 * gg + 0.1804375 * bb;
        double y = 0.2126729 * rr + 0.7151522 * gg + 0.0721750 * bb;
        double z = 0.0193339 * rr + 0.1191920 * gg + 0.9503041 * bb;

        // تقسيم على النقطة البيضاء D65
        x /= 0.95047;
        y /= 1.00000;
        z /= 1.08883;

        x = labF(x); y = labF(y); z = labF(z);

        double L = 116.0 * y - 16.0;
        double A = 500.0 * (x - y);
        double B = 200.0 * (y - z);
        return new double[]{L, A, B};
    }

    public static int[] labToRgb(double L, double a, double bVal) {
        double fy = (L + 16.0) / 116.0;
        double fx = a / 500.0 + fy;
        double fz = fy - bVal / 200.0;

        double x = labFinv(fx) * 0.95047;
        double y = labFinv(fy) * 1.00000;
        double z = labFinv(fz) * 1.08883;

        double rr =  3.2404542 * x - 1.5371385 * y - 0.4985314 * z;
        double gg = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z;
        double bb =  0.0556434 * x - 0.2040259 * y + 1.0572252 * z;

        return new int[]{
            clamp((int) Math.round(linearToGamma(rr) * 255)),
            clamp((int) Math.round(linearToGamma(gg) * 255)),
            clamp((int) Math.round(linearToGamma(bb) * 255))
        };
    }

    // دوال مساعدة لتحويل gamma
    private static double gammaToLinear(double v) {
        return (v <= 0.04045) ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }
    private static double linearToGamma(double v) {
        if (v <= 0) return 0;
        return (v <= 0.0031308) ? 12.92 * v : 1.055 * Math.pow(v, 1.0 / 2.4) - 0.055;
    }
    private static double labF(double t) {
        return (t > 0.008856) ? Math.cbrt(t) : (903.3 * t + 16.0) / 116.0;
    }
    private static double labFinv(double t) {
        double t3 = t * t * t;
        return (t3 > 0.008856) ? t3 : (116.0 * t - 16.0) / 903.3;
    }

    // تحويل عام من RGB الى اي نظام لوني
    public static double[] fromRgb(int r, int g, int b, String cs) {
        if (cs.equals("HSV"))   return rgbToHsv(r, g, b);
        if (cs.equals("CMYK"))  return rgbToCmyk(r, g, b);
        if (cs.equals("YUV"))   return rgbToYuv(r, g, b);
        if (cs.equals("LAB"))   return rgbToLab(r, g, b);
        if (cs.equals("YCbCr")) return rgbToYCbCr(r, g, b);
        return new double[]{r, g, b}; // RGB
    }

    // تحويل من اي نظام لوني الى RGB
    public static int[] toRgb(double[] vals, String cs) {
        if (cs.equals("HSV"))   return hsvToRgb(vals[0], vals[1], vals[2]);
        if (cs.equals("CMYK"))  return cmykToRgb(vals[0], vals[1], vals[2], vals.length > 3 ? vals[3] : 0);
        if (cs.equals("YUV"))   return yuvToRgb(vals[0], vals[1], vals[2]);
        if (cs.equals("LAB"))   return labToRgb(vals[0], vals[1], vals[2]);
        if (cs.equals("YCbCr")) return ycbcrToRgb(vals[0], vals[1], vals[2]);
        return new int[]{clamp((int) vals[0]), clamp((int) vals[1]), clamp((int) vals[2])};
    }

    // اسماء القنوات لكل نظام لوني
    public static String[] getChannelNames(String cs) {
        if (cs.equals("HSV"))   return new String[]{"Hue", "Saturation", "Value"};
        if (cs.equals("CMYK"))  return new String[]{"Cyan", "Magenta", "Yellow", "Key"};
        if (cs.equals("YUV"))   return new String[]{"Y", "U", "V"};
        if (cs.equals("LAB"))   return new String[]{"L*", "a*", "b*"};
        if (cs.equals("YCbCr")) return new String[]{"Y", "Cb", "Cr"};
        return new String[]{"Red", "Green", "Blue"};
    }

    public static int getChannelCount(String cs) {
        if (cs.equals("CMYK")) return 4;
        return 3;
    }

    // المدى لكل قناة {min, max}
    public static double[][] getChannelRanges(String cs) {
        if (cs.equals("HSV"))   return new double[][]{{0, 360}, {0, 100}, {0, 100}};
        if (cs.equals("CMYK"))  return new double[][]{{0, 100}, {0, 100}, {0, 100}, {0, 100}};
        if (cs.equals("YUV"))   return new double[][]{{0, 255}, {0, 255}, {0, 255}};
        if (cs.equals("LAB"))   return new double[][]{{0, 100}, {-128, 127}, {-128, 127}};
        if (cs.equals("YCbCr")) return new double[][]{{0, 255}, {0, 255}, {0, 255}};
        return new double[][]{{0, 255}, {0, 255}, {0, 255}};
    }

    public static int clamp(int val) {
        if (val < 0) return 0;
        if (val > 255) return 255;
        return val;
    }
}
