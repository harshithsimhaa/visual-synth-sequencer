package ui;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;
import model.SequencerModel;

public class GridPanel extends JPanel {
    private final SequencerModel model;
    private int playheadStep = -1;

    // Theme & Visual Colors
    private static final Color BG_DARK = new Color(20, 20, 24);
    private static final Color CELL_INACTIVE = new Color(40, 40, 48);
    private static final Color PURPLE_GLOW = new Color(160, 80, 255);
    private static final Color PURPLE_ACTIVE = new Color(200, 140, 255);
    private static final Color PLAYHEAD_COLOR = new Color(255, 255, 255, 25);
    private static final Color TEXT_LIGHT = new Color(220, 220, 230);
    private static final Color TEXT_MUTED = new Color(140, 140, 160);
    private static final Color GRID_CONTAINER_BG = new Color(30, 30, 36);
    private static final Color GRID_CONTAINER_BORDER = new Color(60, 60, 70);

    private static final String[] ROW_LABELS = {"C5", "B4", "A4", "G4", "F4", "E4", "D4", "C4"};
    private static final int START_X = 50;
    private static final int START_Y = 40;

    public GridPanel(SequencerModel model) {
        this.model = model;
        
        int preferredWidth = START_X + UITheme.STEPS * (UITheme.CELL_SIZE + UITheme.GRID_GAP) + 30;
        int preferredHeight = START_Y + UITheme.ROWS * (UITheme.CELL_SIZE + UITheme.GRID_GAP) + 30;
        setPreferredSize(new Dimension(Math.max(800, preferredWidth), Math.max(400, preferredHeight)));
        setBackground(BG_DARK);

        // Mouse click listener for matrix cell interaction
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleGridClick(e.getX(), e.getY());
            }
        });
    }

    public void setPlayheadStep(int step) {
        this.playheadStep = step;
        repaint();
    }

    public int getPlayheadStep() {
        return playheadStep;
    }

    private void handleGridClick(int mouseX, int mouseY) {
        for (int row = 0; row < UITheme.ROWS; row++) {
            for (int col = 0; col < UITheme.STEPS; col++) {
                int cellX = START_X + col * (UITheme.CELL_SIZE + UITheme.GRID_GAP);
                int cellY = START_Y + row * (UITheme.CELL_SIZE + UITheme.GRID_GAP);

                if (mouseX >= cellX && mouseX <= cellX + UITheme.CELL_SIZE &&
                    mouseY >= cellY && mouseY <= cellY + UITheme.CELL_SIZE) {
                    
                    if (model != null) {
                        try {
                            // Try calling toggleCell or setCell on model if available
                            java.lang.reflect.Method toggleMethod = model.getClass().getMethod("toggleCell", int.class, int.class);
                            toggleMethod.invoke(model, row, col);
                        } catch (Exception ignored) {
                            try {
                                java.lang.reflect.Method isSetMethod = model.getClass().getMethod("isCellActive", int.class, int.class);
                                boolean current = (boolean) isSetMethod.invoke(model, row, col);
                                java.lang.reflect.Method setMethod = model.getClass().getMethod("setCell", int.class, int.class, boolean.class);
                                setMethod.invoke(model, row, col, !current);
                            } catch (Exception ex) {
                                // Fallback if methods have different signatures
                            }
                        }
                    }
                    repaint();
                    return;
                }
            }
        }
    }

    private boolean isCellActive(int row, int col) {
        if (model == null) return false;
        try {
            java.lang.reflect.Method isSetMethod = model.getClass().getMethod("isCellActive", int.class, int.class);
            return (boolean) isSetMethod.invoke(model, row, col);
        } catch (Exception ignored) {
            try {
                java.lang.reflect.Method getMethod = model.getClass().getMethod("getCell", int.class, int.class);
                Object res = getMethod.invoke(model, row, col);
                if (res instanceof Boolean) return (Boolean) res;
                if (res instanceof Number) return ((Number) res).intValue() != 0;
            } catch (Exception ex) {
                // Return false if unresolvable
            }
        }
        return false;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int gridW = UITheme.STEPS * (UITheme.CELL_SIZE + UITheme.GRID_GAP) - UITheme.GRID_GAP + 24;
        int gridH = UITheme.ROWS * (UITheme.CELL_SIZE + UITheme.GRID_GAP) - UITheme.GRID_GAP + 24;

        // Draw Matrix Container Panel
        g2d.setColor(GRID_CONTAINER_BG);
        g2d.fillRoundRect(START_X - 12, START_Y - 12, gridW, gridH, 15, 15);
        g2d.setColor(GRID_CONTAINER_BORDER);
        g2d.setStroke(new BasicStroke(1f));
        g2d.drawRoundRect(START_X - 12, START_Y - 12, gridW, gridH, 15, 15);

        // Draw Playhead Highlight if active
        if (playheadStep >= 0 && playheadStep < UITheme.STEPS) {
            g2d.setColor(PLAYHEAD_COLOR);
            int pX = START_X + playheadStep * (UITheme.CELL_SIZE + UITheme.GRID_GAP) - UITheme.GRID_GAP / 2;
            g2d.fillRoundRect(pX, START_Y - 6, UITheme.CELL_SIZE + UITheme.GRID_GAP, gridH - 12, 10, 10);
        }

        // Draw Column Step Numbers (1 to 16)
        g2d.setFont(UITheme.SMALL_FONT);
        g2d.setColor(TEXT_MUTED);
        FontMetrics fmCol = g2d.getFontMetrics();
        for (int col = 0; col < UITheme.STEPS; col++) {
            String stepStr = String.valueOf(col + 1);
            int cx = START_X + col * (UITheme.CELL_SIZE + UITheme.GRID_GAP) + UITheme.CELL_SIZE / 2;
            int nx = cx - fmCol.stringWidth(stepStr) / 2;
            
            if (col == playheadStep) {
                g2d.setColor(TEXT_LIGHT);
            } else {
                g2d.setColor(TEXT_MUTED);
            }
            g2d.drawString(stepStr, nx, START_Y - 20);
        }

        // Draw Matrix Grid & Row Note Labels
        for (int row = 0; row < UITheme.ROWS; row++) {
            // Note Label (e.g. C5, B4, ...)
            g2d.setColor(TEXT_LIGHT);
            g2d.setFont(UITheme.NORMAL_FONT);
            FontMetrics fmRow = g2d.getFontMetrics();
            String label = row < ROW_LABELS.length ? ROW_LABELS[row] : "R" + (row + 1);
            int lx = START_X - 25 - fmRow.stringWidth(label) / 2;
            int ly = START_Y + row * (UITheme.CELL_SIZE + UITheme.GRID_GAP) + UITheme.CELL_SIZE / 2 + fmRow.getAscent() / 2 - 2;
            g2d.drawString(label, lx, ly);

            // Row Cells
            for (int col = 0; col < UITheme.STEPS; col++) {
                int x = START_X + col * (UITheme.CELL_SIZE + UITheme.GRID_GAP);
                int y = START_Y + row * (UITheme.CELL_SIZE + UITheme.GRID_GAP);

                boolean active = isCellActive(row, col);

                if (active) {
                    // Outer glow
                    g2d.setColor(PURPLE_GLOW);
                    g2d.fillRoundRect(x - 2, y - 2, UITheme.CELL_SIZE + 4, UITheme.CELL_SIZE + 4, 12, 12);
                    // Inner active fill
                    g2d.setColor(PURPLE_ACTIVE);
                } else {
                    g2d.setColor(CELL_INACTIVE);
                }

                g2d.fillRoundRect(x, y, UITheme.CELL_SIZE, UITheme.CELL_SIZE, 8, 8);
            }
        }
    }
}
