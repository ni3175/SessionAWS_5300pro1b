package sessionManagement;

import java.util.Date;
import java.util.Iterator;
import java.util.TimerTask;

public class Cleaner extends TimerTask{
	public void run() {
		Date now = new Date();
		long nowTime = now.getTime();
		System.out.println("\nCleaner start running: " + now);
		for (Iterator<String> iter = Session.sessionPool.keySet().iterator(); iter.hasNext(); ) {
			String sessionID = iter.next();
			// compare current time with expiration time of sessions
			if (nowTime > Session.sessionPool.get(sessionID).getExpireTime().getTime()) {
				Session.sessionPool.remove(sessionID);
				System.out.println("remove session: " + sessionID);
			}
		}	
		System.out.println("Clearner complete.");
	}
}
