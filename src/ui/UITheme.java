package ui;

import java.awt.Font;

public final class UITheme {

    private UITheme() {
        // Prevent creating objects from this class
    }

    public static final int ROWS = 8;
    public static final int STEPS = 16;

    public static final int CELL_SIZE = 45;
    public static final int GRID_GAP = 5;

    public static final Font TITLE_FONT =
            new Font("SansSerif", Font.BOLD, 24);

    public static final Font NORMAL_FONT =
            new Font("SansSerif", Font.PLAIN, 14);

    public static final Font SMALL_FONT =
            new Font("SansSerif", Font.PLAIN, 12);
}
