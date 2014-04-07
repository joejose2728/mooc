/*
 * copyright 2014, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.server.management.managers;

import io.netty.channel.Channel;

import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.server.conf.NodeDesc;
import poke.server.election.ElectionData;

import com.google.protobuf.GeneratedMessage;

import eye.Comm.LeaderElection;
import eye.Comm.LeaderElection.VoteAction;
import eye.Comm.Management;

/**
 * The election manager is used to determine leadership within the network.
 * 
 * @author gash
 * 
 */
public class ElectionManager {
	protected static Logger logger = LoggerFactory.getLogger("management");
	protected static AtomicReference<ElectionManager> instance = new AtomicReference<ElectionManager>();

	public enum ElectionStatus { Unkown, Election, Nominated, Announced};
	public enum NodeStatus {Single, Cluster};

	private String nodeId;
	private String ballotId;
	private ElectionStatus electionStatus;
	private String leaderNode;
	private NodeStatus nodeStatus;
	private TreeMap<String,NodeDesc> nearestNodes;

	/** @brief the number of votes this server can cast */
	private int votes = 1;


	public static ElectionManager getInstance(String id, int votes) {
		instance.compareAndSet(null, new ElectionManager(id, votes));
		return instance.get();
	}

	public static ElectionManager getInstance() {
		return instance.get();
	}

	/**
	 * initialize the manager for this server
	 * 
	 * @param nodeId
	 *            The server's (this) ID
	 */
	protected ElectionManager(String nodeId, int votes) {
		this.nodeId = nodeId;
		this.ballotId = nodeId + "_ballot"; 
		this.electionStatus = ElectionStatus.Unkown;
		if (votes >= 0)
			this.votes = votes;
		this.nodeStatus = NodeStatus.Single; // when starts up default is single
		startElectionInitiator();
	}

	private void startElectionInitiator(){
		ElectionInitiator initiator = new ElectionInitiator();
		initiator.start();
	}
	/**
	 * @param args
	 */
	public void processRequest(LeaderElection req) {
		if (req == null)
			return;

		if (req.hasExpires()) {
			long ct = System.currentTimeMillis();
			if (ct > req.getExpires()) {
				// election is over
				return;
			}
		}

		GeneratedMessage msg = null;

		if (req.getVote().getNumber() == VoteAction.ELECTION_VALUE) {
			// an election is declared!
			if (electionStatus == ElectionStatus.Election)
				return;

			logger.info("Election declared by node - " + req.getNodeId());
			electionStatus = ElectionStatus.Election;

		} else if (req.getVote().getNumber() == VoteAction.DECLAREVOID_VALUE) {
			// no one was elected, I am dropping into standby mode`
		} else if (req.getVote().getNumber() == VoteAction.DECLAREWINNER_VALUE) {
			// some node declared themself the leader
			if (electionStatus != ElectionStatus.Announced){
				//leader elected
				electionStatus = ElectionStatus.Announced;
				leaderNode = req.getNodeId();
				logger.info("Winner elected - " + req.getNodeId());
			}

		} else if (req.getVote().getNumber() == VoteAction.ABSTAIN_VALUE) {
			// for some reason, I decline to vote
		} else if (req.getVote().getNumber() == VoteAction.NOMINATE_VALUE) {
				if (electionStatus == ElectionStatus.Election) {
				nominateSelfToHigherNodes();
			}

		} else if (req.getVote().getNumber() == VoteAction.LEADER_VALUE){
			logger.info("Leader request from - " + req.getNodeId());
			// request for current leader. reply if you know
			if (electionStatus ==
					ElectionStatus.Announced && leaderNode != null){
				NetworkChannelMap ncm = NetworkChannelMap.getInstance();
				msg = generateElectionMsg(new ElectionData(VoteAction.LEADERRESPONSE, leaderNode), "","");

				Channel requestedChannel = ncm.get(req.getNodeId());
				if (requestedChannel != null)
					requestedChannel.writeAndFlush(msg);

				return;
			}
		} else if (req.getVote().getNumber() == VoteAction.LEADERRESPONSE_VALUE){
			leaderNode = req.getNodeId();
			electionStatus = ElectionStatus.Announced;
		}
	}

    void nominateSelfToHigherNodes() {
		GeneratedMessage msg = generateElectionMsg(new ElectionData(VoteAction.NOMINATE, nodeId), ballotId, nodeId);
		NetworkChannelMap channels = NetworkChannelMap.getInstance();
		int k = 0;

		for (String node : channels.keySet()){
			int compareTo = node.compareTo(nodeId);
			if (compareTo == 1) {
				Channel ch = channels.get(node);
				if (ch != null)
					ch.writeAndFlush(msg);
				k++;
			} else if (compareTo == -1) {
				//bully
			}
		}

		if (k==0 && channels.size() > 0) { //all nodes are lower than me
			declareSelfAsWinner();
		}
	}

	public ElectionStatus getElectionStatus() {
		return electionStatus;
	}
	/**
	 * Change this server's status to single or cluster
	 * @param nodeStatus
	 */
	public void changeNodeStatus(NodeStatus nodeStatus){
		this.nodeStatus = nodeStatus;
	}

	public NodeStatus getNodeStatus(){
		return nodeStatus;
	}

	public String getLeaderNode() {
		return leaderNode;
	}

	public void setNearestNodes(TreeMap<String,NodeDesc> nearestNodes){
		this.nearestNodes = nearestNodes;
	}

	/**
	 * Transmit the message to all nearest nodes except the requester
	 * @param msg
	 * @param req
	 */
	private void transmit(GeneratedMessage msg, LeaderElection req){
		NetworkChannelMap channels = NetworkChannelMap.getInstance();
		for (NodeDesc node: nearestNodes.values()){
			//no point in transmitting to the requested node in complete undirected graph
			if (req != null && req.getNodeId().equals(node.getNodeId())) 
				continue; 

			//logger.info("Transmitting election message to " + node.getNodeId());
			Channel ch = channels.get(node.getNodeId());
			if (ch != null)
				ch.writeAndFlush(msg);
		}
	}

	public boolean hasLeader(){
		return electionStatus == ElectionStatus.Announced && leaderNode != null; 
	}

	void declareElection(){
		electionStatus = ElectionStatus.Election;
		GeneratedMessage msg = generateElectionMsg(new ElectionData(VoteAction.ELECTION, nodeId), ballotId, nodeId);
		transmit(msg, null);
	}

	private Management generateElectionMsg(ElectionData election, String ballotId, String desc){
		LeaderElection.Builder leader = LeaderElection.newBuilder();
		leader.setBallotId(ballotId);
		leader.setDesc(desc);
		leader.setNodeId(election.getNode());
		leader.setVote(election.getVoteAction());
		return forward(leader.build());
	}

	private Management forward(LeaderElection req){
		Management.Builder builder = Management.newBuilder();
		builder.setElection(req);
		return builder.build();
	}

	void broadcastForLeader() {
		GeneratedMessage msg = generateElectionMsg(new ElectionData(VoteAction.LEADER, nodeId), "", "");
		transmit(msg, null);
	}

	void declareSelfAsWinner() {
		electionStatus = ElectionStatus.Announced;
		leaderNode = nodeId;
		GeneratedMessage msg = generateElectionMsg(new ElectionData(VoteAction.DECLAREWINNER, nodeId), "", "");
		NetworkChannelMap channels = NetworkChannelMap.getInstance();

		for (String node: channels.keySet()){
			Channel ch = channels.get(node);
			if (ch != null)
				ch.writeAndFlush(msg);
		}
	}

	public void notifyNodeLeave(String node, int size) {
		if (size == 0){
			nodeStatus = NodeStatus.Single;
		}

		// Leader is down. Doom coming. Start election initiator.
		if (node.equals(leaderNode)){
			leaderNode = null;
			electionStatus = ElectionStatus.Unkown;
			startElectionInitiator();
		}
	}
}
