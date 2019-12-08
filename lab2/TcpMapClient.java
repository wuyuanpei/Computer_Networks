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

public class TcpMapClient {
	public static void main(String args[]) throws Exception {
		// Connect to remote server
		int port = 30123; // Default port number
		if (args.length > 1) port = Integer.parseInt(args[1]); // Specified port number
		Socket sock = new Socket(args[0], port); // Socket created
		// Create buffered reader & writer for socket's in/out streams
		BufferedReader  in = new BufferedReader(new InputStreamReader(
				    	 sock.getInputStream(),"US-ASCII"));
		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(
				    	 sock.getOutputStream(),"US-ASCII"));
		// Create buffered reader for System.in
		BufferedReader sysin = new BufferedReader(new InputStreamReader(
						 System.in));
		String line; // Input line
		while (true) {
			line = sysin.readLine(); // Read the line
			if (line == null || line.length() == 0) break; // Blank line
			// Write line on socket and print feedback to System.out
			out.write(line); out.newLine(); out.flush();
			System.out.println(in.readLine());
		}
		sock.close(); // Close connection
	}
}
