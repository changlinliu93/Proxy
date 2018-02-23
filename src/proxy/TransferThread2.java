package proxy;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/*
 * This class is a failed experiment, hence is never used.
 */



public class TransferThread2 implements Runnable{
	public static final int TYPE_HTTP_CLIENT = 0;
	public static final int TYPE_DEFAULT = 1;
	private InputStream in;
	private OutputStream out;
	private int type;
	
	public TransferThread2(InputStream myin,OutputStream myout,int mytype){
		this.in = myin;
		this.out = myout;
		this.type = mytype;
	}
	
	@Override
	public void run() {
		int len=0;
		byte[] buff = new byte[10240];
		String s;
		String header;
		if(type == TransferThread2.TYPE_DEFAULT){			
			while(true){
				try {
					len = in.read(buff);
					if(len == -1) break;
					out.write(buff, 0, len);					
				} catch (IOException e) {
					break;
				} 
			}
		}else if(type == TransferThread2.TYPE_HTTP_CLIENT){
			while(true){
				try {
					len = in.read(buff);
					if(len == -1) break;
					s = new String(Arrays.copyOfRange(buff, 0, len));// transform the content read to string
					header = processHeader(s.substring(0, s.indexOf("\r\n\r\n")));//analysis the header
					System.out.println("##########");
					System.out.println(header);
					System.out.println("##########\n");
					out.write((header+s.substring(s.indexOf("\r\n\r\n"))).getBytes(StandardCharsets.US_ASCII));					
				} catch (IOException e) {
					break;
				} 
			}
		}		
	}
	
	private String processHeader(String header){
		String[] header_lines = header.split("\r\n");//break the header into lines
		String request_line = header_lines[0];//the first line is the request line
		String[] host_line = header_lines[1].split(" ");//the second line is the host line
		String host;
		if(!host_line[1].contains(":")) host = host_line[1];
		else host = host_line[1].substring(0, host_line[1].indexOf(":"));
			
		if(request_line.contains(host)){//change the absolute address to relative address 
			String[] req_fields = request_line.split(" ");
			req_fields[1] = req_fields[1].substring(req_fields[1].indexOf(host)+host.length());
			request_line=String.join(" ", req_fields);
			header_lines[0] = request_line;
		}
		header = String.join("\r\n", header_lines);
		
		//change "Proxy-Connection: keep-alive" to "Connection: keep-alive"
		if(!header.contains("Connection:")||!header.contains("Proxy-Connection")) header+="\r\nConnection: close\r\n\r\n";
		if(header.contains("Proxy-Connection")) header = header.replace("Proxy-Connection", "Connection");
		else header+="\r\n";
		return header;
	}
}