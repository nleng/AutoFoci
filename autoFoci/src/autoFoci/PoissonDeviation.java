package autoFoci;

import autoFoci.HistAnalyzer;

public class PoissonDeviation {

    final int digits = 4;
    final int cell_number_interval = 500;
    final double foci_interval = 0.2;
    
    // interval with 95% of all values over 100000 simulated distributions. values between 0.5 foci/cell and 2 foci per cell were simulated for cell numbers between 500 and 10000. 
    double[][] kl_div_95_arr =  {{0.00808, 0.00421, 0.00292, 0.00224, 0.00184, 0.00156, 0.00135, 0.00119, 0.00104, 0.00093}, {0.00981, 0.00505, 0.00349, 0.00264, 0.00215, 0.00181, 0.00156, 0.00139, 0.00124, 0.00112}, {0.01096, 0.00574, 0.00391, 0.00296, 0.0024, 0.00203, 0.00175, 0.00154, 0.00138, 0.00124}, {0.01201, 0.00622, 0.00425, 0.00325, 0.00264, 0.00221, 0.00192, 0.00168, 0.0015, 0.00135}, {0.01288, 0.00675, 0.00458, 0.00348, 0.00281, 0.00236, 0.00203, 0.0018, 0.00161, 0.00145}, {0.01382, 0.00713, 0.00483, 0.00368, 0.00298, 0.00251, 0.00216, 0.0019, 0.00171, 0.00153}, {0.01443, 0.00752, 0.00508, 0.00387, 0.00313, 0.00262, 0.00226, 0.00199, 0.00178, 0.00162}, {0.01515, 0.00782, 0.00527, 0.00402, 0.00325, 0.00273, 0.00235, 0.00205, 0.00184, 0.00166}, {0.01569, 0.00809, 0.00548, 0.00418, 0.00335, 0.00282, 0.00243, 0.00213, 0.0019, 0.00172}, {0.01622, 0.00838, 0.00564, 0.00428, 0.00345, 0.00289, 0.0025, 0.00219, 0.00196, 0.00176}, {0.01669, 0.00856, 0.0058, 0.0044, 0.00354, 0.00299, 0.00257, 0.00227, 0.00203, 0.00184}};
    double[][] least_squares_95_arr = {{0.00216, 0.00111, 0.00073, 0.00055, 0.00044, 0.00037, 0.00031, 0.00027, 0.00024, 0.00022}, {0.00322, 0.00158, 0.00106, 0.00079, 0.00064, 0.00053, 0.00045, 0.0004, 0.00036, 0.00032}, {0.00361, 0.00181, 0.0012, 0.0009, 0.00073, 0.00061, 0.00051, 0.00045, 0.0004, 0.00036}, {0.00376, 0.00188, 0.00125, 0.00095, 0.00075, 0.00063, 0.00054, 0.00047, 0.00042, 0.00038}, {0.00381, 0.00192, 0.00127, 0.00096, 0.00076, 0.00064, 0.00054, 0.00048, 0.00043, 0.00038}, {0.00382, 0.00192, 0.00127, 0.00095, 0.00076, 0.00064, 0.00055, 0.00048, 0.00042, 0.00038}, {0.0038, 0.0019, 0.00127, 0.00095, 0.00076, 0.00063, 0.00054, 0.00048, 0.00042, 0.00038}, {0.00376, 0.00188, 0.00126, 0.00095, 0.00075, 0.00063, 0.00054, 0.00047, 0.00042, 0.00038}, {0.00375, 0.00187, 0.00124, 0.00094, 0.00075, 0.00063, 0.00053, 0.00047, 0.00042, 0.00037}, {0.00372, 0.00188, 0.00124, 0.00094, 0.00074, 0.00062, 0.00053, 0.00046, 0.00041, 0.00037}, {0.00369, 0.00186, 0.00124, 0.00093, 0.00074, 0.00062, 0.00053, 0.00047, 0.00041, 0.00037}};
    
    
    public String poisson_deviation_text(double foci, int cells, int cells_excluded, double pearson, double kl_divergence, double r_squares) {
        int i_foci = 0, i_cell_number = 0;
        double dist_sq_foci = -1, dist_sq_cell_number = -1;
        for (int i=0; i<10; i++) {
            double f = (i + 1) * foci_interval;
            if (dist_sq_foci == -1 || (foci - f) * (foci - f) < dist_sq_foci) {
                i_foci = i;
                dist_sq_foci = (foci - f) * (foci - f);
            }
        }
        for (int i=0; i<10; i++) {
            double c = (i + 1) * cell_number_interval;
            if (dist_sq_cell_number == -1 || (cells - c) * (cells - c) < dist_sq_cell_number) {
                i_cell_number = i;
                dist_sq_cell_number = (cells - c) * (cells - c);
            }
        }
        return "<html><p>Cells: " + Integer.toString(cells) + " used, " + Integer.toString(cells_excluded) + " excluded" + "<br>Colocalization (Pearson, &lt;0.4 bad): " + Double.toString(pearson) + "<br><br>Difference to perfect Poisson:<br>KL-Divergence: " + Double.toString(HistAnalyzer.round_double(kl_divergence, digits)) + " (ideally &lt;"+Double.toString(HistAnalyzer.round_double(kl_div_95_arr[i_foci][i_cell_number], digits))+")<br>Residual squares: " + Double.toString(HistAnalyzer.round_double(r_squares, digits)) + " (ideally  &lt;"+Double.toString(HistAnalyzer.round_double(least_squares_95_arr[i_foci][i_cell_number], digits))+")<br><br>(Ideal values are based on a 95% confidence<br> interval for "+ Integer.toString((i_cell_number+1) * cell_number_interval) + " cells and " +Double.toString((i_foci+1) * foci_interval)+ " foci/cell.)</p></html>";
    }
}