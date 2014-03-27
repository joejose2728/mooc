package poke.server.election;

import eye.Comm.LeaderElection.VoteAction;

public class ElectionData {

	private VoteAction voteAction;
	private String node;
	
	public ElectionData(VoteAction voteAction, String node) {
		this.voteAction = voteAction;
		this.node = node;
	}

	public VoteAction getVoteAction() {
		return voteAction;
	}

	public String getNode() {
		return node;
	}
}
