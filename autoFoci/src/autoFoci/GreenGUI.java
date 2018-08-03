package autoFoci;

import autoFoci.ObjectFinder;
import autoFoci.HistAnalyzer;
import autoFoci.AnalyzeDialog;
import autoFoci.ImagePanel;

import java.io.*;
import java.awt.*;
import java.awt.Color;
import java.awt.event.*;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyleConstants;
import javax.swing.text.SimpleAttributeSet;
import java.util.ArrayList;
import java.text.NumberFormat;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.HyperlinkEvent;
import java.net.URI;
import java.util.Properties;


public class GreenGUI {
    public static final Color bg = new Color(17, 17, 10, 255);
    public static final Color fg = new Color(100, 150, 0, 255);
    public static final Font font = new Font("SansSerif", Font.PLAIN, 12);

    public static class JGradientButton extends JButton {
        Color color_top, color_bottom;
        public JGradientButton(String text, Color color_top, Color color_bottom) {
            super(text);
            setContentAreaFilled(false);
            this.color_top = color_top;
            this.color_bottom = color_bottom;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setPaint(new GradientPaint(
                new Point(0, 0),
                this.color_top,
                new Point(0, getHeight()),
                this.color_bottom));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();

            super.paintComponent(g);
        }
    }

    public static final class GreenJButton extends JGradientButton {
        public GreenJButton(String text) {
            super(text, new Color(17, 17, 10, 255), new Color(100, 150, 0, 255));
            this.setForeground(new Color(190, 190, 190));
            Border border = BorderFactory.createLineBorder(fg, 1);
            this.setBorder(border);
        }
    }

    public static final class DarkGreenJButton extends JGradientButton {
        public DarkGreenJButton(String text) {
            super(text, new Color(17, 17, 10, 255), new Color(30, 75, 0, 255));
            this.setForeground(new Color(190, 190, 190));
            Border border = BorderFactory.createLineBorder(fg, 1);
            this.setBorder(border);
        }
    }


    public static final class GreenJFormattedTextField extends JFormattedTextField {
        Color bg = new Color(17, 17, 10, 255);
        Color fg = new Color(100, 150, 0, 255);
        public GreenJFormattedTextField(NumberFormat format) {
            super(format);
            this.setBackground(bg);
            this.setForeground(fg);
            this.setCaretColor(fg);
            this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(fg.darker()), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
            this.setComponentPopupMenu(popup());
        }
    }

    public static final class GreenJTextField extends JTextField {
        Color bg = new Color(17, 17, 10, 255);
        Color fg = new Color(100, 150, 0, 255);
        public GreenJTextField() {
            super(60);
            this.setBackground(bg);
            this.setForeground(fg);
            this.setCaretColor(fg);
            this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(fg.darker()), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
            this.setComponentPopupMenu(popup());
        }
        public GreenJTextField(String text) {
            super(text, 60);
            this.setBackground(bg);
            this.setForeground(fg);
            this.setCaretColor(fg);
            this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(fg.darker()), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
            this.setComponentPopupMenu(popup());
        }
        public GreenJTextField(int col, boolean editable) {
            super(col);
            this.setBackground(bg);
            this.setForeground(fg);
            this.setCaretColor(fg);
            this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(fg.darker()), BorderFactory.createEmptyBorder(10, 10, 10, 10)));
            if (!editable)
                this.setEditable(false);

        }
    }

    public static final JPopupMenu popup() {
        JPopupMenu popup = new JPopupMenu();
        Action cut_action = new DefaultEditorKit.CutAction();
        JMenuItem cut = new JMenuItem(cut_action);
        cut.setText("Cut");
        cut.setBackground(fg);
        cut.setForeground(bg);
        Action copy_action = new DefaultEditorKit.CopyAction();
        JMenuItem copy = new JMenuItem(copy_action);
        copy.setText("Copy");
        copy.setBackground(fg);
        copy.setForeground(bg);
        Action paste_action = new DefaultEditorKit.PasteAction();
        JMenuItem paste = new JMenuItem(paste_action);
        paste.setText("Paste");
        paste.setBackground(fg);
        paste.setForeground(bg);

        popup.add(cut);
        popup.add(copy);
        popup.add(paste);

        return popup;
    }

    public static final class GreenJLabel extends JLabel {
        Color bg = new Color(17, 17, 10, 255);
        Color fg = new Color(100, 150, 0, 255);
        public GreenJLabel() {
            super();
            this.setForeground(fg);
        }
        public GreenJLabel(String text) {
            super(text);
            this.setForeground(fg);
        }
        public GreenJLabel(String text, int horizontalAlignment) {
            super(text, horizontalAlignment);
            this.setForeground(fg);
        }
    }


    public static final class GreenJCheckBox extends JCheckBox {
        Color bg = new Color(17, 17, 10, 255);
        Color fg = new Color(100, 150, 0, 255);
        public GreenJCheckBox(String text) {
            super(text);
            this.setBackground(bg);
            this.setForeground(fg);
        }
    }

    public static final class GreenJTextArea extends JTextArea {
        Color bg = new Color(17, 17, 10, 255);
        Color fg = new Color(100, 150, 0, 255);
        public GreenJTextArea() {
            super();
            this.setBackground(bg);
            this.setForeground(fg);
        }
        public GreenJTextArea(String text) {
            super(text);
            this.setBackground(bg);
            this.setForeground(fg);
        }
        public GreenJTextArea(String text, int rows, int columns) {
            super(text, rows, columns);
            this.setBackground(bg);
            this.setForeground(fg);
        }
        public GreenJTextArea(int rows, int columns) {
            super(rows, columns);
            this.setBackground(bg);
            this.setForeground(fg);
        }
    }

    public static final class GreenJTextPane extends JTextPane {
        Color bg = new Color(17, 17, 10, 255);
        Color fg = new Color(100, 150, 0, 255);
        public GreenJTextPane(String text) {
            super();
            this.setContentType("text/html");
            this.setEditable(false);
            this.setText(text);
            this.setBackground(bg);
            StyledDocument doc = this.getStyledDocument();
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, fg);
            doc.setParagraphAttributes(0, doc.getLength(), attrs, false);
            this.setDocument(doc);
        }
    }

    public static final class GreenJMenuBar extends JMenuBar {
        Color bgColor = bg;

        public GreenJMenuBar() {
            super();
        }

        public void setColor(Color color) {
            bgColor = color;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setColor(bgColor);
            g2d.fillRect(0, 0, getWidth() - 1, getHeight() - 1);
        }
    }


    public static final class GreenJMenu extends JMenu {
        public GreenJMenu(String text) {
            super(text);
            this.setForeground(fg);
        }
    }


    public static final class GreenJMenuItem extends JMenuItem {
        public GreenJMenuItem(String text) {
            super(text);
            this.setForeground(fg);
            this.setBackground(bg);
        }
    }


    public static final class GreenJTabbedPane extends JTabbedPane {
        public GreenJTabbedPane() {

            this.setForeground(fg);
            this.setBackground(bg);

        }
    }


    public static final class GreenJPanel extends JPanel {
        Color bg = new Color(17, 17, 10, 255);
        Color fg = new Color(100, 150, 0, 255);
        public GreenJPanel() {
            super();
            this.setBackground(bg);
        }
        public GreenJPanel(LayoutManager layout) {
            super(layout);
            this.setBackground(bg);
        }
    }


    public static class JSystemFileChooser extends JFileChooser {
        public JSystemFileChooser(String path) {
            super(path);
            // SystemLookAndFeel should only apply to JFileChooser, because else in linux mint there were problems with TextArea background colors
            LookAndFeel previousLF = UIManager.getLookAndFeel();
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                super.updateUI();
                UIManager.setLookAndFeel(previousLF);
            } catch (IllegalAccessException | UnsupportedLookAndFeelException | InstantiationException | ClassNotFoundException e) {}

        }
    }
}