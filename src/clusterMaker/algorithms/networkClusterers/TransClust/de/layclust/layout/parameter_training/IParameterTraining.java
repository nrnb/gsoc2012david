/**
 * 
 */
package clusterMaker.algorithms.networkClusterers.TransClust.de.layclust.layout.parameter_training;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import clusterMaker.algorithms.networkClusterers.TransClust.de.layclust.datastructure.ConnectedComponent;
import clusterMaker.algorithms.networkClusterers.TransClust.de.layclust.layout.IParameters;
import clusterMaker.algorithms.networkClusterers.TransClust.de.layclust.layout.LayoutFactory;

/**
 * @author sita
 *
 */
public interface IParameterTraining {
	
	
	public void initialise(LayoutFactory.EnumLayouterClass enumType, int generationsSize, 
			int noOfGenerations);
	
	public IParameters run(ConnectedComponent cc);
	
	public void setMaxThreadSemaphoreAndThreadsList(Semaphore semaphore, ArrayList<Thread> allThreads);

}
