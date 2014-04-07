package poke.server.election;

import eye.Comm.LeaderElection.VoteAction;

public class LCR implements Election {

	private String nodeId;
	
	public LCR(String nodeId){
		this.nodeId = nodeId;
	}
	
	public ElectionData nominate() {
		return new ElectionData(VoteAction.NOMINATE, nodeId);
	}

	public ElectionData nominate(String requestNode) {
		/*Integer reqNode = Integer.parseInt(requestNode);
		Integer self = Integer.parseInt(nodeId);*/
		
		int compareTo = requestNode.compareTo(nodeId);
		if (compareTo == 1){
			//requested node has higher priority.
			return new ElectionData(VoteAction.NOMINATE, requestNode);
		}  else if (compareTo == -1){
			//I have higher priority. Nominate myself
			return nominate();
		} else {
			//I am the leader
			return new ElectionData(VoteAction.DECLAREWINNER, nodeId);
		}
	}
}
