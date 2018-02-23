package proxy;
import java.util.*;
import java.util.concurrent.locks.*;
import java.net.*;
/**
 * @author LCL
 * The Resolver class serves as a DNS resolver which run in an independent thread. It maintains a cache who caches every new DNS mapping.
 * The items in the caches have the same TTL of 30 seconds. A queue and a hash table is used the implement the cache. Every time a DNS
 * request arrives, the resolver will look up in the hash table first. If it's not there, it will be added both to the hash table and 
 * the queue. The resolver will constantly check the head of the queue. If that items becomes stale, delete it both from the hash table
 * and the queue. A lock is used to guarantee the consistency of the cache.
 * 
 */
public class Resolver extends Thread{
	private volatile List<DNSrecord> records_queue;
	private volatile Map<String,InetAddress> cache;
	private Lock lock;
	
	//automatically run itself when instantiated 
	Resolver(){
		this.records_queue = new LinkedList();
		this.cache = new HashMap();
		this.lock=new ReentrantLock();
		start();
	}
	
	//synchronous update method, not used any more
	@Deprecated
	private void update(String myURL,InetAddress myIP){
		lock.lock();
		if(!cache.containsKey(myURL)){
			this.cache.put(myURL, myIP);
			this.records_queue.add(new DNSrecord(myURL));		
		}
		lock.unlock();	
	}
	
	//This method provide the DNS resolving service.
	public InetAddress lookup(String myURL) throws UnknownHostException{
		InetAddress myIP;
		if(cache.containsKey(myURL)){
			myIP =  cache.get(myURL);
			System.out.println("Cache hit!");
		}else{		
			myIP = InetAddress.getByName(myURL);
			//update(myURL, myIP);
			//Start a new thread to update the cache, rather than getting blocked here.
			new Thread(new Updater(myURL,myIP)).start();
			System.out.println("Cache miss!");
		}
		return myIP;
	}

	//Check if the oldest item in the cache is stale every 0.5 second.
	//Use the lock to prevent cache updating while deleting stale items.
	@Override
	public void run() {
		while(true){
			try{
				Thread.sleep(500);
			}catch(Exception e){};
			if(!records_queue.isEmpty()){
				DNSrecord oldest = records_queue.get(0);			
				if(oldest.isStale()) {
					lock.lock();
					cache.remove(oldest.getURL());
					records_queue.remove(0);
					lock.unlock();
					System.out.println("Stale cache entry removed!");
				}
			}			
		}
	}	
	
	
	//Provide asynchronous updating of the cache. Modification of the cache should be a exclusive action. If a lot of threads want to
	//update the cache(like when visiting a brand new web site), they will get blocked. So we can't update the cache in the server thread.
	private class Updater implements Runnable{
		private String URL;
		private InetAddress IP;
		
		public Updater(String myURL,InetAddress myIP){
			this.URL = myURL;
			this.IP = myIP; 
		}
		
		@Override
		public void run(){
			lock.lock();
			if(!cache.containsKey(URL)){
				cache.put(URL, IP);
				records_queue.add(new DNSrecord(URL));		
			}
			lock.unlock();			
		}
	}
}




/*
 * Defines the structure of and the methods provided by the records in the cache. 
 */
class DNSrecord{
	public static long TTL = 30000; 
	private String URL;
	private long times_tamp;
	
	DNSrecord(String myURL){
		this.URL = myURL;
		this.setTimeStamp();
	}
	
	public static void setTTL(long value){
		DNSrecord.TTL = value; 
	}
	
	public String getURL(){
		return this.URL;
	}
	
	public void setTimeStamp(){
		this.times_tamp = System.currentTimeMillis();
	}
	
	public boolean isStale(){
		return this.times_tamp + DNSrecord.TTL < System.currentTimeMillis();
	}
}
