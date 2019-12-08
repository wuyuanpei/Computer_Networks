/** Test reliable data transport protocol.
 *  usage: TestMonitor myIp myPort runLength [ debug ] [ discProb delta ] [
 *				   [ peerIp peerPort  ]
 *  
 *  A pair of TestMonitor processes can be used to test the Monitor class.
 *  One is used as a server, the other as a client. The server should
 *  be started first. Both may send packets.
 *  
 *  myIp	is the IP address to be bound to this program's socket
 *  myPort	is the port number to be bound to this program's socket;
 *  		when starting a client, this can be set to zero
 *  runLength	is the duration of the run (expressed as a floating point value
 *  		n seconds)
 *  debug	if the debug argument is present and equal to the string
 *  		"debug", the program prints every packet sent or received
 *  discProb	is the probability that a generated packet gets discarded,
 *  		allowing us to exercise the protocol's ability to recover;
 *  		default value is 0
 *  delta	is the time to wait between sending packets at the source
 *  		(expressed as a floating point value in seconds);
 *  		if zero, no packets are sent; default is 0
 *  peerIp	is the IP address of the peer host - only required for client
 *  peerPort	is the IP address of the peer host - only required for client
 */
 
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class TestMonitor {
	public static void main(String[] args) throws Exception {
		// process command line arguments
		if (args.length < 3)  {	
			System.out.println("usage: TestMonitor myIp myPort " +
				"runLength [ debug ] [ discProb delta ] " +	
				"[ peerIp peerPort ]");
			System.exit(1);
		}
		InetAddress myIp = InetAddress.getByName(args[0]);
		int myPort = Integer.parseInt(args[1]);
		double runLength = Double.parseDouble(args[2]);

		boolean debug = false; int nextArg = 3;
		if (args.length > nextArg && args[nextArg].equals("debug")) {
			debug = true; nextArg++;
		}
		double discProb = 0;
		if (args.length > nextArg) 
			discProb = Double.parseDouble(args[nextArg++]);
		double delta = 0;
		if (args.length > nextArg) 
			delta = Double.parseDouble(args[nextArg++]);
		System.out.println("delta="+delta);
		InetSocketAddress peerAdr = null;
		if (args.length > nextArg+1) 
			peerAdr = new InetSocketAddress(args[nextArg],
				    	Integer.parseInt(args[nextArg+1]));

		// instantiate components and start their threads
		Substrate sub = new Substrate(myIp,myPort,peerAdr,
					      discProb,debug);
		Monitor mon = new Monitor(sub);
		SrcSnk ss = new SrcSnk(delta,runLength,mon);
		sub.start(); mon.start(); ss.start();

		// wait for substrate to quit, then stop others
		sub.join(); mon.stop(); ss.stop();
	}
}
