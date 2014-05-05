/**
 * 
 */
package in.ac.iitk.cse.mti.aritra.amrs;

import in.ac.iitk.cse.mti.aritra.amrs.vendor.HungarianAlgorithmEdu;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * @author aritra
 * 
 */
public class Recommend {

	public Recommend() {
		double[][] costs = { { 1, 3, 3 }, { 3, 2, 3 }, { 3, 3, 2 } };
		System.out.println(getCost(costs));
	}

	private double getCost(double[][] costs) {
		PrintStream originalStream = System.out;
		PrintStream dummyStream = new PrintStream(new OutputStream() {
			@Override
			public void write(int b) {
			}
		});

		System.setOut(dummyStream);
		int[][] result = HungarianAlgorithmEdu.hgAlgorithm(costs, "min");
		System.setOut(originalStream);

		double cost = 0;
		for (int[] row : result) {
			cost += costs[row[0]][row[1]];
		}
		return cost;
	}

	private void print(int[][] result) {
		for (int[] element : result) {
			for (int element2 : element) {
				System.out.print(element2 + " ");
			}
			System.out.println();
		}
	}

	private void print(double[][] result) {
		for (double[] element : result) {
			for (double element2 : element) {
				System.out.print(element2 + " ");
			}
			System.out.println();
		}
	}
}
