package autoFoci;

import java.util.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.*;
import javax.swing.text.View;

// taken from http://stackoverflow.com/questions/7054466/how-can-i-change-the-shape-of-a-jtabbedpane-tab/7056093#7056093

public class CustomTabbedPaneUI extends BasicTabbedPaneUI {

    private static final Color bg = new Color(17, 17, 10, 255);
    private static final Color fg = new Color(100, 150, 0, 255);

    private Color selectColor;
    private Color deSelectColor;
    private int inclTab = 4;
    private int anchoFocoV = inclTab;
    private int anchoFocoH = 4;
    private int anchoCarpetas = 18;
    private Polygon shape;

    public static ComponentUI createUI(JComponent c) {
        return new CustomTabbedPaneUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        selectColor = fg;
        deSelectColor = bg;
        tabAreaInsets.right = anchoCarpetas;
    }

    @Override
    protected void paintTabArea(Graphics g, int tabPlacement, int selectedIndex) {
        if (runCount > 1) {
            int lines[] = new int[runCount];
            for (int i = 0; i < runCount; i++) {
                lines[i] = rects[tabRuns[i]].y + (tabPlacement == TOP ? maxTabHeight : 0);
            }
            Arrays.sort(lines);
            if (tabPlacement == TOP) {
                int fila = runCount;
                for (int i = 0; i < lines.length - 1; i++, fila--) {
                    Polygon carp = new Polygon();
                    carp.addPoint(0, lines[i]);
                    carp.addPoint(tabPane.getWidth() - 2 * fila - 2, lines[i]);
                    carp.addPoint(tabPane.getWidth() - 2 * fila, lines[i] + 3);
                    if (i < lines.length - 2) {
                        carp.addPoint(tabPane.getWidth() - 2 * fila, lines[i + 1]);
                        carp.addPoint(0, lines[i + 1]);
                    } else {
                        carp.addPoint(tabPane.getWidth() - 2 * fila, lines[i] + rects[selectedIndex].height);
                        carp.addPoint(0, lines[i] + rects[selectedIndex].height);
                    }
                    carp.addPoint(0, lines[i]);
                    g.setColor(bg);
                    g.fillPolygon(carp);
                    g.setColor(bg);
                    g.drawPolygon(carp);
                }
            } else {
                int fila = 0;
                for (int i = 0; i < lines.length - 1; i++, fila++) {
                    Polygon carp = new Polygon();
                    carp.addPoint(0, lines[i]);
                    carp.addPoint(tabPane.getWidth() - 2 * fila - 1, lines[i]);
                    carp.addPoint(tabPane.getWidth() - 2 * fila - 1, lines[i + 1] - 3);
                    carp.addPoint(tabPane.getWidth() - 2 * fila - 3, lines[i + 1]);
                    carp.addPoint(0, lines[i + 1]);
                    carp.addPoint(0, lines[i]);
                    g.setColor(hazAlfa(fila + 2));
                    g.fillPolygon(carp);
                    g.setColor(bg);
                    g.drawPolygon(carp);
                }
            }
        }
        super.paintTabArea(g, tabPlacement, selectedIndex);
    }

    @Override
    protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {
        Graphics2D g2D = (Graphics2D) g;
        GradientPaint gradientShadow;
        int xp[] = null;
        int yp[] = null;
        xp = new int[] {
            x,
            x,
            x + 3,
            x + w - inclTab - 6,
            x + w - inclTab - 2,
            x + w - inclTab,
            x + w - inclTab,
            x
        };
        yp = new int[] {
            y + h, y + 3, y, y, y + 1, y + 3, y + h, y + h
        };
        gradientShadow = new GradientPaint(0, 0, fg, 0, y + h / 2, bg);
        shape = new Polygon(xp, yp, xp.length);
        if (isSelected) {
            g2D.setColor(selectColor);
            g2D.setPaint(gradientShadow);
        } else {
            if (tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex)) {
                g2D.setColor(deSelectColor);
                GradientPaint gradientShadowTmp = new GradientPaint(0, 0, bg.brighter(), 0, y + h / 2, bg);
                g2D.setPaint(gradientShadowTmp);
            } else {
                GradientPaint gradientShadowTmp = new GradientPaint(0, 0, fg, 0, y + 15 + h / 2, fg.darker());
                g2D.setPaint(gradientShadowTmp);
            }
        }
        g2D.fill(shape);
        if (runCount > 1) {
            g2D.setColor(hazAlfa(getRunForTab(tabPane.getTabCount(), tabIndex) - 1));
            g2D.fill(shape);
        }
        g2D.fill(shape);
    }

    @Override
    protected void paintText(Graphics g, int tabPlacement, Font font, FontMetrics metrics, int tabIndex, String title, Rectangle textRect, boolean isSelected) {
        super.paintText(g, tabPlacement, font, metrics, tabIndex, title, textRect, isSelected);
        g.setFont(font);
        View v = getTextViewForTab(tabIndex);
        if (v != null) {
            // html
            v.paint(g, textRect);
        } else {
            // plain text
            int mnemIndex = tabPane.getDisplayedMnemonicIndexAt(tabIndex);
            if (tabPane.isEnabled() && tabPane.isEnabledAt(tabIndex)) {
                g.setColor(fg);
                BasicGraphicsUtils.drawStringUnderlineCharAt(g, title, mnemIndex, textRect.x, textRect.y + metrics.getAscent());
            } else { // tab disabled
                g.setColor(bg);
                BasicGraphicsUtils.drawStringUnderlineCharAt(g, title, mnemIndex, textRect.x, textRect.y + metrics.getAscent());
                g.setColor(bg);
                BasicGraphicsUtils.drawStringUnderlineCharAt(g, title, mnemIndex, textRect.x - 1, textRect.y + metrics.getAscent() - 1);
            }
        }
    }

    @Override
    protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) {
        return 20 + inclTab + super.calculateTabWidth(tabPlacement, tabIndex, metrics);
    }

    @Override
    protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) {
        if (tabPlacement == LEFT || tabPlacement == RIGHT) {
            return super.calculateTabHeight(tabPlacement, tabIndex, fontHeight);
        } else {
            return anchoFocoH + super.calculateTabHeight(tabPlacement, tabIndex, fontHeight);
        }
    }

    @Override
    protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected) {}

    @Override
    protected void paintFocusIndicator(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect, boolean isSelected) {
        if (tabPane.hasFocus() && isSelected) {
            g.setColor(bg); // UIManager.getColor("ScrollBar.thumbShadow")
            g.drawPolygon(shape);
        }
    }

    protected Color hazAlfa(int fila) {
        int alfa = 0;
        if (fila >= 0) {
            alfa = 50 + (fila > 7 ? 70 : 10 * fila);
        }
        return new Color(0, 0, 0, alfa);
    }
}