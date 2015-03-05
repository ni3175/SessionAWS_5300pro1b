package groupMembership;

import data.Address;
import data.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class View {
	private static int viewSize = 5;
	private HashSet<String> svrList = new HashSet<String>();	
    protected final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    protected final Lock readlock = rwl.readLock();
    protected final Lock writelock = rwl.writeLock();
    
	public View() {
		// TODO Auto-generated constructor stub
	}

	protected View(String addr){
		writelock.lock();
		svrList.add(addr);	
		writelock.unlock();
	}
	
	public View(View v){
		writelock.lock();
		svrList = v.getStringList();
		writelock.unlock();
	}
	
	public View(HashSet<String> v){
		writelock.lock();
		svrList = v;
		writelock.unlock();
	}
	
	public void setView(HashSet<String> list){
		writelock.lock();
		svrList = list;
		writelock.unlock();
	}
	
	public HashSet<String> getStringList(){		
		return this.svrList;
	}
	
	public void shrink(){
		writelock.lock();
		while(svrList.size() > viewSize){
			Random random = new Random();
			int index = random.nextInt(svrList.size())+1;
			Iterator<String> items = svrList.iterator();
			int count = 0;
			while(items.hasNext()){
				count++;
				String temp = items.next();
				if(count == index){
					svrList.remove(temp);
					break;
				}
			}
		}
		writelock.unlock();
	}
	
	public void insert(String addr){
		writelock.lock();		
		svrList.add(addr);		
		writelock.unlock();
	}
	
	public void remove(String addr){
		writelock.lock();
		Iterator<String> items = svrList.iterator();
		while(items.hasNext()){
			String temp = items.next();
			if(temp.toString() == addr.toString()){
				items.remove();
			}
		}
		svrList.remove(addr);
		writelock.unlock();
	}
	
	protected String choose(){
		readlock.lock();
		ArrayList<String> temp = new ArrayList<String>(this.getStringList());
		Random random = new Random();
		int index = random.nextInt(svrList.size());
		String res = temp.get(index);
		readlock.unlock();
		return res;
	}
	
	public void union(View v){
		HashSet<String> svr1 = v.getStringList();
		HashSet<String> temp = new HashSet<String>(this.getStringList());
		for(String a : svr1){
			if(!temp.contains(a)){
				this.insert(a);
			}
		}
		this.shrink();
	}
	
    public String toString(){
    	StringBuffer buffer= new StringBuffer();
    	if(svrList.size()>0){
    		for(String s: svrList){  		
    			buffer.append(s);
    			buffer.append("_");    		
    	}
    			buffer.deleteCharAt(buffer.length()-1);
    	}
    	return buffer.toString();    	
    }
    
    public int length(){
    	return svrList.size();
    }

    public ArrayList<Address> getList(){
    	ArrayList<Address> temp = new ArrayList<Address>();
    	for(String s:svrList){
    		Address a = new Address(s);
    		temp.add(a);
    	}
    	return temp;
    }
}
