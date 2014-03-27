package poke.server.election;


public interface Election {

	/**
	 * Nominate the current node for election
	 */
	public ElectionData nominate();
	
	/**
	 * Nominate the requested node
	 * @param requestNode
	 */
	public ElectionData nominate(String requestNode);
	
}
