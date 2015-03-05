package rpc;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.util.ArrayList;

import data.Address;
import data.Data;
import sessionManagement.*;


public class RPCServer extends Thread{
	public boolean running = true;
	private DatagramSocket rpcSocket;
	private int serverPort;
	//private Session message;
	
	// Server instantiation
	public RPCServer() {
		try{
			rpcSocket = new DatagramSocket(5300);
			serverPort=rpcSocket.getLocalPort();
		} catch (SocketException e) {
			e.printStackTrace();
		}
	}
	
	// receive requests from clients and response to clients
	public void run() {
		System.out.println("RPC Server is running");
		while(running) {
			byte[] inBuffer = new byte[4096];
			byte[] outBuffer = new byte[4096];
			
			// Packet to receive operation cmd
			DatagramPacket rcvPacket = new DatagramPacket(inBuffer, inBuffer.length);
		
			try{
				rpcSocket.receive(rcvPacket);
				System.out.println("Packet recieved by RPCServer");
				//Returns the IP address of the machine to which this datagram is being sent 
				//or from which the datagram was received.
				InetAddress RPCClientIP = rcvPacket.getAddress(); 
				String primaryIP = RPCClientIP.getHostAddress();// RPC client IP is the primary IP
				//Returns the port number on the remote host to which this datagram is
				//being sent or from which the datagram was received.
				int RPCClientPort = rcvPacket.getPort(); // inBuffer contains the callID and operationCode
				//change IP Location
				String backupIP =Data.address.getIP(); 
				//compute response
				byte [] rcvData = rcvPacket.getData();
				//parse packet data
				String[] requests = RPCClient.unmarshal(rcvData).split("_");
				String callID = requests[0];
				int operationCode = Integer.parseInt(requests[1]);
				String sessionID = requests[2];
				String version = requests[3];
				
				//System.out.println("Session ID"+sessionID );
				System.out.println("Session version"+version);
				
				//String discard_time = requests[4];
				String response = null;
				
				switch(operationCode) {
					case RPCClient.OPERATION_CHECK:
						System.out.println("RPC Server"+Data.address.getIP()+" is processing Check");
						response = callID;
					break;
					
					case RPCClient.OPERATION_SESSIONREAD:
						Session session = null;
						System.out.println("RPC Server"+Data.address.getIP()+" is processing Read");
						session = Session.sessionPool.get(sessionID);
						if (session == null || session.getVersion() != Integer.parseInt(version)){
							response = null;
						} else {
							response = callID;
							String versionNum =Integer.toString(session.getVersion()); 
							String message = session.getMessage();
							String expireTime = session.getExpireTimeString();
							ArrayList<String> locations = session.getLocation();
							String primaryAddress=null;
							String localAddress = null;
							if(locations.size()==2){
								primaryAddress = locations.get(0);
								localAddress = locations.get(1);
							}else{
								primaryAddress = locations.get(0);
							}
							try {
					            response += "_" + URLEncoder.encode(versionNum,"UTF-8");
					            response += "_" + URLEncoder.encode(message,"UTF-8");
					            response += "_" + URLEncoder.encode(expireTime,"UTF-8");
					            response += "_" + URLEncoder.encode(primaryAddress,"UTF-8");
					            response += "_" + URLEncoder.encode(localAddress,"UTF-8"); 
					          } catch (UnsupportedEncodingException e) {
					            // TODO Auto-generated catch block
					            e.printStackTrace();
					          }
						}
					break;
					
					case RPCClient.OPERATION_SESSIONWRITE:
						System.out.println("RPC Server"+Data.address.getIP()+" is processing Write");
						String message = null;
				        String discard_time = null;
				        try {
				          message = URLDecoder.decode(requests[4],"UTF-8");
				          discard_time = requests[5];
				        } catch (UnsupportedEncodingException e) {
				          // TODO Auto-generated catch block
				          e.printStackTrace();
				        }
						int newVersion=Integer.parseInt(version);// update the version 
				        Timestamp expirationTime = new Timestamp(Long.parseLong(discard_time));
				        ArrayList<String> locations = new ArrayList<String>();
				        locations.add(primaryIP);
				        locations.add(backupIP);
				        //System.out.println("------------------------------------");
				        //System.out.println("Session Read RPC Server: "+"sessionID"+sessionID+"newVersion"+newVersion+"message"+message+"expirationTime"+expirationTime+"locations"+locations);
				        Session s =new Session(sessionID,newVersion,message,expirationTime,locations);
				        //Session.create(sessionID,newVersion,message,expirationTime,locations);
				        Session.sessionPool.put(sessionID,s);
				        //System.out.println("Sllllll");
				        //System.out.println("Session aaaa" +s.toString());
				        response = callID;
				 	break;
				 	
					case RPCClient.OPERATION_SESSIONVIEW:
						System.out.println("RPC Server"+Data.address.getIP()+" is processing View");
						response = callID+"_"+ Data.view.toString();
					break;
				}
				outBuffer = RPCClient.marshal(response);
				 //outBuf contain the callID and results of the call
				DatagramPacket sendPacket = new DatagramPacket(outBuffer, outBuffer.length, RPCClientIP, RPCClientPort);
				rpcSocket.send(sendPacket);
				
			} catch(IOException e){
				e.printStackTrace();
			}
		
		}
	
		}
}