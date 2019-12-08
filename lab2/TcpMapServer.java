/****************************************************************************
 Name: Richard Wu		Student ID: 464493
 Name: Jiaming Qiu	Student ID: 467620
 Date: 2019/9/13
 This program system includes a client program(TcpMapClient) and a server
 program (TcpMapServer). The server will store a set of key and value pairs
 and the client can send instructions to server to get, get all, put, or
 remove any pair stored. The server will accept TCP connections from
 clients, one at a time, and will process multiple operations based on 
 client's input until the client closes the connection. The format of using
 the client program is listed as follows: the first argument is the name/IP
 address of the host that the server is running on; the second argument
 is the server's port number, which is optional and will use 30123 if not 
 sprcified. In the input line, the "get" instruction should be in the format
 of "get:key"; the "get all" instruction should just be "get all"; the "put" 
 instruction should be in the format of "put:key:value"; the "remove" 
 instruction should be in the format of "remove:key". Any other 
 instruction will be considered ill-formatted. When the server receives 
 an instruction, it will send back a feedback to the client.When the user 
 inputs a blank line, the connection will be closed. To start the server, the
 first optional argument should be the IP address (wildcard address if not
 specified), and the second optional argument should be the port number.
 (30123 by default and if the port number is specified, IP address should also
 be specified)
****************************************************************************/
import java.io.*;
import java.net.*;
import java.util.*;
public class TcpMapServer {
	public static void main(String args[]) throws Exception {
		// Process arguments
		int port = 30123; // Default port number
		if (args.length > 1) port = Integer.parseInt(args[1]);
		InetAddress bindAdr = null;
		if (args.length > 0) bindAdr = InetAddress.getByName(args[0]);
		// Create and bind listening socket
		ServerSocket listenSock = new ServerSocket(port,0,bindAdr);
		// Stream buffer
		byte[] buf = new byte[1000];
		// Create HashMap to store the data
		HashMap<String, String> map = new HashMap<>();
		while (true) {
			// Wait for incoming connection request and
			// Create new socket to handle it
			Socket connSock = listenSock.accept();
			// Create buffered versions of socket's in/out streams
			BufferedInputStream in = new BufferedInputStream(
						   connSock.getInputStream());
			BufferedOutputStream out = new BufferedOutputStream(
						   connSock.getOutputStream());
			// Connect with one client
			while (true) {
				int nbytes = in.read(buf, 0, buf.length);
				if (nbytes < 0) break; // -1: the end of the stream
				// Turn the data into string
				String dataString = new String(buf, 0, nbytes, "US-ASCII");
				// Manipulate the data
				dataString = dataString.trim();
				String[] dataStrings = dataString.split(":");
				// Return string
				String returnString = "";
				// get instruction
				if("get".equals(dataStrings[0]) && 
						dataStrings.length == 2) {
					String value = map.get(dataStrings[1]);
					if(value == null) {
						returnString = "no match";
					} else {
						returnString = "ok:"+value;
					}
				}
				// put instruction
				else if("put".equals(dataStrings[0]) && 
						dataStrings.length == 3) {
					String previous = map.put(dataStrings[1], dataStrings[2]);
					if(previous == null) {
						returnString = "Ok";
					} else {
						returnString = "updated:"+dataStrings[1];
					}
				}
				// remove instruction
				else if("remove".equals(dataStrings[0]) &&
						dataStrings.length == 2) {
					String removed = map.remove(dataStrings[1]);
					if(removed == null) {
						returnString = "no match";
					} else {
						returnString = "Ok";
					}
				} 
				// get all instruction
				else if ("get all".equals(dataStrings[0]) &&
						dataStrings.length == 1) {
					Iterator<Map.Entry<String,String>> it =map.entrySet().iterator();
					// Iterate all entries
					if(it.hasNext()) {
						Map.Entry<String,String> pair = it.next();
						returnString+=(pair.getKey()+":"+pair.getValue());
					}
					while(it.hasNext()) {
						Map.Entry<String,String> pair = it.next();
						returnString+=("::"+pair.getKey()+":"+pair.getValue());
					}
				} else { // invalid instruction
					returnString = "error:unrecognizable  input:"+dataString;
				}
				returnString+="\n"; // String terminates with '\n'
				out.write(returnString.getBytes(),0,returnString.length());
				out.flush(); // Flush the buffer
			}
			connSock.close();
		}
	}
}
