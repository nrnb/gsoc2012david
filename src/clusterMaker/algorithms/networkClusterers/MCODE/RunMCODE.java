package clusterMaker.algorithms.networkClusterers.MCODE;

import cytoscape.CyNetwork;
import cytoscape.logger.CyLogger;
import cytoscape.task.TaskMonitor;

import clusterMaker.algorithms.NodeCluster;

import java.util.List;

/**
 * Copyright (c) 2004 Memorial Sloan-Kettering Cancer Center
 * *
 * * Code written by: Gary Bader
 * * Authors: Gary Bader, Ethan Cerami, Chris Sander
 * *
 * * This library is free software; you can redistribute it and/or modify it
 * * under the terms of the GNU Lesser General Public License as published
 * * by the Free Software Foundation; either version 2.1 of the License, or
 * * any later version.
 * *
 * * This library is distributed in the hope that it will be useful, but
 * * WITHOUT ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF
 * * MERCHANTABILITY OR FITNESS FOR A PARTICULAR PURPOSE.  The software and
 * * documentation provided hereunder is on an "as is" basis, and
 * * Memorial Sloan-Kettering Cancer Center
 * * has no obligations to provide maintenance, support,
 * * updates, enhancements or modifications.  In no event shall the
 * * Memorial Sloan-Kettering Cancer Center
 * * be liable to any party for direct, indirect, special,
 * * incidental or consequential damages, including lost profits, arising
 * * out of the use of this software and its documentation, even if
 * * Memorial Sloan-Kettering Cancer Center
 * * has been advised of the possibility of such damage.  See
 * * the GNU Lesser General Public License for more details.
 * *
 * * You should have received a copy of the GNU Lesser General Public License
 * * along with this library; if not, write to the Free Software Foundation,
 * * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA.
 * *
 * * User: GaryBader
 * * Date: Jan 25, 2005
 * * Time: 8:41:53 PM
 * * Description: MCODE Score network and find cluster task
 */

/**
 * MCODE Score network and find cluster task.
 */
public class RunMCODE {
	private TaskMonitor taskMonitor = null;
	private boolean interrupted = false;
	private CyNetwork network = null;
	private MCODEAlgorithm alg = null;
	private boolean completedSuccessfully = false;
	private int analyze;
	private String resultSet;
	private CyLogger logger;

	/**
	 * Scores and finds clusters in a given network
	 *
	 * @param network The network to cluster
	 * @param analyze Tells the task if we need to rescore and/or refind
	 * @param resultSet Identifier of the current result set
	 * @param alg reference to the algorithm for this network
	 */
	public RunMCODE(CyLogger logger, int analyze, String resultSet, CyNetwork network) {
		this.logger = logger;
		this.analyze = analyze;
		this.resultSet = resultSet;
		this.network = network;
		this.alg = new MCODEAlgorithm(network.getIdentifier(), logger);
	}

	/**
	 * Run MCODE (Both score and find steps)
	 */
	public List<NodeCluster> run(TaskMonitor monitor) {
		try {
			//run MCODE scoring algorithm - node scores are saved in the alg object
			alg.setTaskMonitor(monitor, network.getIdentifier());
			//only (re)score the graph if the scoring parameters have been changed
			if (analyze == MCODECluster.RESCORE) {
				monitor.setPercentCompleted(0);
				monitor.setStatus("Scoring Network (Step 1 of 3)");
				alg.scoreGraph(network, resultSet);
				if (interrupted) {
					return null;
				}
				logger.info("Network was scored in " + alg.getLastScoreTime() + " ms.");
			}

			monitor.setPercentCompleted(0);
			monitor.setStatus("Finding Clusters (Step 2 of 3)");

			List<NodeCluster> clusters = alg.findClusters(network, resultSet);

			if (interrupted) {
				return null;
			}

			monitor.setPercentCompleted(0);
			monitor.setStatus("Drawing Results (Step 3 of 3)");
			return clusters;
		} catch (Exception e) {
			//TODO: ask Ethan if interrupt exception should be thrown from within code or should 'return' just be used?
			monitor.setException(e, "MCODE cancelled");
			logger.error("MCODE Error: ", e);
			return null;
		}
	}

	/**
	 * Non-blocking call to interrupt the task.
	 */
	public void halt() {
		this.interrupted = true;
		alg.setCancelled(true);
	}

	/**
	 * Gets the Task Title.
	 *
	 * @return human readable task title.
	 */
	public String getTitle() {
		return new String("MCODE Network Cluster Detection");
	}
	
	public MCODEAlgorithm getAlg() {
		return alg;
	}
}
