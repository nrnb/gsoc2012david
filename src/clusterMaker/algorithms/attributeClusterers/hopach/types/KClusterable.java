package clusterMaker.algorithms.attributeClusterers.hopach.types;

import clusterMaker.algorithms.attributeClusterers.Clusters;

/**
 * KClusterable is an interface for clustering data into k clusters.
 * @author djh.shih
 *
 */
public interface KClusterable {
	
	/**
	 * Cluster into k clusters.
	 * @param k number of clusters
	 * @return clustering results
	 */
	public Clusters cluster(int k);
	
	/**
	 * Number of elements.
	 * @return number of elements
	 */
	public int size();
}
