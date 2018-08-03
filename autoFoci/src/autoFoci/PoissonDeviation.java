package autoFoci;

public class PoissonDeviation {

    int cell_number_interval = 500;
    double foci_interval = 0.5;
    
    // interval with 95% of all values over 100000 simulated distributions. values between 0.5 foci/cell and 2 foci per cell were simulated for cell numbers between 500 and 10000. 
    double[][] kl_div_95_arr =  {{0.01032, 0.00538, 0.00372, 0.00283, 0.00229, 0.00193, 0.00165, 0.00146, 0.0013, 0.00119, 0.00108, 0.00099, 0.00092, 0.00086, 0.0008, 0.00075, 0.00071, 0.00067, 0.00064, 0.00061}, {0.01286, 0.00672, 0.00459, 0.00347, 0.00281, 0.00237, 0.00204, 0.0018, 0.00161, 0.00146, 0.00133, 0.00123, 0.00114, 0.00106, 0.00099, 0.00093, 0.00088, 0.00083, 0.00079, 0.00075}, {0.01474, 0.00769, 0.0052, 0.00396, 0.00319, 0.00267, 0.00232, 0.00204, 0.00182, 0.00164, 0.0015, 0.00138, 0.00127, 0.00118, 0.00111, 0.00104, 0.00098, 0.00092, 0.00088, 0.00084}, {0.01625, 0.00839, 0.00566, 0.00429, 0.00346, 0.00291, 0.0025, 0.0022, 0.00195, 0.00177, 0.00161, 0.00148, 0.00137, 0.00128, 0.0012, 0.00113, 0.00106, 0.00101, 0.00096, 0.00091}};
    double[][] least_squares_95_arr = {{0.00344, 0.00172, 0.00115, 0.00086, 0.00069, 0.00058, 0.0005, 0.00043, 0.00038, 0.00034, 0.00032, 0.00029, 0.00027, 0.00025, 0.00023, 0.00022, 0.0002, 0.00019, 0.00018, 0.00017}, {0.0038, 0.00193, 0.00127, 0.00096, 0.00077, 0.00064, 0.00055, 0.00048, 0.00043, 0.00038, 0.00035, 0.00032, 0.0003, 0.00027, 0.00026, 0.00024, 0.00022, 0.00021, 0.0002, 0.00019}, {0.00379, 0.00191, 0.00127, 0.00094, 0.00076, 0.00063, 0.00054, 0.00047, 0.00042, 0.00038, 0.00034, 0.00031, 0.00029, 0.00027, 0.00025, 0.00024, 0.00022, 0.00021, 0.0002, 0.00019}, {0.00371, 0.00187, 0.00125, 0.00094, 0.00075, 0.00062, 0.00053, 0.00047, 0.00042, 0.00038, 0.00034, 0.00031, 0.00029, 0.00027, 0.00025, 0.00023, 0.00022, 0.00021, 0.0002, 0.00019}};
    
    
    public String poisson_deviation_text(double foci, int cells, int cells_excluded, double pearson, double kl_divergence, double r_squares) {
        int i_foci = 0, i_cell_number = 0;
        double dist_sq_foci = -1, dist_sq_cell_number = -1;
        for (int i=0; i<4; i++) {
            double f = (i + 1) * foci_interval;
            if (dist_sq_foci == -1 || (foci - f) * (foci - f) < dist_sq_foci) {
                i_foci = i;
                dist_sq_foci = (foci - f) * (foci - f);
            }
        }
        for (int i=0; i<20; i++) {
            double c = (i + 1) * cell_number_interval;
            if (dist_sq_cell_number == -1 || (cells - c) * (cells - c) < dist_sq_cell_number) {
                i_cell_number = i;
                dist_sq_cell_number = (cells - c) * (cells - c);
            }
        }
        return "<html><p>Cells: " + Integer.toString(cells) + " used, " + Integer.toString(cells_excluded) + " excluded" + "<br>Colocalization (Pearson, &lt;0.4 bad): " + Double.toString(pearson) + "<br><br>Difference to perfect Poisson:<br>KL-Divergence: " + Double.toString(kl_divergence) + " (ideally &lt;"+Double.toString(kl_div_95_arr[i_foci][i_cell_number])+")<br>Residual squares: " + Double.toString(r_squares) + " (ideally  &lt;"+Double.toString(least_squares_95_arr[i_foci][i_cell_number])+")<br><br>(Ideal values are based on a 95% confidence<br> interval for "+ Integer.toString((i_cell_number+1) * cell_number_interval) + " cells and " +Double.toString((i_foci+1) * foci_interval)+ " foci/cell.)</p></html>";
    }
}