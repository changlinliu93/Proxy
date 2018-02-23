package proxy;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import java.io.*;

public class ServerThread implements Runnable{
	private int https_state = 0;
	private Resolver resolver;
	private Socket client;
	private Socket server;
	private InputStream ic;// input stream of the client
	private InputStream is;// input stream of the server
	private OutputStream oc;// output stream of the client
	private OutputStream os;// output stream of the server
	
	ServerThread (Resolver myResolver,Socket mySocket) throws IOException{
		this.resolver = myResolver;
		this.client = mySocket;
		//get the streams from the client
		this.ic = client.getInputStream();
		this.oc = client.getOutputStream();
	}
		
	public void run(){
		try {
			byte[] cbuff = new byte[10240];//buffer of the client side
			byte[] sbuff = new byte[10240];//buffer of the server side
			int len_c=0;
			int len_s=0;
			String s;
			String header;
			len_c=ic.read(cbuff);
			while(len_c!=-1){ 
				s = new String(Arrays.copyOfRange(cbuff, 0, len_c));// transform the content read to string
				if (s.indexOf("\r\n\r\n") != -1){//check if the content include http header
					header = processHeader(s.substring(0, s.indexOf("\r\n\r\n")));//analyze the header
					System.out.println("##########");
					System.out.println(header);
					System.out.println("##########\n");
					//if it's a https connection, exit and start new threads to serve.
					if(header.equals("New https connection")) {
						new Thread(new TransferThread(ic,os)).start();
						new Thread(new TransferThread(is,oc)).start();
						break;
					}
					//forward the request to the server
					os.write((header+s.substring(s.indexOf("\r\n\r\n"))).getBytes(StandardCharsets.US_ASCII));
				}
				len_s = 0;
				while(oc!=null){//check if the client has closed the socket
					//read response from the server
					len_s = is.read(sbuff);
					if(len_s==-1) break;//check if the server has close the socket
					oc.write(sbuff, 0, len_s);//forward the response to the client
					oc.flush();
				}
				len_c=ic.read(cbuff);
			}			
		}catch (IOException e) {}
	}
	
	private String processHeader(String header) throws IOException{
		String[] header_lines = header.split("\r\n");//break the header into lines
		String request_line = header_lines[0];//the first line is the request line	
		String[] host_line=null;
		for(String s : header_lines) if(s.startsWith("Host")) {//find the host
			host_line= s.split(" ");
			break;
		}
		String host;
		if(!host_line[1].contains(":")){
			host = host_line[1];
		}else{
			host = host_line[1].substring(0, host_line[1].indexOf(":"));
			
		}
		
	
		switch(request_line.split(" ")[0]){//what command is it?
		case "CONNECT"://if it's a https "CONNECT" request.  
			header = "New https connection";			 
			try{//Test if the server is available. If so, confirm the client. Otherwise, send an error message.
				server = new Socket(resolver.lookup(host),443);
				is = server.getInputStream();
				os = server.getOutputStream();		
				oc.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
				oc.flush();
			}catch(NumberFormatException | IOException e){
				
				System.out.println("Exception!");
				System.out.println(e.toString());
				e.printStackTrace();
				oc.write("HTTP/1.1 500 Connection Failed\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
				oc.flush();
			}
			break;
		default://other request
			try{
				if(request_line.contains(host)){//change the absolute address to relative address 
					 String[] req_fields = request_line.split(" ");
					req_fields[1] = req_fields[1].substring(req_fields[1].indexOf(host)+host.length());
					request_line=String.join(" ", req_fields);
					header_lines[0] = request_line;
				}
				header = String.join("\r\n", header_lines);
				
				//change "Connection: keep-alive" or "Proxy-Connection: keep-alive" to "Connection: close"
				if(!header.contains("Connection:")||!header.contains("Proxy-Connection")) header+="\r\nConnection: close\r\n\r\n";
				else if(header.contains("keep-alive")) header = header.replace("keep-alive", "close");
				if(header.contains("Proxy-Connection")) header = header.replace("Proxy-Connection", "Connection");
				else header+="\r\n";
				
				//reach out to the server
				server = new Socket(resolver.lookup(host),80);
				is = server.getInputStream();
				os = server.getOutputStream();
				

			}catch(NumberFormatException | IOException e){
				System.out.println("Exception!");
				System.out.println(e.toString());
				//return an error page.
				oc.write("HTTP/1.1 200 OK\r\n\r\n<!DOCTYPE html><html><head><title>Error</title></head><body><h1>Something's wrong!</h1><p>I cannot connect to that host!</p></body></html>".getBytes(StandardCharsets.US_ASCII));
				oc.flush();
			}		
			break;
		}
		
		return header;
	}
	
	//data transfering threads for https
	private class TransferThread implements Runnable{
		private InputStream in;
		private OutputStream out;
		
		
		public TransferThread(InputStream myin,OutputStream myout){
			this.in = myin;
			this.out = myout;
		}
		
		//keep transfering until either the client or the server closes the connection
		@Override
		public void run() {
			int len;
			byte[] buff = new byte[10240];
			while(true){
				try {
					len = in.read(buff);
					if(len==-1) break;
					out.write(buff, 0, len);					
				} catch (IOException e) {
					break;
				} 
			}
		}
	}
}
