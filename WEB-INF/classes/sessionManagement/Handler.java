package sessionManagement;

import groupMembership.GroupManagement;
import groupMembership.View;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Timer;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import rpc.RPCClient;
import rpc.RPCServer;
import data.Address;
import data.Data;


/**
 * Servlet implementation class Handler
 */
public class Handler extends HttpServlet {
	private static final long serialVersionUID = 1L;
	
	private static final String cookieName = "CS5300PROJ1SESSION";
	
	private String ip = "";
	
	public View view;
	
	private final static Logger LOGGER = Logger.getLogger(Handler.class.getName());

       
    /**
     * @throws IOException 
     * @see HttpServlet#HttpServlet()
     */
    public Handler() throws IOException {
    	
        super();
        
        // get public IP of this server
        getPublicIP();
        Data.address = new Address(ip);
        
        // start RPC server
        startRPCServer();
        
        Data.view = new View();
        
        //TODO
        startGroupManagement();
        Address add = new Address("12.33.44.55");
        boolean flag = RPCClient.check(add );
        System.out.println("==sdfsdgdfgdfgdfsgfdsgdfgfdgfdgfdgfd");
        System.out.println("=="+flag);
        // start cleaner daemon thread
        startCleaner();            
    }
    
    
    private void startRPCServer() {
        RPCServer rpcs = new RPCServer();
        Thread ts = new Thread(rpcs);
        ts.start();
    }
    
    private void startCleaner() {
    	Timer cleanerTimer = new Timer();	// background cleaner "daemon" thread (not actually a daemon thread)
    	Cleaner cleaner = new Cleaner();
    	// cleaner runs periodically
    	cleanerTimer.schedule(cleaner, Session.getExpiry() * 1000, Session.getExpiry() * 1000);
    }
    
    private void startGroupManagement() {
    	GroupManagement gm = new GroupManagement();
        Thread tgm = new Thread(gm);
        tgm.start();
    }
    
    
    private void getLocalIP() {
    	try {
			ip = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
    }
    
    private void getPublicIP() {
      Runtime rt = Runtime.getRuntime();
      Process proc = null;
      BufferedReader br = null;
      InputStreamReader isr = null;
      try {
    	  proc = rt.exec("/opt/aws/bin/ec2-metadata --public-ipv4");
    	  isr = new InputStreamReader(proc.getInputStream());
    	  br = new BufferedReader(isr);
    	  String rawIP = "";
    	  String str;
    	  while ((str = br.readLine()) != null) {
    		  rawIP += str;
    	  }
    	  this.ip = rawIP.split(":")[1].trim();
    	  System.out.println(ip);
      } catch (IOException ie) {
    	  ie.getStackTrace();
      } finally {
    	  try {
    		  br.close();
    		  isr.close();
    	  } catch (IOException ie) {
    		  ie.getStackTrace();
    	  }
      }	
    }
    
    public ArrayList<Address> viewAddr() {
    	ArrayList<Address> viewAddr = new ArrayList<Address>(Data.view.getList());
    	ArrayList<Address> validAddr = new ArrayList<Address>();
    	for (Address a: viewAddr) {
    		if (a.getIP() != Data.address.getIP())
    			validAddr.add(a);
    	}
		return validAddr;
    }
    
    /**
     * create new session, return corresponding session cookie.
     */
    public Session createSession(String ip) { 
//    	System.out.println("session created time: " + new )
    	Session newSession = new Session(ip);
    	System.out.println("new session expire time: " + newSession.getExpireTimeString());
    	String sessionID = newSession.getSessionID();

		// set location in session
		ArrayList<String> locList = new ArrayList<String>();
		locList.add(ip);
		boolean success = false;
		ArrayList<Address> validAddr = viewAddr();
		Date date = new Date();
    	Timestamp t = new Timestamp(0);
    	t.setTime(date.getTime() + Session.getExpiry() * 1000);
		for (Address addr: validAddr) {
			success = RPCClient.sessionWrite(sessionID, 0, "Hello, User!", t, addr);
			if (success) { 
				locList.add(addr.getIP());
				break;
			}
		}
		
		newSession.setLocation(locList);
		return newSession;
    }

    // write session in remote server and locally(update or write)
    public Cookie refresh(ArrayList<String> pList, String sessionID, int version, int status, String data) {
    	System.out.println("refreshed version: " + String.valueOf(version));
    	String primaryIP = ip;
    	String backupIP = "";
    	boolean success = false;
    	ArrayList<Address> validAddr = new ArrayList<Address>();
    	for (String rip: pList)
    		validAddr.add(new Address(rip));
    	ArrayList<Address> viewAddr = viewAddr();
    	validAddr.addAll(viewAddr);
    	Date date = new Date();
    	Timestamp t = new Timestamp(0);
    	t.setTime(date.getTime() + Session.getExpiry() * 1000);
    	for (Address addr: validAddr) {
    		success = RPCClient.sessionWrite(sessionID, version, data, t, addr);
    		if (success == true) {
    			backupIP = addr.getIP();
    			break;
    		}
    	}
    	// update session locally
    	ArrayList<String> loc = new ArrayList<String>();
    	loc.add(primaryIP);
    	if (backupIP != "")
    		loc.add(backupIP);
    	Session s = null;
    	if (status == 1) {
    		Session.create(sessionID, version, data, loc);
    		s = Session.sessionPool.get(sessionID);
    	}
    	else {	// update session locally
    		s = Session.sessionPool.get(sessionID);
    		s.setLocation(loc);
    		s.refresh();
    	}
    	String cookieValue = sessionID + "#" + String.valueOf(s.getVersion()) + "#";
    	cookieValue += loc.get(0);
    	if (loc.size() == 2)
    		cookieValue += "_" + loc.get(1);
    	Cookie sessionCookie = new Cookie(cookieName, cookieValue);
		sessionCookie.setMaxAge(Session.getExpiry());
		return sessionCookie;
    }
    
    public Cookie replace(ArrayList<String> pList, String sessionID, int version, int status, String data) {
    	String primaryIP = ip;
    	String backupIP = "";
    	boolean success = false;
    	
    	// RPC sequence
    	ArrayList<Address> validAddr = new ArrayList<Address>();
    	for (String rip: pList)
    		validAddr.add(new Address(rip));
    	ArrayList<Address> viewAddr = viewAddr();
    	validAddr.addAll(viewAddr);
    	Date date = new Date();
    	Timestamp t = new Timestamp(0);
    	t.setTime(date.getTime() + Session.getExpiry() * 1000);
    	for (Address addr: validAddr) {
    		success = RPCClient.sessionWrite(sessionID, version, data, t, addr);
    		if (success == true) {
    			backupIP = addr.getIP();
    			break;
    		}
    	}
    	
    	// update session locally
    	ArrayList<String> loc = new ArrayList<String>();
    	loc.add(primaryIP);
    	if (backupIP != "")
    		loc.add(backupIP);
    	Session s = null;
    	if (status == 1) {
    		Session.create(sessionID, version, data, loc);
    		s = Session.sessionPool.get(sessionID);
    	}
    		
    	else {
    		s = Session.sessionPool.get(sessionID);
    		System.out.println(s.toString());
    		s.setLocation(loc);
    		s.setMessage(data);
    		s.refresh();
    	}
    	String cookieValue = sessionID + "#" + String.valueOf(s.getVersion()) + "#";
    	cookieValue += loc.get(0);
    	if (loc.size() == 2)
    		cookieValue += "_" + loc.get(1);
    	Cookie sessionCookie = new Cookie(cookieName, cookieValue);
		sessionCookie.setMaxAge(Session.getExpiry());
		return sessionCookie;
    }
    
    
    
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.setContentType("text/html");
		
		String message = "";	// text displayed
		String sessionFoundIP = "";
		boolean loggedout = false;
		boolean sessionNotFound = true;
		boolean sessionTimeout = false;
		boolean firstAccess = false;
		
		
		PrintWriter out = response.getWriter();
		
		// get action from request
		String action = (String) request.getParameter("action");
		
		Cookie sessionCookie = null;
		Session mySession = null;

		// response corresponds to act of client
		if (action == null) {
			// { no action means a new session starts }
			mySession = createSession(ip);
			
			// set cookie
			String id = mySession.getSessionID();
			int version = mySession.getVersion();
			String cookieValue = id + "#" + String.valueOf(version) + "#";
			String location = "";
			for (String s: mySession.getLocation()) {
				location += s + "_";
			}
			location = location.substring(0, location.length()-1);
			cookieValue += location;
			sessionCookie = new Cookie(cookieName, cookieValue);
			sessionCookie.setMaxAge(Session.getExpiry());
			response.addCookie(sessionCookie);
			message = mySession.getMessage();
			firstAccess = true;
		}
		
		else { 
			// { hit replace, refresh or logout }
			Cookie[] cookies = request.getCookies();
			boolean found = false;	// represent whether required cookie/session is found (not expired)
			if (cookies != null) {
				for (Cookie c : cookies) {
					if (c.getName().equals(cookieName)) {
						found = true;
						sessionCookie = c;
						break;
					}
				}
			}
			if (found == true) {
				// { session cookie is found }
				String s = sessionCookie.getValue();
				String[] cookieValues = s.split("#");
				String sessionID = cookieValues[0];
				int version = Integer.parseInt(cookieValues[1]);
			
				String localIP = ip;
				
				// handle location data in session cookie
				String locations = sessionCookie.getValue().split("#")[2];
				String primaryIP = "";
				String backupIP = "";
				String[] ips = locations.split("_");
				primaryIP = ips[0];
				if (ips.length == 2)
					backupIP = ips[1];
				
				// ip status   0: local = primary || backup		1: otherwise
				int ipStatus = -1;
				
				// PRC server preference list
				ArrayList<String> pList = new ArrayList<String>();
				if (localIP.equals(primaryIP)) {
					if (backupIP != "")
						pList.add(backupIP);
					ipStatus = 0;
				}
				else if (localIP.equals(localIP)) {
					pList.add(primaryIP);
					ipStatus = 0;
				}
				else {
					pList.add(primaryIP);
					if (localIP != "")
						pList.add(localIP);
					ipStatus = 1;
				}
					
				// handle actions
				if (action.equals("refresh")) {
					// project 1a
//						mySession.refresh();	// update expiration time
//						sessionCookie.setMaxAge(Session.getExpiry());		// reset cookie expiration time
					
					// TODO project 1b
					String msg = "";
					boolean sessionFound = false;
					if (ipStatus == 0) {
						System.out.println("session ID when refreshed: " + sessionID);
						msg = Session.sessionPool.get(sessionID).getMessage();
						sessionFoundIP = ip;
						sessionFound = true;
					}
					else {
						for (String rip: pList) {
							Address addr = new Address(rip);
							Session sess = null;
							if ((sess = RPCClient.sessionRead(sessionID, version, addr)) != null) {
								msg = sess.getMessage();
								sessionFoundIP = addr.getIP();
								sessionFound = true;
								break;
							}
						}
					}
					if (sessionFound == false) {
						message = "Session not found";
					}
					else {
						sessionNotFound = false;
						message = msg;
						System.out.println("version before refresh: " + String.valueOf(version).trim());
						sessionCookie = refresh(pList, sessionID, version+1, ipStatus, msg);
					}
				}
				else if (action.equals("replace")) {
					// project 1a
//						String newMessage = request.getParameter("newMessage");
//						mySession.update(newMessage);
//						sessionCookie.setMaxAge(Session.getExpiry());
					
					// TODO project 1b
					String newMessage = request.getParameter("newMessage");
					message = newMessage;
					boolean sessionFound = false;
					if (ipStatus == 0) {
						sessionFoundIP = ip;
						sessionFound = true;
					}
					else {
						for (String rip: pList) {
							Address addr = new Address(rip);
							Session sess = null;
							if ((sess = RPCClient.sessionRead(sessionID, version, addr)) != null) {
								sessionFoundIP = addr.getIP();
								sessionFound = true;
								break;
							}
						}
					}
					if (sessionFound == false) {
						message = "Session not found";
					}
					else {
						sessionNotFound = false;
						System.out.println("version before refresh: " + String.valueOf(version).trim());
						sessionCookie = replace(pList, sessionID, version+1, ipStatus, newMessage);
					}
					
				}
				else {
					// { action = "logout" }
					sessionCookie.setMaxAge(0);
					loggedout = true;
					message = "Log out successfully!";
				}
				response.addCookie(sessionCookie);
			}
			else {	// session expires
				sessionTimeout = true;
				message = "Session expires";
			}
		}
		
		out.println("<!DOCTYPE html PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\" \"http://www.w3.org/TR/html4/loose.dtd\">");
		out.println("<html><head></head><body>");
		out.println("<h1>" + message + "</h1>");
		
		// return the request form if not logged out or session timeout
		if ((!loggedout) && (!sessionTimeout) && (!sessionNotFound || firstAccess)) {
			out.println("<form action=\"Handler\" method=\"post\">");
			out.println("<input type=\"submit\" name=\"action\" value=\"replace\" />");
			out.println("<input type=\"text\" maxlength=\"512\" name=\"newMessage\" />");
			out.println("<br /><br />");
			out.println("<input type=\"submit\" name=\"action\" value=\"refresh\" />");
			out.println("<br /><br />");
			out.println("<input type=\"submit\" name=\"action\" value=\"logout\" />");
			out.println("<br /><br />");
			out.println("</form>");
			// session related information
			String[] cookievalues = sessionCookie.getValue().split("#");
			String[] locs = cookievalues[2].split("_");
	
			// TODO project 1b
			out.println("<p>local IP:	" + this.ip + "</p>");
			if (sessionNotFound == false)
				out.println("<p> session found in:	" + sessionFoundIP + "</p>");
			out.println("<p>primary IP:	" + locs[0] + "</p>");
			out.println("<p>backup IP:	" + (locs.length == 1 ? "NULL" : locs[1]) + "</p>");
			out.println("<p>expiry:	" + Session.getExpiry() + "s</p>");
			out.println("<p>view:	" + Data.view.toString() + "</p>");
//			out.println("<p></p>");
			out.println("<p>sessionID:  " + cookievalues[0] + "<p>");
			out.println("<p>Version:  " + cookievalues[1] + "<p>");
			out.println("<p>session cookie:  " + sessionCookie.getValue() + "<p>");
		}
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

}
