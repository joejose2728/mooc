package poke.server.management.managers;

import io.netty.channel.Channel;

import java.util.concurrent.ConcurrentHashMap;

/**
 * NetworkChannelMap manages all active adjacent channels for this server
 * @author Joel
 *
 */
public class NetworkChannelMap {

	//TODO: Do we need an atomic reference?
	//a hash map containing all adjacent channels of this nodes with whom connection has been established
	private ConcurrentHashMap<String, Channel> adjacentChannels	= new ConcurrentHashMap<String, Channel>();
	private static NetworkChannelMap instance;
	
	private NetworkChannelMap(){
	}
	
	public static NetworkChannelMap getInstance(){
		if (instance == null)
			instance = new NetworkChannelMap();
		return instance;
	}
	
	public void put(String node, Channel channel){
		if (!adjacentChannels.containsKey(node)) {
			adjacentChannels.put(node, channel);
		}
	}
	
	public Channel get(String node){
		return adjacentChannels.get(node);
	}
	
	public void clear(){
		adjacentChannels.clear();
	}
	
	public void remove(String node){
		adjacentChannels.remove(node);
	}
}
