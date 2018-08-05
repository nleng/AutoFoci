package AutoFoci;

import AutoFoci.AnalyzeDialog;
import AutoFoci.ProgressFrame;
import AutoFoci.MainFrame;
import AutoFoci.MultiType;
import AutoFoci.GreenGUI.*;
import AutoFoci.AutoThreshold;

import java.io.*;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashSet;


import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.border.Border;
import java.text.NumberFormat;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.RoundingMode;

import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import com.google.common.collect.ComparisonChain;

import java.util.concurrent.CountDownLatch;
import java.nio.file.Files;
import java.nio.file.Paths;

import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageCanvas;
import ij.IJ;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.plugin.Zoom;
import ij.plugin.ContrastEnhancer;
import ij.gui.Toolbar;
import ij.gui.ImageWindow;
import ij.gui.ProgressBar;

import ij.gui.Roi;
// import ij.gui.PointRoi;
import ij.gui.PolygonRoi;
import ij.gui.Overlay;
import ij.gui.OvalRoi;
import ij.gui.Line;


import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;


public class HistAnalyzer {
    // there are two validation methods availabe: validate_threshold and validate_threshold with use_overlay_toggle=true (foci are marked). 

    boolean use_overlay_toggle = true;

    final static int max_cell_foci = 100; // for poisson array, must also be changed in AnalyzeDialog
    final static int image_num_validation = 4; // only works for even numbers right now. search for: image_num_validation/2
    final static int last_num = 6; // 8;  // number of "last" foci numbers to evaluate convergence threshold (stdev). image_num_validation*last_num should be at least 20 cells.
    final static int image_num_validation_big = 20; // must be a even number

    boolean blind_overlay, skip_cells_with_many_foci;
    int foci_automatic, master_channel, second_channel, dapi_channel, overlay_offset, start_foci, max_foci, num_positive_values, num_negative_values;
    double cell_number, oep_thresh, threshy_positive_max, threshy_negative_max, area, mean1, mean2, cell_mean1, cell_mean2, top1, top2, top1trans, top2trans, stDev1, stDev2, overlay_max_length, foci_total, non_foci_total, median, threshy_interval, uncertainty;
    String table_path, image_dir_path, image_name;
    GreenJFormattedTextField foci_count_field, new_thresh_field;
    JFrame validate_frame, check_positive_frame, check_negative_frame;
    JFormattedTextField foci_field;
    MainFrame main_frame;
    NumberFormat int_format = NumberFormat.getIntegerInstance();
    int[] object_counter_arr;
    int[][] xy;
    double[][] oep_arr;
    double[] oep, cell;
    double[] poisson_exp = new double[max_cell_foci];
    double[] poisson_theo = new double[max_cell_foci];
    double[] pearson_correlation_arr = new double[max_cell_foci];
    double kl_divergence = 0.;
    double r_squares = 0.;
    double c_squares = 0.;
    ImagePlus validate_images;
    double[] last_threshies = new double[last_num];
    double[] last_foci = new double[last_num];
    double[] last_directions = new double[last_num];

    double[] threshies_3 = {0, 0, 0};
    double[] foci_3 = {0, 0, 0};

    ArrayList < Double > threshies = new ArrayList < > ();
    ArrayList < Double > foci_differences = new ArrayList < > ();
    ArrayList < Double > smooth_foci_differences = new ArrayList < > ();

    ArrayList < String > image_names;
    ArrayList < String > images_to_show;
    ArrayList < ImageProcessor > images_to_show_ip;
    ImageCanvas ic;
    Overlay olay;

    AnalyzeDialog ana;
    //       ImageJ ij;

    Color bg = new Color(17, 17, 10, 255);

    // for neural network analysis
    boolean start_from_here_nn = false;
    int foci_nn, object_counter_nn;
    JFrame frame_nn;


    public HistAnalyzer() {}

    public HistAnalyzer(AnalyzeDialog ana) {
        this.ana = ana;
    }

    public double focus_evaluation_parameter(Double[] data) {
        this.top1 = data[4];
        this.top2 = data[5];
        this.stDev1 = data[6];
        this.stDev2 = data[7];
        this.cell_mean1 = data[8];
        this.cell_mean2 = data[9];
        this.top1trans = data[16];
        this.top2trans = data[17];
        double mean = data[12];
        double area = data[18];
        // 19 cell_area
        double cell_stdev1 = data[20];
        double cell_stdev2 = data[21];
        double pearson_correlation = data[22];
        double pearson_correlation_object = data[23];
        double compactness1 = data[24];
        double compactness2 = data[25];

        double offset = 2.;
        double red_power = cell_stdev2 / cell_stdev1;
        double maxa = 1.2;
        if (red_power > maxa)
            red_power = maxa;
        else if (red_power < 1. / maxa)
            red_power = 1. / maxa;
        double green_power = 1. / red_power;
        double red = this.top1 / this.cell_mean1 * this.stDev1 / compactness1;
        double green = this.top2 / this.cell_mean2 * this.stDev2 / compactness2;
        return Math.log(Math.pow(red, red_power) * Math.pow(green, green_power));
    } // END focus_evaluation_parameter

    public double[][] get_oep_arrays(String path, double oep_lim, boolean auto_limit, boolean full_output) {
        this.table_path = path;
        double[][] oep_out;
        ArrayList < Double[] > table_data = new ArrayList < Double[] > ();
        table_data = read_table_from_file(path);
        int excluded_cells = read_excluded_cells(path);
        oep_out = new double[13][];

        int table_length = table_data.size();
        double[][] wrong_output = {{}, {}};
        if (table_length < 1)
            return wrong_output;
        if (table_data.get(0).length < 2)
            return wrong_output;

        oep_out[0] = new double[table_length]; // 0 is complete inverse OEP
        // iniciated later                       // 1 is inverse OEP cut for histogram
        oep_out[2] = new double[table_length]; // 2 is cell number
        oep_out[3] = new double[table_length]; // 3 is complete normal OEP (linear to focus fociQuality)
        oep_out[4] = new double[1]; // 4 is the this.plot_limit (40% below)
        oep_out[5] = new double[1]; // 5 is the median for change_factor calculation, calculated from oep_out[3]
        oep_out[6] = new double[table_length]; // 6 is complete OEP without f()
        oep_out[7] = new double[1]; // 7 is mean of biggest X foci
        oep_out[8] = new double[table_length]; // 8 is the pearson correlation coefficient of the cell
        oep_out[9] = new double[1]; // 9 is the mean pearson correlation coefficient of the cell  ?? should be the same as 9 right now?
//         oep_out[10] = new double[table_length]; // NOT USED, 10 is the mean pearson correlation coefficient around single object
        oep_out[10] = new double[1]; // 11 is the total number of excluded cells
        oep_out[10][0] = excluded_cells;
        int n = 0;
        int oep_count = 0;
        double cell_num_tmp = 0.;
        double pearson_correlation_mean = 0.;
        this.cell_number = table_data.get(table_length - 1)[1];
        for (int i = 0; i < table_length; i++) {
            // 0 is not ImageName but Object number, see read_table_from_file
            double pearson_correlation = table_data.get(i)[22];
            if (cell_num_tmp != table_data.get(i)[1]) {
                cell_num_tmp = table_data.get(i)[1];
                pearson_correlation_mean += pearson_correlation;
                n++;
            }
            oep_out[2][i] = (int) cell_num_tmp;

            oep_out[8][i] = pearson_correlation;

//             double pearson_correlation_object = table_data.get(i)[23];
//             oep_out[10][i] = pearson_correlation_object;

            double oep_tmp = focus_evaluation_parameter(table_data.get(i));
            oep_out[3][i] = oep_tmp;
            oep_out[6][i] = f_back(oep_tmp);

            double oep_inverse_tmp = inverse(oep_tmp);
            oep_out[0][i] = oep_inverse_tmp;
            if (oep_inverse_tmp < oep_lim) oep_count++;
        }
        oep_out[9][0] = pearson_correlation_mean / n;
        if (full_output)
            return oep_out;
        // if there should be an auto-plotrange
        if (auto_limit) { //  && oep_out[0].length > 1000
            Arrays.sort(oep_out[0]); // HERE CELL NUMBER IS NOT CORRECT ANYMORE (does not matter since only oep_out[3] is used together with cell num)
            int lim = (int)(0.42 * oep_out[0].length);
            oep_out[1] = Arrays.copyOfRange(oep_out[0], 0, lim);
            oep_out[4][0] = oep_out[0][lim]; // to set the value of plot_limit_field
            int lim_median = (int)(0.5 * oep_out[3].length);
            // copying the array because the original array should still work with the cell num array
            double[] oep_log_sorted = Arrays.copyOf(oep_out[3], oep_out[3].length);
            Arrays.sort(oep_log_sorted);
            double median = oep_log_sorted[lim_median];
            oep_out[5][0] = median; // store the median of the log parameter (not bothering special cases)
            p("-----------------------------------------");
            System.out.print("median: ");
            p(median);
            // calculate the mean of the X biggest foci:
            int number_of_biggest = (int)(0.025 * this.cell_number);
            double biggest_mean = 0.;
            for (int i = oep_log_sorted.length - number_of_biggest; i < oep_log_sorted.length; i++) {
                biggest_mean += oep_log_sorted[i];
            }
            biggest_mean /= number_of_biggest;
            oep_out[7][0] = biggest_mean;
            p("biggest_mean: " + biggest_mean);
        } else {
            oep_out[1] = new double[oep_count];
            oep_count = 0;
            for (int i = 0; i < table_length; i++) {
                double oep_inverse_tmp = inverse(focus_evaluation_parameter(table_data.get(i)));
                if (oep_inverse_tmp < oep_lim) {
                    oep_out[1][oep_count] = oep_inverse_tmp;
                    oep_count++;
                }
            }
        }

        // 0 and 1 are the original array with the same length order of objects. 2 and 3 are slices with different length for displaying the histogram
        return oep_out;
    }

    public void get_XY(String path) {
        ArrayList < Double[] > table_data = new ArrayList < Double[] > ();
        table_data = read_table_from_file(path);
        int table_length = table_data.size();
        this.xy = new int[2][];
        this.xy[0] = new int[table_length];
        this.xy[1] = new int[table_length];
        int oep_count = 0;
        for (int i = 0; i < table_length; i++) {
            // 0 is not ImageName but Object number, see read_table_from_file
            // ImageName\tObjectCounter\tCellNumber\toep\tmax1X\tmax1Y\tArea\tCellArea\tmaxCH1\tmaxCH2\tmaxCH1Transform\tmaxCH2Transform\tmean1\tmean2\ttopPixels1\t topPixels2\ttopPixels1Transform\ttopPixels2Transform\tStDevMax1\tStDevMax2\tcellMean1\tcellMean2\tStDevCell1\tStDevCell2
            double x_tmp = table_data.get(i)[2];
            double y_tmp = table_data.get(i)[3];
            this.xy[0][i] = (int) x_tmp;
            this.xy[1][i] = (int) y_tmp;
        }
    }

    public ArrayList < Double > poisson_threshold(double[] oep, double[] cell, double lower_bound, double upper_bound) {
        //         lower_bound = 0.;
        if (lower_bound < 0. || lower_bound >= upper_bound) lower_bound = 0.;
        double distance = 0.25; // 0.2,  0.33, 0.4
        if (upper_bound < 2 * distance) upper_bound = distance;
        int num = (int)((upper_bound - lower_bound) / distance);
        if (num < 2) num = 2;
        if (num > 100) num = 100;
        double[] test_threshies = new double[num];
        for (int i = 0; i < test_threshies.length; i++)
            test_threshies[i] = lower_bound + i * distance;
        double poisson_threshy = 0.;
        double[] r_squares_foci_arr = new double[num];
        double[] kl_div_foci_arr = new double[num];
        for (int i = 0; i < test_threshies.length; i++) {
            double[] r_squared_and_kldiv = r_squared_and_kldiv(oep, cell, test_threshies[i]);
            r_squares_foci_arr[i] = r_squared_and_kldiv[0];
            kl_div_foci_arr[i] = r_squared_and_kldiv[1];
        }
        kl_div_foci_arr = smooth_histo(kl_div_foci_arr, 90);
        ArrayList < Double > poisson_threshy_arr = new ArrayList < > ();
        AutoThreshold at = new AutoThreshold();
        poisson_threshy_arr.add(test_threshies[Math.max(0, at.MaxEntropy(kl_div_foci_arr))]);
        poisson_threshy_arr.add(test_threshies[Math.max(0, at.RenyiEntropy(kl_div_foci_arr))]);
        poisson_threshy_arr.add(test_threshies[Math.max(0, at.Yen(kl_div_foci_arr))]);
        p("poisson_threshy_arr: " + poisson_threshy_arr);
        return poisson_threshy_arr;
    } // END poisson_threshold


    public double[] r_squared_and_kldiv(double[] oep, double[] cell, double oep_thresh) {
        double[] r_squared_and_kldiv = new double[2];
        double[] poisson_hist_exp = new double[this.max_cell_foci];
        double[] poisson_hist_theo = new double[this.max_cell_foci];
        double foci = 0.;
        double cell_num_tmp = cell[0];
        int cell_foci = 0;

        for (int i = 0; i < oep.length; i++) {
            // in case there are completely empty cells (a jump in cell number)
            if (cell[i] > cell_num_tmp + 1)
                for (int j = 0; j < (int)(cell[i] - cell_num_tmp - 1); j++)
                    poisson_hist_exp[0]++;
            // do this also if there were empty cells
            if (cell[i] > cell_num_tmp || i == oep.length - 1) {
                if (cell_foci < this.max_cell_foci) // to avoid nullpointerexception, but should never be that high
                    poisson_hist_exp[cell_foci]++;
                foci += cell_foci;
                cell_num_tmp = cell[i];
                cell_foci = 0;
            }
            if (oep[i] > oep_thresh) {
                cell_foci++;
            }
        }
        foci = foci / this.cell_number;

        double sum_exp = 0.;
        for (int i = 0; i < poisson_hist_exp.length; i++)
            sum_exp += poisson_hist_exp[i];
        for (int i = 0; i < poisson_hist_exp.length; i++) {
            if (poisson_hist_exp[i] != 0. && sum_exp != 0.) {
                poisson_hist_exp[i] /= sum_exp;
                poisson_hist_theo[i] = poisson(i, foci);
            }
        }
        r_squared_and_kldiv[0] = residual_squares(poisson_hist_exp, poisson_hist_theo);
        r_squared_and_kldiv[1] = kullback_leibler_divergence(poisson_hist_exp, poisson_hist_theo);

        return r_squared_and_kldiv;
    } // END r_squared_and_kldiv


    public double colocalization_pearson_threshold(double[][] oep, double lower_bound, double upper_bound) {
        if (lower_bound < 0. || lower_bound >= upper_bound) lower_bound = 0.;
        double distance = 0.25; // 0.2,  0.33, 0.4
        if (upper_bound < 2 * distance) upper_bound = distance;
        int num = (int)((upper_bound - lower_bound) / distance);
        if (num < 2) num = 2;
        if (num > 100) num = 100;
        double[] test_threshies = new double[num];
        for (int i = 0; i < test_threshies.length; i++) test_threshies[i] = lower_bound + i * distance;
        double[] pearson_difference_arr = new double[num];
        double pearson_threshy = 0.;
        for (int i = 0; i < test_threshies.length; i++) {
            double pearson_difference = colocalization_pearson_difference(oep, test_threshies[i]);
            pearson_difference_arr[i] = pearson_difference;
        }
        pearson_difference_arr = smooth_histo(pearson_difference_arr, 50);
        pearson_threshy = round_double(test_threshies[get_max_index(pearson_difference_arr)], 3);

        p("pearson_threshy: " + pearson_threshy);
        return pearson_threshy;

    } // END colocalization_pearson_threshold


    public double colocalization_pearson_difference(double[][] oep, double oep_thresh) {
        Arrays.fill(this.pearson_correlation_arr, 0);
        int[] n = new int[this.pearson_correlation_arr.length];

        double n_total = 0;
        double cell_num_tmp = oep[2][0];
        double pearson_tmp = oep[8][0]; // pearson correlation coefficient of the cell
        int cell_foci = 0;

        // to consider the total cell oep (from foci not BG objects) rather than only the number of foci.
        ArrayList < double[] > oep_cell_list = new ArrayList < > ();
        double oep_cell = 0.;

        // creating a histogram with x=number_of_foci and y=sum_of_correlation_coefficients
        for (int i = 0; i < oep[3].length; i++) {
            if (cell_num_tmp != oep[2][i] || i == oep[3].length - 1) {
                if (cell_foci < this.max_cell_foci) { // to avoid nullpointerexception, but should never be that high
                    this.pearson_correlation_arr[cell_foci] += pearson_tmp;
                    n[cell_foci]++;
                    n_total++;
                    double[] oep_and_pearson = {
                        oep_cell,
                        pearson_tmp
                    };
                    oep_cell_list.add(oep_and_pearson);
                }
                cell_num_tmp = oep[2][i];
                pearson_tmp = oep[8][i];
                cell_foci = 0;
                oep_cell = 0.;
            }
            // fffff
            if (oep[3][i] > oep_thresh) {
                cell_foci++;
                oep_cell += oep[3][i];
            }
        }
        // normalizing the histogram
        for (int i = 0; i < this.pearson_correlation_arr.length; i++)
            if (n[i] != 0) this.pearson_correlation_arr[i] /= n[i];
        double lower_bound = 1. / 3.;
        double upper_bound = 2. / 3.;
        int num = 7;
        //         double[] n_total_fraction_arr = {0.3, 0.4, 0.5, 0.6, 0.7};
        double[] n_total_fraction_arr = new double[num];
        for (int i = 0; i < n_total_fraction_arr.length; i++) n_total_fraction_arr[i] = lower_bound + i * (upper_bound - lower_bound) / (num - 1);
        ArrayList pearson_difference_list = new ArrayList < > ();
        for (double n_total_fraction: n_total_fraction_arr) {

            double[][] n_start_end = {
                {
                    2. * n_total_fraction - 1., n_total_fraction
                },
                {
                    n_total_fraction,
                    2. * n_total_fraction
                }
            };
            double[] corr_mean = new double[2];
            double[] corr_n = new double[2];

            for (int j = 0; j < n_start_end.length; j++) {
                double start = n_start_end[j][0] * n_total;
                double end = n_start_end[j][1] * n_total;
                if (start < 0)
                    start = 0.;

                boolean first = true;
                int sum = 0;
                for (int i = 0; i < this.pearson_correlation_arr.length; i++) {
                    sum += n[i];
                    if (sum > start && first) {
                        first = false;
                        double add_n = sum - start;
                        // don't add too much.
                        if (sum > end) add_n = end - start - corr_n[j];
                        corr_n[j] += add_n;
                        corr_mean[j] += add_n * this.pearson_correlation_arr[i];
                    } else if (sum > start && sum < end) {
                        corr_n[j] += n[i];
                        corr_mean[j] += n[i] * this.pearson_correlation_arr[i];
                    } else if (sum > end) {
                        double add_n = end - start - corr_n[j];
                        corr_n[j] += add_n;
                        corr_mean[j] += add_n * this.pearson_correlation_arr[i];
                        break; // important!
                    }
                }
            }
            double pearson_difference = corr_mean[1] / corr_n[1] - corr_mean[0] / corr_n[0];
            pearson_difference_list.add(pearson_difference);
        }

        return mean(pearson_difference_list);

    } // END colocalization_pearson_difference


    public double[] count_foci(double[][] oep, double oep_thresh) {
        Arrays.fill(this.poisson_exp, 0);
        Arrays.fill(this.poisson_theo, 0);
        double[] foci = {
            0.,
            0.,
            0.
        };
        double cell_num_tmp = oep[2][0];
        int cell_foci = 0;
        double foci_sum_poisson = 0.;
        double mean_oep = 0.;

        for (int i = 0; i < oep[3].length; i++) {
            if (cell_num_tmp != oep[2][i] || i == oep[3].length - 1) {
                if (cell_foci < this.max_cell_foci) { // to avoid nullpointerexception, but should never be that high
                    this.poisson_exp[cell_foci]++;
                    foci_sum_poisson++;
                }
                foci[0] += cell_foci;
                cell_num_tmp = oep[2][i];
                cell_foci = 0;
            }
            // fffff
            if (oep[3][i] > oep_thresh) {
                mean_oep += oep[3][i];
                cell_foci++;
            }
        }
        print("mean OEP: " + mean_oep / foci[0]);
        foci[1] = round_double(foci[0] / this.cell_number, 3);
        foci[2] = this.cell_number;

        if (foci_sum_poisson != 0.) {
            for (int i = 0; i < this.poisson_exp.length; i++) {
                this.poisson_exp[i] /= foci_sum_poisson;
                this.poisson_theo[i] = poisson(i, foci[1]);
            }
        }
        try {
            this.ana.poisson_exp = this.poisson_exp;
            this.ana.poisson_theo = this.poisson_theo;
            this.ana.kl_divergence = round_double(kullback_leibler_divergence(this.poisson_exp, this.poisson_theo), 5);
            this.ana.r_squares = round_double(residual_squares(this.poisson_exp, this.poisson_theo), 6);
        } catch (Exception e) {
            //             e.printStackTrace ();
        }
        return foci;
    } // END count_foci


    public double[] count_foci_skip_cells(double[][] oep, double oep_thresh, int max_foci_skip) {
        Arrays.fill(this.poisson_exp, 0);
        Arrays.fill(this.poisson_theo, 0);
        int cells_skipped = 0, cell_foci = 0;
        double foci_sum_poisson = 0.;
        double cell_num_tmp = oep[2][0];
        double[] foci = {
            0.,
            0.,
            0.
        };
        double mean_oep = 0.;
        for (int i = 0; i < oep[3].length; i++) {
            if (cell_num_tmp != oep[2][i] || i == oep[3].length - 1) {
                if (cell_foci > max_foci_skip) {
                    cells_skipped++;
                } else {
                    if (cell_foci < this.max_cell_foci) { // to avoid nullpointerexception, but should never be that high
                        this.poisson_exp[cell_foci]++;
                        foci_sum_poisson++;
                    }
                    foci[0] += cell_foci;
                }
                cell_num_tmp = oep[2][i];
                cell_foci = 0;
            }
            // fffff
            if (oep[3][i] > oep_thresh) {
                mean_oep += oep[3][i];
                cell_foci++;
            }
        }
        print("mean OEP: " + mean_oep / foci[0]);
        foci[1] = round_double(foci[0] / (this.cell_number - cells_skipped), 3);
        foci[2] = this.cell_number - cells_skipped;

        if (foci_sum_poisson != 0.) {
            for (int i = 0; i < this.poisson_exp.length; i++) {
                this.poisson_exp[i] /= foci_sum_poisson;
                this.poisson_theo[i] = poisson(i, foci[1]);
            }
        }
//         p("poisson_exp = " + Arrays.toString(this.poisson_exp));
//         p("poisson_theo = " + Arrays.toString(this.poisson_theo));
        this.ana.poisson_exp = this.poisson_exp;
        this.ana.poisson_theo = this.poisson_theo;
        this.ana.kl_divergence = round_double(kullback_leibler_divergence(this.poisson_exp, this.poisson_theo), 5);
        this.ana.r_squares = round_double(residual_squares(this.poisson_exp, this.poisson_theo), 6);
        return foci;
    } // END count_foci_skip_cells


    public void toggle_markers(boolean selected) {
        if (selected) {
            this.ic.setOverlay(this.olay);
        } else {
            Overlay olay_empty = new Overlay();
            this.ic.setOverlay(olay_empty);
        }
    } // END toggle_markers


    public void mark_foci(ImageProcessor ipp, double oep_tmp, double oep_thresh, int overlay_offset, double overlay_max_length, int max1X, int max1Y, int width, int height) {
        int length;
        if (this.blind_overlay)
            length = (int)(1 + overlay_max_length) / 2;
        //             length = overlay_offset;
        else
            length = 1 + (int) Math.round(overlay_max_length * (Math.min(3 * oep_tmp / oep_thresh - 3, 1)));
        int[] white = {220, 220, 220};
        for (int i = 0; i < length; i++) {
            int x = max1X - overlay_offset - i;
            int y = max1Y;
            if (x < width && y < height)
                ipp.putPixel(x, y, white);
        }
        for (int i = 0; i < length; i++) {
            int x = max1X;
            int y = max1Y - overlay_offset - i;
            if (x < width && y < height)
                ipp.putPixel(x, y, white);
        }
    } // END mark_foci


    public ImageProcessor split_view(ImageProcessor ip, ImageProcessor ip_original) {
        int width = ip.getWidth();
        int height = ip.getHeight();

        ImageProcessor ipSplit = new ColorProcessor(width, 3 * height); // ColorProcessor, FloatProcessor ist heller weil nur eine farbe??? komisch, ich glaube wegen putPixelValue, weil das float ist

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int[] rgb = {0, 0,0};
                rgb = ip_original.getPixel(i, j, rgb);

                // or: second - master - together. this way it is easier to look at master+together
                int[] second = {rgb[this.second_channel], rgb[this.second_channel], rgb[this.second_channel]};
                ipSplit.putPixel(i, j, second);

                int[] master = {rgb[this.master_channel], rgb[this.master_channel], rgb[this.master_channel]};
                ipSplit.putPixel(i, j + height, master);

                // take all 3 channels, since we already removed the dapi channel in ip (so that the marks are white not yellow)
                rgb = ip.getPixel(i, j, rgb);
                ipSplit.putPixel(i, j + 2 * height, rgb);
            }
        }
        // 	  ContrastEnhancer ce = new ContrastEnhancer();
        // 	  ce.stretchHistogram(ipSplit, 0.01);
        return ipSplit;
    } // END split_view

    public ImageProcessor split_view_horizontal(ImageProcessor ip, ImageProcessor ip_original) {
        int width = ip.getWidth();
        int height = ip.getHeight();

        // 	  ImagePlus impComposite = new ImagePlus();
        ImageProcessor ipSplit = new ColorProcessor(3 * width, height); // ColorProcessor, FloatProcessor ist heller weil nur eine farbe??? komisch, ich glaube wegen putPixelValue, weil das float ist

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                int[] rgb = {0, 0, 0};
                // take original for single channels
                rgb = ip_original.getPixel(i, j, rgb);

                // oder: second - master - together. this way it is easier to look at master+together
                int[] second = {rgb[this.second_channel], rgb[this.second_channel], rgb[this.second_channel]};
                ipSplit.putPixel(i, j, second);

                int[] master = {rgb[this.master_channel], rgb[this.master_channel], rgb[this.master_channel]};
                ipSplit.putPixel(i + width, j, master);

                // take overlay for both channels together
                rgb = ip.getPixel(i, j, rgb);
                ipSplit.putPixel(i + 2 * width, j, rgb);
            }
        }

        String[] letters = {"R", "G", "B"};
        Color[] colors = {
            new Color(150, 0, 0, 255),
            new Color(0, 150, 0, 255),
            new Color(0, 0, 150, 255)
        };
        ipSplit.setFont(new Font("San Serif", Font.BOLD, 12));

        ipSplit.setColor(colors[this.second_channel]);
        ipSplit.drawString(letters[this.second_channel], 3, 15);
        ipSplit.setColor(colors[this.master_channel]);
        ipSplit.drawString(letters[this.master_channel], 3 + width, 15);
        ipSplit.setColor(colors[this.second_channel]);
        ipSplit.drawString(letters[this.second_channel], 3 + 2 * width, 15);
        ipSplit.setColor(colors[this.master_channel]);
        ipSplit.drawString(letters[this.master_channel], 13 + 2 * width, 15);

        ipSplit.setColor(new Color(200, 200, 200, 255));
        ipSplit.setFont(new Font("San Serif", Font.PLAIN, 12));
        ipSplit.drawString(this.image_name, 3, height - 10);
        // 	  ContrastEnhancer ce = new ContrastEnhancer();
        // 	  ce.stretchHistogram(ipSplit, 0.01);
        return ipSplit;
    } // END split_view_horizontal

    public boolean check_isFile_and_extension(File file, String extension) {
        return file.isFile() && file.getAbsolutePath().toLowerCase().endsWith(extension);
    } // END check_isFile_and_extension

    // if variable==true then the size is dependent on the length of image_list, else it is determined by this.image_num_validation_big

    public ImagePlus compose_images(ArrayList < ImageProcessor > image_list, ArrayList < String > image_names_list, boolean variable, boolean validation_1, int image_num_tmp) {
        int width = image_list.get(0).getWidth();
        int height = image_list.get(0).getHeight();

        if (variable) image_num_tmp = image_list.size();

        int cols = (image_num_tmp + 1) / 2; // +1 because int rounds down

        int rows = 2;
        if (image_num_tmp <= cols)
            rows = 1;
        if (validation_1) {
            if (this.use_overlay_toggle) {
                cols = 1;
                rows = this.image_num_validation;
            } else {
                cols = this.image_num_validation;
                rows = 1;
            }
        }
        ImagePlus impComposite = new ImagePlus();
        ImageProcessor ipComposite = new ColorProcessor(cols * width, rows * height);

        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows; r++) {
                int counter = c * rows + r;
                // they should always have the same size. if not then there is probably something wrong with name <-> image links. search for current_object_image.
                if (counter < image_list.size() && counter < image_names_list.size()) {
                    ImageProcessor ip = image_list.get(counter);
                    if (!this.use_overlay_toggle && c == 0) { // ffff HERE
                        String[] letters = {"R","G", "B"};
                        Color[] colors = {
                            new Color(130, 0, 0, 255),
                            new Color(0, 130, 0, 255),
                            new Color(0, 0, 130, 255)
                        };
                        ip.setFont(new Font("San Serif", Font.BOLD, 12));

                        ip.setColor(colors[this.second_channel]);
                        ip.drawString(letters[this.second_channel], 3, 15);
                        ip.setColor(colors[this.master_channel]);
                        ip.drawString(letters[this.master_channel], 3, height / 3 + 15);
                        ip.setColor(colors[this.second_channel]);
                        ip.drawString(letters[this.second_channel], 3, height * 2 / 3 + 15);
                        ip.setColor(colors[this.master_channel]);
                        ip.drawString(letters[this.master_channel], 13, height * 2 / 3 + 15);
                    }
                    String name_to_write = image_names_list.get(counter);
                    if (name_to_write.length() > width / 8.)
                        name_to_write = name_to_write.substring(0, (int) Math.min(name_to_write.length(), width / 8.)) + "...";
                    ip.setFont(new Font("San Serif", Font.BOLD, 10));
                    ip.setColor(new Color(120, 120, 0, 255));
                    if (!validation_1)
                        ip.drawString(name_to_write, 15, 13);
                    else
                        ip.drawString(name_to_write, 3, height - 3);
                    for (int i = c * width; i < (c + 1) * width; i++) {
                        for (int j = r * height; j < (r + 1) * height; j++) {
                            ipComposite.putPixel(i, j, ip.getPixel(i - c * width, j - r * height));
                        }
                    }
                }
            }
        }
        // 	  ipComposite = ipComposite.rotateRight();
        impComposite.setProcessor(ipComposite);
        return impComposite;
    } // END compose_images

    void waitForKey() {
        final CountDownLatch latch = new CountDownLatch(1);
        KeyEventDispatcher dispatcher = new KeyEventDispatcher() {
            // Anonymous class invoked from EDT
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    //                     frame.dispose();
                    foci_nn = 1;
                    print(foci_nn);
                    frame_nn.setVisible(false);
                    latch.countDown();

                } else if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    foci_nn = 0;
                    print(foci_nn);
                    frame_nn.setVisible(false);
                    latch.countDown();
                } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) { // don't save at any of both in this case.
                    foci_nn = -1;
                    print(foci_nn);
                    frame_nn.setVisible(false);
                    latch.countDown();
                }
                return false;
            }
        };
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher);
        try {
            latch.await(); // current thread waits here until countDown() is called
        } catch (Exception e) {}
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher);
    }


    public boolean create_overlay_stack(String image_dir_path, String table_path, String extension, double[] oep, double oep_thresh, int overlay_offset, double overlay_max_length, int master_channel, int second_channel, int dapi_channel, boolean blind_overlay, boolean only_show_master_and_second_channel, boolean skip_cells_with_many_foci, int max_foci) {
        this.second_channel = second_channel;
        this.master_channel = master_channel;
        this.dapi_channel = dapi_channel;
        this.blind_overlay = blind_overlay;
        this.oep = oep;
        this.oep_thresh = oep_thresh;
        this.overlay_offset = overlay_offset;
        this.overlay_max_length = overlay_max_length;
        this.skip_cells_with_many_foci = skip_cells_with_many_foci;
        this.max_foci = max_foci;
        // here we use the original table
        this.image_names = read_imageNames(table_path);

        ImageStack stack_overlay = new ImageStack();
        get_XY(table_path);
        File dir = new File(image_dir_path);
        boolean overlay_stack_created = false;

        final ImageJ ij = new ImageJ(ImageJ.EMBEDDED);
        ProgressBar pb = ij.getProgressBar();

        int object_counter = 0, image_counter_total = 0;

        ImagePlus imp_wh = new ImagePlus(new File(image_dir_path, this.image_names.get(0)).getAbsolutePath());
        int width = imp_wh.getWidth();
        int height = imp_wh.getHeight();
        ImageStack stack = new ImageStack(10 * width, 6 * height);
        ImageProcessor ip = imp_wh.getProcessor();
        ImageProcessor ip_original = ip.duplicate();


        ImagePlus imp = new ImagePlus();
        object_counter_nn = 0;
        // should not result in infinite loop because of object_counter++;. Not 100% sure though.
        while (object_counter < this.image_names.size()) {
            //             p("while loop 1");
            ArrayList < String > images_to_show = new ArrayList < > ();
            ArrayList < ImageProcessor > images_to_show_ip = new ArrayList < ImageProcessor > ();
            String current_image = "", current_object_image = "", next_image = "";
            int cell_foci = 0;
            int image_counter = 0;
            // here not images_to_show.size() because then only the first object in the last cell is included
            // should not result in infinite loop because of object_counter++;
            while (images_to_show_ip.size() < this.image_num_validation_big && object_counter < this.image_names.size()) {
                current_object_image = this.image_names.get(object_counter);

                if (object_counter + 1 < this.image_names.size()) next_image = this.image_names.get(object_counter + 1);
                else next_image = "last_image";

                if (check_isFile_and_extension(new File(image_dir_path, current_object_image), extension)) {
                    // run this at the first object of a cell.
                    if (!current_object_image.equals(current_image)) {
                        // count foci in image. 
                        cell_foci = 0;
                        for (int j = 0; j < this.oep.length; j++)
                            if (current_object_image.equals(this.image_names.get(j)) && this.oep[j] > this.oep_thresh) cell_foci++;
                        if (!this.skip_cells_with_many_foci || cell_foci <= this.max_foci) {
                            current_image = current_object_image;
                            images_to_show.add(current_object_image);
                            imp = new ImagePlus(new File(image_dir_path, current_object_image).getAbsolutePath());
                            if (imp.getType() != ImagePlus.COLOR_RGB) continue; // non-RGB-bilder are ignored
                            width = imp.getWidth();
                            height = imp.getHeight();
                            ip = imp.getProcessor();
                            ip_original = ip.duplicate(); // are both the same, but it is needed in case the overlay differs
                            // remove the third channel as defined in tab 1
                            if (only_show_master_and_second_channel) {
                                FloatProcessor fp = new FloatProcessor(new float[width][height]);
                                // remove all channels that are neither master nor second channel. this way it will also work if channel 3 is defined as one of the others. 
                                for (int c=0; c<3; c++) if (c != this.master_channel && c != this.second_channel) ip.setPixels(c, fp);
                            }
                        }
                    }

                    if (this.oep[object_counter] > this.oep_thresh && (!this.skip_cells_with_many_foci || cell_foci <= this.max_foci)) {
                        mark_foci(ip, this.oep[object_counter], oep_thresh, overlay_offset, overlay_max_length, this.xy[0][object_counter], this.xy[1][object_counter], width, height);
                    }

                    // run this after the last object of one cell:
                    if (!next_image.equals(current_image) && (!this.skip_cells_with_many_foci || cell_foci <= this.max_foci)) { // this already includes next_image.equals("last_image")
                        ImageProcessor combined = split_view(ip, ip_original);
                        images_to_show_ip.add(combined);
                        image_counter_total++;
                        image_counter++;
                    }
                }
                object_counter++;
                pb.show(object_counter, this.image_names.size());
            }
            if (image_counter < this.image_num_validation_big) {
                this.validate_images = compose_images(images_to_show_ip, images_to_show, true, false, this.image_num_validation_big);
            } else {
                this.validate_images = compose_images(images_to_show_ip, images_to_show, false, false, this.image_num_validation_big);
                ImageProcessor ip_slice = this.validate_images.getProcessor();
                stack.addSlice(ip_slice);
            }
        }
        pb.show(1);

        if (image_counter_total < this.image_num_validation_big) {
            this.validate_images.show();
            final ImageWindow window = this.validate_images.getWindow();
            ImageIcon icon = new ImageIcon(getClass().getResource("/images/zelle1.png"));
            window.setIconImage(icon.getImage());
        } else {
            ImagePlus img_stack = new ImagePlus();
            img_stack.setStack(stack);
            img_stack.setTitle("Overlay Image Stack");
            img_stack.show(); // must come before using the window, else: nullpointerexception
            final ImageWindow window = img_stack.getWindow();
            ImageIcon icon = new ImageIcon(getClass().getResource("/images/zelle1.png"));
            window.setIconImage(icon.getImage());
            Toolbar t = new Toolbar();
            t.setTool(t.HAND);
            Zoom zm = new Zoom();
            zm.run("max");
        }


        return true;
    } // END create_overlay_stack


    public void validate_threshold(String image_dir_path, double[][] oep_arr, double[] oep, ArrayList < String > image_names, boolean skip_cells_with_many_foci, int max_foci, double median, double oep_thresh, int master_channel, int second_channel, int dapi_channel, int overlay_offset, double overlay_max_length, boolean use_overlay_toggle) {
        this.image_dir_path = image_dir_path;
        this.master_channel = master_channel;
        this.second_channel = second_channel;
        this.dapi_channel = dapi_channel;
        this.overlay_offset = overlay_offset;
        this.overlay_max_length = overlay_max_length;
        this.use_overlay_toggle = use_overlay_toggle;
        this.oep_arr = oep_arr;
        this.oep = oep;
        this.image_names = image_names;
        this.skip_cells_with_many_foci = skip_cells_with_many_foci;
        this.max_foci = max_foci;
        this.blind_overlay = true; // for constant marker length
        this.main_frame = new MainFrame();

        Random r = new Random();
        double rand = r.nextDouble() * 0.02 - 0.01;
        this.oep_thresh = (1. + rand) * oep_thresh;
        this.threshy_positive_max = this.oep_thresh;
        this.threshy_negative_max = this.oep_thresh;
        this.threshy_interval = 0.06 * oep_thresh;
//         p("" + oep_thresh);
//         p("" + oep_arr[7][0]);
//         p("#--------------------#");
        this.threshy_interval = 0.05 * (oep_arr[7][0] - oep_thresh);
        this.num_positive_values = 0;
        this.num_negative_values = 0;
        this.threshies = new ArrayList < > ();
        this.foci_differences = new ArrayList < > ();
        this.last_threshies = new double[this.last_num];
        this.last_foci = new double[this.last_num];
        this.last_directions = new double[this.last_num];

        validate_threshold();
    }


    public void validate_threshold() {
        get_XY(this.table_path);

        boolean verified = false;

        // add one extra image for both sides in case there are cells with many foci, which are kicked out after.
        int additional_images = 0;
        if (this.skip_cells_with_many_foci) additional_images = 2;

        this.images_to_show = new ArrayList < > ();
        for (int i = 0; i < this.image_num_validation + additional_images; i++)
            this.images_to_show.add(this.image_names.get(0)); // in case there are not 20 objects, should not be, but anyway
        this.object_counter_arr = new int[this.image_num_validation + additional_images];

        // take n/2 closest objects in both directions. does not work if there are less then n/2 on one side (should not be anyway)-
        double[] worst_foci = new double[(this.image_num_validation + additional_images) / 2];
        double[] best_non_foci = new double[(this.image_num_validation + additional_images) / 2];
        Arrays.fill(worst_foci, Double.POSITIVE_INFINITY);
        Arrays.fill(best_non_foci, Double.NEGATIVE_INFINITY);
        for (int j = 0; j < this.oep.length; j++) {
            if (this.oep[j] > this.oep_thresh) {
                int maxPos = getMaxPosition(worst_foci);
                if (this.oep[j] < worst_foci[maxPos] && !name_in_list(this.images_to_show, this.image_names.get(j))) {
                    worst_foci[maxPos] = this.oep[j];
                    this.object_counter_arr[2 * maxPos] = j;
                    this.images_to_show.set(2 * maxPos, this.image_names.get(j));
                }
            } else if (this.oep[j] < this.oep_thresh) {
                int minPos = getMinPosition(best_non_foci);
                if (this.oep[j] > best_non_foci[minPos] && !name_in_list(this.images_to_show, this.image_names.get(j))) {
                    best_non_foci[minPos] = this.oep[j];
                    this.object_counter_arr[2 * minPos + 1] = j;
                    this.images_to_show.set(2 * minPos + 1, this.image_names.get(j));
                }
            }
        }

        // counting foci in all images. taking the unique one not to count foci twice
        this.foci_automatic = 0;
        this.olay = new Overlay();
        this.images_to_show_ip = new ArrayList < ImageProcessor > ();
        int show_worst_foci = 0, show_best_non_foci = 0;
        for (int i = 0; i < this.images_to_show.size(); i++) {
            int cell_foci = 0;
            ImagePlus imp = new ImagePlus(new File(this.image_dir_path, this.images_to_show.get(i)).getAbsolutePath()); // image.getAbsolutePath()
            ImageProcessor ip = imp.getProcessor();
            ImageProcessor ip_original = ip.duplicate(); // are both the same, but it is needed in case the overlay differs
            // remove the third channel as defined in tab 1
            FloatProcessor fp = new FloatProcessor(new float[ip.getWidth()][ip.getHeight()]);
            ip.setPixels(this.dapi_channel, fp);
            int pos = this.object_counter_arr[i];

            // for overlay mark of foci/background objects
            if (this.use_overlay_toggle) {
                // if the marker should be visible in all channels
                //              for(int j=0;j<3;j++){
                // or only in the image with both channels (should be always in middle position)
                for (int j = 2; j < 3; j++) {
                    int xx = j * ip.getWidth() + this.xy[0][pos];
                    int yy = i * ip.getHeight() + this.xy[1][pos];
                    double line_start = 2 * overlay_offset;
                    double line_end = 1.6 * overlay_offset;
                    Line roi = new Line(xx - line_start, yy, xx - line_end, yy);
                    Color marker_color = new Color(200, 200, 200);
                    roi.setStrokeColor(marker_color);
                    this.olay.add(roi);
                    roi = new Line(xx, yy - line_start, xx, yy - line_end);
                    roi.setStrokeColor(marker_color);
                    this.olay.add(roi);
                }
            } else {
                // count foci in images
                for (int j = 0; j < this.oep.length; j++)
                    if (images_to_show.get(i).equals(this.image_names.get(j)) && this.oep[j] > this.oep_thresh)
                        cell_foci++;
            }
            //              mark_foci(ip, this.oep[pos], oep_thresh, overlay_offset, overlay_max_length, this.xy[0][pos], this.xy[1][pos], ip.getWidth(), ip.getHeight());
            if (!this.skip_cells_with_many_foci || cell_foci <= this.max_foci) {
                // take n/2 above and n/2 below the threshold
                boolean add = false;
                if (i % 2 == 0 && show_worst_foci < this.image_num_validation / 2) {
                    this.foci_automatic += cell_foci;
                    show_worst_foci++;
                    add = true;
                } else if (i % 2 == 1 && show_best_non_foci < this.image_num_validation / 2) {
                    this.foci_automatic += cell_foci;
                    show_best_non_foci++;
                    add = true;
                }
                if (add) {
                    ImageProcessor combined;
                    if (this.use_overlay_toggle) combined = split_view_horizontal(ip, ip_original);
                    else combined = split_view(ip, ip_original);
                    this.images_to_show_ip.add(combined);
                }
                // just take those nearest to threshold
                //                 if(objects_to_show < this.image_num_validation){
                //                     this.foci_automatic += cell_foci;
                //                     objects_to_show++;
                //                     ImageProcessor combined;
                //                     if(this.use_overlay_toggle)
                //                         combined = split_view_horizontal(ip, ip_original);
                //                     else
                //                         combined = split_view(ip, ip_original);
                //                     this.images_to_show_ip.add(combined);
                //                 }
            }
        }
        if (this.images_to_show_ip.size() != 0) {
            this.validate_images = compose_images(this.images_to_show_ip, this.images_to_show, true, true, this.image_num_validation);
        } else {
            this.foci_automatic = 999;
            ImagePlus imp_0 = new ImagePlus(new File(this.image_dir_path, this.images_to_show.get(0)).getAbsolutePath());
            ImageProcessor ip_empty = new ColorProcessor(imp_0.getWidth() * this.image_num_validation, imp_0.getHeight());
            ip_empty.setFont(new Font("San Serif", Font.BOLD, 12));
            ip_empty.setColor(new Color(150, 150, 150, 255));
            ip_empty.drawString("No images could be found. Maybe they are excluded due to option:", 20, 30);
            ip_empty.drawString("'Exclude cells with more foci than...'", 20, 50);
            this.validate_images = new ImagePlus();
            this.validate_images.setProcessor(ip_empty);
        }

        GreenJPanel panel = new GreenJPanel();
        GridBagLayout gbl = new GridBagLayout();
        panel.setLayout(gbl);

        if (this.use_overlay_toggle) {
            JToggleButton toggle_marker_button = new JToggleButton("Toggle marker", true);
            toggle_marker_button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent actionEvent) {
                    AbstractButton abstractButton = (AbstractButton) actionEvent.getSource();
                    boolean selected = abstractButton.getModel().isSelected();
                    toggle_markers(selected);
                }
            });
            // toggle by pressing space key
            panel.getInputMap().put(KeyStroke.getKeyStroke("SPACE"), "toggle_markers");
            panel.getActionMap().put("toggle_markers", new AbstractAction("toggle_markers") {
                public void actionPerformed(ActionEvent evt) {
                    boolean selected = toggle_marker_button.getModel().isSelected();
                    toggle_marker_button.setSelected(!selected);
                    toggle_markers(!selected);
                }
            });


            Border border = BorderFactory.createLineBorder(GreenGUI.fg, 1);
            toggle_marker_button.setBorder(border);
            toggle_marker_button.setFocusPainted(false);

            GreenJLabel validate_label = new GreenJLabel("<html><p> How many foci have been marked? Do not count <br>non-marked foci. The master channel maximum <br>is located at the center of the crosshairs. </p></html>");

            for (int i = 0; i < this.image_num_validation + 1; i++) {
                JButton button_foci = new DarkGreenJButton(Integer.toString(i));
                final int fi = i;
                button_foci.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent actionEvent) {
                        check_values(fi);
                    }
                });
                String fi_str = Integer.toString(i);
                panel.getInputMap().put(KeyStroke.getKeyStroke(fi_str), fi_str);
                panel.getActionMap().put(fi_str, new AbstractAction(fi_str) {
                    public void actionPerformed(ActionEvent evt) {
                        check_values(fi);
                    }
                });

                addComp(panel, gbl, button_foci, 4, i, 1, 1, true, true, 3);
            }
            addComp(panel, gbl, validate_label, 0, 6, 4, 1, true, true, 5);
            addComp(panel, gbl, toggle_marker_button, 4, 6, 1, 1, true, true, 3);
        } else {

            System.out.print("Foci automatic: ");
            p(this.foci_automatic);

            String foci_auto_str = "";
//             if (!this.ana.blind_validate) 
            foci_auto_str = " (autom. " + String.valueOf(this.foci_automatic) + " foci)";

            GreenJLabel validate_label = new GreenJLabel("<html><p> How many foci do you count?" + foci_auto_str + "</p></html>");

            NumberFormat int_format = NumberFormat.getIntegerInstance();
            int_format.setGroupingUsed(false);
            this.foci_field = new GreenJFormattedTextField(int_format);
            // so that enter will submit
            this.foci_field.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent actionEvent) {
                    int foci = ((Number) foci_field.getValue()).intValue();
                    check_values_interval(foci);
                }
            });

            JButton submit_button = new GreenJButton("Submit");
            submit_button.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent actionEvent) {
                    int foci = ((Number) foci_field.getValue()).intValue();
                    check_values_interval(foci);
                }
            });
            addComp(panel, gbl, validate_label, 0, 6, 2, 1, true, true, 5);
            addComp(panel, gbl, foci_field, 2, 6, 1, 1, true, true, 3);
            addComp(panel, gbl, submit_button, 3, 6, 1, 1, true, true, 3);
        }
        // if I want to use a normal JFrame
        this.ic = new ImageCanvas(this.validate_images);
        double mag = 1.0;
        //         double mag = 0.5;
        int w = (int)(this.validate_images.getWidth() * mag);
        int h = (int)(this.validate_images.getHeight() * mag);
        this.ic.setSize(new Dimension(w, h));
        this.ic.setMagnification(mag);
        //          ic.zoomIn();
        this.ic.setOverlay(olay);


        addComp(panel, gbl, this.ic, 0, 0, 4, 6, true, false, 5);
        this.validate_frame = new JFrame("Validate Threshold");
        this.validate_frame.add(panel);
        this.validate_frame.setSize(new Dimension(w, (int)(h * 1.5)));
        this.validate_frame.pack();
        this.validate_frame.setLocationRelativeTo(null);
        this.validate_frame.setVisible(true);

        ImageIcon icon = new ImageIcon(getClass().getResource("/images/zelle1.png"));
        this.validate_frame.setIconImage(icon.getImage());

        return;
    } // END validate_threshold


    // here the threshold is estimated after counting equally spaced thresholds within an interval around the "real" threshold.
    public void check_values_interval(int foci) {
        this.ana.change_count++;

        double foci_difference = foci - this.foci_automatic;
        p("FOCI DIFFERENCE: " + foci_difference);

        // >= to include the first time
        if (this.oep_thresh >= threshy_positive_max) {
            this.threshy_positive_max = this.oep_thresh;
            this.threshies.add(this.oep_thresh);
            this.foci_differences.add(foci_difference);
        } else if (this.oep_thresh < threshy_negative_max) {
            this.threshy_negative_max = this.oep_thresh;
            this.threshies.add(0, this.oep_thresh);
            this.foci_differences.add(0, foci_difference);
        }
        p("foci_differences: " + foci_differences);
        p("threshies: " + threshies);

        this.smooth_foci_differences = smooth_until_monotonic(this.foci_differences);
        p("xxxxx " + this.smooth_foci_differences);
        this.num_positive_values = positive_values(this.smooth_foci_differences);
        this.num_negative_values = negative_values(this.smooth_foci_differences);

        p(this.num_negative_values + " nnn " + this.num_positive_values);

        if (this.num_positive_values > this.num_negative_values)
            this.oep_thresh = this.threshy_negative_max - this.threshy_interval;
        else
            this.oep_thresh = this.threshy_positive_max + this.threshy_interval;


        if (enough_validation()) validation_finished_action_interval();
        else new_thresh_action();

    } // END check_values_interval


    public boolean enough_validation() {
        //     if(this.num_positive_values < 5 || this.num_positive_values != this.num_negative_values)
        if (this.smooth_foci_differences.size() < 9 || (Math.abs(this.num_positive_values - this.num_negative_values) > 2 && this.smooth_foci_differences.size() < 15))
            return false;
        int[] lims = {-1,
            0,
            1
        };
        for (int i = 0; i < this.threshies_3.length; i++)
            this.threshies_3[i] = 0.;
        double[] foci_3 = {
            0,
            0,
            0
        };
        for (int i = 1; i < this.smooth_foci_differences.size(); i++) {
            for (int j = 0; j < lims.length; j++) {
                if (this.threshies_3[j] == 0 && this.smooth_foci_differences.get(i - 1) < lims[j] && this.smooth_foci_differences.get(i) > lims[j]) {
                    double x_intersect = point_of_intersection(i - 1., i, this.smooth_foci_differences.get(i - 1), this.smooth_foci_differences.get(i), lims[j]);
                    this.threshies_3[j] = this.threshies.get(0) + x_intersect * this.threshy_interval;
                    //                 this.threshies_3[j] = this.threshy_start + x_intersect * this.threshy_interval;
                } else if (this.smooth_foci_differences.get(i) == lims[j]) {
                    this.threshies_3[j] = this.threshies.get(i);
                }
            }
        }
        p("yyyyy " + Arrays.toString(this.threshies_3));
        for (double t: this.threshies_3)
            if (t == 0.)
                return false;
        if (this.skip_cells_with_many_foci)
            for (int i = 0; i < this.threshies_3.length; i++)
                this.foci_3[i] = count_foci_skip_cells(this.oep_arr, this.threshies_3[i], this.max_foci)[1];
        else
            for (int i = 0; i < this.threshies_3.length; i++)
                this.foci_3[i] = count_foci(this.oep_arr, this.threshies_3[i])[1];
        p("zzzzz " + Arrays.toString(this.foci_3));
        p(this.foci_3[1] + " FOCI " + (this.foci_3[0] + this.foci_3[2]) / 2.);
        this.uncertainty = (this.foci_3[0] - this.foci_3[2]) / 2.;
        return true;
    } // END enough_validation


    // currently used with marks
    public void check_values(int foci) {
        this.ana.change_count++;

        double x = this.image_num_validation / 2. - (double) foci;
        double direction = Math.signum(x);
        double change_factor = 1. + x * 0.007 * (1. + 6. / this.ana.change_count);

        if (x == 0.) {
            Random r = new Random();
            double rand;
            // ensure a minimum change
            rand = r.nextDouble() * 0.0035 + 0.0015;
            if (r.nextDouble() < 0.5)
                rand *= -1;
            change_factor += rand;
        }
        // if the relative difference is small (e.g. for high numbers of foci), then use the relative and not the absolute difference.
        if (x != 0.) {
            double mean_foci = (this.foci_automatic + foci) / 2.;
            double relative_difference_half = x / mean_foci / 2.;
            if (relative_difference_half * relative_difference_half < (change_factor - 1.) * (change_factor - 1.))
                change_factor = 1. + relative_difference_half;
        }
        p("change_factor: " + change_factor);
        double max_change = 1.25;
        if (change_factor > max_change) change_factor = max_change;
        else if (change_factor < 1. / max_change) change_factor = 1. / max_change;
        this.oep_thresh *= change_factor;

        this.last_threshies[(int) this.ana.change_count % last_num] = this.oep_thresh;
        this.last_foci[(int) this.ana.change_count % last_num] = this.ana.foci[1];
        this.last_directions[(int) this.ana.change_count % last_num] = direction;

        // 	p("STD/Mean (last thresholds): "+round_double(stDev(this.last_threshies)/mean(this.last_threshies), 2));
        p("foci per cell: " + round_double(mean(this.last_foci), 2));
        p("STD/Mean (last foci per cell): " + round_double(stDev(this.last_foci) / mean(this.last_foci), 2));

        // if all are in one direction it should never finish as it is obviously not near the correct threshold.
        boolean different_directions = false;
        for (int i = 1; i < this.last_directions.length; i++)
            if (this.last_directions[0] != this.last_directions[i])
                different_directions = true;
        // we need at least last_num threshies and either the ratio must be close to 1 or we counted 8 times 1 foci during the last 10 counts
        // can take either last_threshies or last_foci. last_threshies converges a bit faster.
        if (different_directions && this.ana.change_count >= last_num && mean(this.last_foci) != 0. && Math.abs(mean(this.last_directions)) <= 2 && stDev(this.last_foci) / mean(this.last_foci) <= 0.05)
            validation_finished_action();
        else
            new_thresh_action();

    } // END check_values


    private void new_thresh_action() {
        this.validate_frame.dispose();
        this.ana.oep_thresh = this.oep_thresh;
        this.ana.oep_hist_panel.threshold_field.setValue(inverse(this.ana.oep_thresh));
        this.ana.oep_hist_panel.threshold_field_log.setValue(this.ana.oep_thresh);
        this.ana.oep_hist_panel.set_x();

        validate_threshold();
    }


    private void validation_finished_action_interval() {
        p("Cells counted: " + String.valueOf((int) this.image_num_validation * this.ana.change_count));

        this.validate_frame.dispose();

        this.oep_thresh = this.threshies_3[1];

        this.ana.oep_thresh = this.oep_thresh;
        this.ana.oep_hist_panel.threshold_field.setValue(inverse(this.ana.oep_thresh));
        this.ana.oep_hist_panel.threshold_field_log.setValue(this.ana.oep_thresh);
        this.ana.oep_hist_panel.set_x();

        this.check_positive_frame = new JFrame();
        this.check_positive_frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // DISPOSE_ON_CLOSE only closes the window, EXIT_ON_CLOSE will exit application, DO_NOTHING_ON_CLOSE does nothing
        // set icon with relative path
        Image img = new ImageIcon(this.getClass().getResource("/images/zelle1.png")).getImage();
        this.check_positive_frame.setIconImage(img);

        JPanel panel = new JPanel();
        panel.setBackground(this.bg);
        GridBagLayout gbl = new GridBagLayout();
        panel.setLayout(gbl);

        GreenJButton button_done = new GreenJButton("Ok");
        button_done.setForeground(new Color(200, 200, 200));
        this.check_positive_frame.getRootPane().setDefaultButton(button_done); // so you can use enter instead of clicking the button
        button_done.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                HistAnalyzer.this.check_positive_frame.dispose();
            }
        });

        GreenJLabel label = new GreenJLabel("<html><p> Threshold validation successful. <br><br>Foci/cell: " + round_double(this.foci_3[1], 3) + " &plusmn; " + round_double(this.uncertainty, 3) + " </p></html>");
        addComp(panel, gbl, label, 0, 0, 1, 1, true, true, 1);
        addComp(panel, gbl, button_done, 0, 1, 1, 1, true, true, 2);

        this.check_positive_frame.add(panel, BorderLayout.CENTER);
        this.check_positive_frame.pack();
        this.check_positive_frame.validate();
        this.check_positive_frame.setLocationRelativeTo(null);
        this.check_positive_frame.setVisible(true);
        button_done.requestFocusInWindow();
    } // END validation_finished_action_interval


    private void validation_finished_action() {
        double[] max_arr = {
            0.,
            0.,
            0.,
            0.,
            0.,
            0.,
            0.,
            0.,
            0.,
            0.
        };
        for (int i = 0; i < this.oep.length; i++) {
            int minPos = getMinPosition(max_arr);
            if (this.oep[i] > max_arr[minPos])
                max_arr[minPos] = this.oep[i];
        }
        double maxi = mean(max_arr);

        double ratio_to_max = (this.ana.oep_thresh - this.median) / (maxi - this.median);

        p("#-----------------------------------#");
        System.out.print("FINAL threshold: ");
        p(mean(this.last_threshies));
        System.out.print("ana.change_count: ");
        p(this.ana.change_count);
        p("Cells counted: " + String.valueOf((int) this.image_num_validation * this.ana.change_count));

        this.validate_frame.dispose();

        this.oep_thresh = mean(this.last_threshies);
        this.ana.oep_thresh = this.oep_thresh;
        this.ana.oep_hist_panel.threshold_field.setValue(inverse(this.ana.oep_thresh));
        this.ana.oep_hist_panel.threshold_field_log.setValue(this.ana.oep_thresh);
        this.ana.oep_hist_panel.set_x();

        this.check_positive_frame = new JFrame();
        this.check_positive_frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // DISPOSE_ON_CLOSE only closes the window, EXIT_ON_CLOSE will exit application, DO_NOTHING_ON_CLOSE does nothing
        // set icon with relative path
        Image img = new ImageIcon(this.getClass().getResource("/images/zelle1.png")).getImage();
        this.check_positive_frame.setIconImage(img);

        JPanel panel = new JPanel();
        panel.setBackground(this.bg);
        GridBagLayout gbl = new GridBagLayout();
        panel.setLayout(gbl);

        GreenJButton button_done = new GreenJButton("Ok");
        button_done.setForeground(new Color(200, 200, 200));
        this.check_positive_frame.getRootPane().setDefaultButton(button_done); // so you can use enter instead of clicking the button
        button_done.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent actionEvent) {
                HistAnalyzer.this.check_positive_frame.dispose();
            }
        });
        double[] foci;
        if (this.skip_cells_with_many_foci)
            foci = count_foci_skip_cells(this.oep_arr, this.oep_thresh, this.max_foci);
        else
            foci = count_foci(this.oep_arr, this.oep_thresh);
        GreenJLabel label = new GreenJLabel("<html><p> Threshold validation successful. <br><br>Foci/cell: " + round_double(foci[1], 3) + " &plusmn; " + round_double(stDev(this.last_foci), 3) + " </p></html>");
        addComp(panel, gbl, label, 0, 0, 1, 1, true, true, 1);
        addComp(panel, gbl, button_done, 0, 1, 1, 1, true, true, 2);

        this.check_positive_frame.add(panel, BorderLayout.CENTER);
        this.check_positive_frame.pack();
        this.check_positive_frame.validate();
        this.check_positive_frame.setLocationRelativeTo(null);
        this.check_positive_frame.setVisible(true);
        button_done.requestFocusInWindow();
    }

    public static int getMinPosition(double[] numbers) {
        int minPosition = 0;
        for (int i = 0; i < numbers.length; i++)
            if (numbers[i] < numbers[minPosition])
                minPosition = i;
        return minPosition;
    }

    public static int getMaxPosition(double[] numbers) {
        int maxPosition = 0;
        for (int i = 0; i < numbers.length; i++)
            if (numbers[i] > numbers[maxPosition])
                maxPosition = i;
        return maxPosition;
    }

    public static double[] smooth_histo(int[] data, int smooth_iterations) {
        //smooth with a 3 point running mean filter
        double[] iHisto = new double[data.length];
        for (int i = 0; i < data.length; i++)
            iHisto[i] = (double) data[i];
        for (int j = 0; j < smooth_iterations; j++) {
            double previous = 0, current = 0, next = iHisto[0]; // original, why start at zero if the histogram starts/ends at other values.
            //             double previous = iHisto[0], current = iHisto[0], next = iHisto[0];
            for (int i = 0; i < data.length - 1; i++) {
                previous = current;
                current = next;
                next = iHisto[i + 1];
                iHisto[i] = (previous + current + next) / 3.;
            }
            iHisto[data.length - 1] = (current + next) / 3.; // original
            //             iHisto[data.length - 1] = (current + 2.*next) / 3.;
        }
        //         int[] data_out = new int[data.length];
        //         for(int i=0;i<data.length;i++)
        //             data_out[i] = (int)Math.round(iHisto[i]);
        //         return data_out;
        return iHisto;
    }


    public static double[] smooth_histo(double[] iHisto, int smooth_iterations) {
        //smooth with a 3 point running mean filter
        for (int j = 0; j < smooth_iterations; j++) {
            //             double previous = 0, current = 0, next = iHisto[0];  // original, why start at zero if the histogram starts/ends at other values.
            double previous = iHisto[0], current = iHisto[0], next = iHisto[0];
            for (int i = 0; i < iHisto.length - 1; i++) {
                previous = current;
                current = next;
                next = iHisto[i + 1];
                iHisto[i] = (previous + current + next) / 3.;
            }
            //             iHisto[data.length - 1] = (current + next) / 3.;  // original
            iHisto[iHisto.length - 1] = (current + 2. * next) / 3.;
        }
        return iHisto;
    }


    public ArrayList < Double > smooth_until_monotonic(ArrayList < Double > arr) {
        //smooth with a 3 point running mean filter
        //         for(int j=0;j<smooth_iterations;j++){
        int iterations = 0;
        ArrayList < Double > arr_out = arr_with_limits(arr);
        if (arr_out.size() <= 2)
            return arr_out;
        while (!monotonic(arr_out) || iterations < 3) {
            //             double previous = 0, current = 0, next = arr_out[0];  // original, why start at zero if the histogram starts/ends at other values.
            double previous = arr_out.get(0), current = arr_out.get(0), next = arr_out.get(0);
            for (int i = 0; i < arr_out.size() - 1; i++) {
                previous = current;
                current = next;
                next = arr_out.get(i + 1);
                arr_out.set(i, (previous + current + next) / 3.);
            }
            //             arr[data.size() - 1] = (current + next) / 3.;  // original
            arr_out.set(arr_out.size() - 1, (current + 2. * next) / 3.);
            iterations++;
            if (iterations > 100) break;
        }
        return arr_out;
    } // END smooth_until_monotonic


    public ArrayList < Double > arr_with_limits(ArrayList < Double > arr) {
        ArrayList < Double > arr_out = new ArrayList < > (arr);
        // minimum range [-2, 2]
        double positive_max = 2, negative_max = -2;
        for (double a: arr_out)
            if (a > positive_max)
                positive_max = a;
            else if (a < negative_max)
            negative_max = a;
        // take the min of both maxima
        if (-negative_max < positive_max)
            positive_max = -negative_max;
        p("positive_max: " + positive_max);
        for (int i = 0; i < arr_out.size(); i++)
            if (arr_out.get(i) > positive_max)
                arr_out.set(i, positive_max);
            else if (arr_out.get(i) < -positive_max)
            arr_out.set(i, -positive_max);
        return arr_out;
    } // END arr_with_limits


    public boolean monotonic(ArrayList < Double > arr) {
        if (arr.size() <= 2)
            return true;
        boolean goes_up = true;
        if (arr.get(1) < arr.get(0))
            goes_up = false;
        for (int i = 0; i < arr.size() - 1; i++)
            if (goes_up && arr.get(i + 1) < arr.get(i))
                return false;
            else if (!goes_up && arr.get(i + 1) > arr.get(i))
            return false;
        return true;
    }

    public double point_of_intersection(double x1, double x2, double y1, double y2, double y_intersect) {
        return (x2 - x1) / (y2 - y1) * (y_intersect - y1) + x1;
    }


    public int positive_values(ArrayList < Double > arr) {
        int num = 0;
        for (double a: arr)
            if (a > 0)
                num++;
        return num;
    }


    public int negative_values(ArrayList < Double > arr) {
        int num = 0;
        for (double a: arr)
            if (a < 0)
                num++;
        return num;
    }


    public boolean name_in_list(ArrayList < String > list, String name) {
        for (String s: list)
            if (s.equals(name))
                return true;
        return false;
    }


    public ArrayList < String > apply_max_objects_per_cell_to_image_names(double[] oep, double[] cell, int max_objects_per_cell, ArrayList < String > image_names) {
        ArrayList < String > image_names_out = new ArrayList < > ();
        int cell_tmp = 1;
        int cell_objects = 0;
        for (int i = 0; i < oep.length; i++) {
            if (cell[i] == cell_tmp) { //  && oep[i] != 1.23456789
                image_names_out.add(image_names.get(i));
                cell_objects++;
            }
            // no "else if" here because of the second condition!
            if (cell[i] == cell_tmp + 1 || cell_objects > max_objects_per_cell) {
                cell_tmp++;
                cell_objects = 0;
            } else if (cell[i] > cell_tmp + 1) {
                cell_tmp += (int)(cell[i] - cell_tmp - 1); // pruefen ob noetig!!!
                cell_objects = 0;
            }
        }
        return image_names_out;
    }

    public double[][] apply_max_objects_per_cell(double[] oep, double[] cell, int max_objects_per_cell) {
        //     public ArrayList<ArrayList< Double > > apply_max_objects_per_cell(double[] oep, double[] cell, int max_objects_per_cell){
        ArrayList < ArrayList < Double > > oep_cell_out = new ArrayList < > ();
        oep_cell_out.add(new ArrayList < > ());
        oep_cell_out.add(new ArrayList < > ());
        int cell_tmp = 1;
        int cell_objects = 0;
        for (int i = 0; i < oep.length; i++) {
            if (cell[i] == cell_tmp) { //  && oep[i] != 1.23456789
                oep_cell_out.get(0).add(oep[i]);
                oep_cell_out.get(1).add(cell[i]);
                cell_objects++;
            }
            // no "else if" here because of the second condition!
            if (cell[i] == cell_tmp + 1 || cell_objects > max_objects_per_cell) {
                cell_tmp++;
                cell_objects = 0;
            } else if (cell[i] > cell_tmp + 1) {
                cell_tmp += (int)(cell[i] - cell_tmp - 1); // pruefen ob noetig!!!
                cell_objects = 0;
            }
        }
        double[][] oep_cell_out_arr = new double[2][oep_cell_out.get(0).size()];
        for (int i = 0; i < oep_cell_out.size(); i++) {
            for (int j = 0; j < oep_cell_out.get(0).size(); j++) {
                oep_cell_out_arr[i][j] = oep_cell_out.get(i).get(j);
            }
        }
        return oep_cell_out_arr;
        //         return oep_cell_out;
    }

    // minimum = place where the standard deviation is maximal, because the distribution is flat
    public double hist_minimum_stdev(double[] arr, double upper_limit, int stdev_of_num) {
        // for example a single cell with no big objects
        if (arr.length <= 1) return 0.;
        double minimum = get_minimum(arr);
        if (upper_limit < minimum) return 0.;
        double max_thresh = minimum + 0.6 * (upper_limit - minimum);
        ArrayList < Double > x_out = new ArrayList < Double > ();
        ArrayList < Double > y_out = new ArrayList < Double > ();
        Arrays.sort(arr);
        for (int i = 0; i < arr.length - stdev_of_num; i++) {
            if (arr[i] < max_thresh) { // arr[i] > min_thresh && , does not need min_thresh since it starts at the first object value. 
                double[] slice = Arrays.copyOfRange(arr, i, i + stdev_of_num);
                x_out.add(mean(slice));
                y_out.add(stDev(slice));
            }
        }
        if (y_out.size() == 0) return 0.;
        return x_out.get(get_max_index(y_out));
    }


    public double hist_minimum_range(double[] arr, double upper_limit, double half_range) {
        // for example a single cell with no big objects
        if (arr.length <= 1) return 0.;
        double minimum = get_minimum(arr);
        if (upper_limit < minimum) return 0.;
        int divisions = 60;
        double max_thresh = minimum + 0.6 * (upper_limit - minimum);

        double min_thresh = minimum + 2. * half_range;
        double min_counts = 1.e9;
        double min_position = 0;
        double[] linspace = new double[divisions];

        // in case the distribution is smaller than the given half_range
        int iterations = 0;
        while (max_thresh < min_thresh + 7. * half_range) {
            half_range *= 0.5;
            min_thresh = minimum + 2. * half_range;
            if (iterations > 100) break;
            iterations++;
        }
        double interval = (max_thresh - half_range - min_thresh) / (divisions - 1);

        for (int i = 0; i < linspace.length; i++) linspace[i] = min_thresh + interval * i;
        for (int i = 0; i < linspace.length; i++) {
            int tmp_counts = 0;
            for (int j = 0; j < arr.length; j++)
                if (arr[j] > linspace[i] - half_range && arr[j] < linspace[i] + half_range) tmp_counts += 1;
            if (tmp_counts < min_counts) {
                min_counts = tmp_counts;
                min_position = linspace[i];
            }
        }

        return min_position;
    }

    // def hist_minimum_range(arr):
    //     
    //     for x_i in x:
    //         tmp_counts = 0
    //         for arr_i in arr:
    //             if arr_i > x_i-rangala and arr_i < x_i+rangala:
    //                 tmp_counts += 1
    //         if tmp_counts < min_counts:
    //             min_counts = tmp_counts
    //             min_position = x_i

    public static double round_double(double x, int digits) { // ueberladen
        double rounda = Math.pow(10, digits);
        return (double) Math.round(x * rounda) / rounda;
    }


    public static double mean(double[] arr) {
        double sum = 0.;
        for (int i = 0; i < arr.length; i++)
            sum += arr[i];
        if (arr.length < 1)
            return 0.;
        return sum / arr.length;
    }

    public static double mean(ArrayList < Double > arr) {
        double sum = 0.;
        for (int i = 0; i < arr.size(); i++)
            sum += arr.get(i);
        if (arr.size() < 1)
            return 0.;
        return sum / arr.size();
    }

    public static double mean(Set < Double > set) {
        ArrayList < Double > arr = new ArrayList < Double > (set);
        double sum = 0.;
        for (int i = 0; i < arr.size(); i++)
            sum += arr.get(i);
        if (arr.size() < 1)
            return 0.;
        return sum / arr.size();
    }

    public static double mean_int_list(ArrayList < Integer > arr) {
        double sum = 0.;
        for (int i = 0; i < arr.size(); i++)
            sum += arr.get(i);
        if (arr.size() < 1)
            return 0.;
        return sum / arr.size();
    }

    public static double stDev(double[] arr) {
        int n = arr.length;
        double mean = mean(arr);
        double variance = 0.;
        for (int i = 0; i < n; i++) {
            variance += (arr[i] - mean) * (arr[i] - mean) / (n - 1);
        }
        if (n > 1)
            return Math.sqrt(variance);
        return 0;
    }



    public static double stDev(ArrayList < Double > arr) {
        int n = arr.size();
        double mean = mean(arr);
        double variance = 0.;
        for (int i = 0; i < n; i++) {
            variance += (arr.get(i) - mean) * (arr.get(i) - mean) / (n - 1);
        }
        if (n > 1)
            return Math.sqrt(variance);
        return 0;
    }


    public ArrayList < Double[] > read_table_from_file(String fileName) {
        //             String line = "";
        ArrayList < Double[] > data = new ArrayList < Double[] > ();
        try {
            List < String > lines = Files.readAllLines(Paths.get(fileName)); // , StandardCharsets.UTF_8
            for (int i=0; i<lines.size(); i++) {
                if (i < 2) continue;
                // p(lines.get(i));
                String[] lineSplit = lines.get(i).split("\t");
                Double[] lineSplitDouble = new Double[lineSplit.length - 1];
                for (int bb = 1; bb < lineSplit.length; bb++) lineSplitDouble[bb - 1] = Double.parseDouble(lineSplit[bb]);
                data.add(lineSplitDouble);
            }
        } catch (Exception e) {
            // we don't want an error message, but rather to skip those files.
            // 		      error_message(e);
        }
        return data;
    } // END read_table_from_file


    public ArrayList < String > read_imageNames(String fileName) {
        //         String line = "";
        ArrayList < String > imageNames = new ArrayList < String > ();
        try {
            List < String > lines = Files.readAllLines(Paths.get(fileName)); // , StandardCharsets.UTF_8
            for (int i = 0; i < lines.size(); i++) {
                if (i < 2) continue;
                String[] lineSplit = lines.get(i).split("\t");
                imageNames.add(lineSplit[0]);
            }
        } catch (Exception e) {
            error_message(e);
        }
        return imageNames;
    } // END read_imageNames


    public int read_excluded_cells(String fileName) {
        String line = "";
        int excluded_cells = 0;
        try {
            FileReader fr = new FileReader(fileName);
            BufferedReader br = new BufferedReader(fr); //Can also use a Scanner to read the file
            line = br.readLine();
            String[] lineSplit = line.split(" ");
            excluded_cells = Integer.parseInt(lineSplit[0]);
            p("excluded cells during object detection (not because of max foci option): " + excluded_cells);
        } catch (Exception e) {
            // we don't want an error message, but rather to skip those files.
            //               error_message(e);
        }
        return excluded_cells;
    } // END read_excluded_cells


    public int get_max_index(ArrayList < Double > list) {
        if (list.size() == 0) return 0;
        int max_index = 0;
        double max = list.get(max_index);
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i) > max) {
                max = list.get(i);
                max_index = i;
            }
        }
        return max_index;
    } // END get_max_index


    public int get_max_index(double[] arr) {
        if (arr.length == 0)
            return 0;
        int max_index = 0;
        double max = arr[max_index];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > max) {
                max = arr[i];
                max_index = i;
            }
        }
        return max_index;
    } // END get_max_index


    public int get_min_index(double[] arr) {
        if (arr.length == 0)
            return 0;
        int min_index = 0;
        double min = arr[min_index];
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > min) {
                min = arr[i];
                min_index = i;
            }
        }
        return min_index;
    } // END get_max_index


    public double get_minimum(double[] arr) {
        if (arr.length == 0)
            return 0.;
        double min = arr[0];
        for (double a: arr) {
            if (a < min) {
                min = a;
            }
        }
        return min;
    } // END get_minimum


    public void write_output(ArrayList < MultiType > table, String filename) {
        PrintStream ps;
        try {
            File f = new File(filename);
            ps = new PrintStream(new FileOutputStream(filename));
            ArrayList < Double > oep_thresh_array = new ArrayList < Double > ();
            for (int i = 0; i < table.size(); i++) {
                MultiType row = table.get(i);
                oep_thresh_array.add(row.oep_thresh);
            }
            String mean = String.valueOf(round_double(mean(oep_thresh_array), 6));
            String stderr = String.valueOf(round_double(stDev(oep_thresh_array) / Math.sqrt((double) oep_thresh_array.size()), 6));
            ps.println("OEP threshold mean and standard error: \t" + mean + " (" + stderr + ")");
            ps.println("name\tthreshold\tcells\tfoci\tfoci/cell\tpoisson_kldiv\tpoisson_lsquares");
            for (int i = 0; i < table.size(); i++) {
                MultiType row = table.get(i);
                ps.println(row.name + "\t" + String.valueOf(row.oep_thresh) + "\t" + String.valueOf(row.cells) + "\t" + String.valueOf(row.foci) + "\t" + String.valueOf(row.foci_cell) + "\t" + String.valueOf(row.kl_divergence) + "\t" + String.valueOf(row.r_squares));
            }
            ps.close();
        } catch (Exception e) {
            error_message(e);
        }
    } // END write_output


    public int count_files_in_dir(File dir) {
        int count = 0;
        for (File file: dir.listFiles()) {
            if (file.isFile() && file.getPath().toLowerCase().endsWith(".csv")) {
                count++;
            }
        }
        return count;
    } // END count_files_in_dir  

    public static String convert_millis_to_time_string(long milliseconds) {
        long seconds = milliseconds / 1000 % 60;
        long minutes = (milliseconds / (1000 * 60)) % 60;
        long hours = milliseconds / (1000 * 60 * 60); //  % 24
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    } // END convert_millis_to_time_string
    //-------------------------------  NOT USED -------------------------------//


    // not used, better to stick to arrays, since i need the original anyway
    public static double[] convertDoubles(ArrayList < Double > doubles) {
        double[] target = new double[doubles.size()];
        for (int i = 0; i < target.length; i++) {
            // 	      target[i] = doubles.get(i).doubleValue();  // java 1.4 style
            // or:
            target[i] = doubles.get(i); // java 1.5+ style (outboxing)
        }
        return target;
    }

    // not used, it is faster to use ArrayList and convertDoubles after
    static double[] addElement(double[] a, double e) {
        a = Arrays.copyOf(a, a.length + 1);
        a[a.length - 1] = e;
        return a;
    }

    static int[] addElement(int[] a, int e) {
        a = Arrays.copyOf(a, a.length + 1);
        a[a.length - 1] = e;
        return a;
    }

    // to calculate the difference between theoretical and experimental poisson distribution
    private double kullback_leibler_divergence(double[] p, double[] q) {
        double sum = 0.;
        for (int i = 0; i < p.length; i++) {
            if (p[i] != 0. && q[i] != 0.)
                // 			if(p[i] > 0.01 && q[i] != 0.)
                sum += p[i] * Math.log(p[i] / q[i]);
        }
        return sum;
    }

    private double residual_squares(double[] p, double[] q) {
        double sum = 0.;
        for (int i = 0; i < p.length; i++) {
            if (p[i] != 0.)
                sum += Math.pow(p[i] - q[i], 2);
        }
        return sum;
    }

    private double chi_squares(double[] p, double[] q) {
        double sum = 0.;
        for (int i = 0; i < p.length; i++) {
            if (p[i] != 0. && q[i] != 0.)
                sum += Math.pow(p[i] - q[i], 2) / q[i];
        }
        return sum;
    }

    public static double poisson(int k, double lambda_double) {
        // need BigDecimal here, because else factorial can get negative
        BigDecimal fact = factorial(k);
        BigDecimal lambda = BigDecimal.valueOf(lambda_double);

        double value = lambda.pow(k).divide(fact, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(Math.exp(-lambda_double))).doubleValue();
        // 		double value = Math.pow(lambda_double, k) / factorial_2(k) * Math.exp(-lambda_double);
        // 		if(value <= 0)
        // 			return 0;
        return value;
    }

    private static BigDecimal factorial(long n) {
        BigDecimal result = BigDecimal.ONE;
        for (long i = 2; i <= n; i++)
            result = result.multiply(BigDecimal.valueOf(i));
        return result;
    }

    public static long factorial_2(int N) {
        long multi = 1;
        for (int i = 1; i <= N; i++) {
            multi = multi * i;
        }
        return multi;
    }


    public double PearsonCorrelation(int array1[], int array2[]) {
        int length = array1.length;
        if (length != array2.length)
            return Double.NaN;
        double r;
        double scores1[] = new double[length];
        double scores2[] = new double[length];

        for (int l = 0; l < length; l++) {
            scores1[l] = array1[l];
            scores2[l] = array2[l];
        }

        double sum_sq_x = 0;
        double sum_sq_y = 0;
        double sum_coproduct = 0;
        double mean_x = scores1[0];
        double mean_y = scores2[0];
        for (int i = 2; i < scores1.length + 1; i += 1) {
            double sweep = Double.valueOf(i - 1) / i;
            double delta_x = scores1[i - 1] - mean_x;
            double delta_y = scores2[i - 1] - mean_y;
            sum_sq_x += delta_x * delta_x * sweep;
            sum_sq_y += delta_y * delta_y * sweep;
            sum_coproduct += delta_x * delta_y * sweep;
            mean_x += delta_x / i;
            mean_y += delta_y / i;
        }
        double pop_sd_x = (double) Math.sqrt(sum_sq_x / scores1.length);
        double pop_sd_y = (double) Math.sqrt(sum_sq_y / scores1.length);
        double cov_x_y = sum_coproduct / scores1.length;
        r = cov_x_y / (pop_sd_x * pop_sd_y);
        p("Pearson coefficient: " + r);
        return r;
    }

    public double PearsonCorrelation2(int[] arr_1, int[] arr_2) {
        int length = arr_1.length;
        if (length != arr_2.length) return Double.NaN;
        double sum_squared_1 = 0., sum_squared_2 = 0., sum_squared_1_2 = 0., mean_1 = 0., mean_2 = 0.;
        // calculate the mean
        for (int i = 0; i < length; i++) {
            mean_1 += arr_1[i];
            mean_2 += arr_2[i];
        }
        mean_1 /= length;
        mean_2 /= length;
        // calculate the correlation coefficient
        for (int i = 0; i < length; i++) {
            sum_squared_1 += Math.pow(arr_1[i] - mean_1, 2);
            sum_squared_2 += Math.pow(arr_2[i] - mean_2, 2);
            sum_squared_1_2 += (arr_1[i] - mean_1) * (arr_2[i] - mean_2);
        }
        double p = sum_squared_1_2 / (Math.sqrt(sum_squared_1 * sum_squared_2));
        p("Pearson coefficient: " + p);
        return p;
    }

    public void p(String str) {
        System.out.println(str);
    }

    public void p(boolean str) {
        System.out.println(str);
    }

    public void p(int str) {
        System.out.println(str);
    }

    public void p(double str) {
        System.out.println(str);
    }


    private void error_message(Exception e) {
        StringWriter errors = new StringWriter();
        e.printStackTrace(new PrintWriter(errors));
        GreenJTextArea ta = new GreenJTextArea("Something went wrong. Please check all file paths and that (if any used) the .csv files are result files (not foci tables) created by AutoFoci. \n\nError message:\n\n " + errors, 15, 50);
        ta.setWrapStyleWord(true);
        ta.setLineWrap(true);
        ta.setCaretPosition(0);
        ta.setEditable(false);
        JOptionPane.showMessageDialog(null, new JScrollPane(ta), "RESULT", JOptionPane.INFORMATION_MESSAGE);
    }

    static void addComp(Container cont, GridBagLayout gbl, Component c, int x, int y, int width, int height, boolean fill_both, boolean button, int padding) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = 1.;
        gbc.weighty = 1.;
        if (button) {
            gbc.ipadx = 15;
            gbc.ipady = 15;
        }
        if (fill_both)
            gbc.fill = GridBagConstraints.BOTH;
        else
            gbc.fill = GridBagConstraints.NONE;
        if (padding == 1)
            gbc.insets = new Insets(10, 10, 10, 10);
        else if (padding == 2)
            gbc.insets = new Insets(15, 60, 15, 60);
        else if (padding == 3) {
            gbc.insets = new Insets(20, 20, 20, 20);
            gbc.ipadx = 20;
            gbc.ipady = 20;
        } else if (padding == 4)
            gbc.insets = new Insets(40, 0, 40, 0);
        else if (padding == 5)
            gbc.insets = new Insets(0, 40, 0, 0);
        else
            gbc.insets = new Insets(0, 0, 0, 0);
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.gridwidth = width;
        gbc.gridheight = height;
        gbl.setConstraints(c, gbc);
        cont.add(c);
    }

    public double f(double x) {
        return Math.log(x);
    }

    public double f_back(double x) {
        return Math.exp(x);
    }

    public double inverse(double x) {
        if (x <= 0.) return 1.;
        return 1. / x;
    }

    void print(Object obj) {
        System.out.println(obj);
    }
}