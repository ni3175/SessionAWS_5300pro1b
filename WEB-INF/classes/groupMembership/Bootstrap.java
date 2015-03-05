package groupMembership;

//import ProjectStart;
import data.Address;
import data.Data;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import com.amazonaws.auth.*;
import com.amazonaws.services.simpledb.AmazonSimpleDB;
import com.amazonaws.services.simpledb.AmazonSimpleDBClient;
import com.amazonaws.services.simpledb.model.DeleteAttributesRequest;
import com.amazonaws.services.simpledb.model.Item;
import com.amazonaws.services.simpledb.model.PutAttributesRequest;
import com.amazonaws.services.simpledb.model.ReplaceableAttribute;
import com.amazonaws.services.simpledb.model.SelectRequest;

public class Bootstrap {

	  public static final String simpleDBDomain = "CS5300Project1b";
	  AmazonSimpleDB sdb;
	  private final static Logger LOGGER = Logger.getLogger(Bootstrap.class.getName());
	  
	  public Bootstrap() {		
		  try{
			  sdb = new AmazonSimpleDBClient(
						 new BasicAWSCredentials("AKIAIHUS6LXVLZCASQ2Q",
								 				"nJkvnNa382pQ4JhXqADYvc/2IjcYKDUE1n4gR3wP"));
	       }
	       catch(Exception e){
	            System.out.println("error in AmazonSimpleDB");
	            e.printStackTrace();
	            	
	       }
	  }
	  
	  public void addServer(Address svr){
		  //svr.toString();getIp and getIPAddress?
	      List<ReplaceableAttribute> replaceableAttributes = new ArrayList<ReplaceableAttribute>();
	      replaceableAttributes.add(new ReplaceableAttribute("ipAddress", svr.getIP(), true));

	      replaceableAttributes.add(new ReplaceableAttribute("portNumber", svr.getPort()+"", true));

	      sdb.putAttributes(new PutAttributesRequest(simpleDBDomain, svr.toString(), replaceableAttributes));
	  }
	  
	  public View getView(){
	        SelectRequest selectRequest = new SelectRequest("select * from "
                    + simpleDBDomain);
	        View view = new View();
	        for (Item item : sdb.select(selectRequest).getItems()) {
	        	view.insert(new String(item.getName()));
	        }
	        System.out.println(view.toString());
	        return view;
	  }
	  
	  public void removeAll(){
		  View preView = getView();
		  if(preView.getList() != null){
			  for(Address svr : preView.getList()){
				  removeServer(svr);
			  }
		  }
	  }
	  
	  public void updateView(View v){
		 // LOGGER.info("start testing update view in Bootstrap View!");
		  View preView = getView();
		  if(preView.getList() != null){
			  for(Address svr : preView.getList()){
				  removeServer(svr);
			  }
		  }
		 // LOGGER.info("first remove:should be empty:");
	     // LOGGER.info(getView().toString());
	      
		  for(Address svr : v.getList()){
			  addServer(svr);
		  }
		 // LOGGER.info("then add new items:");
	      //LOGGER.info(getView().toString());
	  }
	  
	  protected void removeServer(Address svr){		  
	      sdb.deleteAttributes(
	          new DeleteAttributesRequest(simpleDBDomain, svr.toString()));
	  }
}
