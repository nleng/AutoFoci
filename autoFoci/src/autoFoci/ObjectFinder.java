/**

edgeThreshold gar nicht benutzt gerade!?!?!?!?!

ich sollte PrintStream ersetzen durch:
Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true), "UTF-8"));
writer.close();
siehe:
http://stackoverflow.com/questions/4614227/how-to-add-a-new-line-of-text-to-an-existing-file-in-java

right now minThresh = 0 ! but this only affects the search for maxima, not the area. 
area is influenced by edgeThreshold, eThresh, and cellMean.

autoFoci implements an automatic foci counting method, which is applicable for large numbers of nuclei images.
author: Nicor Lengert (nicorlengert@gmx.de)

1. the image is processed by use of top-hat transformation to filter foci (< structuring element) from background.
2. via threshold (red and green channel), which depends on the total cell intensity of the individual cell, seeds are setup.
3. these seeds are used for a region growing algorithm. its boundaries are defined by an edge threshold (only red channel)

reasons for an object not to be identified:
green topPixels < cellMean2
area < minArea

**/

package autoFoci;

import autoFoci.ResultType;
import autoFoci.ProgressFrame;
import autoFoci.MainFrame;
import autoFoci.HistAnalyzer;
import autoFoci.AutoThreshold;

import java.io.*;
import java.nio.file.Files;
import java.util.BitSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.ListIterator;
import java.util.LinkedHashSet;
import java.util.Date;
import java.util.Collections;
import java.util.Comparator;
import java.text.SimpleDateFormat;
import java.awt.Color;
import java.awt.Font;
import java.awt.BorderLayout;
import java.awt.Container;

import autoFoci.GreenGUI.GreenJTextArea;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;

import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.plugin.FolderOpener;
import ij.plugin.filter.RankFilters;
import ij.plugin.ImageCalculator;
import ij.plugin.RGBStackMerge;
import ij.plugin.Zoom;
import ij.gui.Toolbar;
import ij.gui.ImageWindow;
import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.Blitter;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.border.Border;



public class ObjectFinder {

    static final boolean use_tophat_for_search = false;
    
    static final int cell_thresh = 3; // threshold for dapi channel

    static final String freak_identifier = "00freak";

    int local_hist_length = 30;
    double local_hist_max = 20.;

    BitSet isObj, isObj_saturated;

    boolean minimum_output, rename_freaks;
    int width, height, cell_area, minThresh_above_cell_mean, added_counter, curr_min_pos, area = 0, visitedCounter = 0, gCounter, cellFoci, file_in_dir_counter = 0, cellNumber = 1, master_channel, second_channel, dapi_channel, maxCH1, maxCH2, maxCH1Transform, maxCH2Transform, minSeparation, minArea, total_cell_num, total_cell_num_analyzed = 0, total_files_in_dir = 0, max1X, max1Y, max2X, max2Y, objectCounter, maxArea_radius, freak_threshold, freak_low_threshold, freak_stdev_threshold, freak_counter_low = 0, freak_counter_high = 0, freak_counter_stdev = 0;
    int[] maxPixels1, maxPixels2, maxPixels1Transform, maxPixels2Transform, thresholds_satisfied_arr_1, thresholds_satisfied_arr_2, thresholds_satisfied_arr_all;
    double minThresh, cell_max1, cell_max2, foci, oep_thresh, oep, stDev_max1, stDev_max2, radius_structuring_element, edgeThreshold, maxDiff, topPixels1, topPixels2, topPixels1Transform, topPixels2Transform, cell_sum1, cell_sum2, cell_sum_squares1, cell_sum_squares2, cellMean1, cellMean2, cellMean1_gradient, cellMean2_gradient, BGlocalSumCH1, BGlocalSumCH2, BGlocalCH1, BGlocalCH2, meanSum1, meanSum2, mean1, mean2, fociPerCell, fociPerCellAME, normDiff, treshForAll, ameTresh, areaBig = 45., stDev_cell1, stDev_cell2, pearson_correlation, pearson_correlation_object, moment_of_inertia_1, moment_of_inertia_2;
    double[] stDev_max_pixels_1, stDev_max_pixels_2;
    double[] oep_arr_1, oep_arr_2, oep_arr_multi;

    String root_path, path, save_path, result_dir, takeString, image_name, extension, dir_name;
    File image_file;

    ArrayList < LocalMax > localMax;
    ArrayList < ResultType > result_table;
    ArrayList < int[] > foci_xy, next_pixels;
    ArrayList < Integer > remove_max;
    ArrayList < LocalMax > add_max;
    ArrayList < Double > oep_arr_all_1, oep_arr_all_2, threshold_arr_1, threshold_arr_2, threshold_arr_all, oep_arr_multi_all;

    int[][] reserved_for;

    float[] kernel = {-2,-4,-4,-4,-2,
                    -4,0,10,0,-4,
                    -4,10,32,10,-4,
                    -4,0,10,0,-4,
                    -2,-4,-4,-4,-2};

    ImageProcessor ip1, ip2, ip1original, ip2original, ip3original, ip1_variance, ip2_variance, ipp, ipp_original, ip1_convolution, ip2_convolution, oep_1, oep_2, oep_gray, ip1_duplicate, ip2_duplicate;

    ImageStack stack_overlay;

    final MainFrame main_frame;
    ProgressFrame progress_gui = new ProgressFrame();


    public ObjectFinder(MainFrame main_frame_tmp, String root_path_tmp, String result_dir_tmp, String extension_tmp, int master_channel_tmp, int second_channel_tmp, int dapi_channel_tmp, int freak_threshold_tmp, int freak_low_threshold_tmp, int freak_stdev_threshold_tmp, double radius_structuring_element_tmp, double edgeThreshold_tmp, int minArea_tmp, int minSeparation_tmp, int minThresh_above_cell_mean_tmp, double oep_thresh, boolean rename_freaks_tmp) {

        this.main_frame = main_frame_tmp;
        this.root_path = root_path_tmp;
        this.result_dir = result_dir_tmp;
        this.extension = extension_tmp;

        this.master_channel = master_channel_tmp; // red is 0 not 1 ! but the -1 is done in MainFrame.java
        this.second_channel = second_channel_tmp;
        this.dapi_channel = dapi_channel_tmp;
        this.freak_threshold = freak_threshold_tmp;
        this.freak_low_threshold = freak_low_threshold_tmp;
        this.freak_stdev_threshold = freak_stdev_threshold_tmp;
        this.radius_structuring_element = radius_structuring_element_tmp;
        this.edgeThreshold = edgeThreshold_tmp;
        this.minArea = minArea_tmp;
        if (this.minArea < 1) this.minArea = 1;
        this.maxArea_radius = 2 * minSeparation_tmp;
        this.minSeparation = minSeparation_tmp;
        this.minThresh_above_cell_mean = minThresh_above_cell_mean_tmp;
        this.oep_thresh = oep_thresh;
        this.rename_freaks = rename_freaks_tmp;
    } // END set_params


    public boolean run() {
        // first open the progress frame, because counting all images could take a while...
        this.progress_gui.create_frame(this.main_frame);

        File root_dir = new File(this.root_path);
        this.total_cell_num = count_files_in_subdirs(root_dir);
        if (this.total_cell_num == 0) {
            this.progress_gui.dispose();
            return false;
        }
        // log total number of images
        this.progress_gui.log("Total number of images: \t" + String.valueOf(this.total_cell_num));
        this.progress_gui.time_start = System.currentTimeMillis();

        // creates result_files_dir and also parent directory if not exisits
        new File(this.root_path, this.result_dir).mkdirs();


        File[] dirList = root_dir.listFiles();
        Arrays.sort(dirList);
        for (File dir: dirList) {
            this.dir_name = dir.getName();
            // skip the result_tables directory
            if (this.dir_name.contentEquals(this.result_dir)) // .contains()
                continue;
            if (dir.isDirectory() && !this.dir_name.contains(this.freak_identifier)) {
                analyze_folder(dir);
            }
        }

        // also analyze the root_folder (if there are no subdirectories)
        File dir = new File(this.root_path);
        this.dir_name = dir.getName();
        analyze_folder(dir);

        this.progress_gui.time_label.setText("Estimated duration: " + convert_millis_to_time_string(0));
        this.progress_gui.change(this.progress_gui.progressBar_total, 100);
        this.progress_gui.change(this.progress_gui.progressBar, 100);
        this.progress_gui.log("DONE!");
        this.progress_gui.log("------------------------------");
        this.progress_gui.log("Total duration: " + convert_millis_to_time_string(System.currentTimeMillis() - this.progress_gui.time_start));
        this.progress_gui.log("Images per minute: " + Double.toString(roundIt((double)(this.total_cell_num) / (System.currentTimeMillis() - this.progress_gui.time_start) * 60. * 1000., 0)));
        this.progress_gui.log("Duration for 5000 images: " + convert_millis_to_time_string((long)(5000. / this.total_cell_num * (System.currentTimeMillis() - this.progress_gui.time_start))));

        return true;
    } // END run


    // ??? hier ging es von int i=1 los, warum hatte ich das?
    public static int getMinPosition(int[] numbers) {
        int minPosition = 0;
        for (int i = 0; i < numbers.length; i++) {
            if (numbers[i] < numbers[minPosition]) {
                minPosition = i;
            }
        }
        return minPosition;
    } // END getMinPosition


    public static int getMinPosition(double[] numbers) {
        int minPosition = 0;
        for (int i = 0; i < numbers.length; i++) {
            if (numbers[i] < numbers[minPosition]) {
                minPosition = i;
            }
        }
        return minPosition;
    } // END getMinPosition


    public static int getMinPosition(ArrayList < Integer > numbers) {
        int minPosition = 0;
        for (int i = 0; i < numbers.size(); i++) {
            if (numbers.get(i) < numbers.get(minPosition)) {
                minPosition = i;
            }
        }
        return minPosition;
    } // END getMinPosition


    public static double getMean(int[] numbers) {
        double sum = 0;
        for (int i = 0; i < numbers.length; i++) {
            sum += numbers[i];
        }
        return sum / numbers.length;
    } // END getMean


    public static double getMean(double[] numbers) {
        double sum = 0;
        for (int i = 0; i < numbers.length; i++) {
            sum += numbers[i];
        }
        return sum / numbers.length;
    } // END getMean


    public static double getMean_without0(int[] numbers) {
        double length_without0 = 0.;
        double sum = 0.;
        for (int i = 0; i < numbers.length; i++) {
            if (numbers[i] != 0.) {
                sum += numbers[i];
                length_without0++;
            }
        }
        if (length_without0 == 0)
            return 0.;
        return sum / length_without0;
    } // END getMean_without0


    public static double getMean_without0(double[] numbers) {
        double length_without0 = 0.;
        double sum = 0.;
        for (int i = 0; i < numbers.length; i++) {
            if (numbers[i] != 0.) {
                sum += numbers[i];
                length_without0++;
            }
        }
        if (length_without0 == 0)
            return 0.;
        return sum / length_without0;
    } // END getMean_without0


    // checks if the pixel is local max inside minSeparation radius
    public boolean newRegionMax(ImageProcessor ip, int v, int u, BitSet isMax) {
        if (ip.get(v, u) <= this.minThresh) return false;
        for (int i = v - this.minSeparation; i <= v + this.minSeparation; i++) {
            for (int j = u - this.minSeparation; j <= u + this.minSeparation; j++) {
                if (i < 0) continue;
                if (j < 0) continue;
                if (i > this.width - 1) continue;
                if (j > this.height - 1) continue;
                if (i == v && j == u) continue;
                if ((i - v) * (i - v) + (j - u) * (j - u) >= this.minSeparation * this.minSeparation) continue;
                // first condition: if there is another pixel higher inside minSeparation radius. second condition: if there is already a max inside this radius
                if (ip.get(i, j) > ip.get(v, u) || isMax.get(i * height + j)) return false;
            }
        }
        return true;
    } // END regionMax


    public boolean regionMax(ImageProcessor ip, int v, int u) {
        if (ip.get(v, u) <= this.minThresh) return false;
        for (int i = v - this.minSeparation; i <= v + this.minSeparation; i++) {
            for (int j = u - this.minSeparation; j <= u + this.minSeparation; j++) {
                if (i < 0) continue;
                if (j < 0) continue;
                if (i > this.width - 1) continue;
                if (j > this.height - 1) continue;
                if (i == v && j == u) continue;
                if ((i - v) * (i - v) + (j - u) * (j - u) < this.minSeparation * this.minSeparation)
                    // first condition: if there is another pixel higher inside minSeparation radius. second condition: if there is already a max inside this radius
                    if (ip.get(i, j) > ip.get(v, u)) return false;
            }
        }
        return true;
    } // END regionMax

    public int count_files_in_subdirs_silent(File dir) {
        // 	int count = 0;
        int count = count_files_in_dir(dir);
        for (File file: dir.listFiles()) {
            if (file.getName().contentEquals(this.result_dir)) // .contains()
                continue;
            if (file.isDirectory()) {
                count += count_files_in_subdirs(file);
            }
        }
        return count;
    } // END count_files_in_subdirs_silent

    public int count_files_in_subdirs(File dir) {
        // 	int count = 0;
        int count = count_files_in_dir(dir);
        for (File file: dir.listFiles()) {
            if (file.getName().contentEquals(this.result_dir)) // .contains()
                continue;
            if (file.isDirectory()) {
                // could just take count_files_in_subdirs(file), but the number should be printed in the main loop
                count += count_files_in_subdirs_silent(file);
            }
            this.progress_gui.time_label.setText("Counting total number of images: " + String.valueOf(count));
        }
        return count;
    } // END count_files_in_subdirs

    public int count_files_in_dir(File dir) {
        int count = 0;
        for (File file: dir.listFiles()) {
            if (check_isFile_and_extension(file)) {
                count++;
            }
        }
        return count;
    } // END count_files_in_dir

    public boolean check_isFile_and_extension(File file) {
        return file.isFile() && file.getAbsolutePath().toLowerCase().endsWith(this.extension);
    } // END check_isFile_and_extension


    public void analyze_folder(File dir) {
        // 	    this.path = dir.getAbsolutePath();
        this.total_files_in_dir = count_files_in_dir(dir); // dir.listFiles().length; only valid when all files are images
        if (this.total_files_in_dir == 0)
            return;
        this.progress_gui.log(this.dir_name + "\t" + String.valueOf(this.total_files_in_dir) + " images");
        this.result_table = new ArrayList < > ();

        File[] imageList = dir.listFiles();
        Arrays.sort(imageList);
        this.objectCounter = 0;
        this.file_in_dir_counter = 0;
        this.cellNumber = 1;
        this.freak_counter_low = 0;
        this.freak_counter_high = 0;
        this.freak_counter_stdev = 0;
        for (File file: imageList) {
            //             print(file);
            if (check_isFile_and_extension(file)) {
                try {
                    this.image_file = file;
                    this.image_name = file.getName();

                    ImagePlus imp = new ImagePlus(file.getAbsolutePath());
                    if (imp.getType() != ImagePlus.COLOR_RGB) continue; // non-RGB-bilder are ignored

                    // to manipulate the original image
                    this.ipp = imp.getProcessor();

                    ImageStack[] channels = splitRGB(imp.getStack(), true);
                    ImagePlus originalCH1 = new ImagePlus("originalCH1", channels[this.master_channel]);
                    ImagePlus originalCH2 = new ImagePlus("originalCH2", channels[this.second_channel]);
                    ImagePlus originalCH3 = new ImagePlus("originalCH3", channels[this.dapi_channel]);

                    this.ip1original = originalCH1.getProcessor();
                    this.ip2original = originalCH2.getProcessor();
                    this.ip3original = originalCH3.getProcessor();


                    this.width = ip1original.getWidth();
                    this.height = ip1original.getHeight();
                    // 			  this.thisCellNumber = Integer.parseInt(imp.getShortTitle());

                    ImagePlus TopHatTransformCH1 = originalCH1.duplicate();
                    ImagePlus TopHatTransformCH2 = originalCH2.duplicate();

                    this.ip1 = TopHatTransformCH1.getProcessor();
                    this.ip2 = TopHatTransformCH2.getProcessor();

                    ImageCalculator ic = new ImageCalculator();
                    morphological_opening(this.ip1, this.radius_structuring_element, "red");
                    TopHatTransformCH1 = ic.run("Subtract create", originalCH1, TopHatTransformCH1);

                    morphological_opening(this.ip2, this.radius_structuring_element, "green");
                    TopHatTransformCH2 = ic.run("Subtract create", originalCH2, TopHatTransformCH2);

                    // update imageProcessors
                    this.ip1 = TopHatTransformCH1.getProcessor();
                    this.ip2 = TopHatTransformCH2.getProcessor();

                    // file_in_dir_counter counts for every dir/file +1 (starts at 0), but cellNumber only counts +1 if the cell is not a 'freak' (starts at 1)
                    this.file_in_dir_counter++; // in directory
                    this.total_cell_num_analyzed++; // total for all directory
                    calculate_mean_cell_intensity();

                    // sometimes the edge values are quite high because of some artefacts from the top hat algorithm
                    for (int v = 0; v < this.width; v++) {
                        for (int u = 0; u < this.height; u++) {
                            if (this.ip3original.get(v, u) >= this.cell_thresh) {
                                if (is_edge_pixel(v, u)) { //  && !isMax.get(v * this.height + u)
                                    this.ip1.set(v, u, this.ip1.get(v, u) / 3);
                                    this.ip2.set(v, u, this.ip2.get(v, u) / 3);
                                }
                            }
                        }
                    }
                    // alternative: this uses the Sobel operator to find edges. 
                    // if used, need to change radial_stDev() to convolution_value(), which just takes the value of ip1_convolution
                    //              this.ip1_convolution = ip1original.duplicate();
                    //              this.ip2_convolution = ip2original.duplicate();
                    this.ip1_convolution = ip1.duplicate();
                    this.ip2_convolution = ip2.duplicate();
                    //              this.ip1_convolution.findEdges();
                    //              this.ip2_convolution.findEdges();

                    // alternative: laplace after gauss. needs to be calculated after the ipX edge correction above
                    this.ip1_convolution.convolve(this.kernel, 5, 5);
                    this.ip2_convolution.convolve(this.kernel, 5, 5);
                    calculate_mean_cell_gradient();
                    this.stDev_cell1 = this.cellMean1_gradient;
                    this.stDev_cell2 = this.cellMean2_gradient;

                    //starting the foci-detection with findObjects
                    if (this.use_tophat_for_search) this.cellNumber += findObjects(this.ip1);
                    else this.cellNumber += findObjects(this.ip1original);
                } catch (Exception e) {
//                     p("File name: " + this.image_name);
                    StringWriter errors = new StringWriter();
                    e.printStackTrace(new PrintWriter(errors));
                    GreenJTextArea ta = new GreenJTextArea("Something went wrong while processing file " + this.image_name + ". \n\nError message:\n\n " + errors, 15, 50);
                    ta.setWrapStyleWord(true);
                    ta.setLineWrap(true);
                    ta.setCaretPosition(0);
                    ta.setEditable(false);
                    JOptionPane.showMessageDialog(null, new JScrollPane(ta), "RESULT", JOptionPane.INFORMATION_MESSAGE);
                }
            }

            if (this.total_cell_num_analyzed % (this.total_files_in_dir / 100 + 1) == 1 || this.total_cell_num_analyzed == this.total_cell_num) {
                this.progress_gui.time_current = System.currentTimeMillis();
                this.progress_gui.time_duration = (long)((this.progress_gui.time_current - this.progress_gui.time_start) * ((double) this.total_cell_num / this.total_cell_num_analyzed - 1.));
                this.progress_gui.time_label.setText("Estimated duration: " + convert_millis_to_time_string(this.progress_gui.time_duration + 900)); // +900 so it does not show 0 for the last second
                // 99 so it does not show 100 while saving the results (to prevent people from closing the windows because it shows 100%)
                this.progress_gui.change(this.progress_gui.progressBar_total, 99 * this.total_cell_num_analyzed / this.total_cell_num);
                this.progress_gui.change(this.progress_gui.progressBar, 99 * this.file_in_dir_counter / this.total_files_in_dir);
            }
        }

        // 		System.out.print("FindObjects duration: ");
        // 		System.out.println(convert_millis_to_time_string(System.currentTimeMillis() - this.progress_gui.time_start));

        this.save_path = new File(this.root_path, this.result_dir + File.separator + this.dir_name + ".csv").getPath();
        this.progress_gui.log("Writing results...");
        save_results(this.save_path);
        if (this.freak_counter_low != 0)
            this.progress_gui.log("Freaks (low intensity): " + String.valueOf(this.freak_counter_low));
        if (this.freak_counter_high != 0)
            this.progress_gui.log("Freaks (high intensity): " + String.valueOf(this.freak_counter_high));
        if (this.freak_counter_stdev != 0)
            this.progress_gui.log("Freaks (low STD): " + String.valueOf(this.freak_counter_stdev));
    } // END analyze_folder


    public static String convert_millis_to_time_string(long milliseconds) {
        long seconds = milliseconds / 1000 % 60;
        long minutes = (milliseconds / (1000 * 60)) % 60;
        long hours = milliseconds / (1000 * 60 * 60); //  % 24
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }


    public void calculate_mean_cell_intensity() {
        // to calculate the mean and standard deviation of all cell pixels
        this.cell_sum1 = 0.;
        this.cell_sum2 = 0.;
        this.cell_sum_squares1 = 0.;
        this.cell_sum_squares2 = 0.;
        this.cell_area = 0;
        for (int v = 0; v < this.width; v++) {
            for (int u = 0; u < this.height; u++) {
                if (this.ip3original.get(v, u) >= this.cell_thresh) {
                    int value1 = this.ip1original.get(v, u);
                    int value2 = this.ip2original.get(v, u);
                    this.cell_sum1 += value1;
                    this.cell_sum2 += value2;
                    this.cell_area++;
                    if (value1 > this.cell_max1)
                        this.cell_max1 = value1;
                    if (value2 > this.cell_max2)
                        this.cell_max2 = value2;
                    this.cell_sum_squares1 += value1 * value1;
                    this.cell_sum_squares2 += value2 * value2;
                }
            }
        }
        this.cellMean1 = this.cell_sum1 / this.cell_area;
        this.cellMean2 = this.cell_sum2 / this.cell_area;

        // pearson correlation coefficient:
        double sum_squared_1 = 0.;
        double sum_squared_2 = 0.;
        double sum_squared_1_2 = 0.;
        for (int v = 0; v < this.width; v++) {
            for (int u = 0; u < this.height; u++) {
                if (this.ip3original.get(v, u) >= this.cell_thresh) {
                    //                     double diff_1 = this.ip1original.get(v, u) - this.cellMean1;
                    //                     double diff_2 = this.ip2original.get(v, u) - this.cellMean2;
                    // better use relative values
                    double diff_1 = (this.ip1original.get(v, u) - this.cellMean1) / this.cellMean1;
                    double diff_2 = (this.ip2original.get(v, u) - this.cellMean2) / this.cellMean2;
                    sum_squared_1 += diff_1 * diff_1;
                    sum_squared_2 += diff_2 * diff_2;
                    sum_squared_1_2 += diff_1 * diff_2;
                }
            }
        }
        this.pearson_correlation = sum_squared_1_2 / (Math.sqrt(sum_squared_1 * sum_squared_2));
        //         System.out.println("Pearson coefficient: "+this.pearson_correlation);
    } // END calculate_mean_cell_intensity


    public void calculate_mean_cell_gradient() {
        // to calculate the mean and standard deviation of all cell pixels
        double cell_sum1_tmp = 0.;
        double cell_sum2_tmp = 0.;
        double cell_sum_squares1_tmp = 0.;
        double cell_sum_squares2_tmp = 0.;
        double cell_area_tmp = 0;
        for (int v = 0; v < this.width; v++) {
            for (int u = 0; u < this.height; u++) {
                if (this.ip3original.get(v, u) >= this.cell_thresh) {
                    int value1 = this.ip1_convolution.get(v, u);
                    int value2 = this.ip2_convolution.get(v, u);
                    cell_sum1_tmp += value1;
                    cell_sum2_tmp += value2;
                    cell_area_tmp++;
                }
            }
        }
        this.cellMean1_gradient = cell_sum1_tmp / cell_area_tmp;
        this.cellMean2_gradient = cell_sum2_tmp / cell_area_tmp;
    } // END calculate_mean_cell_gradient

    // finds objects with region grow algorithm
    public int findObjects(ImageProcessor ip_search) {
        this.localMax = new ArrayList < > ();
        this.reserved_for = new int[this.width][this.height];
        this.remove_max = new ArrayList < > ();
        this.add_max = new ArrayList < > ();
        this.minThresh = this.cellMean1 + this.minThresh_above_cell_mean;
        if (use_tophat_for_search) this.minThresh = 0.;  // in case we use top-hat transformation for object search the local background is already subtracted. 
        isObj_saturated = new BitSet(width * height);
        isObj = new BitSet(width * height);
        BitSet isMax = new BitSet(width * height);
        BitSet isMax_CH2 = new BitSet(width * height);

        // don't count 'freaks' or wrong images
        if (this.cell_area == 0 || this.cellMean1 < this.freak_low_threshold || this.cellMean2 < this.freak_low_threshold) {
            this.freak_counter_low++;
            if (this.rename_freaks)
                rename_freak("low");
            return 0;
        } else if (this.cellMean1 > this.freak_threshold || this.cellMean2 > this.freak_threshold) {
            this.freak_counter_high++;
            if (this.rename_freaks)
                rename_freak("high");
            return 0;
        } else if (this.stDev_cell1 < this.freak_stdev_threshold || this.stDev_cell2 < this.freak_stdev_threshold) {
            this.freak_counter_stdev++;
            if (this.rename_freaks)
                rename_freak("std");
            return 0;
        }

        // searching for regionMax. cellMean1 has to be known
        for (int v = 0; v < this.width; v++) {
            for (int u = 0; u < this.height; u++) {
                boolean is_max;
                is_max = newRegionMax(ip_search, v, u, isMax);
                if (is_max) { // finds object maxima
                    LocalMax lm = new LocalMax();
                    lm.value = ip_search.get(v, u);
                    lm.x = v;
                    lm.y = u;
                    this.localMax.add(lm);
                    isMax.set(v * this.height + u); // to check in regionMax, whether another object is close
                }
            }
        }

        // first run grower only for saturated pixels (or == maxThresh) and delete other maxima in the saturated area (else there is only minSeparation to prevent having two maxima in a big saturated focus)
        // 	    for(this.curr_min_pos=0; this.curr_min_pos<this.localMax.size();this.curr_min_pos++){ // this delete the last one found
        for (this.curr_min_pos = this.localMax.size() - 1; this.curr_min_pos >= 0; this.curr_min_pos--) { // this delets the first one found
            this.next_pixels = new ArrayList < > ();
            int v = this.localMax.get(this.curr_min_pos).x;
            int u = this.localMax.get(this.curr_min_pos).y;
            int maxThresh = ip_search.get(v, u);
            isObj_saturated.set(v * height + u);
            this.max1X = v;
            this.max1Y = u;
            this.area = 1;
            this.visitedCounter = 0;
            this.added_counter = 0;

            int xy[] = {
                v,
                u
            };
            this.next_pixels.add(xy);

            grower_saturated(ip_search, isObj_saturated, isMax, this.next_pixels.get(this.next_pixels.size() - 1)[0], this.next_pixels.get(this.next_pixels.size() - 1)[1], maxThresh);
            while (this.visitedCounter < this.area) {
                if (this.next_pixels.size() == 0) break;
                int i = this.next_pixels.get(this.next_pixels.size() - 1)[0];
                int j = this.next_pixels.get(this.next_pixels.size() - 1)[1];
                // always count up and remove the pixel, also if it is an edge pixel, else it might create an infinite loop(?).
                this.visitedCounter++;
                this.next_pixels.remove(this.next_pixels.size() - 1);
                // IMPORTANT: should not be too small else big objects would not have the correct pixels because the grower does not grow in circles. 
                if (this.visitedCounter > this.width * this.height) break; // should never happen because next_pixels should be empty before. 
                if (i < 1) continue;
                if (j < 1) continue;
                if (i > this.width - 2) continue;
                if (j > this.height - 2) continue;
                grower_saturated(ip_search, isObj_saturated, isMax, i, j, maxThresh);
                // in case it runs out of the -30 +50 boundary. then area can't reach the visitedCounter value
            }
        }

        // delete duplicates, HashSet would suffice, LinkedHashSet preserves order, but it is sorted anyway
        if (this.remove_max.size() != 0) {
            LinkedHashSet < Integer > hs = new LinkedHashSet < > ();
            hs.addAll(this.remove_max);
            this.remove_max.clear();
            this.remove_max.addAll(hs);
            // sort the array
            Collections.sort(this.remove_max);
            for (int k = this.remove_max.size() - 1; k >= 0; k--) {
                // need to call intValue() because remove() is overloaded with Object and int. could also call: this.localMax.remove(this.localMax.get(k));
                this.localMax.remove(this.remove_max.get(k).intValue());
            }
            for (LocalMax lm: this.add_max) this.localMax.add(lm);
        }

        // sort the localMax list
        Collections.sort(this.localMax, new Comparator < LocalMax > () {
            @Override
            public int compare(LocalMax lm1, LocalMax lm2) {
                return lm1.value - lm2.value; // need ComparisonChain only for sorting of multiple columns
                // 		    return ComparisonChain.start().compare(lm1.localMax, lm2.localMax).compare(lm1.otherVariable, lm2.otherVariable).result();
            }
        });

        // fill reserved_for array. IMPORTANT: go from dark to bright objects, because the later objects might overwrite this.reserved_for and only big ones should take away from small ones not the other way around (so cruel)!
        for (this.curr_min_pos = 0; this.curr_min_pos < this.localMax.size(); this.curr_min_pos++) {
            // 			System.out.println(String.valueOf(v) + "\t" + String.valueOf(u));
            int v = this.localMax.get(this.curr_min_pos).x;
            int u = this.localMax.get(this.curr_min_pos).y;
            double radius_sqrt = this.minSeparation * this.minSeparation / 4.;
            int half_minSep = (int) Math.ceil(this.minSeparation / 2.);
            for (int i = v - half_minSep; i <= v + half_minSep; i++) {
                for (int j = u - half_minSep; j <= u + half_minSep; j++) {
                    if (i < 0) continue;
                    if (j < 0) continue;
                    if (i > this.width - 1) continue;
                    if (j > this.height - 1) continue;
                    if ((i - v) * (i - v) + (j - u) * (j - u) < radius_sqrt)
                        // reserve near pixels so that lower max can't take them away.
                        if (ip_search.get(i, j) > this.cellMean1)
                            //                     if (ip_search.get(i, j) > Math.max(this.minThresh, this.cellMean1))
                            // 					if (ip_search.get(i, j) > Math.max(this.edgeThreshold * ip_search.get(v, u), this.cellMean1))
                            this.reserved_for[i][j] = this.curr_min_pos;
                }
            }
        }

        // going from bright to dark foci now. reserved_for array should prevent smaller foci from being eating by big ones.
        // for(this.curr_min_pos=0; this.curr_min_pos<this.localMax.size();this.curr_min_pos++){
        for (this.curr_min_pos = this.localMax.size() - 1; this.curr_min_pos >= 0; this.curr_min_pos--) {
            //             print(this.curr_min_pos);
            // 		    int minPosition = getMinPosition(localMax);
            // 		    localMax.set(minPosition, 256);	//damit es nicht ein zweites mal benutzt wird
            int v = this.localMax.get(this.curr_min_pos).x;
            int u = this.localMax.get(this.curr_min_pos).y;

            int maxThresh = ip_search.get(v, u);

            isObj.set(v * height + u);
            // if tophat is not used for area determination
            double eThresh = Math.max(this.edgeThreshold * ip_search.get(v, u), this.minThresh);
            this.foci_xy = new ArrayList < > ();
            this.next_pixels = new ArrayList < > ();
            int xy[] = {
                v,
                u
            };
            this.next_pixels.add(xy);
            // we got maxCH1 from the localMax finder, maxCH2 is determined by grower(...)
            this.maxCH1Transform = this.ip1.get(v, u);
            this.maxCH2Transform = 0;
            this.maxCH1 = this.ip1original.get(v, u);
            this.maxCH2 = 0;
            this.max1X = v;
            this.max1Y = u;
            this.meanSum1 = 0.;
            this.meanSum2 = 0.;
            this.maxPixels1 = new int[this.minArea];
            this.maxPixels2 = new int[this.minArea];
            this.maxPixels1Transform = new int[this.minArea];
            this.maxPixels2Transform = new int[this.minArea];
            this.stDev_max_pixels_1 = new double[this.minArea];
            this.stDev_max_pixels_2 = new double[this.minArea];

            // run grower for the whole focus
            this.area = 1;
            this.visitedCounter = 0;
            grower(ip_search, isObj, isMax_CH2, this.next_pixels.get(this.next_pixels.size() - 1)[0], this.next_pixels.get(this.next_pixels.size() - 1)[1], maxThresh, eThresh);
            // the last condition is here to prevent infinite loops even though that should not happen. IMPORTANT: should not be too small else big objects would not have the correct pixels because the grower does not grow in circles. 
            while (this.visitedCounter < this.area) {
                //                 p("ObjectFinder while loop 2");
                //                 print(this.visitedCounter + " | " + this.area);
                if (this.next_pixels.size() == 0) break;
                int i = this.next_pixels.get(this.next_pixels.size() - 1)[0];
                int j = this.next_pixels.get(this.next_pixels.size() - 1)[1];
                // always count up and remove the pixel, also if it is an edge pixel, else it might create an infinite loop(?).
                this.visitedCounter++;
                this.next_pixels.remove(this.next_pixels.size() - 1);
                // IMPORTANT: should not be too small else big objects would not have the correct pixels because the grower does not grow in circles. 
                if (this.visitedCounter > this.width * this.height) break; // should never happen because next_pixels should be empty before. 
                if (i < 1) continue;
                if (j < 1) continue;
                if (i > this.width - 2) continue;
                if (j > this.height - 2) continue;
                grower(ip_search, isObj, isMax_CH2, i, j, maxThresh, eThresh);
            }
            if (this.area < this.minArea)
                continue;
            if (this.maxCH2 == 0)
                continue;
            calculate_max2_top_and_stDev();
            // hier eventuell auch mean checken ?????
            this.topPixels1 = getMean_without0(this.maxPixels1);
            this.topPixels2 = getMean_without0(this.maxPixels2);

            this.mean1 = this.meanSum1 / this.area;
            this.mean2 = this.meanSum2 / this.area;
            // calculate the mean of the birghtest pixels (number of pixels = this.minArea)
            this.topPixels1Transform = getMean_without0(this.maxPixels1Transform);
            this.topPixels2Transform = getMean_without0(this.maxPixels2Transform);

            if (this.topPixels1Transform <= 0. || this.topPixels2Transform <= 0.)
                continue;

            // fffff: weg? damit sich numpy nachher nicht ueber nuller beschwert, solche objekte fallen sowieso raus
            if (this.maxCH2Transform == 0) {
                this.maxCH2Transform = 1;
                this.topPixels2Transform = 1.;
            }
            // not using getMean_without0() here, because stDev can be 0.
            this.stDev_max1 = getMean(this.stDev_max_pixels_1);
            this.stDev_max2 = getMean(this.stDev_max_pixels_2);

            // if stDev is very small, don't take it except if it is caused by a large saturated area, then give stDev a high value
            if (this.stDev_max1 < 1.) {
                if (this.topPixels1 < 200.) continue;
                else this.stDev_max1 = 20.; // set it to a high value if it is caused by a high intensity area.
            }
            if (this.stDev_max2 < 1.) {
                if (this.topPixels2 < 200.) continue;
                else this.stDev_max2 = 20.;
            }
            add_results();
            this.objectCounter++;
        }
        // always +1, even if there was no object found.
        return 1;
    } // END findObjects




    public void grower_saturated(ImageProcessor ip_search, BitSet isObj, BitSet isMax, int i, int j, int maxThresh) {
        // this will remove all other max in the same saturated area if they are within this.maxArea_radius!
        // so it is still possible that a big saturated area gets two maxima, but this is intended as they would also be counted as two by a human counter.
        // since isObj is set, later maxima can't remove earlier ones.

        // don't throw away the first max (else we don't have any max left)
        if (this.visitedCounter > 1) {
            // ask this, because it will be false most of the time
            if (isMax.get(i * height + j))
                for (int k = 0; k < this.localMax.size(); k++)
                    if (this.localMax.get(k).x == i && this.localMax.get(k).y == j) {
                        //                         System.out.println("Saturated maximum detected: "+String.valueOf(i)+" "+String.valueOf(j));
                        // 						System.out.print("Maxima merged for ");
                        // 						System.out.println(this.image_name);
                        // if the pixel between both max has the same brightness, add it and remove both other max
                        int x_new = (i + this.localMax.get(this.curr_min_pos).x) / 2;
                        int y_new = (j + this.localMax.get(this.curr_min_pos).y) / 2;
                        // if there are 3 (rather unlikely) then only one should be added
                        if (this.added_counter < 1 && ip_search.get(x_new, y_new) == maxThresh) {
                            LocalMax lm = new LocalMax();
                            lm.x = x_new;
                            lm.y = y_new;
                            lm.value = maxThresh;
                            this.add_max.add(lm);
                            // both this.curr_min_pos and k should be deleted as they are merged in the new lm. need to delete duplicates later!
                            this.remove_max.add(this.curr_min_pos);
                            this.remove_max.add(k);
                            this.added_counter++;
                        } else {
                            this.remove_max.add(k);
                        }
                    }
        }

        if (!is_edge_pixel_image(i + 1, j) && !isObj.get((i + 1) * this.height + j) && ip_search.get(i + 1, j) == maxThresh && Math.pow(i + 1 - this.max1X, 2) + Math.pow(j - this.max1Y, 2) <= this.maxArea_radius * this.maxArea_radius) {
            isObj.set((i + 1) * this.height + j); // here isObj is an argument of grower_saturation(), so isObj_saturated is used.
            this.area++;
            int xy_next[] = {
                i + 1,
                j
            };
            this.next_pixels.add(xy_next);
        }
        if (!is_edge_pixel_image(i - 1, j) && !isObj.get((i - 1) * this.height + j) && ip_search.get(i - 1, j) == maxThresh && Math.pow(i - 1 - this.max1X, 2) + Math.pow(j - this.max1Y, 2) <= this.maxArea_radius * this.maxArea_radius) {
            isObj.set((i - 1) * this.height + j);
            this.area++;
            int xy_next[] = {
                i - 1,
                j
            };
            this.next_pixels.add(xy_next);
        }
        if (!is_edge_pixel_image(i, j + 1) && !isObj.get(i * this.height + j + 1) && ip_search.get(i, j + 1) == maxThresh && Math.pow(i - this.max1X, 2) + Math.pow(j + 1 - this.max1Y, 2) <= this.maxArea_radius * this.maxArea_radius) {
            isObj.set(i * this.height + j + 1);
            this.area++;
            int xy_next[] = {
                i,
                j + 1
            };
            this.next_pixels.add(xy_next);
        }
        if (!is_edge_pixel_image(i, j - 1) && !isObj.get(i * this.height + j - 1) && ip_search.get(i, j - 1) == maxThresh && Math.pow(i - this.max1X, 2) + Math.pow(j - 1 - this.max1Y, 2) <= this.maxArea_radius * this.maxArea_radius) {
            isObj.set(i * this.height + j - 1);
            this.area++;
            int xy_next[] = {
                i,
                j - 1
            };
            this.next_pixels.add(xy_next);
        }
    } // END grower_saturated


    public void grower(ImageProcessor ip_search, BitSet isObj, BitSet isMax_CH2, int i, int j, int maxThresh, double eThresh) {
        // we got already max CH1. now searching for max of CH2, which has to be inside radius radMax
        // needs to be cahnge if use_tophat_for_search=true!
        // 		int radMaxSquared = this.minSeparation * this.minSeparation / 2;
        int radMaxSquared = this.minSeparation * this.minSeparation / 4;
        if (Math.pow(this.max1X - i, 2) + Math.pow(this.max1Y - j, 2) <= radMaxSquared) {
            if (this.ip2original.get(i, j) > this.maxCH2) { //  && newRegionMax(this.ip2original, i, j, isMax_CH2)
                this.maxCH2 = this.ip2original.get(i, j);
                // 		      this.maxDiff = Math.sqrt((this.max1X - i)*(this.max1X - i) + (this.max1Y - j)*(this.max1Y - j));
                this.max2X = i;
                this.max2Y = j;
                // 				isMax_CH2.set(i * this.height + j);
            }
            if (this.ip2.get(i, j) > this.maxCH2Transform) this.maxCH2Transform = this.ip2.get(i, j);
            // replace the lowest value of maxPixels array. position has to be inside radMaxSquared. should probably be smaller than this.minSeparation
            int minPosTmp = getMinPosition(this.maxPixels1);
            if (this.ip1original.get(i, j) > this.maxPixels1[minPosTmp])
                this.maxPixels1[minPosTmp] = this.ip1original.get(i, j);
            minPosTmp = getMinPosition(this.maxPixels1Transform);
            if (this.ip1.get(i, j) > this.maxPixels1Transform[minPosTmp])
                this.maxPixels1Transform[minPosTmp] = this.ip1.get(i, j);

            // stDev has to be calculated after tophat because else the edge of the cell will have a high value
            double stDev_tmp = convolution_value(i, j, 1);
            minPosTmp = getMinPosition(this.stDev_max_pixels_1);
            if (stDev_tmp > this.stDev_max_pixels_1[minPosTmp])
                this.stDev_max_pixels_1[minPosTmp] = stDev_tmp;
        }
        this.meanSum1 += this.ip1original.get(i, j);
        this.meanSum2 += this.ip2original.get(i, j);

        // add this pixel to the foci_xy list, so we can calculate topPixels and stDev for channel 2
        int xy[] = {
            i,
            j
        };
        this.foci_xy.add(xy);

        if (!is_edge_pixel_image(i + 1, j) && !isObj.get((i + 1) * this.height + j) && ip_search.get(i + 1, j) >= eThresh && ip_search.get(i + 1, j) <= maxThresh && Math.pow(i + 1 - this.max1X, 2) + Math.pow(j - this.max1Y, 2) <= this.maxArea_radius * this.maxArea_radius && !other_max_near(i + 1, j)) {
            isObj.set((i + 1) * this.height + j);
            this.area++;
            // 			add_value_to_local_hist(i, j);
            int xy_next[] = {
                i + 1,
                j
            };
            this.next_pixels.add(xy_next);
        }
        if (!is_edge_pixel_image(i - 1, j) && !isObj.get((i - 1) * this.height + j) && ip_search.get(i - 1, j) >= eThresh && ip_search.get(i - 1, j) <= maxThresh && Math.pow(i - 1 - this.max1X, 2) + Math.pow(j - this.max1Y, 2) <= this.maxArea_radius * this.maxArea_radius && !other_max_near(i - 1, j)) {
            isObj.set((i - 1) * this.height + j);
            this.area++;
            // 			add_value_to_local_hist(i, j);
            int xy_next[] = {
                i - 1,
                j
            };
            this.next_pixels.add(xy_next);
        }
        if (!is_edge_pixel_image(i, j + 1) && !isObj.get(i * this.height + j + 1) && ip_search.get(i, j + 1) >= eThresh && ip_search.get(i, j + 1) <= maxThresh && Math.pow(i - this.max1X, 2) + Math.pow(j + 1 - this.max1Y, 2) <= this.maxArea_radius * this.maxArea_radius && !other_max_near(i, j + 1)) {
            isObj.set(i * this.height + j + 1);
            this.area++;
            // 			add_value_to_local_hist(i, j);
            int xy_next[] = {
                i,
                j + 1
            };
            this.next_pixels.add(xy_next);
        }
        if (!is_edge_pixel_image(i, j - 1) && !isObj.get(i * this.height + j - 1) && ip_search.get(i, j - 1) >= eThresh && ip_search.get(i, j - 1) <= maxThresh && Math.pow(i - this.max1X, 2) + Math.pow(j - 1 - this.max1Y, 2) <= this.maxArea_radius * this.maxArea_radius && !other_max_near(i, j - 1)) {
            isObj.set(i * this.height + j - 1);
            this.area++;
            // 			add_value_to_local_hist(i, j);
            int xy_next[] = {
                i,
                j - 1
            };
            this.next_pixels.add(xy_next);
        }
    } // END grower

    // this version should be faster, but right now it is also the only correct one
    //     tests if there is a other max near, which should then get this pixel for its area, also if no max is there (==0) it is ok as well
    public boolean other_max_near(int v, int u) {
        if (this.reserved_for[v][u] == 0 || this.reserved_for[v][u] == this.curr_min_pos)
            return false;
        return true;
    } // END other_max_near

    // needs to be calculated after max2X and max2Y are determined
    public void calculate_max2_top_and_stDev() {
        int n = this.foci_xy.size(); // == area
        int minPosTmp;
        double stDev_tmp;
        for (int k = 0; k < n; k++) {
            int i = this.foci_xy.get(k)[0];
            int j = this.foci_xy.get(k)[1];
            if (Math.pow(i - this.max2X, 2) + Math.pow(j - this.max2Y, 2) <= this.minSeparation) {
                minPosTmp = getMinPosition(this.maxPixels2);
                if (this.ip2original.get(i, j) > this.maxPixels2[minPosTmp])
                    this.maxPixels2[minPosTmp] = this.ip2original.get(i, j);
                minPosTmp = getMinPosition(this.maxPixels2Transform);
                if (this.ip2.get(i, j) > this.maxPixels2Transform[minPosTmp])
                    this.maxPixels2Transform[minPosTmp] = this.ip2.get(i, j);

                minPosTmp = getMinPosition(this.stDev_max_pixels_2);
                // 				stDev_tmp = radial_stDev(this.ip2, i, j);
                stDev_tmp = convolution_value(i, j, 2);
                if (stDev_tmp > this.stDev_max_pixels_2[minPosTmp])
                    this.stDev_max_pixels_2[minPosTmp] = stDev_tmp;
            }

        }
        // calculate moment_of_inertia around z-axis
        // center of mass
        double center_x = 0.;
        double center_y = 0.;
        double total_intensity = 0.;
        this.moment_of_inertia_1 = 0.;
        for (int k = 0; k < n; k++) {
            int i = this.foci_xy.get(k)[0];
            int j = this.foci_xy.get(k)[1];
            int intensity = this.ip1original.get(i, j);
            center_x += i * intensity;
            center_y += j * intensity;
            total_intensity += intensity;
        }
        center_x /= total_intensity;
        center_y /= total_intensity;
        // calculate within radius
        int rad = 3;
        total_intensity = 0.; // this is not the same total_intensity as above!
        int v = (int) Math.round(center_x);
        int u = (int) Math.round(center_y);
        if (v >= rad && v < this.width - rad && u >= rad && u < this.height - rad) {
            for (int i = v - rad; i <= v + rad; i++) {
                for (int j = u - rad; j <= u + rad; j++) {
                    if ((i - v) * (i - v) + (j - u) * (j - u) <= rad * rad) {
                        double rad_sq = Math.pow(center_x - i, 2) + Math.pow(center_y - j, 2);
                        double intensity = this.ip1original.get(i, j);
                        if (this.ip3original.get(i, j) < this.cell_thresh) // else objects at the cell edge have lower values
                            intensity = this.cellMean1;
                        this.moment_of_inertia_1 += rad_sq * intensity;
                        total_intensity += intensity;
                    }
                }
            }
            this.moment_of_inertia_1 /= total_intensity;
        } else {
            this.moment_of_inertia_1 = 30.;
        }

        center_x = 0.;
        center_y = 0.;
        total_intensity = 0.;
        this.moment_of_inertia_2 = 0.;
        for (int k = 0; k < n; k++) {
            int i = this.foci_xy.get(k)[0];
            int j = this.foci_xy.get(k)[1];
            int intensity = this.ip2original.get(i, j);
            center_x += i * intensity;
            center_y += j * intensity;
            total_intensity += intensity;
        }
        center_x /= total_intensity;
        center_y /= total_intensity;
        // calculate within radius
        total_intensity = 0.; // this is not the same total_intensity as above!
        v = (int) Math.round(center_x);
        u = (int) Math.round(center_y);
        if (v >= rad && v < this.width - rad && u >= rad && u < this.height - rad) {
            for (int i = v - rad; i <= v + rad; i++) {
                for (int j = u - rad; j <= u + rad; j++) {
                    if ((i - v) * (i - v) + (j - u) * (j - u) <= rad * rad) {
                        double rad_sq = Math.pow(center_x - i, 2) + Math.pow(center_y - j, 2);
                        double intensity = this.ip2original.get(i, j);
                        if (this.ip3original.get(i, j) < this.cell_thresh) // else objects at the cell edge have lower values
                            intensity = this.cellMean2;
                        this.moment_of_inertia_2 += rad_sq * intensity;
                        total_intensity += intensity;
                    }
                }
            }
            this.moment_of_inertia_2 /= total_intensity;
        } else {
            this.moment_of_inertia_2 = 30.;
        }
    } // END calculate_max2_top_and_stDev


    public double pixel_moment_of_inertia(int v, int u, int channel) {
        // calculate moment_of_inertia around z-axis
        // calculate within radius
        int rad = 3;
        double moi = 0;
        double total_intensity = 0.;
        if (v >= rad && v < this.width - rad && u >= rad && u < this.height - rad) {
            for (int i = v - rad; i <= v + rad; i++) {
                for (int j = u - rad; j <= u + rad; j++) {
                    if ((i - v) * (i - v) + (j - u) * (j - u) <= rad * rad) {
                        double rad_sq = Math.pow(v - i, 2) + Math.pow(u - j, 2);
                        //                         double rad_sq = Math.sqrt(Math.pow(v-i, 2) + Math.pow(u-j, 2));
                        double intensity = this.ip1original.get(i, j);
                        if (channel == 2) intensity = this.ip2original.get(i, j);
                        if (this.ip3original.get(i, j) < this.cell_thresh) // else objects at the cell edge have lower values
                            intensity = this.cellMean1;
                        moi += rad_sq * intensity;
                        total_intensity += intensity;
                    }
                }
            }
            moi /= total_intensity;
        } else {
            moi = 30.;
        }
        return moi;
    }


    public static double roundIt(double x, int digits) {
        double rounda = Math.pow(10, digits);
        return (double) Math.round(x * rounda) / rounda;
    }

    // default round to 3 digits
    public static double roundIt(double x) {
        return roundIt(x, 3);
    }

    public void morphological_opening(ImageProcessor ip, double radius, String color) {
        RankFilters rf = new RankFilters();
        rf.rank(ip, radius, rf.MIN);
        rf.rank(ip, radius, rf.MAX);
    } // END morphological_opening

    public double convolution_value(int v, int u, int channel) {
        if (channel == 1)
            return (double) this.ip1_convolution.get(v, u);
        else
            return (double) this.ip2_convolution.get(v, u);
    }

    public boolean is_edge_pixel(int i, int j) {
        if (i <= 0 || j <= 0 || i >= this.width - 1 || j >= this.height - 1) return true;
        if (this.ip3original.get(i + 1, j) == 0 || this.ip3original.get(i - 1, j) == 0 || this.ip3original.get(i, j + 1) == 0 || this.ip3original.get(i, j - 1) == 0) return true;
        return false;
    }

    public boolean is_edge_pixel_image(int i, int j) {
        if (i <= 0 || j <= 0 || i >= this.width - 1 || j >= this.height - 1) return true;
        return false;
    }

    void print(Object o) {
        System.out.println(o);
    }

    public void add_results() {
        ResultType result = new ResultType();
        result.image_name = this.image_name;
        result.objectCounter = this.objectCounter;
        result.cellNumber = this.cellNumber;

        if (roundIt(this.stDev_max2) == 0.) print(this.stDev_max2);

        result.area = this.area;
        result.max1X = this.max1X;
        result.max1Y = this.max1Y;
        result.maxCH1 = this.maxCH1;
        result.maxCH2 = this.maxCH2;
        result.maxCH1Transform = this.maxCH1Transform;
        result.maxCH2Transform = this.maxCH2Transform;
        result.mean1 = roundIt(this.mean1);
        result.mean2 = roundIt(this.mean2);
        result.topPixels1 = roundIt(this.topPixels1);
        result.topPixels2 = roundIt(this.topPixels2);
        result.topPixels1Transform = roundIt(this.topPixels1Transform);
        result.topPixels2Transform = roundIt(this.topPixels2Transform);
        result.stDev_max1 = roundIt(this.stDev_max1);
        result.stDev_max2 = roundIt(this.stDev_max2);
        result.cell_area = this.cell_area;
        result.cellMean1 = roundIt(this.cellMean1);
        result.cellMean2 = roundIt(this.cellMean2);
        result.stDev_cell1 = roundIt(this.stDev_cell1);
        result.stDev_cell2 = roundIt(this.stDev_cell2);
        result.pearson_correlation = roundIt(this.pearson_correlation);
        result.pearson_correlation_object = roundIt(this.pearson_correlation_object);

        result.moment_of_inertia_1 = roundIt(this.moment_of_inertia_1);
        result.moment_of_inertia_2 = roundIt(this.moment_of_inertia_2);

        this.result_table.add(result);
    } // END add_results


    public void save_results(String filename) {

        PrintStream ps;
        try {
            ps = new PrintStream(new FileOutputStream(filename));
            int freak_counter_total = freak_counter_low + freak_counter_high + freak_counter_stdev;
            ps.println(freak_counter_total + " cell images excluded: " + freak_counter_low + " (low intensity), " + freak_counter_high + " (high intensity), " + freak_counter_stdev + " (low intensity standard deviation)");
            ps.println("ImageName\tObjectCounter\tCellNumber\tmax1X\tmax1Y\ttopPixels1\ttopPixels2\tlocalGradMax1\tlocalGradMax2\tcellMean1\tcellMean2\tmaxCH1\tmaxCH2\tmean1\tmean2\tmaxCH1Transform\tmaxCH2Transform\ttopPixels1Transform\ttopPixels2Transform\tArea\tCellArea\tStDevCell1\tStDevCell2\tpearson_correlation_cell\tpearson_correlation_object\tmomentOfInertia1\tmomentOfInertia2");
            for (int i = 0; i < this.result_table.size(); i++) {
                ResultType row = this.result_table.get(i);

                String out_line = row.image_name + "\t" + String.valueOf(row.objectCounter) + "\t" + String.valueOf(row.cellNumber) + "\t" + String.valueOf(row.max1X) + "\t" + String.valueOf(row.max1Y) + "\t";
                out_line += String.valueOf(row.topPixels1) + "\t" + String.valueOf(row.topPixels2) + "\t" + String.valueOf(row.stDev_max1) + "\t" + String.valueOf(row.stDev_max2) + "\t" + String.valueOf(row.cellMean1) + "\t";
                out_line += String.valueOf(row.cellMean2) + "\t" + String.valueOf(row.maxCH1) + "\t" + String.valueOf(row.maxCH2) + "\t" + String.valueOf(row.mean1) + "\t" + String.valueOf(row.mean2) + "\t";
                out_line += String.valueOf(row.maxCH1Transform) + "\t" + String.valueOf(row.maxCH2Transform) + "\t" + String.valueOf(row.topPixels1Transform) + "\t" + String.valueOf(row.topPixels2Transform) + "\t";
                out_line += String.valueOf(row.area) + "\t" + String.valueOf(row.cell_area) + "\t" + String.valueOf(row.stDev_cell1) + "\t" + String.valueOf(row.stDev_cell2) + "\t";
                out_line += String.valueOf(row.pearson_correlation) + "\t" + String.valueOf(row.pearson_correlation_object) + "\t";
                out_line += String.valueOf(row.moment_of_inertia_1) + "\t" + String.valueOf(row.moment_of_inertia_2);
                ps.println(out_line);
            }
            ps.close();
        } catch (FileNotFoundException e) {}

    } // END save_results


    public void rename_freak(String reason) {
        File freak_dir = new File(this.image_file.getParentFile().getAbsolutePath(), this.freak_identifier + "_" + reason);
        File new_file = new File(freak_dir, this.image_name);
        new_file.mkdirs(); // create dir if not exisits
        if (!this.image_name.contains(this.freak_identifier)) {
            try {
                Files.move(this.image_file.toPath(), new_file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                System.out.println(e);
            }
        }
    }

    /** from the imagej source code (method of ChannelSplitter):

        Splits the specified RGB stack into three 8-bit grayscale stacks.
        Deletes the source stack if keepSource is false. */
    // ????? war eigentlich: public static, aber geht mit this.cellNumber nicht
    public ImageStack[] splitRGB(ImageStack rgb, boolean keepSource) {
        int w = rgb.getWidth();
        int h = rgb.getHeight();
        ImageStack[] channels = new ImageStack[3];
        for (int i = 0; i < 3; i++)
            channels[i] = new ImageStack(w, h);
        byte[] r, g, b;
        ColorProcessor cp;
        int slice = 1;
        int inc = keepSource ? 1 : 0;
        int n = rgb.getSize();
        for (int i = 1; i <= n; i++) {
            r = new byte[w * h];
            g = new byte[w * h];
            b = new byte[w * h];
            cp = (ColorProcessor) rgb.getProcessor(slice);
            slice += inc;
            cp.getRGB(r, g, b);
            if (!keepSource) rgb.deleteSlice(1);
            channels[0].addSlice(null, r);
            channels[1].addSlice(null, g);
            channels[2].addSlice(null, b);

        }
        return channels;
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

    public class LocalMax {
        int value, x, y;
    }
}