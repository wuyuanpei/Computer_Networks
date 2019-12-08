import java.io.BufferedReader;
import java.net.*;
import java.io.*;
import java.util.*;
/*
 * DhtClient.java
 * This is the DhtClient. This program will take from 4 or 5
 * command line arguments. The first is the IP address of the
 * socket that the client will bind to. The second is the
 * name of a configuration file containing the IP address and
 * port number of one of the DhtServers. The third is an
 * operation like "get" or "put" and the remaining arguments
 * specify the key and/or value for the operation.
 */
public class DhtClient {
	
	public static void main(String args[]) throws Exception {
		// Check the argument number
		if (args.length != 4 && args.length != 5) {
			System.err.println("usage: DhtClient myIp " +
					   "serverFile put/get [ key ] [ value ] ");
			System.exit(1);
		}
		InetAddress myIp = null;
		DatagramSocket sock = null;
		InetSocketAddress server = null;
		// Read arguments
		try {
			myIp = InetAddress.getByName(args[0]);
			sock = new DatagramSocket(0,myIp);
			BufferedReader serv =
					new BufferedReader(
					    new InputStreamReader(
						new FileInputStream(args[1]),
						"US-ASCII"));
			String s = serv.readLine();
			serv.close();
			String[] chunks = s.split(" ");
			server = new InetSocketAddress(
					chunks[0],Integer.parseInt(chunks[1]));
		} catch(Exception e) {
			System.err.println("usage: DhtClient myIp " +
					   "cfgFile put/get [ key ] [ value ] ");
			System.exit(1);
		}
		// Construct the packet
		Packet request = new Packet();
		if(args[2].equals("get")) {
			request.type = "get";
		}else if(args[2].equals("put")) {
			request.type = "put";
			if(args.length == 5)
				request.val = args[4];
		}else {
			System.err.println("usage: DhtClient myIp " +
					   "cfgFile put/get [ key ] [ value ] ");
			System.exit(1);
		}
		request.key = args[3];
		request.tag = new Random().nextInt(100000);
		// Send the request
		request.send(sock,server,true);
		// Receive the reply and quit
		Packet reply = new Packet();
		if(reply.receive(sock,true) == null) {
			System.err.println("received packet failure");
			System.exit(2);
		}
		return;
	}
}