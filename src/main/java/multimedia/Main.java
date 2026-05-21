package multimedia;

import javax.swing.*;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PixelLabFrame frame = new PixelLabFrame();
            frame.setVisible(true);
        });
    }
}
