/* vim: set ts=2: */
/**
 * Copyright (c) 2008 The Regents of the University of California.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *   1. Redistributions of source code must retain the above copyright
 *      notice, this list of conditions, and the following disclaimer.
 *   2. Redistributions in binary form must reproduce the above
 *      copyright notice, this list of conditions, and the following
 *      disclaimer in the documentation and/or other materials provided
 *      with the distribution.
 *   3. Redistributions must acknowledge that this software was
 *      originally developed by the UCSF Computer Graphics Laboratory
 *      under support by the NIH National Center for Research Resources,
 *      grant P41-RR01081.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDER "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package clusterMaker.algorithms.attributeClusterers.kmeans;

import java.awt.GridLayout;
import java.lang.Math;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import javax.swing.JPanel;

// Cytoscape imports
import cytoscape.Cytoscape;
import cytoscape.CyNode;
import cytoscape.data.CyAttributes;
import cytoscape.layout.Tunable;
import cytoscape.logger.CyLogger;
import cytoscape.task.TaskMonitor;
import cytoscape.groups.CyGroup;
import cytoscape.groups.CyGroupManager;

// clusterMaker imports
import clusterMaker.ClusterMaker;
import clusterMaker.algorithms.attributeClusterers.AbstractAttributeClusterAlgorithm;
import clusterMaker.algorithms.attributeClusterers.DistanceMetric;
import clusterMaker.algorithms.attributeClusterers.Matrix;
// import clusterMaker.algorithms.attributeClusterers.silhouette.SilhouetteResult;
// import clusterMaker.algorithms.attributeClusterers.silhouette.SilhouetteUtil;

public class KCluster extends AbstractAttributeClusterAlgorithm {
	Random random = null;
	HashMap<String,List<CyNode>> groupMap = null;

	public KCluster(String weightAttributes[], DistanceMetric metric, CyLogger log, TaskMonitor monitor) {
		this.logger = log;
		this.weightAttributes = weightAttributes;
		this.metric = metric;
		this.monitor = monitor;
		resetAttributes();
	}

	// The kmeans implementation of a k-clusterer
	public int kcluster(int nClusters, int nIterations, Matrix matrix, DistanceMetric metric, int[] clusterID) {
		if (monitor != null)
			monitor.setPercentCompleted(0);

		// System.out.println("RUnning kmeans with "+nClusters+" clusters");

		random = null;
		int nelements = matrix.nRows();
		int ifound = 1;

		int[] tclusterid = new int[nelements];

		int[] saved = new int[nelements];

		int[] mapping = new int[nClusters];
		int[] counts = new int[nClusters];

		double error = Double.MAX_VALUE;

		// This matrix will store the centroid data
		Matrix cData = new Matrix(nClusters, matrix.nColumns());

		// Outer initialization
		if (nIterations <= 1) {
			for (int i=0; i < clusterID.length; i++) {
				tclusterid[i] = clusterID[i];
			}
			nIterations = 1;
		} else {
			for (int i = 0; i < nelements; i++) 
				clusterID[i] = 0;
		}

		int iteration = 0;
		do {

			if (monitor != null)
				monitor.setPercentCompleted((int)(((double)iteration/(double)nIterations)*100));

			double total = Double.MAX_VALUE;
			int counter = 0;
			int period = 10;

			// Randomly assign elements to clusters
			if (nIterations != 0) {
				if (!initializeNearCenter) {
					// Use the cluster 3.0 version to be consistent
					randomAssign(nClusters, nelements, tclusterid);
					// if (nIterations != 0) debugAssign(nClusters, nelements, tclusterid);
				} else {
					tclusterid = chooseCentralElementsAsCenters(nelements, nClusters, matrix.getDistanceMatrix(metric));
				}
			}

			// Initialize
			for (int i = 0; i < nClusters; i++) counts[i] = 0;
			for (int i = 0; i < nelements; i++) counts[tclusterid[i]]++;

			while (true) {
				double previous = total;
				total = 0.0;
				if (counter % period == 0) // Save the current cluster assignments
				{
					for (int i = 0; i < nelements; i++)
						saved[i] = tclusterid[i];
					if (period < Integer.MAX_VALUE / 2) 
						period *= 2;
				}
				counter++;

				// Find the center
				getClusterMeans(nClusters, matrix, cData, tclusterid);

				/*
				for (int i = 0; i < nClusters; i++) {
					System.out.print("cluster "+i+": ");
					for (int j = 0; j < matrix.nColumns(); j++) {
						System.out.print(cData.getValue(i,j)+"\t");
					}
					System.out.println();
				}
				*/

				for (int i = 0; i < nelements; i++) {
					// Calculate the distances
					double distance;
					int k = tclusterid[i];
					if (counts[k]==1) continue;

					// Get the distance
					// distance = metric(ndata,data,cdata,mask,cmask,weight,i,k,transpose);
					distance = metric.getMetric(matrix, cData, matrix.getWeights(), i, k);
					for (int j = 0; j < nClusters; j++) { 
						double tdistance;
						if (j==k) continue;
						// tdistance = metric(ndata,data,cdata,mask,cmask,weight,i,j,transpose);
						tdistance = metric.getMetric(matrix, cData, matrix.getWeights(), i, j);
						if (tdistance < distance) 
						{ 
							distance = tdistance;
            	counts[tclusterid[i]]--;
            	tclusterid[i] = j;
            	counts[j]++;
						}
          }
        	total += distance;
        }
				// System.out.println("total = "+total+", previous = "+previous);
      	if (total>=previous) break;
      	/* total>=previous is FALSE on some machines even if total and previous
				 * are bitwise identical. */
				int i;
	      for (i = 0; i < nelements; i++)
	        if (saved[i]!=tclusterid[i]) break;
	      if (i==nelements)
	        break; /* Identical solution found; break out of this loop */
    	}

			if (nIterations<=1)
			{ error = total;
				break;
			}

			for (int i = 0; i < nClusters; i++) mapping[i] = -1;

			int element = 0;
			for (element = 0; element < nelements; element++)
			{ 
				int j = tclusterid[element];
				int k = clusterID[element];
				if (mapping[k] == -1) 
					mapping[k] = j;
				else if (mapping[k] != j)
      	{ 
					if (total < error)
        	{ 
						ifound = 1;
          	error = total;
						// System.out.println("Mapping tclusterid to clusterid");
          	for (int i = 0; i < nelements; i++) clusterID[i] = tclusterid[i];
        	}
        	break;
      	}
    	}
    	if (element==nelements) ifound++; /* break statement not encountered */
  	} while (++iteration < nIterations);

		// System.out.println("ifound = "+ifound+", error = "+error);
  	return ifound;
	}

	public static void getClusterMeans(int nClusters, Matrix data, Matrix cdata, int[] clusterid) {

		double[][]cmask = new double[nClusters][cdata.nColumns()];

		for (int i = 0; i < nClusters; i++) {
			for (int j = 0; j < data.nColumns(); j++) {
				cdata.setValue(i, j, null);
				cmask[i][j] = 0.0;
			}
		}

		for (int k = 0; k < data.nRows(); k++) {
			int i = clusterid[k];
			for (int j = 0; j < data.nColumns(); j++) {
				if (data.hasValue(k,j)) {
					double cValue = 0.0;
					double dataValue = data.getValue(k,j).doubleValue();
					if (cdata.hasValue(i,j)) {
						cValue = cdata.getValue(i,j).doubleValue();
					}
					cdata.setValue(i,j, Double.valueOf(cValue+dataValue));
					cmask[i][j] = cmask[i][j] + 1.0;
				}
			}
		}
		for (int i = 0; i < nClusters; i++) {
			for (int j = 0; j < data.nColumns(); j++) {
				if (cmask[i][j] > 0.0) {
					double cData = cdata.getValue(i,j).doubleValue() / cmask[i][j];
					cdata.setValue(i,j,Double.valueOf(cData));
				}
			}
		}
	}

	private void randomAssign (int nClusters, int nElements, int[] clusterID) {
		int n = nElements - nClusters;
		int k = 0;
		int i = 0;
		for (i = 0; i < nClusters-1; i++) {
			double p = 1.0/(nClusters-1);
			int j = binomial(n, p);
			n -= j;
			j += k+1; // Assign at least one element to cluster i
			for (;k<j; k++) clusterID[k] = i;
		}
		// Assign the remaining elements to the last cluster
		for (; k < nElements; k++) clusterID[k] = i;

		// Create a random permutation of the cluster assignments
		for (i = 0; i < nElements; i++) {
			int j = (int) (i + (nElements-i)*uniform());
			k = clusterID[j];
			clusterID[j] = clusterID[i];
			clusterID[i] = k;
		}
	}

	// Debug version of "randomAssign" that isn't random
	private void debugAssign (int nClusters, int nElements, int[] clusterID) {
		for (int element = 0; element < nElements; element++) {
			clusterID[element] = element%nClusters;
		}
	}

	/**
	 * This routine generates a random number between 0 and n inclusive, following
	 * the binomial distribution with probability p and n trials. The routine is
	 * based on the BTPE algorithm, described in:
	 * 
	 * Voratas Kachitvichyanukul and Bruce W. Schmeiser:
	 * Binomial Random Variate Generation
	 * Communications of the ACM, Volume 31, Number 2, February 1988, pages 216-222.
	 * 
	 * @param p The probability of a single event.  This should be less than or equal to 0.5.
	 * @param n The number of trials
	 * @return An integer drawn from a binomial distribution with parameters (p, n).
	 */

	private int binomial (int n, double p) {
		double q = 1 - p;
		if (n*p < 30.0) /* Algorithm BINV */
		{ 
			double s = p/q;
			double a = (n+1)*s;
			double r = Math.exp(n*Math.log(q)); /* pow() causes a crash on AIX */
			int x = 0;
			double u = uniform();
			while(true)
			{ 
				if (u < r) return x;
				u-=r;
				x++;
				r *= (a/x)-s;
			}
		}
		else /* Algorithm BTPE */
		{ /* Step 0 */
			double fm = n*p + p;
			int m = (int) fm;
			double p1 = Math.floor(2.195*Math.sqrt(n*p*q) -4.6*q) + 0.5;
			double xm = m + 0.5;
			double xl = xm - p1;
			double xr = xm + p1;
			double c = 0.134 + 20.5/(15.3+m);
			double a = (fm-xl)/(fm-xl*p);
			double b = (xr-fm)/(xr*q);
			double lambdal = a*(1.0+0.5*a);
			double lambdar = b*(1.0+0.5*b);
			double p2 = p1*(1+2*c);
			double p3 = p2 + c/lambdal;
			double p4 = p3 + c/lambdar;
			while (true)
			{ /* Step 1 */
				int y;
				int k;
				double u = uniform();
				double v = uniform();
				u *= p4;
				if (u <= p1) return (int)(xm-p1*v+u);
				/* Step 2 */
				if (u > p2)
				{ /* Step 3 */
					if (u > p3)
					{ /* Step 4 */
						y = (int)(xr-Math.log(v)/lambdar);
						if (y > n) continue;
						/* Go to step 5 */
						v = v*(u-p3)*lambdar;
					}
					else
					{
						y = (int)(xl+Math.log(v)/lambdal);
						if (y < 0) continue;
						/* Go to step 5 */
						v = v*(u-p2)*lambdal;
					}
				}
				else
				{
					double x = xl + (u-p1)/c;
					v = v*c + 1.0 - Math.abs(m-x+0.5)/p1;
					if (v > 1) continue;
					/* Go to step 5 */
					y = (int)x;
				}
				/* Step 5 */
				/* Step 5.0 */
				k = Math.abs(y-m);
				if (k > 20 && k < 0.5*n*p*q-1.0)
				{ /* Step 5.2 */
					double rho = (k/(n*p*q))*((k*(k/3.0 + 0.625) + 0.1666666666666)/(n*p*q)+0.5);
					double t = -k*k/(2*n*p*q);
					double A = Math.log(v);
					if (A < t-rho) return y;
					else if (A > t+rho) continue;
					else
					{ /* Step 5.3 */
						double x1 = y+1;
						double f1 = m+1;
						double z = n+1-m;
						double w = n-y+1;
						double x2 = x1*x1;
						double f2 = f1*f1;
						double z2 = z*z;
						double w2 = w*w;
						if (A > xm * Math.log(f1/x1) + (n-m+0.5)*Math.log(z/w)
						      + (y-m)*Math.log(w*p/(x1*q))
						      + (13860.-(462.-(132.-(99.-140./f2)/f2)/f2)/f2)/f1/166320.
						      + (13860.-(462.-(132.-(99.-140./z2)/z2)/z2)/z2)/z/166320.
						      + (13860.-(462.-(132.-(99.-140./x2)/x2)/x2)/x2)/x1/166320.
						      + (13860.-(462.-(132.-(99.-140./w2)/w2)/w2)/w2)/w/166320.)
							continue;
						return y;
					}
				}
				else
				{ /* Step 5.1 */
					int i;
					double s = p/q;
					double aa = s*(n+1);
					double f = 1.0;
					for (i = m; i < y; f *= (aa/(++i)-s));
					for (i = y; i < m; f /= (aa/(++i)-s));
					if (v > f) continue;
					return y;
				}
			}
		}
	}

	private double uniform() {
		if (random == null) {
			// Date date = new Date();
			// random = new Random(date.getTime());
			// Use an unseeded random so that our silhouette results are comparable
			random = new Random();
		}
		return random.nextDouble();
	}

}
