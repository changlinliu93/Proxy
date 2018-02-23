package proxy;
import java.io.*; 
import java.net.*; 
public class proxyd {
	//usage: java proxyd -port xxx
	public static void main(String[] args) throws IOException{
		int port=5506;
		//parse command line arguments 
		for(int i=0;i<args.length;i++){
			if(args[i].equals("-port")) port=Integer.parseInt(args[i+1]);
		}
		System.out.println(String.format("Proxy running on port#:%d",port));
		//initiate a Resolver instance
		Resolver resolver = new Resolver();
		//listen on the port
		ServerSocket welcomeSocket = new ServerSocket(port);
		while(true) {		    			    	
		        Socket connectionSocket = welcomeSocket.accept(); 
		        //start a new thread to serve the client
		        new Thread(new ServerThread(resolver,connectionSocket)).start();		        
		    } 
	}  
	
}

