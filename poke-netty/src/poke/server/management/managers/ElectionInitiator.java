package poke.server.management.managers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.management.managers.ElectionManager.ElectionStatus;
import poke.server.management.managers.ElectionManager.NodeStatus;


public class ElectionInitiator extends Thread {
	private static Logger logger = LoggerFactory.getLogger("management");
	private static final long pollTime = 5000;

	private boolean alreadyBroadcasted = false;

	public void run() {
		while (true){

			try {

				Thread.sleep(pollTime);

				ElectionManager electionManager = ElectionManager.getInstance();
				if (electionManager.getLeaderNode() != null && 
						electionManager.getElectionStatus() == ElectionStatus.Announced)
					break;

				logger.info("Election status - " + electionManager.getElectionStatus() + ", Leader - " + electionManager.getLeaderNode());

				//node has neighbors but leader is unknown
				if (alreadyBroadcasted && electionManager.getNodeStatus() == NodeStatus.Cluster
						&& electionManager.getElectionStatus() == ElectionStatus.Unkown && 
						electionManager.getLeaderNode() == null){
					// broadcasted but leader not in cluster. declare new election
					if (electionManager.getElectionStatus() != ElectionStatus.Election) {
						electionManager.declareElection();
						electionManager.nominateSelfToHigherNodes();
					}

				} else if (!alreadyBroadcasted && electionManager.getNodeStatus() == NodeStatus.Cluster
						&& electionManager.getElectionStatus() == ElectionStatus.Unkown && 
						electionManager.getLeaderNode() == null){
					//ask each node for leader
					electionManager.broadcastForLeader();
					alreadyBroadcasted = true;
				} 
			} catch (InterruptedException e) {
				e.printStackTrace();
			}        	  
		}
	}
}
