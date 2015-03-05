package rpc;

import groupMembership.View;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.UUID;

import sessionManagement.Session;



import data.Address;

public class RPCClient {
	public static final double SUCCESS_RATE =0.7;
	public static final int BACKUP_SERVER =3;
	public static final int RPC_TIMEOUT=1000;
	public static final  int OPERATION_CHECK = 0;
	public static final  int OPERATION_SESSIONREAD = 1;
	public static final  int OPERATION_SESSIONWRITE = 2;
	public static final  int OPERATION_SESSIONVIEW = 3;

	//return true if server is accessible 
	public static boolean check(Address serverAddress){
		System.out.println("Check whether server: "+serverAddress.getIPAddress().getHostAddress()+",port:"+serverAddress.getPort()+ " is accessible");
		try{
			DatagramSocket rpcSocket= new DatagramSocket();
			rpcSocket.setSoTimeout(RPC_TIMEOUT);
			//get a unique ID for each serveran when it has remote call. 
			String callID=UUID.randomUUID().toString();
			String output=callID+"_"+OPERATION_CHECK+"_0_0";
			byte[] outBuffer=marshal(output);
			
			DatagramPacket sendPacket;
	        try{
	        	sendPacket = new DatagramPacket(outBuffer,outBuffer.length,serverAddress.getIPAddress(),serverAddress.getPort());
	        	rpcSocket.send(sendPacket);
	        	//System.out.println("client sent packet to server:"+serverAddress.getIPAddress().getHostAddress());
	        }catch(IOException e){
	        	e.printStackTrace();
	        }
	        
	        //response from server
	        byte[] inBuffer =new byte[4096];
	        DatagramPacket rcvPacket= new DatagramPacket(inBuffer, inBuffer.length);
	        do{
		        try{
						rcvPacket.setLength(inBuffer.length);
						rpcSocket.receive(rcvPacket);
						String response = unmarshal(inBuffer);	
					    //String num= response.split("_")[0]; 
					    //System.out.println("callID"+num);
				}
				catch(SocketException e){
					System.out.println("Server: "+serverAddress.getIPAddress().getHostAddress()+" is not accessible");
					return false;
				}catch(Exception e){
					System.out.println("Server: "+serverAddress.getIPAddress().getHostAddress()+" is not accessible");
					rcvPacket= null;
					return false;	
				} 
	        
	        }while(!(RPCClient.unmarshal(rcvPacket.getData()).split("_")[0]).equals(callID));
			// close socket;
			//rpcSocket.close();
		}
		catch(SocketException e){
			e.printStackTrace();		
			System.out.println("Server: "+serverAddress.getIPAddress().getHostAddress()+" is not accessible");
			return false;
		}
		System.out.println("Server :"+serverAddress.getIPAddress().getHostAddress()+" has been reached");
		return true;
	}
	
	//Session Read 
	public static Session sessionRead(String sessionID, int version, Address serverAddress){
		
		System.out.println("Client begin to send session read request to Server: "+serverAddress.getIPAddress().getHostAddress());
		Session session = null;
		try{
			
			DatagramSocket rpcSocket = new DatagramSocket();
			rpcSocket.setSoTimeout(RPC_TIMEOUT);
			//generate unique id for call
			String callID = UUID.randomUUID().toString();
			//fill outBuf with [ callID, operation_SESSIONREAD, sessionID, sessionVersionNum ]
			String output = callID + "_" + OPERATION_SESSIONREAD + "_" + sessionID + "_" + version;
			byte[] outBuffer = marshal(output);
			DatagramPacket sendPacket = new DatagramPacket(outBuffer, outBuffer.length,serverAddress.getIPAddress(), serverAddress.getPort());
		    try{
		    	rpcSocket.send(sendPacket);
		    	//System.out.println("Client has sent session read request to Server: "+serverAddress.getIPAddress().getHostAddress());
		    } catch (IOException e){
		    	System.out.println("Session Read Failed");
		    	e.printStackTrace();
		    }
			//response from server
			//System.out.println("Wait for response from Server"+serverAddress.getIPAddress().getHostAddress());
		    byte [] inBuffer = new byte[4096];
			DatagramPacket rcvPacket = new DatagramPacket(inBuffer, inBuffer.length);
			rcvPacket.setLength(inBuffer.length);
			String response = null;
			do {
				try {
						try{
							rpcSocket.receive(rcvPacket);
							response = unmarshal(inBuffer);
							System.out.println("Recieved session read response from server"+serverAddress.getIPAddress().getHostAddress());
						} catch (IOException e){
							e.printStackTrace();
							return null;
						}
				    
					String[] subResponses = response.split("_");
					session = new Session();
					int versionNum = Integer.parseInt(URLDecoder.decode(subResponses[1], "UTF-8"));
					String message = URLDecoder.decode(subResponses[2],"UTF-8");
					Timestamp expirationTime = new Timestamp(Long.parseLong(URLDecoder.decode(subResponses[3], "UTF-8")));
					String primaryIP = URLDecoder.decode(subResponses[4],"UTF-8");
					String backupIP = URLDecoder.decode(subResponses[5],"UTF-8");
					ArrayList<String> locations = new ArrayList<String>();
					locations.add(primaryIP);
					locations.add(backupIP);
				    session.setMessage(message);
				    session.setVersion(versionNum);
				    session.setExpireTime(expirationTime);
				    session.setLocation(locations);
					//session location change
				}  catch (IOException e){
					e.printStackTrace();
					System.out.println("IO Exception Session read failed");
				}
			} while( response == null || !(response.split("_")[0].equals(callID)) );
			
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Session read failed");
			return null;
		}
		return session;
	}
	
	//Session Write
		public static boolean sessionWrite(String sessionID,int version,String message,Timestamp expireTime ,Address serverAddress){
			try {
				System.out.println("Client RPC begins to send session write request to Server: "+serverAddress.getIPAddress().getHostAddress());
				DatagramSocket rpcSocket = new DatagramSocket();
				rpcSocket.setSoTimeout(RPC_TIMEOUT);
				
				//generate unique id for call
				String callID = UUID.randomUUID().toString();
				
				//fill outBuf with [ callID, operationSESSIONREAD, sessionID, sessionVersionNum, discardtime ]
				String output = callID + "_" + OPERATION_SESSIONWRITE + "_" + sessionID + "_" + version + "_"
								+ URLEncoder.encode(message, "UTF-8") + "_" +expireTime.getTime();
				byte[] outBuffer = marshal(output);
			    try{
			    	DatagramPacket sendPacket = new DatagramPacket(outBuffer, outBuffer.length,serverAddress.getIPAddress() , serverAddress.getPort());
			    	rpcSocket.send(sendPacket);
			    	//System.out.println("Session write has been sended to "+serverAddress.getIPAddress().getHostAddress());
			    } catch (IOException e){
			    	e.printStackTrace();
			    }
				// response from server
			     // System.out.println("Wait for response from RPC Server");
			      byte[] inBuffer = new byte[4096];
			      DatagramPacket rcvPacket = new DatagramPacket(inBuffer, inBuffer.length);
			      do {
			      try {
			          rcvPacket.setLength(inBuffer.length);
			          rpcSocket.receive(rcvPacket);
			          System.out.println("Recieved session write response from Server"+serverAddress.getIPAddress().getHostAddress());
			      } catch (UnsupportedEncodingException e) {
			    	  System.out.println("Session write failed");
			    	  e.printStackTrace();
			    	  return false;
				  } catch (Exception e) {
			    	rcvPacket = null;
			    	System.out.println("Session write failed");
			    	return false;
			      }
			    } while ( !(unmarshal(rcvPacket.getData())).split("_")[0].equals(callID));
				
		}catch (Exception e) {
			e.printStackTrace();
			System.out.println("Session read failed");
			return false;
		}
			return true;
		}		
			
			
		// get View from session table
		public static View getView(Address serverAddress){
			//DatagramSocket rpcSocket;
			//System.out.println("Client RPC begin to send getView request to server: "+serverAddress.getIPAddress().getHostAddress());
			View view = null;
			try{
				DatagramSocket rpcSocket= new DatagramSocket();
				rpcSocket.setSoTimeout(RPC_TIMEOUT);
				String callID= UUID.randomUUID().toString();
				String output= callID + "_"+OPERATION_SESSIONVIEW+"_0_0";
				byte[] outbuffer= marshal(output);
				DatagramPacket sendPacket;
				try{
					sendPacket= new DatagramPacket(outbuffer, outbuffer.length, serverAddress.getIPAddress(),serverAddress.getPort());
					rpcSocket.send(sendPacket);
					//System.out.println("getView Request has been sent to server: "+serverAddress.getIPAddress().getHostAddress());
				}
				catch(IOException e){
					e.printStackTrace();				
				}		
				
				//reponse from server
				//System.out.println("Waiting for server response from server");
				String response= null;
				byte[] inBuffer= new byte[4096];
				DatagramPacket rcvPacket= new DatagramPacket(inBuffer, inBuffer.length);
				do{
				try{
					
						rcvPacket.setLength(inBuffer.length);
						rpcSocket.receive(rcvPacket);
						//System.out.println("Recieved getView response from Server"+serverAddress.getIPAddress().getHostAddress());
						response= unmarshal(rcvPacket.getData());
					
				}
				catch(IOException e){
					//System.out.println("Session getView failed");
					rcvPacket= null;
					return null;
				}
				}while(!response.split("_")[0].equals(callID));	
				ArrayList<String> list1= new ArrayList<String>();
				String[] subResponses= response.split("_");
				for(int i = 1; i<subResponses.length; i++){				 
					list1.add(subResponses[i]);							
				}				
				HashSet<String> list2 = new HashSet<String>(list1);
				view = new View(list2);
				rpcSocket.close();
			}
			catch(Exception e){
				//System.out.println("Session getView failed");
				return null;
			}
			return view;
		}
		
		//Convert String to bytes into UDP call
		public static byte[] marshal(String s){
			
		    try {
		        ByteArrayOutputStream bos = new ByteArrayOutputStream();
		        ObjectOutput out = new ObjectOutputStream(bos);
		        out.writeObject(s);
		        byte[] output = bos.toByteArray();
		        return output;
		      } catch (Exception e) {
		        e.printStackTrace();
		        return null;
		      }
		    
		}

		// Convert transmitted bytes back into UDP call
		public static String unmarshal(byte[] b) {
		   try {
			      ByteArrayInputStream bis = new ByteArrayInputStream(b);
			      ObjectInput in = new ObjectInputStream(bis);
			      String output = (String) in.readObject();
			      return output;
			    } catch (Exception e) {
			      e.printStackTrace();
			      return null;
			    }
		}
	
}
