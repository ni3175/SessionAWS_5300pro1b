package groupMembership;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import rpc.RPCClient;
import data.Address;
import data.Data;

import com.amazonaws.auth.*;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;

public class GroupManagement extends Thread{
	
	protected static Bootstrap bootstrap = new Bootstrap();
	protected static int gossip_secs = 1000;
	protected static boolean running = true;
	static Random generator = new Random();
	private final static Logger LOGGER = Logger.getLogger(GroupManagement.class.getName());
	public GroupManagement(){
		super();
	}
	
    public void run(){
        while(running){
           try {
                Thread.sleep((int) ((gossip_secs/2) + generator.nextInt(gossip_secs)));
//                Data.view.remove(Data.address.getIP());
                gossip();
//                Data.view.remove(Data.address.getIP());
            } 
            catch (InterruptedException e) {
                e.printStackTrace();
            } 
        }
    }
    
    protected void gossip(){
    	//update Bootstrap View in SimpleBD and view in current server
    	View currentView= Data.view;
    	View bootView = bootstrap.getView();
    	
    	bootView.remove(Data.address.getIP());//remove self from Bootstrap View read from SimpleDB
    	Data.view.remove(Data.address.toString());
    	
    	bootView.union(currentView);
/*    	LOGGER.info("after union with bootstrap:");
    	LOGGER.info(bootView.toString());*/
    	
    	bootView.shrink();
    	Data.view = bootView;
    	Data.view.remove(Data.address.getIP());
    	
    	bootView.insert(Data.address.toString());
    	bootView.shrink();
    	bootstrap.updateView(bootView);
    	
//    	LOGGER.info("GOSSIP step 1: print updated bootstrap");
//    	LOGGER.info(bootstrap.getView().toString());
    	
    	//gossip with one of the server chosen from its view
    	if(Data.view != null){
	    	currentView = Data.view;
/*	    	LOGGER.info("==begin gossip with other server==");
	    	LOGGER.info("print current View");
	    	LOGGER.info(currentView.toString());*/
	    	
	    	String temp = currentView.choose();
	    	Address tempSvr = new Address(temp);
	    	if(RPCClient.check(tempSvr)){	    		
	    		View tempView = RPCClient.getView(tempSvr);
/*	    		LOGGER.info("RPC get other server View:");
	    		LOGGER.info(tempView.toString());*/
	    		
	    		tempView.union(currentView);
	    		tempView.remove(Data.address.getIP());
	    		Data.view = tempView;
	    		Data.view.remove(Data.address.getIP());
//	    		LOGGER.info("gossip temp server");
//	    		LOGGER.info(Data.view.toString());
	    	}
	    	else{
	    		Data.view.remove(tempSvr.getIP());
	    	}
    	}
    }
    
    protected void addBootstrap(){
    	Address add = Data.address;
    	bootstrap.addServer(add);   	
    }
    
    protected void cleanup(){
    	running = false;
    }
}
