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
package clusterMaker.ui;

import java.awt.Color;
import java.awt.Paint;

import java.beans.PropertyChangeSupport;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.swing.JPanel;

// Cytoscape imports
import cytoscape.Cytoscape;
import cytoscape.CyNetwork;
import cytoscape.CyNode;
import cytoscape.CyEdge;
import cytoscape.command.CyCommandException;
import cytoscape.command.CyCommandResult;
import cytoscape.data.CyAttributes;
import cytoscape.layout.CyLayoutAlgorithm;
import cytoscape.layout.CyLayouts;
import cytoscape.layout.Tunable;
import cytoscape.logger.CyLogger;
import cytoscape.task.Task;
import cytoscape.task.TaskMonitor;
import cytoscape.task.ui.JTaskConfig;
import cytoscape.task.util.TaskManager;
import cytoscape.view.CyEdgeView;
import cytoscape.view.CyNetworkView;
import cytoscape.view.CytoscapeDesktop;

import cytoscape.visual.CalculatorCatalog;
import cytoscape.visual.EdgeAppearanceCalculator;
import cytoscape.visual.VisualMappingManager;
import cytoscape.visual.VisualPropertyType;
import cytoscape.visual.VisualStyle;
import cytoscape.visual.calculators.BasicCalculator;
import cytoscape.visual.calculators.Calculator;
import cytoscape.visual.mappings.DiscreteMapping;
import cytoscape.visual.mappings.ObjectMapping;

import giny.view.EdgeView;

// ClusterMaker imports
import clusterMaker.ClusterMaker;
import clusterMaker.algorithms.edgeConverters.EdgeAttributeHandler;
import clusterMaker.algorithms.edgeConverters.EdgeWeightConverter;
import clusterMaker.algorithms.networkClusterers.AbstractNetworkClusterer;
import clusterMaker.algorithms.ClusterAlgorithm;
import clusterMaker.algorithms.ClusterProperties;
import clusterMaker.algorithms.ClusterResults;
import clusterMaker.ui.ClusterTask;

/**
 * The ClusterViz class provides the primary interface to the
 * Cytoscape plugin mechanism
 */
public class NewNetworkView implements ClusterViz, ClusterAlgorithm {

	private static String appName = "ClusterMaker New Network View";
	private boolean selectedOnly = false;
	private boolean restoreEdges = false;
	private boolean checkForAvailability = false;
	private String clusterAttribute = null;
	private CyLogger myLogger = null;
	private ClusterProperties clusterProperties = null;
	private String[] attributeArray = new String[1];
	protected PropertyChangeSupport pcs;

	public NewNetworkView() {
		this(false);
	}

	public NewNetworkView(boolean available) {
		super();
		initialize();
		checkForAvailability = available;
	}

	public void setVisible(boolean visibility) {
	}

	public String getAppName() {
		return appName;
	}

	// ClusterViz methods
	public String getShortName() { return "newnetworkview"; }

	public String getName() { 
		if (checkForAvailability) {
			return "Create New Network from Clusters";
		} else {
			return "Create New Network from Attribute"; 
		}
	}

	public JTaskConfig getDefaultTaskConfig() { return ClusterTask.getDefaultTaskConfig(false); }

	public ClusterResults getResults() { return null; }

	public CyCommandResult startViz() throws CyCommandException {
		CyCommandResult result = new CyCommandResult();
		updateSettings();
		if (clusterAttribute == null || clusterAttribute.length() == 0) {
			if (!isAvailable())
				throw new CyCommandException("No attribute specified and no previous network attribute run found");
		}
		startup();
		result.addMessage("New network for attribute "+clusterAttribute+" created");
		return result;
	}

	public boolean isAvailable() {
		if (!checkForAvailability)
			return true;
		
		CyNetwork network = Cytoscape.getCurrentNetwork();
		CyAttributes networkAttributes = Cytoscape.getNetworkAttributes();
		String netId = network.getIdentifier();
		if (!networkAttributes.hasAttribute(netId, ClusterMaker.CLUSTER_TYPE_ATTRIBUTE)) {
			return false;
		}

		String cluster_type = networkAttributes.getStringAttribute(netId, ClusterMaker.CLUSTER_TYPE_ATTRIBUTE);
		ClusterMaker instance = ClusterMaker.getInstance();
		if (!(instance.getAlgorithm(cluster_type) instanceof AbstractNetworkClusterer))
			return false;

		if (networkAttributes.hasAttribute(netId, ClusterMaker.CLUSTER_ATTRIBUTE)) {
			clusterAttribute = networkAttributes.getStringAttribute(netId, ClusterMaker.CLUSTER_ATTRIBUTE);
			return true;
		}
		return false;
	}

	private void startup() {
		// Set up a new task
		CreateNetworkTask task = new CreateNetworkTask(clusterAttribute);
		TaskManager.executeTask( task, ClusterTask.getDefaultTaskConfig(false) );
	}

	protected void initialize() {
		myLogger = CyLogger.getLogger(TreeView.class);
		clusterProperties = new ClusterProperties(getShortName());
		pcs = new PropertyChangeSupport(new Object());
		initializeProperties();
	}

	// ClusterAlgorithm methods
	public void initializeProperties() {
		// The attribute to use to get the weights
		attributeArray = getAllAttributes();

		clusterProperties.add(new Tunable("attribute",
		                                  "Cluster Attribute to Use",
		                                  Tunable.LIST, new Integer(0),
		                                  (Object)attributeArray, (Object)null, 0));

		clusterProperties.add(new Tunable("selectedOnly",
		                                  "Display only selected nodes (or edges)",
		                                  Tunable.BOOLEAN, new Boolean(false)));

		clusterProperties.add(new Tunable("restoreEdges",
		                                  "Restore inter-cluster edges after layout",
		                                  Tunable.BOOLEAN, new Boolean(false)));

		clusterProperties.initializeProperties();
		updateSettings(true);
	}

	public void updateSettings() {
		updateSettings(false);
	}

	public void updateSettings(boolean force) {
		clusterProperties.updateValues();

		Tunable t = clusterProperties.get("attribute");
		if ((t != null) && (t.valueChanged() || force)) {
			int val = ((Integer) t.getValue()).intValue();
			clusterAttribute = attributeArray[val];
		} else if (clusterAttribute == null && attributeArray.length > 0) {
			clusterAttribute = attributeArray[0];
		}

		t = clusterProperties.get("selectedOnly");
		if ((t != null) && (t.valueChanged() || force))
			selectedOnly = ((Boolean) t.getValue()).booleanValue();

		t = clusterProperties.get("restoreEdges");
		if ((t != null) && (t.valueChanged() || force))
			restoreEdges = ((Boolean) t.getValue()).booleanValue();
	}

	public JPanel getSettingsPanel() {
		// Everytime we ask for the panel, we want to update our attributes
		Tunable attributeTunable = clusterProperties.get("attribute");
		attributeArray = getAllAttributes();
		attributeTunable.setLowerBound((Object)attributeArray);

		return clusterProperties.getTunablePanel();
	}

	public ClusterViz getVisualizer() {
		return this;
	}

	public void doCluster(TaskMonitor monitor) {
		return;
	}

	public void revertSettings() {
		clusterProperties.revertProperties();
	}

	public ClusterProperties getSettings() {
		return clusterProperties;
	}

	public String toString() { return getName(); }

	public void halt() { }

	public PropertyChangeSupport getPropertyChangeSupport() {return pcs;}

	@SuppressWarnings("unchecked")
	private void createClusteredNetwork(String clusterAttribute) {
		CyNetwork currentNetwork = Cytoscape.getCurrentNetwork();
		CyAttributes nodeAttributes = Cytoscape.getNodeAttributes();
		CyAttributes edgeAttributes = Cytoscape.getEdgeAttributes();
		CyAttributes netAttributes = Cytoscape.getNetworkAttributes();

		// Get the clustering parameters
		List<String> params = null;
		if (netAttributes.hasAttribute(currentNetwork.getIdentifier(), ClusterMaker.CLUSTER_PARAMS_ATTRIBUTE))
			params = netAttributes.getListAttribute(currentNetwork.getIdentifier(), 
			                                        ClusterMaker.CLUSTER_PARAMS_ATTRIBUTE);
		else
			params = new ArrayList<String>();

		// Create the new network
		CyNetwork net = Cytoscape.createNetwork(currentNetwork.getTitle()+"--clustered",currentNetwork,false);
		if (params.size() > 0)
			netAttributes.setListAttribute(net.getIdentifier(), ClusterMaker.CLUSTER_PARAMS_ATTRIBUTE, params);

		// Create the cluster Map
		HashMap<Object, List<CyNode>> clusterMap = new HashMap<Object, List<CyNode>>();
		for (CyNode node: (List<CyNode>)currentNetwork.nodesList()) {
			// For each node -- see if it's in a cluster.  If so, add it to our map
			if (nodeAttributes.hasAttribute(node.getIdentifier(), clusterAttribute)) {
				Object cluster = nodeAttributes.getAttribute(node.getIdentifier(), clusterAttribute);
				switch (nodeAttributes.getType(clusterAttribute)) {
				case CyAttributes.TYPE_SIMPLE_LIST:
					// As a convenience, take the first element of the list
					List lObj = nodeAttributes.getListAttribute(node.getIdentifier(), clusterAttribute);
					if (lObj != null && lObj.size() > 0)
						cluster = lObj.get(0);
					else
						continue;
				}
				if (!clusterMap.containsKey(cluster)) {
					// System.out.println("Creating new entry for: "+cluster.toString());
					clusterMap.put(cluster, new ArrayList<CyNode>());
				}
				// System.out.println("Adding node "+node+" to "+cluster.toString());
				clusterMap.get(cluster).add(node);
			}
			net.addNode(node);
		}

		// Special handling for edge weight thresholds
		EdgeWeightConverter converter = null;
		String dataAttribute = null;
		double cutOff = 0.0;
		for (String param: params) {
			if (param.startsWith("converter")) {
				String[] conv = param.split("=");
				converter = new EdgeAttributeHandler(null, false).getConverter(conv[1]);
			} else if (param.startsWith("edgeCutOff")) {
				String[] cut = param.split("=");
				cutOff = Double.parseDouble(cut[1]);
			} else if (param.startsWith("dataAttribute")) {
				String[] attr = param.split("=");
				dataAttribute = attr[1];
			}
		}

		HashMap<CyEdge,CyEdge> edgeMap = new HashMap<CyEdge,CyEdge>();
		for (Object cluster: clusterMap.keySet()) {
			// Get the list of nodes
			List<CyNode> nodeList = clusterMap.get(cluster); 
			// Get the list of edges
			List<CyEdge> edgeList = currentNetwork.getConnectingEdges(nodeList);
			for (CyEdge edge: edgeList) { 
				if (converter != null && dataAttribute != null) {
					if (edgeWeightCheck(edgeAttributes, edge, dataAttribute, converter, cutOff)) 
						continue;
				}
				net.addEdge(edge); 
				edgeMap.put(edge,edge);
				// Add the cluster attribute to the edge so we can style it later
				edgeAttributes.setAttribute(edge.getIdentifier(), clusterAttribute, new Integer(1));
			}
		}

		// Create the network view
		CyNetworkView view = Cytoscape.createNetworkView(net);

		// OK, now we need to explicitly remove any edges from our old network
		// that should not be in the new network (why is this necessary????)
		// for (CyEdge edge: edges) {
		// 	if (!edgeMap.containsKey(edge))
		// 		net.hideEdge(edge);
		// }

		// If available, do a force-directed layout
		CyLayoutAlgorithm alg = CyLayouts.getLayout("force-directed");

		if (alg != null)
			view.applyLayout(alg);

		// Get the current visual mapper
		VisualStyle vm = Cytoscape.getVisualMappingManager().getVisualStyle();

		// Now, if we're supposed to, restore the inter-cluster edges
		if (restoreEdges) {
			// Create new visual style
			vm = createNewStyle(clusterAttribute, "-cluster");

			// Add edge width and opacity descrete mappers
			for (CyEdge edge: (List<CyEdge>)currentNetwork.edgesList()) {
				if (!edgeMap.containsKey(edge)) {
					net.addEdge(edge);
					edgeAttributes.setAttribute(edge.getIdentifier(), clusterAttribute, new Integer(0));
				}
			}
		}

		view.applyVizmapper(vm);

		Cytoscape.setCurrentNetwork(net.getIdentifier());
		Cytoscape.setCurrentNetworkView(view.getIdentifier());
		return;
	}

	private boolean edgeWeightCheck(CyAttributes edgeAttributes, CyEdge edge, String dataAttribute,
	                                EdgeWeightConverter converter, double cutoff) {
		if (!edgeAttributes.hasAttribute(edge.getIdentifier(), dataAttribute))
			return false;
		byte type = edgeAttributes.getType(dataAttribute);
		double val;
		if (type == CyAttributes.TYPE_FLOATING)
			val = edgeAttributes.getDoubleAttribute(edge.getIdentifier(), dataAttribute).doubleValue();
		else if (type == CyAttributes.TYPE_INTEGER)
			val = edgeAttributes.getIntegerAttribute(edge.getIdentifier(), dataAttribute).doubleValue();
		else
			return false;

		if (converter.convert(val, 0.0, Double.MAX_VALUE) > cutoff)
			return false;

		return true;
	}

	@SuppressWarnings("deprecation")
	private VisualStyle createNewStyle(String attribute, String suffix) { 
		boolean newStyle = false;

		// Get our current vizmap
		VisualMappingManager manager = Cytoscape.getVisualMappingManager();
		CalculatorCatalog calculatorCatalog = manager.getCalculatorCatalog();

		// Get the current style
		VisualStyle style = Cytoscape.getCurrentNetworkView().getVisualStyle();
		// Create a new vizmap
		Set<String> styles = calculatorCatalog.getVisualStyleNames();
		if (styles.contains(style.getName()+suffix))
			style = calculatorCatalog.getVisualStyle(style.getName()+suffix);
		else {
			style = new VisualStyle(style, style.getName()+suffix);
			newStyle = true;
		}

		// Set up our line width descrete mapper
		DiscreteMapping lineWidth = new DiscreteMapping(new Double(.5), attribute, ObjectMapping.EDGE_MAPPING);
		lineWidth.putMapValue(new Integer(0), new Double(1));
		lineWidth.putMapValue(new Integer(1), new Double(5));
   	Calculator widthCalculator = new BasicCalculator("Edge Width Calculator",
		                                                 lineWidth, VisualPropertyType.EDGE_LINE_WIDTH);

		DiscreteMapping lineOpacity = new DiscreteMapping(new Integer(50), attribute, ObjectMapping.EDGE_MAPPING);
		lineOpacity.putMapValue(new Integer(0), new Integer(50));
		lineOpacity.putMapValue(new Integer(1), new Integer(255));
   	Calculator opacityCalculator = new BasicCalculator("Edge Opacity Calculator",
		                                                 lineOpacity, VisualPropertyType.EDGE_OPACITY);

		
		EdgeAppearanceCalculator edgeAppCalc = style.getEdgeAppearanceCalculator();
   	edgeAppCalc.setCalculator(widthCalculator);
   	edgeAppCalc.setCalculator(opacityCalculator);
		style.setEdgeAppearanceCalculator(edgeAppCalc);
		if (newStyle) {
			calculatorCatalog.addVisualStyle(style);
			manager.setVisualStyle(style);
		} 
		return style;
	}

	private void getAttributesList(List<String>attributeList, CyAttributes attributes, 
	                              String prefix) {
		String[] names = attributes.getAttributeNames();
		for (int i = 0; i < names.length; i++) {
			if (attributes.getType(names[i]) == CyAttributes.TYPE_FLOATING ||
			    attributes.getType(names[i]) == CyAttributes.TYPE_INTEGER ||
			    attributes.getType(names[i]) == CyAttributes.TYPE_BOOLEAN ||
			    attributes.getType(names[i]) == CyAttributes.TYPE_STRING ||
			    attributes.getType(names[i]) == CyAttributes.TYPE_SIMPLE_LIST) {
				attributeList.add(prefix+names[i]);
			}
		}
	}

	private String[] getAllAttributes() {
		attributeArray = new String[1];
		// Create the list by combining node and edge attributes into a single list
		List<String> attributeList = new ArrayList<String>();
		getAttributesList(attributeList, Cytoscape.getNodeAttributes(),"");
		String[] attrArray = attributeList.toArray(attributeArray);
		Arrays.sort(attrArray);
		return attrArray;
	}

	private class CreateNetworkTask implements Task {
		TaskMonitor monitor;
		String attribute;

		public CreateNetworkTask(String attribute) {
			this.attribute = attribute;
		}

		public void setTaskMonitor(TaskMonitor monitor) {
			this.monitor = monitor;
		}

		public void run() {
			createClusteredNetwork(attribute);
		}

		public void halt() {
		}

		public String getTitle() {
			return "Creating new network";
		}

	}

}
