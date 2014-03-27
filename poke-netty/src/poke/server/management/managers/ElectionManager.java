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
import poke.server.election.Election;
import poke.server.election.ElectionData;
import poke.server.election.LCR;

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

	public enum ElectionStatus { Unkown, Election, Announced};

	private String nodeId;
	private ElectionStatus electionStatus;
	private Election election;
	private String leaderNode;
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
		this.election = new LCR(nodeId);
		this.electionStatus = ElectionStatus.Unkown;
		if (votes >= 0)
			this.votes = votes;
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
		ElectionData electionData = null;

		if (req.getVote().getNumber() == VoteAction.ELECTION_VALUE) {
			// an election is declared!

			if (electionStatus != ElectionStatus.Election) {
				logger.info("Election declared - " + req.getNodeId());
				electionStatus = ElectionStatus.Election;
				msg = forward(req);
			}
			else {
				//election initiator has received back the declare election message 
				// now nominate my self
				logger.info("Election status - election. Nominating myself - " + req.getNodeId());
				electionData = election.nominate();
				msg = generateElectionMsg(electionData.getVoteAction(), electionData.getNode());
			}

		} else if (req.getVote().getNumber() == VoteAction.DECLAREVOID_VALUE) {
			// no one was elected, I am dropping into standby mode`
		} else if (req.getVote().getNumber() == VoteAction.DECLAREWINNER_VALUE) {
			// some node declared themself the leader
			if (electionStatus != ElectionStatus.Announced){
				//leader elected
				electionStatus = ElectionStatus.Announced;
				leaderNode = req.getNodeId();
				logger.info("Winner elected - " + req.getNodeId());
				
				//forward that leader is elected to adjacent nodes
				msg = forward(req);
				logger.info("Forwarding winner to my neighbors - " + req.getNodeId());
			}

		} else if (req.getVote().getNumber() == VoteAction.ABSTAIN_VALUE) {
			// for some reason, I decline to vote
		} else if (req.getVote().getNumber() == VoteAction.NOMINATE_VALUE) {
			electionData = election.nominate(req.getNodeId());
			msg = generateElectionMsg(electionData.getVoteAction(), electionData.getNode());
			
			logger.info("Nominating me|requester  - " + electionData.getNode());
			//if this nomination results in leader then change election status
			if (electionData.getVoteAction() == VoteAction.DECLAREWINNER) {
				electionStatus = ElectionStatus.Announced;
				leaderNode = nodeId;
				logger.info("I am the winner - " + nodeId);
			}
		}
		
		if (msg != null)
		  transmit(msg);
	}

	public ElectionStatus getElectionStatus() {
		return electionStatus;
	}

	public String getLeaderNode() {
		return leaderNode;
	}

	public void setNearestNodes(TreeMap<String,NodeDesc> nearestNodes){
		this.nearestNodes = nearestNodes;
	}

	private void transmit(GeneratedMessage msg){
		NetworkChannelMap channels = NetworkChannelMap.getInstance();
		for (NodeDesc node: nearestNodes.values()){
			logger.info("Near me - " + node.getHost() +", Me - " + nodeId);
			Channel ch = channels.get(node.getHost());
			ch.writeAndFlush(msg);
		}
	}

	public boolean hasLeader(){
		return electionStatus == ElectionStatus.Announced && leaderNode != null; 
	}

	public void declareElection(){
		GeneratedMessage msg = generateElectionMsg(VoteAction.ELECTION, nodeId);
		transmit(msg);
	}

	String ballotId = "cluster_5";
	String desc = "desc";
	
	private Management generateElectionMsg(VoteAction voteAction, String node){
		LeaderElection.Builder leader = LeaderElection.newBuilder();
		leader.setBallotId(ballotId);
		leader.setDesc(desc);
		leader.setNodeId(node);
		leader.setVote(voteAction);
		return forward(leader.build());
	}

	private Management forward(LeaderElection req){
		Management.Builder builder = Management.newBuilder();
		builder.setElection(req);
		return builder.build();
	}
}
