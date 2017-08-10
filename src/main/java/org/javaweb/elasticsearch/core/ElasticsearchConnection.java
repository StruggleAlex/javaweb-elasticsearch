package org.javaweb.elasticsearch.core;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetSocketAddress;

/**
 * Created by yz on 2017/1/4.
 */
public class ElasticsearchConnection {

	/**
	 * 集群客户端连接对象
	 */
	private Client client;

	/**
	 * 集群名称
	 */
	private String clusterName = "elasticsearch";

	/**
	 * 集群主机地址
	 */
	private String clusterHost = "127.0.0.1";

	/**
	 * 集群transport端口
	 */
	private int clusterPort = 9300;

	/**
	 * 是否嗅探集群
	 */
	private boolean transportSniff = true;

	public Client getClient() {
		return client;
	}

	public String getClusterName() {
		return clusterName;
	}

	public void setClusterName(String clusterName) {
		this.clusterName = clusterName;
	}

	public String getClusterHost() {
		return clusterHost;
	}

	public void setClusterHost(String clusterHost) {
		this.clusterHost = clusterHost;
	}

	public int getClusterPort() {
		return clusterPort;
	}

	public void setClusterPort(int clusterPort) {
		this.clusterPort = clusterPort;
	}

	public boolean isTransportSniff() {
		return transportSniff;
	}

	public void setTransportSniff(boolean transportSniff) {
		this.transportSniff = transportSniff;
	}

	private void init() {
		Settings.Builder settingsBuilder = Settings.builder();
		settingsBuilder.put("cluster.name", clusterName);
		settingsBuilder.put("client.transport.sniff", transportSniff);

		InetSocketAddress          inetSocketAddress = new InetSocketAddress(clusterHost, clusterPort);
		InetSocketTransportAddress transportAddress  = new InetSocketTransportAddress(inetSocketAddress);

		this.client = new PreBuiltTransportClient(settingsBuilder.build()).addTransportAddress(transportAddress);
	}

}
