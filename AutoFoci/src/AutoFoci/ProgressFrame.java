package AutoFoci;

import AutoFoci.MainFrame;
import AutoFoci.GreenGUI.*;

import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.util.ArrayList;
import java.text.NumberFormat;

import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.plaf.basic.BasicProgressBarUI;



public class ProgressFrame {

    JFrame frame;
    public JProgressBar progressBar, progressBar_total;
    public GreenJTextArea scrollText;
    public GreenJLabel time_label;
    public long time_start, time_current, time_duration;

    public Color ui_color = new Color(222, 122, 33, 255);
    public Font ui_font = new Font("SansSerif", Font.PLAIN, 10);

    static void addCompWeight(Container cont, GridBagLayout gbl, Component c, int x, int y, int width, int height, double weightx, double weighty) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.gridheight = height;
        gbc.weightx = weightx;
        gbc.weighty = weighty;
        gbl.setConstraints(c, gbc);
        cont.add(c);
    }

    public void change(final JProgressBar progBar, final int aantal) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                if (aantal <= progBar.getMaximum() && aantal >= 0) {
                    progBar.setValue(aantal);
                }
            }
        });
    }

    public void log(final String text) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                scrollText.append(text + "\n");
            }
        });
    }

    public void create_frame(final MainFrame main_frame) {
        //     JFrame.setDefaultLookAndFeelDecorated(true);
        frame = new JFrame();
        frame.setTitle("Progress");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // DISPOSE_ON_CLOSE only closes the window, EXIT_ON_CLOSE will exit application, DO_NOTHING_ON_CLOSE does nothing
        Image img = new ImageIcon(this.getClass().getResource("/images/zelle1.png")).getImage();
        frame.setIconImage(img);
        Container c = frame.getContentPane();
        c.setBackground(GreenGUI.bg);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (progressBar_total.getValue() > 0 && progressBar_total.getValue() < 99) {
                    GreenJTextPane message = new GreenJTextPane("<html><p>Closing the window will terminate all running processes and exit the program. Continue?</p></html>");
                    int confirmed = JOptionPane.showConfirmDialog(null, message, "Exit Program", JOptionPane.YES_NO_OPTION);
                    if (confirmed == JOptionPane.YES_OPTION) {
                        main_frame.save_config(main_frame.last_config_file);
                        System.exit(1);
                    }
                } else {
                    frame.dispose();
                }
            }
        });

        GridBagLayout gbl = new GridBagLayout();
        c.setLayout(gbl);
        GreenJPanel progress_panel_total = new GreenJPanel();
        progress_panel_total.setLayout(new GridLayout(0, 1));

        UIManager.put("ProgressBar.selectionForeground", new Color(200, 200, 200));
        UIManager.put("ProgressBar.selectionBackground", GreenGUI.fg);

        this.progressBar_total = new JProgressBar();
        this.progressBar_total.setBorderPainted(false);
        Border border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(GreenGUI.fg.darker()), "Total progress ...", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, GreenGUI.font, GreenGUI.fg);
        progress_panel_total.add(this.progressBar_total);
        progress_panel_total.setBorder(border);
        this.progressBar_total.setStringPainted(true);
        this.progressBar_total.setForeground(GreenGUI.fg);
        this.progressBar_total.setBackground(GreenGUI.bg);

        // the only way to set background to GreenGUI,bg with WindowsLookAndFeel. no idea why this works. 
        this.progressBar_total.setUI(new BasicProgressBarUI() {
            @Override protected Color getSelectionBackground() {
                return GreenGUI.fg;
            }
        });
        // weightx/weighty is used to distribute the extra space to the individual components, usually 1.0 for CENTER and 0 for side
        //                                             x  y  w  h  wx   wy
        addCompWeight(c, gbl, progress_panel_total, 0, 0, 1, 2, 1.0, 0.1);

        GreenJPanel progress_panel = new GreenJPanel();
        progress_panel.setLayout(new GridLayout(0, 1));

        this.progressBar = new JProgressBar();
        this.progressBar.setBorderPainted(false);
        border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(GreenGUI.fg.darker()), "Single directory", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, GreenGUI.font, GreenGUI.fg);
        //     this.progressBar.setBorder(border);
        this.progressBar.setStringPainted(true);
        this.progressBar.setForeground(GreenGUI.fg);
        this.progressBar.setBackground(GreenGUI.bg);

        this.progressBar.setUI(new BasicProgressBarUI() {
            @Override protected Color getSelectionBackground() {
                return GreenGUI.fg;
            }
        });

        progress_panel.add(this.progressBar);
        progress_panel.setBorder(border);

        addCompWeight(c, gbl, progress_panel, 0, 2, 1, 1, 1.0, 0.05);

        time_label = new GreenJLabel("Counting the total number of images...", SwingConstants.RIGHT);
        time_label.setBorder(BorderFactory.createEmptyBorder(7, 0, 7, 20));
        addCompWeight(c, gbl, time_label, 0, 3, 1, 1, 0., 0.05);

        scrollText = new GreenJTextArea(470, 650);
        scrollText.setMargin(new Insets(5, 5, 5, 5));
        scrollText.setEditable(false);

        addCompWeight(c, gbl, new JScrollPane(scrollText), 0, 4, 1, 1, 1.0, 0.8);



        frame.setSize(500, 700);
        frame.setVisible(true);

    }


    public void dispose() {
        frame.dispose();
    }

}