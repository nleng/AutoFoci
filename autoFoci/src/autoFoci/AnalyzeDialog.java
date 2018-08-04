package autoFoci;
import autoFoci.MultiType;
import autoFoci.HistPanel;
import autoFoci.HistAnalyzer;
import autoFoci.GreenGUI.*;
import autoFoci.AutoThreshold;
import autoFoci.PoissonDeviation;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.swing.JScrollPane;
import javax.swing.JOptionPane;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JToggleButton;
import javax.swing.AbstractButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JFormattedTextField;
import javax.swing.SwingUtilities;
import javax.swing.BorderFactory;
import javax.swing.border.EmptyBorder;
import java.text.NumberFormat;
import javax.swing.border.EtchedBorder;

import java.util.Arrays;
import java.util.ArrayList;


public class AnalyzeDialog {

    //     boolean debug = true;
    boolean debug = false;

    final static int max_cell_foci = 100; // for poisson array, must also be changed in HistAnalyzer

    Object lock = new Object();

    HistPanel oep_hist_panel;

    JFrame frame;
    JPanel poisson_chart, oep_chart;
    GreenJLabel poisson_label = new GreenJLabel();
    GreenJPanel analyze_panel;
    GreenJTextField combined_foci_title;
    GreenJFormattedTextField plot_limit_field;

    String image_root_path, image_dir_path, dir_name;

    boolean use_overlay_images, done = false, blind, use_minimum_algorithms, skip_cells_with_many_foci = false, auto_limit = true;
    int counter, stdev_of_num = 500, minArea, master_channel, second_channel, dapi_channel, max_foci, result_files_total, overlay_offset, max_objects;
    double oep_thresh, change_count, kl_divergence, r_squares, overlay_max_length;
    // double master_weight;

    double plot_limit = 1.;
    double half_range_oep = 0.02;

    double[] oep;
    double[] cell;
    double[] foci;
    double[][] oep_arr;
    double[] poisson_exp = new double[max_cell_foci];
    double[] poisson_theo = new double[max_cell_foci];
    // these 3 lists are needed for the back_button to work
    ArrayList < Integer > found_indices = new ArrayList < > ();
    ArrayList < Boolean > output_written = new ArrayList < > ();
    ArrayList < File > image_file_arr = new ArrayList < > ();
    ArrayList < String > image_names = new ArrayList < > ();
    // MultiType is a class with mixed types
    ArrayList < MultiType > output_table = new ArrayList < MultiType > ();

    public AnalyzeDialog(int stdev_of_num, double half_range_oep, boolean use_overlay_images, int minArea, int master_channel, int second_channel, int dapi_channel, boolean blind, boolean use_minimum_algorithms, boolean skip_cells_with_many_foci, int max_foci, int overlay_offset, double overlay_max_length) {
        this.stdev_of_num = stdev_of_num;
        this.half_range_oep = half_range_oep;
        this.minArea = minArea;
        this.master_channel = master_channel;
        this.second_channel = second_channel;
        this.dapi_channel = dapi_channel;
        this.overlay_offset = overlay_offset;
        this.overlay_max_length = overlay_max_length;

        this.use_overlay_images = use_overlay_images;
        this.blind = blind;
        this.use_minimum_algorithms = use_minimum_algorithms;
        this.skip_cells_with_many_foci = skip_cells_with_many_foci;
        this.max_foci = max_foci;
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

    static void addComp(Container cont, GridBagLayout gbl, Component c, int x, int y, int width, int height, boolean fill_both, int padding) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = 1.;
        gbc.weighty = 1.;
        if (fill_both)
            gbc.fill = GridBagConstraints.BOTH;
        else
            gbc.fill = GridBagConstraints.NONE;
        // 	gbc.fill = GridBagConstraints.HORIZONTAL;
        if (padding == 1)
            gbc.insets = new Insets(10, 10, 10, 10);
        else if (padding == 2)
            gbc.insets = new Insets(30, 80, 30, 80);
        else if (padding == 3)
            gbc.insets = new Insets(20, 20, 20, 20);
        else if (padding == 4)
            gbc.insets = new Insets(40, 0, 40, 0);
        else
            gbc.insets = new Insets(0, 0, 0, 0);
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.gridheight = height;
        gbl.setConstraints(c, gbc);
        cont.add(c);
        //       gbc.ipady = 40;      //make this component tall
    }

    public void save_panel_image(Component component, String save_dir, String file_name) {
        if (!this.use_overlay_images) {
            BufferedImage image = new BufferedImage(component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_RGB);
            component.paint(image.getGraphics());
            try {
                file_name = file_name + ".png";
                // 	      file_name = file_name.substring(0, file_name.length()-4)+".png";
                File save_file = new File(save_dir, file_name);
                AnalyzeDialog.this.image_file_arr.add(save_file);
                save_file.mkdirs();
                ImageIO.write(image, "png", save_file);
            } catch (Exception e) {}
        }
    }

    public boolean create_analyze_panel(final File[] fileList, final String approved_dir, final String rejected_dir, final String output_table_filename, boolean new_thresh) {
        this.analyze_panel = new GreenJPanel();
        GridBagLayout gbl = new GridBagLayout();
        this.analyze_panel.setLayout(gbl);

        this.result_files_total = fileList.length;

        File result_file = new File("");
        final HistAnalyzer hista = new HistAnalyzer(this);
        boolean found = false;
        while (!found) {
            if (this.counter >= fileList.length) {
                exit_analyze_dialog(hista, output_table_filename);
                return found;
            } else {
                result_file = fileList[this.counter];
            }
            if (result_file.isFile()) {
                try {
                    this.oep_arr = hista.get_oep_arrays(result_file.getAbsolutePath(), this.plot_limit, this.auto_limit, false);
                    if (this.debug) {
                        System.out.println(this.oep_arr.length);
                        System.out.println(this.oep_arr[0].length);
                        System.out.println(this.oep_arr[1].length);
                    }
                    if (this.oep_arr[1].length < 1) {
                        if (this.debug) {
                            System.out.println("length 0");
                            System.out.println(result_file);

                        }
                        this.counter++;
                        continue;
                    }
                    if (this.auto_limit) this.plot_limit = this.oep_arr[4][0];
                    found = true;
                } catch (Exception e) {
                    error_message(e);
                    if (this.debug) {
                        System.out.println("catch");
                        System.out.println(result_file);
                    }
                    found = false;
                }
            } else {
                if (this.debug) {
                    System.out.println("not file");
                    System.out.println(result_file);
                }
                found = false;
            }
            if (!found) this.counter++;

        }


        this.dir_name = result_file.getName().substring(0, result_file.getName().length() - 4);
        File image_dir = new File(this.image_root_path, this.dir_name);
        // take the root path if there are no subdirectories (also take full name):
        if (image_dir.isDirectory()) {
            this.image_dir_path = image_dir.getAbsolutePath();
        } else {
            this.dir_name = result_file.getName();
            this.image_dir_path = this.image_root_path;
        }
        this.oep_hist_panel = new HistPanel(hista, this);
        AutoThreshold autoThreshy = new AutoThreshold();

        //################## threshold algorithms ##################
        int take_oep = 3; // with log()
        double number_of_bins = 300;

        double maxi = Double.NEGATIVE_INFINITY;
        double mini = Double.POSITIVE_INFINITY;
        for (int i = 0; i < this.oep_arr[take_oep].length; i++) {
            if (this.oep_arr[take_oep][i] > maxi)
                maxi = this.oep_arr[take_oep][i];
            if (this.oep_arr[take_oep][i] < mini)
                mini = this.oep_arr[take_oep][i];
        }
        int[] data = new int[(int) number_of_bins];
        for (int i = 0; i < this.oep_arr[take_oep].length; i++) {
            int value = (int)((this.oep_arr[take_oep][i] - mini) / (maxi - mini) * number_of_bins);
            if (value > number_of_bins - 1) value = (int) number_of_bins - 1;
            if (value >= 0) data[value]++;
        }

        double[] iHisto = hista.smooth_histo(data, 10);
        double estimated_threshold = autoThreshy.MaxEntropy(iHisto) * (maxi - mini) / number_of_bins + mini;
        this.image_names = hista.read_imageNames(result_file.getAbsolutePath());
        this.oep = this.oep_arr[3];
        this.cell = this.oep_arr[2];

        maxi = Double.NEGATIVE_INFINITY;
        mini = Double.POSITIVE_INFINITY;
        for (int i = 0; i < this.oep.length; i++) {
            if (this.oep[i] > maxi)
                maxi = this.oep[i];
            if (this.oep[i] < mini)
                mini = this.oep[i];
        }
        data = new int[(int) number_of_bins];
        for (int i = 0; i < this.oep.length; i++) {
            // faster version:
            if (this.oep[i] < mini)
                continue;
            int value = (int)((this.oep[i] - mini) / (maxi - mini) * number_of_bins);
            if (value > number_of_bins - 1)
                value = (int) number_of_bins - 1;
            if (value >= 0)
                data[value]++;
        }
        System.out.println(mini + " mini/maxi " + maxi);

        iHisto = hista.smooth_histo(data, 10);

        int max_index = hista.get_max_index(iHisto);
        double max_position = max_index * (maxi - mini) / number_of_bins + mini;
        double max_threshy = 2 * max_index * (maxi - mini) / number_of_bins + mini;
        int n = 0;
        System.out.print("max_position: ");
        System.out.println(max_position);

        ArrayList < Double > threshy_list = new ArrayList < > ();

        double upper_bound = 2. * estimated_threshold;
        double pearson_threshold = hista.colocalization_pearson_threshold(this.oep_arr, max_position, upper_bound);
        threshy_list.add(pearson_threshold);
        ArrayList < Double > poisson_threshy_arr = hista.poisson_threshold(this.oep, this.cell, max_position, upper_bound);
        threshy_list.add(poisson_threshy_arr.get(1));

        threshy_list.add((autoThreshy.MaxEntropy(iHisto) * (maxi - mini) / number_of_bins + mini));
        threshy_list.add((autoThreshy.RenyiEntropy(iHisto) * (maxi - mini) / number_of_bins + mini));
        threshy_list.add((autoThreshy.Yen(iHisto) * (maxi - mini) / number_of_bins + mini));
        threshy_list.add(autoThreshy.Intermodes(iHisto) * (maxi - mini) / number_of_bins + mini);

        // for Triangle algorithm take f_back(oep)
        number_of_bins = 1000;
        maxi = Double.NEGATIVE_INFINITY;
        mini = Double.POSITIVE_INFINITY;

        ArrayList < Double > triangle_oep = new ArrayList < > ();
        for (int i = 0; i < this.oep.length; i++)
            triangle_oep.add(hista.f_back(this.oep[i]));

        for (int i = 0; i < triangle_oep.size(); i++) {
            if (triangle_oep.get(i) > maxi)
                maxi = triangle_oep.get(i);
            if (triangle_oep.get(i) < mini)
                mini = triangle_oep.get(i);
        }
        data = new int[(int) number_of_bins];
        for (int i = 0; i < triangle_oep.size(); i++) {
            // faster version:
            if (triangle_oep.get(i) < mini)
                continue;
            int value = (int)((triangle_oep.get(i) - mini) / (maxi - mini) * number_of_bins);
            if (value > number_of_bins - 1)
                value = (int) number_of_bins - 1;
            if (value >= 0)
                data[value]++;
        }

        // no smoothing here
        iHisto = hista.smooth_histo(data, 0);
//         System.out.println("x: " + mini + " " + maxi + " " + autoThreshy.Triangle(iHisto));

        threshy_list.add(hista.f(autoThreshy.Triangle(iHisto) * (maxi - mini) / number_of_bins + mini));

        double stDev_value = hista.hist_minimum_stdev(this.oep_arr[1], this.plot_limit, this.stdev_of_num);
        double range_value = hista.hist_minimum_range(this.oep_arr[1], this.plot_limit, this.half_range_oep);
        if (this.debug) {
            System.out.print("plot_limit\t");
            System.out.println(this.plot_limit);
            System.out.print("stDev_min\t");
            System.out.println(stDev_value);
            System.out.print("range_min\t");
            System.out.println(range_value);
            System.out.println("threshy_list");
            System.out.println(threshy_list);
        }
    
        if (this.use_minimum_algorithms) {
            threshy_list.add(hista.inverse(stDev_value));
            threshy_list.add(hista.inverse(range_value));
        }
        if (new_thresh) this.oep_thresh = hista.mean(threshy_list);

        this.oep_chart = this.oep_hist_panel.hist_panel(this.oep_arr[1], this.oep, "", "1 / log(OEP)", "log(OEP)", "Frequency", stDev_value, range_value, threshy_list, true, this.use_minimum_algorithms);
        this.oep_chart.setPreferredSize(new Dimension(900, 400));

        GreenJButton validate_button_1 = new GreenJButton("Threshold validation");
        validate_button_1.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                AnalyzeDialog.this.frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                AnalyzeDialog.this.change_count = 0.;
                hista.validate_threshold(AnalyzeDialog.this.image_dir_path, AnalyzeDialog.this.oep_arr, AnalyzeDialog.this.oep, AnalyzeDialog.this.image_names, AnalyzeDialog.this.skip_cells_with_many_foci, AnalyzeDialog.this.max_foci, AnalyzeDialog.this.oep_arr[5][0], AnalyzeDialog.this.oep_thresh, AnalyzeDialog.this.master_channel, AnalyzeDialog.this.second_channel, AnalyzeDialog.this.dapi_channel, overlay_offset, overlay_max_length, true);
                // not really needed.
                AnalyzeDialog.this.oep_hist_panel.threshold_field.setValue(hista.inverse(AnalyzeDialog.this.oep_thresh));
                AnalyzeDialog.this.oep_hist_panel.set_x();
                AnalyzeDialog.this.frame.setCursor(Cursor.getDefaultCursor());
            }
        });

        // an alternative way of validation. It is not visible to not cause confusion. 
//         GreenJButton validate_button_2 = new GreenJButton("Validate 2");
//         validate_button_2.addActionListener(new ActionListener() {
//             public void actionPerformed(ActionEvent actionEvent) {
//                 AnalyzeDialog.this.frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
//                 AnalyzeDialog.this.change_count = 0.;
//                 hista.validate_threshold(AnalyzeDialog.this.image_dir_path, AnalyzeDialog.this.oep_arr, AnalyzeDialog.this.oep, AnalyzeDialog.this.image_names, AnalyzeDialog.this.skip_cells_with_many_foci, AnalyzeDialog.this.max_foci, AnalyzeDialog.this.oep_arr[5][0], AnalyzeDialog.this.oep_thresh, AnalyzeDialog.this.master_channel, AnalyzeDialog.this.second_channel, AnalyzeDialog.this.dapi_channel, overlay_offset, overlay_max_length, false);
//                 // not really needed.
//                 AnalyzeDialog.this.oep_hist_panel.threshold_field.setValue(hista.inverse(AnalyzeDialog.this.oep_thresh));
//                 AnalyzeDialog.this.oep_hist_panel.set_x();
//                 AnalyzeDialog.this.frame.setCursor(Cursor.getDefaultCursor());
//             }
//         });

        GreenJButton plot_limit_button = new GreenJButton("Set limit");
        plot_limit_button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                AnalyzeDialog.this.frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                AnalyzeDialog.this.auto_limit = false;
                AnalyzeDialog.this.plot_limit = ((Number) AnalyzeDialog.this.plot_limit_field.getValue()).doubleValue();

                create_analyze_panel(fileList, approved_dir, rejected_dir, output_table_filename, true);
                AnalyzeDialog.this.frame.getContentPane().removeAll();
                AnalyzeDialog.this.frame.setContentPane(AnalyzeDialog.this.analyze_panel);
                AnalyzeDialog.this.frame.pack();
                AnalyzeDialog.this.frame.validate(); // revalidate()
                AnalyzeDialog.this.frame.repaint();
                AnalyzeDialog.this.frame.setCursor(Cursor.getDefaultCursor());

            }
        });


        GreenJButton approve_button = new GreenJButton("Approve");
        if (AnalyzeDialog.this.use_overlay_images) approve_button = new GreenJButton("Create images with markers");
        approve_button.setForeground(new Color(250, 250, 50));
        approve_button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                AnalyzeDialog.this.frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                if (!AnalyzeDialog.this.use_overlay_images) {
                    if (AnalyzeDialog.this.blind) {
                        // create a new panel with blind=false
                        AnalyzeDialog.this.blind = false;
                        create_analyze_panel(fileList, approved_dir, rejected_dir, output_table_filename, false);
                        AnalyzeDialog.this.frame.getContentPane().removeAll();
                        AnalyzeDialog.this.frame.setContentPane(AnalyzeDialog.this.analyze_panel);
                        AnalyzeDialog.this.frame.pack();
                        AnalyzeDialog.this.frame.validate(); // revalidate()
                        AnalyzeDialog.this.frame.repaint();
                        save_panel_image(AnalyzeDialog.this.analyze_panel, approved_dir, AnalyzeDialog.this.dir_name);
                        AnalyzeDialog.this.blind = true;
                    } else {
                        save_panel_image(AnalyzeDialog.this.analyze_panel, approved_dir, AnalyzeDialog.this.dir_name);
                    }
                    MultiType output = new MultiType();
                    output.name = AnalyzeDialog.this.dir_name;
                    AnalyzeDialog.this.oep_thresh = ((Number) AnalyzeDialog.this.oep_hist_panel.threshold_field_log.getValue()).doubleValue();
                    output.oep_thresh = hista.round_double(AnalyzeDialog.this.oep_thresh, 3);
                    output.foci = AnalyzeDialog.this.foci[0];
                    output.foci_cell = AnalyzeDialog.this.foci[1];
                    output.cells = (int) AnalyzeDialog.this.foci[2];
                    output.kl_divergence = AnalyzeDialog.this.kl_divergence;
                    output.r_squares = AnalyzeDialog.this.r_squares;
                    AnalyzeDialog.this.output_table.add(output);
                    AnalyzeDialog.this.output_written.add(true);
                } else {
                    // 		  AnalyzeDialog.this.output_written.add(false);
                    AnalyzeDialog.this.frame.setCursor(Cursor.getDefaultCursor());
                    AnalyzeDialog.this.frame.dispose();

                }
                synchronized(lock) {
                    AnalyzeDialog.this.done = true;
                    lock.notify();
                }
                if (!AnalyzeDialog.this.use_overlay_images && AnalyzeDialog.this.output_table.size() > 0)
                    hista.write_output(AnalyzeDialog.this.output_table, output_table_filename);
                if (AnalyzeDialog.this.counter < AnalyzeDialog.this.result_files_total) {
                    if (AnalyzeDialog.this.debug) {
                        System.out.println("A");
                        System.out.println(AnalyzeDialog.this.counter);
                        System.out.println(AnalyzeDialog.this.result_files_total);
                    }
                    AnalyzeDialog.this.found_indices.add(AnalyzeDialog.this.counter);
                    AnalyzeDialog.this.counter++;
                    AnalyzeDialog.this.auto_limit = true;
                    create_analyze_panel(fileList, approved_dir, rejected_dir, output_table_filename, true);
                    AnalyzeDialog.this.frame.getContentPane().removeAll();
                    AnalyzeDialog.this.frame.setContentPane(AnalyzeDialog.this.analyze_panel);
                    AnalyzeDialog.this.frame.pack();
                    AnalyzeDialog.this.frame.validate(); // revalidate()
                    AnalyzeDialog.this.frame.repaint();
                    AnalyzeDialog.this.frame.setCursor(Cursor.getDefaultCursor());
                } else {
                    AnalyzeDialog.this.frame.setCursor(Cursor.getDefaultCursor());
                    AnalyzeDialog.this.frame.dispose();
                }
            }
        });

        GreenJButton reject_button = new GreenJButton("Reject");
        reject_button.setForeground(new Color(255, 50, 50));
        reject_button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                AnalyzeDialog.this.frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                if (AnalyzeDialog.this.blind) {
                    // create a new panel with blind=false
                    AnalyzeDialog.this.blind = false;
                    create_analyze_panel(fileList, approved_dir, rejected_dir, output_table_filename, false);
                    AnalyzeDialog.this.frame.getContentPane().removeAll();
                    AnalyzeDialog.this.frame.setContentPane(AnalyzeDialog.this.analyze_panel);
                    AnalyzeDialog.this.frame.pack();
                    AnalyzeDialog.this.frame.validate(); // revalidate()
                    AnalyzeDialog.this.frame.repaint();
                    save_panel_image(AnalyzeDialog.this.analyze_panel, rejected_dir, AnalyzeDialog.this.dir_name);
                    AnalyzeDialog.this.blind = true;
                } else {
                    save_panel_image(AnalyzeDialog.this.analyze_panel, rejected_dir, AnalyzeDialog.this.dir_name);
                }
                synchronized(lock) {
                    AnalyzeDialog.this.done = true;
                    lock.notify();
                }
                if (!AnalyzeDialog.this.use_overlay_images && AnalyzeDialog.this.output_table.size() > 0)
                    hista.write_output(AnalyzeDialog.this.output_table, output_table_filename);
                if (AnalyzeDialog.this.counter < AnalyzeDialog.this.result_files_total) {
                    if (AnalyzeDialog.this.debug) {
                        System.out.println("R");
                        System.out.println(AnalyzeDialog.this.counter);
                        System.out.println(AnalyzeDialog.this.result_files_total);
                    }
                    AnalyzeDialog.this.output_written.add(false);
                    AnalyzeDialog.this.found_indices.add(AnalyzeDialog.this.counter);
                    AnalyzeDialog.this.counter++;
                    create_analyze_panel(fileList, approved_dir, rejected_dir, output_table_filename, true);
                    AnalyzeDialog.this.frame.getContentPane().removeAll();
                    AnalyzeDialog.this.frame.setContentPane(AnalyzeDialog.this.analyze_panel);
                    AnalyzeDialog.this.frame.pack();
                    AnalyzeDialog.this.frame.validate(); // revalidate()
                    AnalyzeDialog.this.frame.repaint();
                    AnalyzeDialog.this.frame.setCursor(Cursor.getDefaultCursor());
                } else {
                    AnalyzeDialog.this.frame.setCursor(Cursor.getDefaultCursor());
                    AnalyzeDialog.this.frame.dispose();
                }
            }
        });

        GreenJButton back_button = new GreenJButton("Back");
        back_button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                AnalyzeDialog.this.frame.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                synchronized(lock) {
                    AnalyzeDialog.this.done = true;
                    lock.notify();
                }
                // back_button should only dispose frame if it is the first, not the last frame
                if (AnalyzeDialog.this.found_indices.size() > 0 && AnalyzeDialog.this.counter < fileList.length) {
                    // not every index is a file (found==true), so the found_indices have to be stored
                    AnalyzeDialog.this.counter = AnalyzeDialog.this.found_indices.get(AnalyzeDialog.this.found_indices.size() - 1);
                    AnalyzeDialog.this.found_indices.remove(AnalyzeDialog.this.found_indices.size() - 1);

                    if (AnalyzeDialog.this.output_written.size() > 0) {
                        if (AnalyzeDialog.this.output_written.get(AnalyzeDialog.this.output_written.size() - 1) == true) {
                            AnalyzeDialog.this.output_table.remove(AnalyzeDialog.this.output_table.size() - 1);
                        }
                        AnalyzeDialog.this.output_written.remove(AnalyzeDialog.this.output_written.size() - 1);
                    }
                    if (!AnalyzeDialog.this.use_overlay_images && AnalyzeDialog.this.output_table.size() > 0)
                        hista.write_output(AnalyzeDialog.this.output_table, output_table_filename);
                    // delete image
                    if (AnalyzeDialog.this.image_file_arr.size() > 0) {
                        AnalyzeDialog.this.image_file_arr.get(AnalyzeDialog.this.image_file_arr.size() - 1).delete();
                        AnalyzeDialog.this.image_file_arr.remove(AnalyzeDialog.this.image_file_arr.size() - 1);
                    }

                    create_analyze_panel(fileList, approved_dir, rejected_dir, output_table_filename, true);
                    AnalyzeDialog.this.frame.getContentPane().removeAll();
                    AnalyzeDialog.this.frame.setContentPane(AnalyzeDialog.this.analyze_panel);
                    AnalyzeDialog.this.frame.pack();
                    AnalyzeDialog.this.frame.validate(); // revalidate()
                    AnalyzeDialog.this.frame.repaint();
                    AnalyzeDialog.this.frame.setCursor(Cursor.getDefaultCursor());
                } else {
                    AnalyzeDialog.this.frame.setCursor(Cursor.getDefaultCursor());
                    AnalyzeDialog.this.frame.dispose();
                }

            }
        });
        GreenJPanel oep_textfield_panel = new GreenJPanel(); // new GridLayout(6,1)
        oep_textfield_panel.setLayout(gbl);
        addComp(oep_textfield_panel, gbl, new GreenJLabel("Threshold:"), 0, 0, 1, 1, true, 1);
        this.oep_hist_panel.threshold_field.setFont(new Font("San Serif", Font.PLAIN, 16));
        addComp(oep_textfield_panel, gbl, this.oep_hist_panel.threshold_field, 1, 0, 1, 1, true, 1);
        this.oep_hist_panel.threshold_field.setValue(hista.inverse(this.oep_thresh));

        addComp(oep_textfield_panel, gbl, this.oep_hist_panel.threshold_button, 2, 0, 1, 1, true, 1);

        this.oep_hist_panel.threshold_field_log.setFont(new Font("San Serif", Font.PLAIN, 16));
        addComp(oep_textfield_panel, gbl, this.oep_hist_panel.threshold_field_log, 3, 0, 1, 1, true, 1);
        this.oep_hist_panel.threshold_field_log.setValue(this.oep_thresh);


        JToggleButton toggle_log_button = new JToggleButton("Log scale", true);
        toggle_log_button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                AbstractButton abstractButton = (AbstractButton) actionEvent.getSource();
                boolean selected = abstractButton.getModel().isSelected();
                // fffff
                AnalyzeDialog.this.oep_hist_panel.toggle_log_scale(selected, stDev_value, range_value, threshy_list);
            }
        });
        addComp(oep_textfield_panel, gbl, toggle_log_button, 4, 0, 1, 1, true, 3);

        if (!this.use_overlay_images && this.skip_cells_with_many_foci) {
            addComp(oep_textfield_panel, gbl, new GreenJLabel("Max foci/cell:"), 5, 0, 1, 1, true, 1);
            this.oep_hist_panel.max_foci_field.setValue(this.max_foci);
            addComp(oep_textfield_panel, gbl, this.oep_hist_panel.max_foci_field, 6, 0, 1, 1, true, 1);
            addComp(oep_textfield_panel, gbl, this.oep_hist_panel.max_foci_button, 7, 0, 1, 1, true, 1);
        } else {
            for (int i = 0; i < 3; i++)
                addComp(oep_textfield_panel, gbl, new GreenJLabel(""), 5 + i, 0, 1, 1, true, 1);
        }

        // even with option "blind" they need to be initialized so they can be filled before the images are saved
        final GreenJTextField main_title = new GreenJTextField(0, false);
        main_title.setText(AnalyzeDialog.this.dir_name);
        main_title.setFont(new Font("San Serif", Font.PLAIN, 20)); // Font.PLAIN, 22
        main_title.setBorder(BorderFactory.createEmptyBorder());



        this.combined_foci_title = new GreenJTextField(8, false);

        if (this.skip_cells_with_many_foci)
            this.foci = hista.count_foci_skip_cells(this.oep_arr, this.oep_thresh, this.max_foci);
        else
            this.foci = hista.count_foci(this.oep_arr, this.oep_thresh);

        this.combined_foci_title.setText("Foci/cell: " + String.valueOf(this.foci[1]));
        this.combined_foci_title.setFont(new Font("San Serif", Font.BOLD, 16));
        this.combined_foci_title.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(5, 5, 5, 5), (BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(GreenGUI.fg.darker(), 3), new EmptyBorder(5, 5, 5, 5)))));

        final GreenJButton unblind_button = new GreenJButton("Unblind");

        GreenJPanel blind_panel = new GreenJPanel(new GridLayout(1, 3));
        blind_panel.add(main_title);

        if (this.blind) {
            main_title.setVisible(false);
            blind_panel.add(unblind_button);
        } else {
            blind_panel.add(new JLabel(""));
        }

        final GreenJPanel combined_foci_panel = new GreenJPanel(new GridLayout(1, 2));
        combined_foci_panel.add(new GreenJLabel(""));
        combined_foci_panel.add(this.combined_foci_title);
        blind_panel.add(combined_foci_panel);
        if (this.blind) combined_foci_panel.setVisible(false);
        unblind_button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                unblind_button.setVisible(false);
                main_title.setVisible(true);
                combined_foci_panel.setVisible(true);
            }
        });

        addComp(this.analyze_panel, gbl, blind_panel, 0, 0, 3, 1, true, 1);

        GreenJPanel approve_textfield_panel = new GreenJPanel(); // new GridLayout(6,1)
        approve_textfield_panel.setLayout(gbl);

        NumberFormat format_2_digits = NumberFormat.getNumberInstance();
        format_2_digits.setMinimumFractionDigits(2);
        this.plot_limit_field = new GreenJFormattedTextField(format_2_digits);
        this.plot_limit_field.setValue(this.plot_limit);

        addComp(approve_textfield_panel, gbl, validate_button_1, 0, 0, 1, 1, true, 1);
//         addComp(approve_textfield_panel, gbl, validate_button_2, 1, 0, 1, 1, true, 1);
        addComp(approve_textfield_panel, gbl, approve_button, 1, 0, 1, 1, true, 1);

        if (!this.use_overlay_images) {
            addComp(approve_textfield_panel, gbl, reject_button, 2, 0, 1, 1, true, 1);
            addComp(approve_textfield_panel, gbl, back_button, 3, 0, 1, 1, true, 1);
        }

        addComp(approve_textfield_panel, gbl, new GreenJLabel("<html>Upper plot limit:<br>(Limit of minimum algorithms<br>is half the plot range.)</html>"), 4, 0, 1, 1, true, 1);
        GreenJPanel plot_limit_panel = new GreenJPanel(new GridLayout(1, 2));
        plot_limit_panel.add(this.plot_limit_field);
        plot_limit_panel.add(plot_limit_button);
        addComp(approve_textfield_panel, gbl, plot_limit_panel, 5, 0, 1, 1, true, 1);

        this.poisson_chart = this.oep_hist_panel.poisson_panel(this.poisson_exp, this.poisson_theo, "", "Foci per cell (Poisson check)", "Relative frequency");
        this.poisson_chart.setPreferredSize(new Dimension(300, 400));

        GreenJPanel kl_panel = new GreenJPanel(new GridLayout(0, 1));
        poisson_label_setText();
        kl_panel.add(this.poisson_label);

        addComp(this.analyze_panel, gbl, oep_chart, 0, 1, 2, 2, true, 1);
        addComp(this.analyze_panel, gbl, poisson_chart, 2, 1, 1, 2, true, 1);
        addComp(this.analyze_panel, gbl, oep_textfield_panel, 0, 4, 2, 1, true, 1);
        addComp(this.analyze_panel, gbl, kl_panel, 2, 4, 1, 2, true, 1);
        addComp(this.analyze_panel, gbl, approve_textfield_panel, 0, 5, 2, 1, true, 1);

        return true;
    }

    public boolean create_analyze_frame(String root_path, String image_path, String output_table_filename) {
        this.image_root_path = image_path;
        File[] fileList;
        if (use_overlay_images) {
            File res_file = new File(root_path);
            if (!file_used(res_file)) return false;
            fileList = new File[1];
            fileList[0] = res_file;
        } else {
            File dir = new File(root_path);
            fileList = get_file_list(dir);
            if (fileList.length == 0) return false;
            if (this.blind) shuffle(fileList);
            else Arrays.sort(fileList);
        }

        this.frame = new JFrame();
        this.frame.getContentPane().setBackground(GreenGUI.fg);
        // needed for the frame to get updated in case the validation is done with the frame iconified.
        this.frame.addWindowListener(new WindowListener() {
            public void windowClosed(WindowEvent arg0) {}
            public void windowActivated(WindowEvent arg0) {
                AnalyzeDialog.this.oep_hist_panel.set_x();
            }
            public void windowClosing(WindowEvent arg0) {}
            public void windowDeactivated(WindowEvent arg0) {}
            public void windowDeiconified(WindowEvent arg0) {
                AnalyzeDialog.this.oep_hist_panel.set_x();
            }
            public void windowIconified(WindowEvent arg0) {}
            public void windowOpened(WindowEvent arg0) {}
        });
        this.frame.setTitle("Histogram");
        this.frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        this.counter = 0;
        boolean found = create_analyze_panel(fileList, new File(root_path, "approved").getAbsolutePath(), new File(root_path, "rejected").getAbsolutePath(), output_table_filename, true);
        if (!found)
            return false;
        this.frame.setContentPane(this.analyze_panel);
        Image img = new ImageIcon(this.getClass().getResource("/images/zelle1.png")).getImage();
        this.frame.setIconImage(img);
        this.frame.setResizable(false);
        this.frame.pack();
        this.frame.setVisible(true);

        // 	  waitUntilDone();
        return true;
    }

    // Fisher-Yates shuffle
    static void shuffle(File[] arr) {
        int n = arr.length;
        for (int i = 0; i < n; i++) {
            int r = i + (int)(Math.random() * (n - i));
            File arr_r = arr[r];
            arr[r] = arr[i];
            arr[i] = arr_r;
        }
    } // END shuffle

    public void poisson_label_setText() {
        PoissonDeviation pd = new PoissonDeviation();
        this.poisson_label.setText(pd.poisson_deviation_text(this.foci[1], (int) this.oep_arr[2][this.oep_arr[2].length - 1], (int) this.oep_arr[10][0], round_double(this.oep_arr[9][0], 3), kl_divergence, r_squares));
    }

    public static double round_double(double x, int digits) {
        double rounda = Math.pow(10, digits);
        return (double) Math.round(x * rounda) / rounda;
    }


    public File[] get_file_list(File dir) {
        int num_files = 0;
        for (File file: dir.listFiles()) if (file_used(file)) num_files++;
        File[] fileList = new File[num_files];
        num_files = 0;
        for (File file: dir.listFiles()) {
            if (file_used(file)) {
                fileList[num_files] = file;
                num_files++;
            }
        }
        return fileList;
    } // END get_file_list
    
    public boolean file_used(File file) {
        String file_name = file.getAbsolutePath().toLowerCase();
        return file.isFile() && file_name.endsWith(".csv") && !file_name.contains("foci_table");
    }


    public boolean check_isFile_and_extension(File file, String extension) {
        return file.isFile() && file.getAbsolutePath().toLowerCase().endsWith(extension);
    } // END check_isFile_and_extension


    public void exit_analyze_dialog(final HistAnalyzer hista, String output_table_filename) {
        this.frame.setCursor(Cursor.getDefaultCursor());
        this.frame.dispose();
        if (this.debug) {
            System.out.println("exit");
            System.out.println(this.output_table.size());
        }
        if (!this.use_overlay_images && this.output_table.size() > 0)
            hista.write_output(this.output_table, output_table_filename);
    } // END exit_analyze_dialog


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
}