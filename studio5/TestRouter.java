import java.net.*;
import java.util.*;
import java.io.*;

/** Test overlay IP Router.
 *  
 *  usage: TestRouter configFile delta runLength [ debug ]
 *  
 *  This program implements a simplified IP router, including a
 *  random packet forwarder.
 *  It also includes a test application that sends packets to
 *  random destinations in the network.
 *  
 *  configFile	is the name of a configuration file that provides
 *  		essential information for this router, including the
 *  		identity of its neighbors.
 *  delta 	is the time (in seconds) that the application layer waits
 * 			between sending new packets
 *  runLength 	is the amount of time (in seconds) that the application runs
 *  debug	is an optional argument that controls whether debugging output
 *  		is printed or not
 */
public class TestRouter {
	private static InetAddress hostIp; // IP to bind to socket for this host
	private static int myIp;	// raw IP of router in overlay net

	private static ArrayList<NborInfo> nborList; // list of neighbors
	private static ArrayList<String> destList; // list of dest IP strings

	/** Main method parses command line arguments, reads config file
	 *  and starts other components
 	 */
	public static void main(String [] args) {
		// process command line arguments
		if (args.length < 3)  {
			System.out.println("usage: TestRouter configFile " +
				"delta runLength [debug]");
			System.exit(1);
		}
		String configFile = args[0];
		double delta = Double.parseDouble(args[1]);
		double runLength = Double.parseDouble(args[2]);

		boolean staticDelay = false; int debug = 0; int nextArg = 4;
		for (int i = 3; i < args.length; i++) {
			if (args[i].equals("static")) staticDelay = true;
			else if (args[i].equals("debug")) debug = 1;
		}

		nborList = new ArrayList<NborInfo>();
		destList = new ArrayList<String>();

		if (!readConfigFile(configFile)) {
			System.out.println("cannot read config file");
			System.exit(1);
		}

		// instantiate components and start their threads
		Substrate sub = new Substrate(
			hostIp,nborList,staticDelay,debug);
		Forwarder fwdr = new Forwarder(myIp,sub,debug);
		SrcSnk ss = new SrcSnk(delta,runLength,fwdr,destList);

		try {
			sub.start(); fwdr.start();
			Thread.sleep(2000); ss.start();
	
			// wait for substrate to quit, then stop others
			sub.join(); Thread.sleep(100);
			fwdr.stop(); ss.stop();
		} catch(Exception e) {}
	}

	/** Read a configuration file and initialize internal data structures.
	 *
	 *  configFile is the name of a file containing configuration info
	 *  for this router. An example illustrating the format is shown below.
	 *
	 *  hostIp: 192.168.4.2
	 *  myIp: 1.1.0.1
	 *  neighbor: 1.2.0.1 192.168.7.1 .01
	 *  neighbor: 1.3.0.1 192.168.2.4 .05
	 *  destination: 1.2.0.1
	 *  destination: 1.3.0.1
	 *
	 *  Each line starts with a label. The hostIp line identifies the
	 *  IP address of the host that runs the overlay router (that is,
	 *  the IP address in the "substrate" network).
	 *
	 *  The myIp line identifies the IP address of this router in the
	 *  overlay network.
	 *
	 *  Each neighbor line gives the overlay IP address and substrate
	 *  IP address of a neighbor, plus the initial cost of the link
	 *  connecting this router to the neighbor. These costs are used to
	 *  set initial link delays (in seconds).
	 *
	 *  Each destination line give the overlay IP address of a router
	 *  in the network. These are used by the application layer to send
	 *  test packets to others in the network.
	 *
	 *  The configuration information extracted from the file is used
	 *  to initialize the static instance variables: hostIp, myIp,
	 *  pfxList, nborList and destList.
	 */
	public static boolean readConfigFile(String configFile) {
		BufferedReader config = null;
		try {
			config = new BufferedReader(new InputStreamReader(
				 new FileInputStream(configFile), "US-ASCII"));
		} catch(Exception e) {
			System.out.println("Cannot open config file ("
					    + e + ")");
			return false;
		}

		String line;
		try {
			while ((line = config.readLine()) != null) {
				String[] chunks = line.split(":");
				String left = chunks[0].trim();
				String right = chunks[1].trim();
				if (left.equals("hostIp")) {
					hostIp = InetAddress.getByName(right);
				} else if (left.equals("myIp")) {
					myIp = Util.string2ip(right);
				} else if (left.equals("neighbor")) {
					nborList.add(new NborInfo(right));
				} else if (left.equals("destination")) {
					destList.add(right);
				} 
			}
			config.close();
		} catch (Exception e) {
			System.err.println("Can't read configuration file");
			System.exit(1);
		}
		return true;
	}
}
