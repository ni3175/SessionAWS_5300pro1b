package sessionManagement;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

public class Session {

	public static int expiry = 10 * 60;	// session expires in 10 minutes
	private static int sess_num = 0;
	private static Object lock = new Object();	// mutex lock for globally unique sessionID
	
	
	// HashMap keyed by sessionID
	public static ConcurrentHashMap<String, Session> sessionPool = new ConcurrentHashMap<String, Session>();

	private String sessionID;	// key for Session instances stored in sessionPool
	private int version;
	private String message;
	private Timestamp expireTime;
	private ArrayList<String> locations = new ArrayList<String>();

	/** Constructor */
	
	public Session() {}
	
	public Session(String addr) {
		setSessionID(addr);
		version = 0;
		setMessage("Hello, User!");
		setExpireTime();
		sessionPool.put(sessionID, this);
	}
	
	public Session(String ID, int v, String m, Timestamp t, ArrayList<String> l) {
		sessionID = ID;
		version = v;
		setMessage(m);
		expireTime = t;
		locations = new ArrayList<String>(l);
	}
	
	// create session from a certain sessionID and version
	public static void create(String ID, int v, String m, ArrayList<String> l) {
		Session s = new Session();
		s.sessionID = ID;
		s.version = v;
		s.message = m;
		s.setExpireTime();
		s.locations = new ArrayList<String>(l);
		sessionPool.put(ID, s);
	}
	
	public static void create(String ID, int v, String m, Timestamp t, ArrayList<String> l) {
		Session s = new Session();
		s.sessionID = ID;
		s.version = v;
		s.message = m;
		s.expireTime = t;
		s.locations = new ArrayList<String>(l);
		sessionPool.put(ID, s);
	}
	
	private void setSessionID(String addr) {
		synchronized(lock) {
			sess_num++;
		}
		sessionID = sess_num + "*" + addr;
	}
	
	public String getSessionID() {
		return sessionID;
	}

	public void setMessage(String s) {
		message = s;
	}

	public String getMessage() {
		return message;
	}

	private void updateVersion() {
		version++;
	}

	public void setVersion(int v) {
		version = v;
	}
	
	public int getVersion() {
		return version;
	}

	private void setExpireTime() {
		Date date = new Date();
		expireTime = new Timestamp(0);
		/** in milliseconds */
		expireTime.setTime(date.getTime() + expiry * 1000);
	}
	
	public void setExpireTime(Timestamp t) {
		expireTime = t;
	}

	public String getExpireTimeString() {
		return expireTime.toString();
	}
	
	public Timestamp getExpireTime() {
		return expireTime;
	}
	
	public void setLocation(ArrayList<String> loc) {
		locations = new ArrayList<String>(loc);
	}
	
	public ArrayList<String> getLocation() {
		return locations;
	}

	public void refresh() {
		updateVersion();
		expireTime.setTime(new Date().getTime() + expiry * 1000);
	}
	
	public void update(String data) {
		updateVersion();
		if (data.length() > 512)
			data = data.substring(0, 512);
		setMessage(data);
		expireTime.setTime(new Date().getTime() + expiry * 1000);
	}
	
	public void delete() {
		sessionPool.remove(sessionID);
	}

	public static int getExpiry() {
		return expiry;
	}
	
	public String toString(){
		String result= "sessionID: "+sessionID+" version:"+ version +"Message: "+
	    message+"Expire Time: "+expireTime.getTime()+"Primary IP: "+locations.get(0) +"Backup IP: ";
		if (locations.size() == 2)
			result += locations.get(1);
		return result;
	}
	

}