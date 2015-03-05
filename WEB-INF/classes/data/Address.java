package data;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class Address {
	
	private String ip;
	private final int port = 5300;
	
	public Address(String IP) {
		ip = IP;
	}
	
	public String getIP() {
		return ip;
	}
	
	public int getPort() {
		return port;
	}
	
	public InetAddress getIPAddress() {
		InetAddress inetAdd = null;
		try {
			inetAdd = InetAddress.getByName(ip);
		} catch (UnknownHostException e) {
			System.out.println("ip translation fail.");
			e.printStackTrace();
		}
		return inetAdd;
	}
	
	public String toString() {
		return ip;
	}
	
	public boolean equals(Address a) {
		return this.getIP() == a.getIP();
	}

}
