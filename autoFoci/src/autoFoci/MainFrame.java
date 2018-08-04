package autoFoci;

import autoFoci.ObjectFinder;
import autoFoci.HistAnalyzer;
import autoFoci.AnalyzeDialog;
import autoFoci.ImagePanel;
import autoFoci.GreenGUI.*;
import autoFoci.CustomTabbedPaneUI;

import java.io.*;
import java.awt.*;
import java.awt.Cursor;
import java.awt.event.*;
import java.util.ArrayList;
import java.text.NumberFormat;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.ColorUIResource;
import java.net.URI;
import java.util.Properties;


public class MainFrame implements ActionListener {
    File last_config_file = new File("autoFoci_last_config.txt");
    Properties configProps;

    Image icon = new ImageIcon(this.getClass().getResource("/images/zelle1.png")).getImage();

    JFrame main_frame;
    GreenJMenuBar menu;
    JMenu file_menu, help_menu;
    JMenuItem open_menu_item, save_menu_item, restore_default_menu_item, exit_menu_item, info_menu_item, license_menu_item;
    GreenJButton button_open, button_open_images, button_images, button_start, button_start_images, button_start_overlay, button_overlay_save, button_open_images_overlay, button_open_file_overlay;
    GreenJTextField root_path_field, root_path_field_images, image_path_field, extension_field, result_dir_field, image_path_overlay_field, root_path_overlay_file_field;
    GreenJFormattedTextField stdev_of_num_field, range_oep_field, master_channel_field, second_channel_field, dapi_channel_field, struct_dia_field, edgeThreshold_field, minArea_field, maxArea_field, minSeparation_field, minRelativeIntensity_field, overlay_offset_field, overlay_max_length_field, max_foci_field, freak_threshold_field, freak_low_threshold_field, freak_stdev_threshold_field;
    GreenJCheckBox minimum_output_checkbox, only_show_master_and_second_channel_checkbox, blind_overlay_checkbox, only_cells_with_objects_checkbox, blind_checkbox, use_minimum_algorithms_checkbox, skip_cells_with_many_foci_checkbox, rename_freaks_checkbox;
    final String foci_table_name = "00foci_table.csv";  // if changed, also search for "foci_table" in AnalyzeDialog. 
    final String foci_table_name_backup = "00foci_table_old.csv";
    final String result_dir = "00result_tables";

    // GreenJFormattedTextField master_weight_field;

    static void addComp(Container cont, GridBagLayout gbl, Component c, int x, int y, int width, int height, boolean fill_both, int padding) {
        addComp(cont, gbl, c, x, y, width, height, fill_both, false, padding);
    }
    static void addComp(Container cont, GridBagLayout gbl, Component c, int x, int y, int width, int height, boolean fill_both, boolean button, int padding) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = 1.;
        gbc.weighty = 1.;
        if (button) {
            // 	  gbc.ipadx = 15; 
            gbc.ipady = 12;
        }
        if (fill_both)
            gbc.fill = GridBagConstraints.BOTH;
        else
            gbc.fill = GridBagConstraints.NONE;
        if (padding == 0)
            gbc.insets = new Insets(3, 3, 3, 3);
        else if (padding == 1)
            gbc.insets = new Insets(10, 10, 10, 10);
        else if (padding == 2)
            gbc.insets = new Insets(30, 80, 30, 80);
        else if (padding == 3)
            gbc.insets = new Insets(0, 50, 20, 50);
        else if (padding == 4)
            gbc.insets = new Insets(0, 150, 20, 150);
        else
            gbc.insets = new Insets(0, 0, 0, 0);
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.gridheight = height;
        gbl.setConstraints(c, gbc);
        cont.add(c);
    }

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

    public void actionPerformed(ActionEvent object) {
        if (object.getSource() == open_menu_item) {
            JFrame frame = new JFrame();
            frame.setIconImage(this.icon);
            JFileChooser chooser = new JSystemFileChooser(".");
            chooser.setDialogTitle("Open configuration file");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int status = chooser.showOpenDialog(frame);
            if (status == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                load_config(selectedFile);
            }
        } else if (object.getSource() == save_menu_item) {
            JFrame frame = new JFrame();
            frame.setIconImage(this.icon);
            JFileChooser chooser = new JSystemFileChooser(".");
            chooser.setDialogTitle("Save configuration file");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int status = chooser.showSaveDialog(frame);
            if (status == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                save_config(selectedFile);
            }
        } else if (object.getSource() == restore_default_menu_item) {
            load_config(new File(""));
        } else if (object.getSource() == exit_menu_item) {
            confirm_closing();
        } else if (object.getSource() == info_menu_item) {
            GreenJTextPane info = new GreenJTextPane("<html><p style='width: 500px; font-family: san-serif;'>AutoFoci implements an automatic foci counting method, which is applicable for large numbers of single cell images. <br><br>" +
                "- The original image is used to find local maxima, which are defined as having a higher pixel value than their adjacent pixels. <br>" +
                "- These local maxima are used for a region growing algorithm, starting at the lowest maximum. Its boundaries are defined by an edge threshold. <br>" +
                "- A top-hat transformation as well as a local curvature transformation are calculated. Various object properties, like mean and maximum top-hat intensity, are stored in result files.<br>" +
                "- These object properties are combined into one object evaluation parameter (OEP), which correlates well with by eye focus evaluation.<br>" +
                "- With a short manual intervention an OEP threshold can be validated afterwards. <br><br>" +
                "For more information please visit: https://github.com/nleng/autoFoci</p></html>");

            JOptionPane.showMessageDialog(null, info, "Info", JOptionPane.INFORMATION_MESSAGE);
        } else if (object.getSource() == license_menu_item) {
            GreenJTextPane license = new GreenJTextPane("<html><p style='text-align: center; font-family: san-serif;'>Author: Nicor Lengert <br><br>autoFoci license: GNU General Public License v3.0 <br><br> Lincense from used libraries: <br><br> ImageJ: Simplified BSD License<br>Website: <a href=\"https://github.com/imagej/imagej\">https://github.com/imagej/imagej</a><br><br> JFreeChart: GNU Lesser General Public Licence (LGPL)<br>Website: <a href=\"http://www.jfree.org/jfreechart/\">http://www.jfree.org/jfreechart/</a></p></html>");
            license.addHyperlinkListener(new HyperlinkListener() {
                @Override
                public void hyperlinkUpdate(HyperlinkEvent e) {
                    if (e.getEventType().equals(HyperlinkEvent.EventType.ACTIVATED))
                        try {
                            Desktop.getDesktop().browse(new URI(e.getURL().toString()));
                        } catch (Exception ex) {} // URISyntaxException | IO
                }
            });

            license.setEditable(false);
            JOptionPane.showMessageDialog(null, license, "License", JOptionPane.INFORMATION_MESSAGE);
        } else if (object.getSource() == button_open) {
            JFrame frame = new JFrame();
            frame.setIconImage(this.icon);
            JFileChooser chooser = new JSystemFileChooser(root_path_field.getText());
            chooser.setDialogTitle("Open result file directory");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int status = chooser.showOpenDialog(frame);
            if (status == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                root_path_field.setText(selectedFile.getAbsolutePath());
            }
        } else if (object.getSource() == button_images) {
            JFrame frame = new JFrame();
            frame.setIconImage(this.icon);
            JFileChooser chooser = new JSystemFileChooser(image_path_field.getText());
            chooser.setDialogTitle("Select output save directory");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int status = chooser.showDialog(frame, "Select");
            if (status == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                image_path_field.setText(selectedFile.getAbsolutePath());
            }
        } else if (object.getSource() == button_open_images) {
            JFrame frame = new JFrame();
            frame.setIconImage(this.icon);
            JFileChooser chooser = new JSystemFileChooser(root_path_field_images.getText());
            chooser.setDialogTitle("Open image directory");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int status = chooser.showOpenDialog(frame);
            if (status == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                root_path_field_images.setText(selectedFile.getAbsolutePath());
            }
        } else if (object.getSource() == button_open_images_overlay) {
            JFrame frame = new JFrame();
            frame.setIconImage(this.icon);
            JFileChooser chooser = new JSystemFileChooser(image_path_overlay_field.getText());
            chooser.setDialogTitle("Open image directory");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int status = chooser.showOpenDialog(frame);
            if (status == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                image_path_overlay_field.setText(selectedFile.getAbsolutePath());
            }
        } else if (object.getSource() == button_open_file_overlay) {
            JFrame frame = new JFrame();
            frame.setIconImage(this.icon);
            JFileChooser chooser = new JSystemFileChooser(root_path_overlay_file_field.getText());
            chooser.setDialogTitle("Open result file");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            int status = chooser.showOpenDialog(frame);
            if (status == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                root_path_overlay_file_field.setText(selectedFile.getAbsolutePath());
            }
        } else if (object.getSource() == button_start) {
            Runnable run_AnalyzeDialog = new Runnable() {
                public void run() {

                    final boolean use_overlay_images = false;

                    final int minArea = ((Number) minArea_field.getValue()).intValue();

                    String root_path = root_path_field.getText();
                    final String image_path = image_path_field.getText();
                    final int stdev_of_num = ((Number) stdev_of_num_field.getValue()).intValue();
                    final double half_range_oep = ((Number) range_oep_field.getValue()).doubleValue() / 2.;
                    final boolean blind = blind_checkbox.isSelected();
                    final boolean use_minimum_algorithms = use_minimum_algorithms_checkbox.isSelected();
                    final int master_channel = ((Number) master_channel_field.getValue()).intValue() - 1;
                    final int second_channel = ((Number) second_channel_field.getValue()).intValue() - 1;
                    final int dapi_channel = ((Number) dapi_channel_field.getValue()).intValue() - 1;
                    final int overlay_offset = ((Number) overlay_offset_field.getValue()).intValue();
                    final double overlay_max_length = ((Number) overlay_max_length_field.getValue()).doubleValue();

                    final boolean skip_cells_with_many_foci = skip_cells_with_many_foci_checkbox.isSelected();
                    final int max_foci = ((Number) max_foci_field.getValue()).intValue();

                    if (wrong_channels()) {
                        GreenJTextPane message = new GreenJTextPane("Channels can only have the values 1, 2 or 3.");
                        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }


                    if (root_path.equals("")) {
                        root_path = new File(image_path, result_dir).getAbsolutePath();
                    }
                    final String output_table_filename = new File(root_path, foci_table_name).getAbsolutePath();
                    File f = new File(root_path);
                    if (!f.exists()) {
                        GreenJTextPane message = new GreenJTextPane("<html><p>Error: Result file directory does not exists.</p></html>");
                        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    f = new File(image_path);
                    if (!f.exists()) {
                        GreenJTextPane message = new GreenJTextPane("<html><p>Error: Output save directory does not exists.</p></html>");
                        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    AnalyzeDialog ad = new AnalyzeDialog(stdev_of_num, half_range_oep, use_overlay_images, minArea, master_channel, second_channel, dapi_channel, blind, use_minimum_algorithms, skip_cells_with_many_foci, max_foci, overlay_offset, overlay_max_length);
                    try {
                        main_frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                        boolean success = ad.create_analyze_frame(root_path, image_path, output_table_filename);
                        System.out.println(success);
                        main_frame.setCursor(Cursor.getDefaultCursor());
                        backup_file(root_path);
                        if (!success) {
                            GreenJTextPane message = new GreenJTextPane("<html><p>Something went wrong. Possible reasons:<br>" +
                                "Csv files not found or with wrong format.</p></html>");
                            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        }
                    } catch (Exception e) { // catch all and print
                        main_frame.setCursor(Cursor.getDefaultCursor());
                        error_message(e);
                    }
                }
            };

            Thread thread = new Thread(run_AnalyzeDialog);
            thread.start();
        } else if (object.getSource() == button_start_images) {
            Runnable run_autoFoci = new Runnable() {
                public void run() {
                    final String root_path_images = root_path_field_images.getText();
                    String extension = extension_field.getText();
                    if (!extension.startsWith(".")) extension = "." + extension;
                    // 		final String result_dir = result_dir_field.getText();
                    final String result_dir = MainFrame.this.result_dir;
                    final int master_channel = ((Number) master_channel_field.getValue()).intValue() - 1;
                    final int second_channel = ((Number) second_channel_field.getValue()).intValue() - 1;
                    final int dapi_channel = ((Number) dapi_channel_field.getValue()).intValue() - 1;
                    final int freak_threshold = ((Number) freak_threshold_field.getValue()).intValue();
                    final int freak_low_threshold = ((Number) freak_low_threshold_field.getValue()).intValue();
                    final int freak_stdev_threshold = ((Number) freak_stdev_threshold_field.getValue()).intValue();
                    final double radStructEle = ((Number) struct_dia_field.getValue()).doubleValue();
                    final double edgeThreshold = ((Number) edgeThreshold_field.getValue()).doubleValue();
                    final int minArea = ((Number) minArea_field.getValue()).intValue();
                    final int minSeparation = ((Number) minSeparation_field.getValue()).intValue();
                    final double minRelativeIntensity = ((Number) minRelativeIntensity_field.getValue()).doubleValue();

                    final boolean use_overlay_images = false;
                    final boolean rename_freaks = rename_freaks_checkbox.isSelected();
                    final double oep_thresh = 0.;

                    if (wrong_channels()) {
                        GreenJTextPane message = new GreenJTextPane("Channels can only have the values 1, 2 or 3.");
                        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    File f = new File(root_path_images);
                    if (!(f.exists() && f.isDirectory())) {
                        GreenJTextPane message = new GreenJTextPane("<html><p>Error: Image directory does not exists.</p></html>");
                        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    try {
                        ObjectFinder of = new ObjectFinder(MainFrame.this, root_path_images, result_dir, extension, master_channel, second_channel, dapi_channel, freak_threshold, freak_low_threshold, freak_stdev_threshold, radStructEle, edgeThreshold, minArea, minSeparation, minRelativeIntensity, oep_thresh, rename_freaks);
                        boolean success = of .run();
                        if (!success) {
                            GreenJTextPane message = new GreenJTextPane("<html><p>No images with file extension " + extension + " were found in the specified location. <br>" + 
                                                                        "The file extension must be specified exactly as given in the file name.</p></html>");
                            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        }


                    } catch (Exception e) {
                        error_message(e);
                    }
                }
            };

            Thread thread = new Thread(run_autoFoci);
            thread.start();
        } else if (object.getSource() == button_start_overlay) {
            Runnable run_autoFoci = new Runnable() {
                public void run() {

                    final boolean use_overlay_images = true;
                    final boolean only_show_master_and_second_channel = only_show_master_and_second_channel_checkbox.isSelected();
                    final boolean blind_overlay = blind_overlay_checkbox.isSelected();

                    String extension = extension_field.getText();
                    if (!extension.startsWith(".")) extension = "." + extension;
                    final String result_dir = "00results_overlay";
                    final int master_channel = ((Number) master_channel_field.getValue()).intValue() - 1;
                    final int second_channel = ((Number) second_channel_field.getValue()).intValue() - 1;
                    final int dapi_channel = ((Number) dapi_channel_field.getValue()).intValue() - 1;
                    final double radStructEle = ((Number) struct_dia_field.getValue()).doubleValue();
                    final double edgeThreshold = ((Number) edgeThreshold_field.getValue()).doubleValue();
                    final int minArea = ((Number) minArea_field.getValue()).intValue();
                    final int minSeparation = ((Number) minSeparation_field.getValue()).intValue();
//                     final double minRelativeIntensity = ((Number) minRelativeIntensity_field.getValue()).doubleValue();
                    final int overlay_offset = ((Number) overlay_offset_field.getValue()).intValue();
                    final double overlay_max_length = ((Number) overlay_max_length_field.getValue()).doubleValue();

                    final String root_path_overlay_file = root_path_overlay_file_field.getText();
                    final String image_path_overlay = image_path_overlay_field.getText();
                    final String output_table_filename = new File(image_path_overlay, foci_table_name).getAbsolutePath(); // not used with overlay option
                    final int stdev_of_num = ((Number) stdev_of_num_field.getValue()).intValue();
                    final double half_range_oep = ((Number) range_oep_field.getValue()).doubleValue() / 2.;
                    final boolean blind = blind_checkbox.isSelected();
                    final boolean use_minimum_algorithms = use_minimum_algorithms_checkbox.isSelected();
                    
                    final boolean skip_cells_with_many_foci = skip_cells_with_many_foci_checkbox.isSelected();
                    final int max_foci = ((Number) max_foci_field.getValue()).intValue();

                    boolean run_overlay_images = true;

                    if (wrong_channels()) {
                        GreenJTextPane message = new GreenJTextPane("Channels can only have the values 1, 2 or 3.");
                        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    File f = new File(image_path_overlay);
                    if (!(f.exists() && f.isDirectory())) {
                        GreenJTextPane message = new GreenJTextPane("<html><p>Error: Image directory does not exists.</p></html>");
                        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    f = new File(root_path_overlay_file);
                    if (!(f.exists() && f.isFile())) {
                        GreenJTextPane message = new GreenJTextPane("<html><p>Error: Result file does not exists.</p></html>");
                        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    String table_extension = root_path_overlay_file.substring(root_path_overlay_file.lastIndexOf(".") + 1, root_path_overlay_file.length());
                    if (!"csv".equalsIgnoreCase(table_extension)) {
                        GreenJTextPane message = new GreenJTextPane("<html><p>Error: Result file is not a csv file.</p></html>");
                        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }

                    AnalyzeDialog ad = new AnalyzeDialog(stdev_of_num, half_range_oep, use_overlay_images, minArea, master_channel, second_channel, dapi_channel, blind, use_minimum_algorithms, skip_cells_with_many_foci, max_foci, overlay_offset, overlay_max_length);
                    try {
                        main_frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                        boolean success = ad.create_analyze_frame(root_path_overlay_file, image_path_overlay, output_table_filename);

                        main_frame.setCursor(Cursor.getDefaultCursor());
                        if (!success) {
                            GreenJTextPane message = new GreenJTextPane("No .csv files were found in the specified location.");
                            JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                        }

                    } catch (Exception e) { // catch all and print
                        main_frame.setCursor(Cursor.getDefaultCursor());
                        run_overlay_images = false;
                        error_message(e);
                    }

                    // lock the thread until the analyze window is closed
                    synchronized(ad.lock) {
                        while (!ad.done) {
                            try {
                                ad.lock.wait();
                            } catch (Exception e) {
                                error_message(e);
                            }
                        }
                        // if the analyze window is closed, find objects and mark foci
                        double oep_thresh = ad.oep_thresh;
                        // here we use oep from the original table
                        double[] oep = ad.oep_arr[3];
                        // muss noch das save to table ausschalten
                        if (run_overlay_images) {
                            try {
                                HistAnalyzer hista = new HistAnalyzer();
                                boolean success = hista.create_overlay_stack(image_path_overlay, root_path_overlay_file, extension, oep, oep_thresh, overlay_offset, overlay_max_length, master_channel, second_channel, dapi_channel, blind_overlay, only_show_master_and_second_channel, skip_cells_with_many_foci, max_foci);

                                if (!success) {
                                    GreenJTextPane message = new GreenJTextPane("No " + extension + " images were found in the specified location.");
                                    JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.INFORMATION_MESSAGE);
                                }
                            } catch (Exception e) {
                                error_message(e);
                            }
                        }
                    }

                }
            };
            Thread thread = new Thread(run_autoFoci);
            thread.start();
            // 	    thread.join();
        }
    } // END actionPerformed

    public void create_tabbed_dialog() {
        UIManager.put("ToggleButton.select", GreenGUI.fg.darker().darker());
        UIManager.put("ToggleButton.background", GreenGUI.bg);
        UIManager.put("ToggleButton.highlight", GreenGUI.bg);
        UIManager.put("ToggleButton.darkShadow", GreenGUI.bg);
        UIManager.put("ToggleButton.foreground", new Color(200, 200, 200));
        UIManager.put("ToggleButton.background", GreenGUI.bg);

        UIManager.put("MenuBar.border", BorderFactory.createLineBorder(GreenGUI.fg.darker(), 1));
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(GreenGUI.fg.darker(), 1));
        UIManager.put("MenuItem.border", BorderFactory.createLineBorder(GreenGUI.bg.brighter().brighter(), 1));
        UIManager.put("MenuItem.opaque", true);

        UIManager.put("TabbedPane.highlight", GreenGUI.fg.darker());
        UIManager.put("TabbedPane.shadow", GreenGUI.fg);
        UIManager.put("TabbedPane.darkShadow", GreenGUI.fg.darker());
        UIManager.put("TabbedPane.contentAreaColor", GreenGUI.fg);

        // for message dialog
        UIManager.put("OptionPane.background", GreenGUI.bg);
        UIManager.put("Panel.background", GreenGUI.bg);
        UIManager.put("OptionPane.cancelButtonText", "Cancel");
        UIManager.put("OptionPane.noButtonText", "No");
        UIManager.put("OptionPane.okButtonText", "Ok");
        UIManager.put("OptionPane.yesButtonText", "Yes");

        this.main_frame = new JFrame();
        this.main_frame.setTitle("autoFoci");
        //     this.main_frame.setResizable(false); // else the images are buggy
        this.main_frame.getContentPane().setBackground(GreenGUI.fg);
        this.main_frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE); // DISPOSE_ON_CLOSE only closes the window, EXIT_ON_CLOSE will exit application, DO_NOTHING_ON_CLOSE does nothing
        // set icon with relative path
        Image img = new ImageIcon(this.getClass().getResource("/images/zelle1.png")).getImage();
        this.main_frame.setIconImage(img);
        // ask user before exit
        this.main_frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                confirm_closing();
            }
        });

        menu = new GreenJMenuBar();
        file_menu = new GreenJMenu("File");
        open_menu_item = new GreenJMenuItem("Load config");
        open_menu_item.addActionListener(this);
        save_menu_item = new GreenJMenuItem("Save config");
        save_menu_item.addActionListener(this);
        restore_default_menu_item = new GreenJMenuItem("Restore default config");
        restore_default_menu_item.addActionListener(this);
        exit_menu_item = new GreenJMenuItem("Exit");
        exit_menu_item.addActionListener(this);

        help_menu = new GreenJMenu("Help");
        info_menu_item = new GreenJMenuItem("Info");
        info_menu_item.addActionListener(this);
        license_menu_item = new GreenJMenuItem("License");
        license_menu_item.addActionListener(this);

        menu.add(file_menu);
        menu.add(help_menu);

        file_menu.add(open_menu_item);
        file_menu.add(save_menu_item);
        file_menu.add(restore_default_menu_item);
        file_menu.add(exit_menu_item);

        help_menu.add(info_menu_item);
        help_menu.add(license_menu_item);

        GreenJPanel image_panel = image_start_dialog();
        GreenJPanel files_panel = result_files_dialog();
        GreenJPanel overlay_panel = overlay_dialog();

        load_config();

        Border padding = BorderFactory.createEmptyBorder(20, 20, 5, 20);
        image_panel.setBorder(padding);
        files_panel.setBorder(padding);


        JTabbedPane tabbedPane = new JTabbedPane(); // (JTabbedPane.TOP,JTabbedPane.SCROLL_TAB_LAYOUT)
        tabbedPane.setUI(new CustomTabbedPaneUI());

        tabbedPane.addTab("Create results files from images", null, image_panel, "All images in a directory and its subdirectories will be analyzed.");
        tabbedPane.addTab("Analyze result files", null, files_panel, "Analyzes all result files in one directory.");
        tabbedPane.addTab("Mark foci on images", null, overlay_panel, "Marks foci and opens the images as a stack.");

        this.main_frame.add(menu, BorderLayout.NORTH);
        this.main_frame.add(tabbedPane, BorderLayout.CENTER);

        this.main_frame.pack();
        this.main_frame.setLocationRelativeTo(null);
        this.main_frame.setVisible(true);

    }

    public GreenJPanel image_start_dialog() {
        GreenJPanel panel = new GreenJPanel();
        GridBagLayout gbl = new GridBagLayout();
        panel.setLayout(gbl);

        NumberFormat int_format = NumberFormat.getIntegerInstance();
        int_format.setGroupingUsed(false);
        NumberFormat double_format = NumberFormat.getNumberInstance();
        double_format.setGroupingUsed(false);
        double_format.setMaximumFractionDigits(5);

        root_path_field_images = new GreenJTextField();

        button_open_images = new GreenJButton("Open image directory");
        button_open_images.addActionListener(this);

        addComp(panel, gbl, new GreenJLabel("<html><p>Open a directory with images or a directory with multiple image subdirectories (only first order subdirectories are analyzed).</p></html>"), 0, 0, 4, 1, true, 1);

        addComp(panel, gbl, button_open_images, 0, 1, 2, 1, true, true, 2);
        addComp(panel, gbl, root_path_field_images, 2, 1, 2, 1, true, 1);

        addComp(panel, gbl, new GreenJLabel("File extension"), 0, 2, 2, 1, true, 1);
        extension_field = new GreenJTextField();
        addComp(panel, gbl, extension_field, 2, 2, 2, 1, true, 1);

        addComp(panel, gbl, new GreenJLabel("Master channel (1: red, 2: green, 3: blue)"), 0, 4, 1, 1, true, 1);
        master_channel_field = new GreenJFormattedTextField(int_format);
        addComp(panel, gbl, master_channel_field, 1, 4, 1, 1, true, 1);

        addComp(panel, gbl, new GreenJLabel("Second channel"), 2, 4, 1, 1, true, 1);
        second_channel_field = new GreenJFormattedTextField(int_format);
        addComp(panel, gbl, second_channel_field, 3, 4, 1, 1, true, 1);

        addComp(panel, gbl, new GreenJLabel("<html>DAPI channel (Used to define the nuclear area of the cell. Can be any other channel, which shows a nucleus marker.)</html>"), 0, 5, 3, 1, true, 1);
        dapi_channel_field = new GreenJFormattedTextField(int_format);
        addComp(panel, gbl, dapi_channel_field, 3, 5, 1, 1, true, 1);

        addComp(panel, gbl, new GreenJLabel("<html>Exclude 'freaks': nuclei with mean intensity below the first<br> or above the second value in one of the channels</html>"), 0, 6, 2, 1, true, 1);
        freak_low_threshold_field = new GreenJFormattedTextField(int_format);
        addComp(panel, gbl, freak_low_threshold_field, 2, 6, 1, 1, true, 1);
        freak_threshold_field = new GreenJFormattedTextField(int_format);
        addComp(panel, gbl, freak_threshold_field, 3, 6, 1, 1, true, 1);

        addComp(panel, gbl, new GreenJLabel("<html>Exclude also cells with a very small intensity standard deviation below this value.</html>"), 0, 7, 2, 1, true, 1);
        freak_stdev_threshold_field = new GreenJFormattedTextField(int_format);
        addComp(panel, gbl, freak_stdev_threshold_field, 2, 7, 1, 1, true, 1);

        rename_freaks_checkbox = new GreenJCheckBox("move freaks to subdirectory");
        addComp(panel, gbl, rename_freaks_checkbox, 3, 7, 1, 1, true, 1);

        GreenJPanel sub_panel = new GreenJPanel();
        GridBagLayout gbl_sub = new GridBagLayout();
        sub_panel.setLayout(gbl_sub);
        Border border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(GreenGUI.fg.darker()), "Optimized for 120x120 pixels cell images", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, GreenGUI.font, GreenGUI.fg);
        sub_panel.setBorder(border);

        addComp(sub_panel, gbl_sub, new GreenJLabel("Edge threshold (as a fraction of the peak threshold)"), 0, 0, 1, 1, true, 1);
        edgeThreshold_field = new GreenJFormattedTextField(double_format);
        addComp(sub_panel, gbl_sub, edgeThreshold_field, 1, 0, 1, 1, true, 1);

        addComp(sub_panel, gbl_sub, new GreenJLabel("Local maximum radius in pixels (object separation)"), 0, 1, 1, 1, true, 1);
        minSeparation_field = new GreenJFormattedTextField(int_format);
        addComp(sub_panel, gbl_sub, minSeparation_field, 1, 1, 1, 1, true, 1);

        addComp(sub_panel, gbl_sub, new GreenJLabel("Minimum area in pixels"), 0, 2, 1, 1, true, 1);
        minArea_field = new GreenJFormattedTextField(int_format);
        addComp(sub_panel, gbl_sub, minArea_field, 1, 2, 1, 1, true, 1);

        addComp(sub_panel, gbl_sub, new GreenJLabel("<html>Minimum intensity (realtive to nuclear mean)<html>"), 0, 4, 1, 1, true, 1);
        minRelativeIntensity_field = new GreenJFormattedTextField(double_format);
        addComp(sub_panel, gbl_sub, minRelativeIntensity_field, 1, 4, 1, 1, true, 1);

        addComp(sub_panel, gbl_sub, new GreenJLabel("<html>Diameter of structuring element (>= focus diameter) for tophat <br>transformation, which is applied before variance calculation<html>"), 0, 5, 1, 1, true, 1);
        struct_dia_field = new GreenJFormattedTextField(double_format);
        addComp(sub_panel, gbl_sub, struct_dia_field, 1, 5, 1, 1, true, 1);

        addComp(panel, gbl, sub_panel, 2, 8, 2, 2, true, 1);

        GreenJTextPane image_text = new GreenJTextPane("<html><p style='width: 210px; font-family: san-serif;'>This process detects all local intensity maxima without distingishing true foci from background objects. This differentiation is done in the second step \"Analyze result files\".<br><br> For more information about the method see Help->Info.</p></html>");

        addComp(panel, gbl, image_text, 0, 8, 2, 1, true, 1);

        button_start_images = new GreenJButton("Create result files");
        button_start_images.addActionListener(this);
        addComp(panel, gbl, button_start_images, 0, 9, 2, 1, true, true, 2);

        return panel;
    }

    public GreenJPanel result_files_dialog() {
        GreenJPanel panel = new GreenJPanel();
        GridBagLayout gbl = new GridBagLayout();
        panel.setLayout(gbl);

        button_images = new GreenJButton("Open image directory");
        button_images.addActionListener(this);
        image_path_field = new GreenJTextField();

        button_open = new GreenJButton("Open result file directory");
        button_open.addActionListener(this);
        root_path_field = new GreenJTextField();
        Border root_path_field_border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(GreenGUI.fg.darker()), "If empty, image path + " + result_dir + " is used.", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.BELOW_BOTTOM, GreenGUI.font, GreenGUI.fg); // DEFAULT_POSITION
        root_path_field.setBorder(root_path_field_border);

        button_start = new GreenJButton("Analyze result files");
        button_start.addActionListener(this);


        GreenJPanel subpanel_oep = new GreenJPanel();
        GridBagLayout gbl_oep = new GridBagLayout();
        subpanel_oep.setLayout(gbl_oep);

        NumberFormat int_format = NumberFormat.getIntegerInstance();
        int_format.setGroupingUsed(false);
        NumberFormat double_format = NumberFormat.getNumberInstance();
        double_format.setGroupingUsed(false);
        double_format.setMaximumFractionDigits(5);
        
        use_minimum_algorithms_checkbox = new GreenJCheckBox("Include minimum algorithms for estimated threshold");
        addComp(subpanel_oep, gbl_oep, use_minimum_algorithms_checkbox, 0, 0, 2, 1, true, 1);

        addComp(subpanel_oep, gbl_oep, new GreenJLabel("Interval for the 'range-algorithm'"), 0, 1, 1, 1, true, 1);
        range_oep_field = new GreenJFormattedTextField(double_format);
        addComp(subpanel_oep, gbl_oep, range_oep_field, 1, 1, 1, 1, true, 1);

        addComp(subpanel_oep, gbl_oep, new GreenJLabel("<html><p>Number of adjacent objects used to calculate the<br> standard deviation in the 'stDev-algorithm'</p></html>"), 0, 2, 1, 1, true, 1);
        stdev_of_num_field = new GreenJFormattedTextField(int_format);
        addComp(subpanel_oep, gbl_oep, stdev_of_num_field, 1, 2, 1, 1, true, 1);

        Border border = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(GreenGUI.fg.darker()), "Minimum algorithm parameters", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, GreenGUI.font, GreenGUI.fg);
        subpanel_oep.setBorder(border);


        ImagePanel histo_image_panel = new ImagePanel("/images/histo.png");
        histo_image_panel.setPreferredSize(new Dimension(360, 200));
        histo_image_panel.setMinimumSize(new Dimension(360, 200));
        histo_image_panel.setBorder(BorderFactory.createEtchedBorder(GreenGUI.fg, GreenGUI.fg.darker()));
        addComp(panel, gbl, histo_image_panel, 0, 0, 2, 1, false, 1);


        GreenJTextPane histo_text = new GreenJTextPane("<html><p style='width: 500px; font-family: san-serif;'>This process will analyze all result files in the selected directory. " +
            "A histogram will be created for the object evaluation parameter (OEP) for each sample set. For more information about the method see Help->Info. <br>" +
            "The threshold seperating true foci from background objects will be calculated automatically by multiple algorithms, but it can be changed manually by clicking onto the graph or is adjusted automatically during the validation process.<br>" + 
            "The threshold validation images will show the master and second channels as defined in the first tab.<br>" + 
            "A table with the counted foci values will be saved inside the result file directory. Images of the approved/rejected histograms will be saved there as well. <br><br>" +
            "<b>The image directory can also be a directory with multiple subdirectories in case there are multiple result files to analyze.</b></p></html>"); 

        addComp(panel, gbl, histo_text, 2, 0, 2, 1, true, 1);

        addComp(panel, gbl, button_images, 0, 1, 2, 1, true, true, 2);
        addComp(panel, gbl, image_path_field, 2, 1, 2, 1, true, 1);

        addComp(panel, gbl, button_open, 0, 2, 2, 1, true, true, 2);
        addComp(panel, gbl, root_path_field, 2, 2, 2, 1, true, 1);

        blind_checkbox = new GreenJCheckBox("blind threshold validation (hides file name and foci number)");
        addComp(panel, gbl, blind_checkbox, 0, 5, 2, 1, true, 1);

        skip_cells_with_many_foci_checkbox = new GreenJCheckBox("<html>Exclude cells with more foci than:<html>");
        addComp(panel, gbl, skip_cells_with_many_foci_checkbox, 0, 7, 1, 1, true, 1);

        max_foci_field = new GreenJFormattedTextField(int_format);
        addComp(panel, gbl, max_foci_field, 1, 7, 1, 1, true, 1);
        addComp(panel, gbl, subpanel_oep, 2, 5, 2, 3, true, 1);
        addComp(panel, gbl, button_start, 2, 8, 2, 1, true, true, 4);

        return panel;
    }


    public GreenJPanel overlay_dialog() {
        GreenJPanel panel = new GreenJPanel();
        GridBagLayout gbl = new GridBagLayout();
        panel.setLayout(gbl);

        ImagePanel cell_image_panel = new ImagePanel("/images/cell.png");
        cell_image_panel.setPreferredSize(new Dimension(250, 250));
        cell_image_panel.setMinimumSize(new Dimension(250, 250));
        cell_image_panel.setBorder(BorderFactory.createEtchedBorder(GreenGUI.fg, GreenGUI.fg.darker()));
        addComp(panel, gbl, cell_image_panel, 0, 0, 2, 1, false, 1);

        String label_string = "<html><p style='width: 350px; font-family: san-serif;'>This process marks the foci positions, determined by the threshold of the " +
            "object evaluation parameter, and creates an image stack that will be opened in ImageJ. " +
            "The longer the lines to mark the object  the further the OEP value is above the threshold.<br><br>" +
            "The parameters used, e.g. the file extension, are taken from the definitions in the other two tabs.<br><br>" +
            "<b>You have to run \"create result files from images\" before this function can be applied.</b></p></html>";
        GreenJTextPane textLabel = new GreenJTextPane(label_string);

        addComp(panel, gbl, textLabel, 2, 0, 2, 1, true, 1);

        image_path_overlay_field = new GreenJTextField();
        button_open_images_overlay = new GreenJButton("Open image directory");
        button_open_images_overlay.addActionListener(this);
        addComp(panel, gbl, button_open_images_overlay, 0, 1, 2, 1, true, 2);
        addComp(panel, gbl, image_path_overlay_field, 2, 1, 2, 1, true, 1);

        root_path_overlay_file_field = new GreenJTextField();
        button_open_file_overlay = new GreenJButton("Open result file");
        button_open_file_overlay.addActionListener(this);
        addComp(panel, gbl, button_open_file_overlay, 0, 2, 2, 1, true, 2);
        addComp(panel, gbl, root_path_overlay_file_field, 2, 2, 2, 1, true, 1);

        NumberFormat int_format = NumberFormat.getIntegerInstance();
        int_format.setGroupingUsed(false);
        NumberFormat double_format = NumberFormat.getNumberInstance();
        double_format.setGroupingUsed(false);
        double_format.setMaximumFractionDigits(5);

        addComp(panel, gbl, new GreenJLabel("Marker offset in pixels"), 0, 4, 1, 1, true, 1);
        overlay_offset_field = new GreenJFormattedTextField(int_format);
        addComp(panel, gbl, overlay_offset_field, 1, 4, 1, 1, true, 1);

        addComp(panel, gbl, new GreenJLabel("Marker max length in pixels"), 0, 5, 1, 1, true, 1);
        overlay_max_length_field = new GreenJFormattedTextField(double_format);
        addComp(panel, gbl, overlay_max_length_field, 1, 5, 1, 1, true, 1);

        // theoretically channel 3 as defined in the first tab could be the same as one of the first two. therefore, this solution is better than the old one where we just removed the dapi channel. 
        only_show_master_and_second_channel_checkbox = new GreenJCheckBox("Only show master and second channel (as defined in first tab)");
        addComp(panel, gbl, only_show_master_and_second_channel_checkbox, 2, 4, 2, 1, true, 1);

        blind_overlay_checkbox = new GreenJCheckBox("Constant marker length (for 'blind' foci evalulation by eye)");
        addComp(panel, gbl, blind_overlay_checkbox, 2, 5, 2, 1, true, 1);

        button_start_overlay = new GreenJButton("Set foci threshold");
        button_start_overlay.addActionListener(this);
        addComp(panel, gbl, button_start_overlay, 0, 6, 1, 1, true, 3);

        return panel;
    }

    private boolean wrong_channels() {
        int master_channel = ((Number) master_channel_field.getValue()).intValue();
        int second_channel = ((Number) second_channel_field.getValue()).intValue();
        int dapi_channel = ((Number) dapi_channel_field.getValue()).intValue();
        if (master_channel < 1 || master_channel > 3 || second_channel < 1 || second_channel > 3 || dapi_channel < 1 || dapi_channel > 3) return true;
        return false;
    }

    public void load_config() {
        load_config(this.last_config_file);
    }
    private void load_config_without_setting() {
        try {
            InputStream inputStream = new FileInputStream(last_config_file);
            configProps.load(inputStream);
            inputStream.close();
        } catch (Exception e) {}
    }


    private void load_config(File file) {
        Properties defaultProps = new Properties();
        // set default config
        defaultProps.setProperty("root_path_field_images", System.getProperty("user.dir"));
        defaultProps.setProperty("extension_field", ".tif");
        defaultProps.setProperty("master_channel_field", "1");
        defaultProps.setProperty("second_channel_field", "2");
        defaultProps.setProperty("dapi_channel_field", "3");
        defaultProps.setProperty("freak_threshold_field", "120");
        defaultProps.setProperty("freak_low_threshold_field", "5");
        defaultProps.setProperty("freak_stdev_threshold_field", "1");
        defaultProps.setProperty("rename_freaks_checkbox", "false");
        defaultProps.setProperty("struct_dia_field", "10");
        defaultProps.setProperty("edgeThreshold_field", "0.5");
        defaultProps.setProperty("minArea_field", "3");
        defaultProps.setProperty("minSeparation_field", "3");
        defaultProps.setProperty("minRelativeIntensity_field", "1.1");

        defaultProps.setProperty("root_path_field", ""); // System.getProperty("user.dir")
        defaultProps.setProperty("image_path_field", System.getProperty("user.dir"));
        defaultProps.setProperty("stdev_of_num_field", "400");
        defaultProps.setProperty("range_oep_field", "0.02");
        defaultProps.setProperty("blind_checkbox", "true");
        defaultProps.setProperty("use_minimum_algorithms_checkbox", "true");
        defaultProps.setProperty("skip_cells_with_many_foci_checkbox", "true");
        defaultProps.setProperty("max_foci_field", "10");

        defaultProps.setProperty("image_path_overlay_field", System.getProperty("user.dir"));
        defaultProps.setProperty("root_path_overlay_file_field", System.getProperty("user.dir"));
        defaultProps.setProperty("overlay_offset_field", "3");
        defaultProps.setProperty("overlay_max_length_field", "5.0");
        defaultProps.setProperty("only_show_master_and_second_channel_checkbox", "true");
        defaultProps.setProperty("blind_overlay_checkbox", "false");

        configProps = new Properties(defaultProps);

        // try to load config from file
        try {
            InputStream inputStream = new FileInputStream(file);
            configProps.load(inputStream);
            inputStream.close();
        } catch (Exception e) {}

        // update text fields
        root_path_field_images.setText(configProps.getProperty("root_path_field_images"));
        extension_field.setText(configProps.getProperty("extension_field"));
        master_channel_field.setValue(Integer.parseInt(configProps.getProperty("master_channel_field")));
        second_channel_field.setValue(Integer.parseInt(configProps.getProperty("second_channel_field")));
        dapi_channel_field.setValue(Integer.parseInt(configProps.getProperty("dapi_channel_field")));
        freak_threshold_field.setValue(Integer.parseInt(configProps.getProperty("freak_threshold_field")));
        freak_low_threshold_field.setValue(Integer.parseInt(configProps.getProperty("freak_low_threshold_field")));
        freak_stdev_threshold_field.setValue(Integer.parseInt(configProps.getProperty("freak_stdev_threshold_field")));
        rename_freaks_checkbox.setSelected(Boolean.parseBoolean(configProps.getProperty("rename_freaks_checkbox")));

        struct_dia_field.setValue(Double.parseDouble(configProps.getProperty("struct_dia_field")));
        edgeThreshold_field.setValue(Double.parseDouble(configProps.getProperty("edgeThreshold_field")));
        minArea_field.setValue(Integer.parseInt(configProps.getProperty("minArea_field")));
        minSeparation_field.setValue(Integer.parseInt(configProps.getProperty("minSeparation_field")));
        minRelativeIntensity_field.setValue(Double.parseDouble(configProps.getProperty("minRelativeIntensity_field")));

        root_path_field.setText(configProps.getProperty("root_path_field"));
        image_path_field.setText(configProps.getProperty("image_path_field"));
        stdev_of_num_field.setValue(Integer.parseInt(configProps.getProperty("stdev_of_num_field")));
        range_oep_field.setValue(Double.parseDouble(configProps.getProperty("range_oep_field")));
        blind_checkbox.setSelected(Boolean.parseBoolean(configProps.getProperty("blind_checkbox")));
        use_minimum_algorithms_checkbox.setSelected(Boolean.parseBoolean(configProps.getProperty("use_minimum_algorithms_checkbox")));
        skip_cells_with_many_foci_checkbox.setSelected(Boolean.parseBoolean(configProps.getProperty("skip_cells_with_many_foci_checkbox")));
        max_foci_field.setValue(Integer.parseInt(configProps.getProperty("max_foci_field")));

        image_path_overlay_field.setText(configProps.getProperty("image_path_overlay_field"));
        root_path_overlay_file_field.setText(configProps.getProperty("root_path_overlay_file_field"));
        overlay_offset_field.setValue(Integer.parseInt(configProps.getProperty("overlay_offset_field")));
        overlay_max_length_field.setValue(Double.parseDouble(configProps.getProperty("overlay_max_length_field")));
        only_show_master_and_second_channel_checkbox.setSelected(Boolean.parseBoolean(configProps.getProperty("only_show_master_and_second_channel_checkbox")));
        blind_overlay_checkbox.setSelected(Boolean.parseBoolean(configProps.getProperty("blind_overlay_checkbox")));
        //     only_cells_with_objects_checkbox.setSelected(Boolean.parseBoolean(configProps.getProperty("only_cells_with_objects_checkbox")));
    }

    public void save_config() {
        save_config(this.last_config_file);
    }

    public void save_config(File file) {
        // need to be here, so that saving the others does not override them.
        load_config_without_setting();
        configProps.setProperty("root_path_field_images", root_path_field_images.getText());
        configProps.setProperty("extension_field", extension_field.getText());
        configProps.setProperty("master_channel_field", String.valueOf(((Number) master_channel_field.getValue()).intValue()));
        configProps.setProperty("second_channel_field", String.valueOf(((Number) second_channel_field.getValue()).intValue()));
        configProps.setProperty("dapi_channel_field", String.valueOf(((Number) dapi_channel_field.getValue()).intValue()));
        configProps.setProperty("freak_threshold_field", String.valueOf(((Number) freak_threshold_field.getValue()).intValue()));
        configProps.setProperty("freak_low_threshold_field", String.valueOf(((Number) freak_low_threshold_field.getValue()).intValue()));
        configProps.setProperty("freak_stdev_threshold_field", String.valueOf(((Number) freak_stdev_threshold_field.getValue()).intValue()));
        configProps.setProperty("rename_freaks_checkbox", Boolean.toString(rename_freaks_checkbox.isSelected()));
        configProps.setProperty("struct_dia_field", String.valueOf(((Number) struct_dia_field.getValue()).intValue()));
        configProps.setProperty("edgeThreshold_field", String.valueOf(((Number) edgeThreshold_field.getValue()).doubleValue()));
        configProps.setProperty("minArea_field", String.valueOf(((Number) minArea_field.getValue()).intValue()));
        configProps.setProperty("minSeparation_field", String.valueOf(((Number) minSeparation_field.getValue()).intValue()));
        configProps.setProperty("minRelativeIntensity_field", String.valueOf(((Number) minRelativeIntensity_field.getValue()).doubleValue()));

        configProps.setProperty("root_path_field", root_path_field.getText());
        configProps.setProperty("image_path_field", image_path_field.getText());
        configProps.setProperty("stdev_of_num_field", String.valueOf(((Number) stdev_of_num_field.getValue()).intValue()));
        configProps.setProperty("range_oep_field", String.valueOf(((Number) range_oep_field.getValue()).doubleValue()));
        configProps.setProperty("blind_checkbox", Boolean.toString(blind_checkbox.isSelected()));
        configProps.setProperty("use_minimum_algorithms_checkbox", Boolean.toString(use_minimum_algorithms_checkbox.isSelected()));
        configProps.setProperty("skip_cells_with_many_foci_checkbox", Boolean.toString(skip_cells_with_many_foci_checkbox.isSelected()));
        configProps.setProperty("max_foci_field", String.valueOf(((Number) max_foci_field.getValue()).intValue()));

        configProps.setProperty("image_path_overlay_field", image_path_overlay_field.getText());
        configProps.setProperty("root_path_overlay_file_field", root_path_overlay_file_field.getText());
        configProps.setProperty("overlay_offset_field", String.valueOf(((Number) overlay_offset_field.getValue()).intValue()));
        configProps.setProperty("overlay_max_length_field", String.valueOf(((Number) overlay_max_length_field.getValue()).intValue()));
        configProps.setProperty("only_show_master_and_second_channel_checkbox", Boolean.toString(only_show_master_and_second_channel_checkbox.isSelected()));
        configProps.setProperty("blind_overlay_checkbox", Boolean.toString(blind_overlay_checkbox.isSelected()));
        try {
            OutputStream outputStream = new FileOutputStream(file);
            configProps.store(outputStream, "settings");
            outputStream.close();
        } catch (Exception e) {}
    }

    private void confirm_closing() {
        GreenJTextPane message = new GreenJTextPane("Are you sure you want to exit the program?");
        int confirmed = JOptionPane.showConfirmDialog(null, message, "Exit Program", JOptionPane.YES_NO_OPTION);
        if (confirmed == JOptionPane.YES_OPTION) {
            save_config();
            // 	  main_frame.dispose();
            System.exit(0);
        }
    }

    private void error_message(Exception e) {
        StringWriter errors = new StringWriter();
        e.printStackTrace(new PrintWriter(errors));
        GreenJTextArea ta = new GreenJTextArea("Something went wrong. Please check all file paths and that (if any used) the .csv files are result files (not foci tables) created by autoFoci. \n\nError message:\n\n " + errors, 15, 50);
        ta.setWrapStyleWord(true);
        ta.setLineWrap(true);
        ta.setCaretPosition(0);
        ta.setEditable(false);
        JOptionPane.showMessageDialog(null, new JScrollPane(ta), "RESULT", JOptionPane.INFORMATION_MESSAGE);
    }

    // creats backup file copying this.foci_table_name to this.foci_table_name_backup
    private void backup_file(String root_dir) {
        File file = new File(root_dir, this.foci_table_name);
        if (file.exists()) {
            File backup = new File(root_dir, this.foci_table_name_backup);
            try {
                copy_file(file, backup);
            } catch (Exception e) {
                error_message(e);
            }
        }
    }

    private static void copy_file(File source, File dest) throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new FileInputStream(source);
            os = new FileOutputStream(dest);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } finally {
            is.close();
            os.close();
        }
    }
}