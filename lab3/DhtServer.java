
/** Server for simple distributed hash table that stores (key,value) strings.
 *  
 *  usage: DhtServer myIp numRoutes cfgFile [ cache ] [ debug ] [ predFile ]
 *  
 *  myIp	is the IP address to use for this server's socket
 *  numRoutes	is the max number of nodes allowed in the DHT's routing table;
 *  		typically lg(numNodes)
 *  cfgFile	is the name of a file in which the server writes the IP
 *		address and port number of its socket
 *  cache	is an optional argument; if present it is the literal string
 *		"cache"; when cache is present, the caching feature of the
 *		server is enabled; otherwise it is not
 *  debug	is an optional argument; if present it is the literal string
 *		"debug"; when debug is present, a copy of every packet received
 *		and sent is printed on stdout
 *  predFile	is an optional argument specifying the configuration file of
 *		this node's predecessor in the DHT; this file is used to obtain
 *		the IP address and port number of the predecessor's socket,
 *		allowing this node to join the DHT by contacting predecessor
 *  
 *  The DHT uses UDP packets containing ASCII text. Here's an example of the
 *  UDP payload for a get request from a client.
 *  
 *  CSE473 DHTPv0.1
 *  type:get
 *  key:dungeons
 *  tag:12345
 *  ttl:100
 *  
 *  The first line is just an identifying string that is required in every
 *  DHT packet. The remaining lines all start with a keyword and :, usually
 *  followed by some additional text. Here, the type field specifies that
 *  this is a get request; the key field specifies the key to be looked up;
 *  the tag is a client-specified tag that is returned in the response; and
 *  can be used by the client to match responses with requests; the ttl is
 *  decremented by every DhtServer and if <0, causes the packet to be discarded.
 *  
 *  Possible responses to the above request include:
 *  
 *  CSE473 DHTPv0.1
 *  type:success
 *  key:dungeons
 *  value:dragons
 *  tag:12345
 *  ttl:95
 *  
 *  or
 *  
 *  CSE473 DHTPv0.1
 *  type:no match
 *  key:dungeons
 *  tag:12345
 *  ttl:95
 *  
 *  Put requests are formatted similarly, but in this case the client typically
 *  specifies a value field (omitting the value field causes the pair with the
 *  specified key to be removed).
 *  
 *  The packet type "failure" is used to indicate an error of some sort; in 
 *  this case, the "reason" field provides an explanation of the failure. 
 *  The "join" type is used by a server to join an existing DHT. In the same
 *  way, the "leave" type is used by the leaving server to circle around the 
 *  DHT asking other servers to delete it from their routing tables.  The 
 *  "transfer" type is used to transfer (key,value) pairs to a newly added 
 *  server. The "update" type is used to update the predecessor, successor, 
 *  or hash range of another DHT server, usually when a join or leave even 
 *  happens. 
 *
 *  Other fields and their use are described briefly below
 *  clientAdr 	is used to specify the IP address and port number of the 
 *              client that sent a particular request; it is added to a request
 *              packet by the first server to receive the request, before 
 *              forwarding the packet to another node in the DHT; an example of
 *              the format is clientAdr:123.45.67.89:51349.
 *  relayAdr  	is used to specify the IP address and port number of the first
 *              server to receive a request packet from the client; it is added
 *              to the packet by the first server before forwarding the packet.
 *  hashRange 	is a pair of integers separated by a colon, specifying a range
 *              of hash indices; it is included in the response to a "join" 
 *              packet, to inform the new DHT server of the set of hash values
 *              it is responsible for; it is also included in the update packet
 *              to update the hash range a server is responsible for.
 *  succInfo  	is the IP address and port number of a server, followed by its
 *              first hash index; this information is included in the response
 *              to a join packet to inform the new DHT server about its 
 *              immediate successor; it's also included in the update packet 
 *              to change the immediate successor of a DHT server; an example 
 *              of the format is succInfo:123.45.6.7:5678:987654321.
 *  predInfo	is also the IP address and port number of a server, followed
 *              by its first hash index; this information is included in a join
 *              packet to inform the successor DHT server of its new 
 *              predecessor; it is also included in update packets to update 
 *              the new predecessor of a server.
 *  senderInfo	is the IP address and port number of a DHT server, followed by
 *              its first hash index; this information is sent by a DHT to 
 *              provide routing information that can be used by other servers.
 *              It also used in leave packet to let other servers know the IP
 *              address and port number information of the leaving server.
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.lang.*;

import sun.misc.Signal;
import sun.misc.SignalHandler;

public class DhtServer {
	private static int numRoutes; // number of routes in routing table
	private static boolean cacheOn; // enables caching when true
	private static boolean debug; // enables debug messages when true

	private static HashMap<String, String> map; // key/value pairs
	private static HashMap<String, String> cache; // cached pairs
	private static List<Pair<InetSocketAddress, Integer>> rteTbl;

	private static DatagramSocket sock;
	private static InetSocketAddress myAdr;
	private static InetSocketAddress predecessor; // DHT predecessor
	private static Pair<InetSocketAddress, Integer> myInfo;
	private static Pair<InetSocketAddress, Integer> predInfo;
	private static Pair<InetSocketAddress, Integer> succInfo; // successor
	private static Pair<Integer, Integer> hashRange; // my DHT hash range
	private static int sendTag; // tag for new outgoing packets
	// flag for waiting leave message circle back
	private static boolean stopFlag;

	/**
	 * Main method for DHT server. Processes command line arguments, initializes
	 * data, joins DHT, then starts processing requests from clients.
	 */
	public static void main(String[] args) {
		// process command-line arguments
		if (args.length < 3) {
			System.err.println("usage: DhtServer myIp numRoutes " + 
					"cfgFile [ cache ] [ debug ] " + 
					"[ predFile ] ");
			System.exit(1);
		}
		numRoutes = Integer.parseInt(args[1]);
		String cfgFile = args[2];
		cacheOn = debug = false;
		stopFlag = false;
		String predFile = null;
		for (int i = 3; i < args.length; i++) {
			if (args[i].equals("cache"))
				cacheOn = true;
			else if (args[i].equals("debug"))
				debug = true;
			else
				predFile = args[i];
		}

		// open socket for receiving packets
		// write ip and port to config file
		// read predecessor's ip/port from predFile (if there is one)
		InetAddress myIp = null;
		sock = null;
		predecessor = null;
		try {
			myIp = InetAddress.getByName(args[0]);
			sock = new DatagramSocket(0, myIp);
			BufferedWriter cfg = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(cfgFile), "US-ASCII"));
			cfg.write("" + myIp.getHostAddress() + 
					" " + sock.getLocalPort());
			cfg.newLine();
			cfg.close();
			if (predFile != null) {
				BufferedReader pred = new BufferedReader(
						new InputStreamReader(
								new FileInputStream(predFile), "US-ASCII"));
				String s = pred.readLine();
				String[] chunks = s.split(" ");
				predecessor = new InetSocketAddress(chunks[0], 
						Integer.parseInt(chunks[1]));
			}
		} catch (Exception e) {
			System.err.println("usage: DhtServer myIp numRoutes " + 
					"cfgFile [ cache ] [ debug ] " + 
					"[ predFile ] ");
			System.exit(1);
		}
		myAdr = new InetSocketAddress(myIp, sock.getLocalPort());

		// initialize data structures
		map = new HashMap<String, String>();
		cache = new HashMap<String, String>();
		rteTbl = new LinkedList<Pair<InetSocketAddress, Integer>>();

		// join the DHT (if not the first node)
		hashRange = new Pair<Integer, Integer>(0, Integer.MAX_VALUE);
		myInfo = null;
		succInfo = null;
		predInfo = null;
		if (predecessor != null) {
			join(predecessor); 
		} else { 
			myInfo = new Pair<InetSocketAddress, Integer>(myAdr, 0);
			succInfo = new Pair<InetSocketAddress, Integer>(myAdr, 0);
			predInfo = new Pair<InetSocketAddress, Integer>(myAdr, 0);
		}

		// start processing requests from clients
		Packet p = new Packet();
		Packet reply = new Packet();
		InetSocketAddress sender = null;
		sendTag = 1;

		/*
		 * this function will be called if there's a "TERM" or "INT" captured by the
		 * signal handler. It simply execute the leave function and leave the program.
		 */
		SignalHandler handler = new SignalHandler() {
			public void handle(Signal signal) {
				leave();
				System.exit(0);
			}
		};
		// Signal.handle(new Signal("KILL"), handler); // capture kill -9 signal
		Signal.handle(new Signal("TERM"), handler); // capture kill -15 signal
		Signal.handle(new Signal("INT"), handler); // capture ctrl+c

		while (true) {
			try {
				sender = p.receive(sock, debug);
			} catch (Exception e) {
				System.err.println("received packet failure");
				continue;
			}
			if (sender == null) {
				System.err.println("received packet failure");
				continue;
			}
			if (!p.check()) {
				reply.clear();
				reply.type = "failure";
				reply.reason = p.reason;
				reply.tag = p.tag;
				reply.ttl = p.ttl;
				reply.send(sock, sender, debug);
				continue;
			}
			handlePacket(p, sender);
		}
	}

	/**
	 * Hash a string, returning a 32 bit integer.
	 * 
	 * @param s is a string, typically the key from some get/put operation.
	 * @return and integer hash value in the interval [0,2^31).
	 */
	public static int hashit(String s) {
		while (s.length() < 16)
			s += s;
		byte[] sbytes = null;
		try {
			sbytes = s.getBytes("US-ASCII");
		} catch (Exception e) {
			System.out.println("illegal key string");
			System.exit(1);
		}
		int i = 0;
		int h = 0x37ace45d;
		while (i + 1 < sbytes.length) {
			int x = (sbytes[i] << 8) | sbytes[i + 1];
			h *= x;
			int top = h & 0xffff0000;
			int bot = h & 0xffff;
			h = top | (bot ^ ((top >> 16) & 0xffff));
			i += 2;
		}
		if (h < 0)
			h = -(h + 1);
		return h;
	}

	/**
	 * Leave an existing DHT.
	 * 
	 * Send a leave packet to it's successor and wait until stopFlag is set to
	 * "true", which means leave packet is circle back.
	 *
	 * Send an update packet with the new hashRange and succInfo fields to its
	 * predecessor, and sends an update packet with the predInfo field to its
	 * successor.
	 * 
	 * Transfers all keys and values to predecessor. Clear all the existing cache,
	 * map and rteTbl information
	 */
	public static void leave() {
		// your code here
		// a random generator
		Random r = new Random();
		// send leave packet to successor
		Packet p = new Packet();
		p.type = "leave";
		p.tag = r.nextInt(100000);
		p.senderInfo = myInfo;
		p.send(sock, succInfo.left, debug);
		// wait until stopFlag is set to "true"
		while(!stopFlag);
		// send update packet to pred and succ
		p.clear();
		p.type = "update";
		p.tag = r.nextInt(100000);
		p.succInfo = succInfo;
		p.hashRange = new Pair<Integer, Integer>(predInfo.right,hashRange.right);
		p.send(sock,predInfo.left,debug);
		p.clear();
		p.type = "update";
		p.tag = r.nextInt(100000);
		p.predInfo = predInfo;
		p.send(sock,succInfo.left,debug);
		// transfer all keys and empty data structures
		p.clear();
		p.type = "transfer";
		for (Map.Entry<String, String> entry : map.entrySet()) {
			p.tag = r.nextInt(100000);
			p.key = entry.getKey();
			p.val = entry.getValue();
			p.send(sock,predInfo.left,debug);
		}
		map.clear();
		cache.clear();
		rteTbl.clear();
	}

	/**
	 * Handle a update packet from a prospective DHT node.
	 * 
	 * @param p   is the received update packet
	 * @param adr is the socket address of the host that
	 * 
	 *            The update message might contains information need update,
	 *            including predInfo, succInfo, and hashRange. And add the new
	 *            Predecessor/Successor into the routing table. If succInfo is
	 *            updated, succInfo should be removed from the routing table and the
	 *            new succInfo should be added into the new routing table.
	 */
	public static void handleUpdate(Packet p, InetSocketAddress adr) {
		if (p.predInfo != null) {
			predInfo = p.predInfo;
		}
		if (p.succInfo != null) {
			succInfo = p.succInfo;
			addRoute(succInfo);
		}
		if (p.hashRange != null) {
			hashRange = p.hashRange;
		}
	}

	/**
	 * Handle a leave packet from a leaving DHT node.
	 * 
	 * @param p   is the received leave packet
	 * @param adr is the socket address of the host that sent the leave packet
	 *
	 *            If the leave packet is sent by this server, set the stopFlag.
	 *            Otherwise firstly send the received leave packet to its successor,
	 *            and then remove the routing entry with the senderInfo of the
	 *            packet.
	 */
	public static void handleLeave(Packet p, InetSocketAddress adr) {
		if (p.senderInfo.equals(myInfo)) {
			stopFlag = true;
			return;
		}
		// send the leave message to successor
		p.send(sock, succInfo.left, debug);

		// remove the senderInfo from route table
		removeRoute(p.senderInfo);
	}

	/**
	 * Join an existing DHT.
	 * 
	 * @param predAdr is the socket address of a server in the DHT,
	 * 			Send a join packet to the predecessor, and then wait
	 * 			for reply. The reply should typically be success
	 * 			packet which contains succInfo, predInfo, and 
	 * 			hashRange. All the informtaion is set and the sucInfo
	 * 			is added to the route table.              
	 */
	public static void join(InetSocketAddress predAdr) {
		// your code here
		// send join packet
		Packet p = new Packet();
		p.tag = new Random().nextInt(100000);
		p.type = "join";
		p.send(sock,predAdr,debug);
		// wait for receiving information
		p.clear();
		p.receive(sock,debug);
		succInfo = p.succInfo;
		predInfo = p.predInfo;
		hashRange = p.hashRange;
		myInfo = new Pair<InetSocketAddress, Integer>(myAdr, hashRange.left);
		addRoute(succInfo);
	}

	/**
	 * Handle a join packet from a prospective DHT node.
	 * 
	 * @param p       is the received join packet
	 * @param succAdr is the socket address of the host that sent the join packet
	 *                (the new successor)
	 *				This function handls join request. It divides its hash
	 *				range by half and send the top half to the join server.
	 *				It also sends succInfo and predInfo to the server to
	 *				set up the links. It then sends update packet to its
	 *				original successor to update its predecessor. It
	 *				finally updates its own information and send transfer
	 *				packets to the new server.
	 */
	public static void handleJoin(Packet p, InetSocketAddress succAdr) {
		// your code here
		// send success packet to new server
		int left = hashRange.left.intValue();
		int right = hashRange.right.intValue();
		int mid = left + (right-left)/2;
		p.type = "success";
		p.hashRange = new Pair<Integer, Integer>(mid+1, right);
		p.succInfo = succInfo;
		p.predInfo = myInfo;
		p.senderInfo = myInfo;
		p.send(sock,succAdr,debug);
		// send update packet to original successor
		p.clear();
		p.type = "update";
		p.tag = new Random().nextInt(100000);
		p.senderInfo = myInfo;
		Pair<InetSocketAddress, Integer> joinInfo = new Pair<InetSocketAddress, Integer>(succAdr, mid+1);
		p.predInfo = joinInfo;
		p.send(sock,succInfo.left,debug);
		// update some information
		succInfo = joinInfo;
		addRoute(succInfo);
		hashRange = new Pair<Integer, Integer>(left, mid);
		// send transfer packets to new server
		p.clear();
		p.type = "transfer";
		for(Iterator<Map.Entry<String, String>> it = map.entrySet().iterator(); it.hasNext(); ) {
		    Map.Entry<String, String> entry = it.next();
		    int hashValue = hashit(entry.getKey());
		    if(mid+1 <= hashValue && hashValue <= right) {
		    	p.key = entry.getKey();
		    	p.val = entry.getValue();
		    	p.tag = new Random().nextInt(100000);
		    	p.send(sock,succAdr,debug);
		        it.remove();
		    }
		}
	}

	/**
	 * Handle a get packet.
	 * 
	 * @param p         is a get packet
	 * @param senderAdr is the socket address of the sender
	 *			This function handles get packet. If the hash is in its
	 *			range. It send the information back either to the client
	 *			or to the relay server. If it's not in its range, it will
	 *			first look up the informtion in cache. If it cannot find
	 *			the entry, it will forward the request to another server.
	 */
	public static void handleGet(Packet p, InetSocketAddress senderAdr) {
		// this version is incomplete; you will have to extend
		// it to support caching
		InetSocketAddress replyAdr;
		int hash = hashit(p.key);
		int left = hashRange.left.intValue();
		int right = hashRange.right.intValue();

		if (left <= hash && hash <= right) {
			// respond to request using map
			if (p.relayAdr != null) {
				replyAdr = p.relayAdr;
				p.senderInfo = myInfo;
			} else {
				replyAdr = senderAdr;
			}
			if (map.containsKey(p.key)) {
				p.type = "success";
				p.val = map.get(p.key);
			} else {
				p.type = "no match";
			}
			p.send(sock, replyAdr, debug);
		} else {
			if(cacheOn){
				// Iterate through cache
				for (Map.Entry<String, String> entry : cache.entrySet()) {
					if(entry.getKey().equals(p.key)) {
						if (p.relayAdr != null) {
							replyAdr = p.relayAdr;
							p.senderInfo = myInfo;
						} else {
							replyAdr = senderAdr;
						}
						p.type = "success";
						p.val = entry.getValue();
						p.send(sock, replyAdr, debug);
						return;
					}
				}
			}
			// forward around DHT
			if (p.relayAdr == null) {
				p.relayAdr = myAdr;
				p.clientAdr = senderAdr;
			}
			forward(p, hash);
		}
	}

	/**
	 * Handle a put packet.
	 * 
	 * @param p         is a put packet
	 * @param senderAdr is the the socket address of the sender
	 *			This function handles put packet. If the hash is in its
	 *			range, it handles the request and sends information back 
	 *			either to the client or to the relay server. If it's
	 *		    not in its range, it will first look up the informtion
	 *			in cache. If it finds the entry, it will delete it and
	 *			then forward request to another server.
	 */
	public static void handlePut(Packet p, InetSocketAddress senderAdr) {
		// your code here
		InetSocketAddress replyAdr;
		int hash = hashit(p.key);
		int left = hashRange.left.intValue();
		int right = hashRange.right.intValue();

		if (left <= hash && hash <= right) {
			// respond to request using map
			if (p.relayAdr != null) {
				replyAdr = p.relayAdr;
				p.senderInfo = myInfo;
			} else {
				replyAdr = senderAdr;
			}
			if (p.val != null) {
				map.put(p.key, p.val); //put or update
				p.type = "success";
			} else {
				// remove instruction
				if(map.remove(p.key) != null) {
					p.type = "success";
				} else {
					int ttl = p.ttl; // Use the original information
					int tag = p.tag;
					p.clear();
					p.type = "failure";
					p.reason = "no corresponding (key, value) pair";
					p.tag = tag;
					p.ttl = ttl;
					p.senderInfo = myInfo;
				}
			}
			p.send(sock, replyAdr, debug);
		} else {
			if(cacheOn){
				// Iterate through cache
				for (Map.Entry<String, String> entry : cache.entrySet()) {
					if(entry.getKey().equals(p.key)) {
						cache.remove(p.key);
						break;
					}
				}
			}
			// forward around DHT
			if (p.relayAdr == null) {
				p.relayAdr = myAdr;
				p.clientAdr = senderAdr;
			}
			forward(p, hash);
		}
	}

	/**
	 * Handle a transfer packet.
	 * 
	 * @param p         is a transfer packet
	 * @param senderAdr is the the address (ip:port) of the sender
	 * 
	 *                  This function handls a transfer packet.
	 *                  It silently puts the entry into map and then
	 *                  return.
	 */
	public static void handleXfer(Packet p, InetSocketAddress senderAdr) {
		// your code here
		map.put(p.key, p.val);
	}

	/**
	 * Handle a reply packet.
	 * 
	 * @param p         is a reply packet, more specifically, a packet of type
	 *                  "success", "failure" or "no match"
	 * @param senderAdr is the the address (ip:port) of the sender
	 * 
	 *                  This function handles success, failure, and no match
	 *                  packet. It assumes it's the relay server and clears
	 *                  all the server information and then send the packet
	 *                  back to the client. If the packet is success and has
	 *                  key/value, it will put the entry into cache.
	 */
	public static void handleReply(Packet p, InetSocketAddress senderAdr) {
		// your code here
		p.relayAdr = null;
		InetSocketAddress client = p.clientAdr;
		p.clientAdr = null;
		p.senderInfo = null;
		// Add into cache
		if(cacheOn && p.type.equals("success") && !cache.containsKey(p.key) && p.val != null) {
			cache.put(p.key, p.val);
		}
		p.send(sock,client,debug);
	}

	/**
	 * Handle packets received from clients or other servers
	 * 
	 * @param p         is a packet
	 * @param senderAdr is the address (ip:port) of the sender
	 */
	public static void handlePacket(Packet p, InetSocketAddress senderAdr) {
		if (p.senderInfo != null & !p.type.equals("leave"))
			addRoute(p.senderInfo);
		if (p.type.equals("get")) {
			handleGet(p, senderAdr);
		} else if (p.type.equals("put")) {
			handlePut(p, senderAdr);
		} else if (p.type.equals("transfer")) {
			handleXfer(p, senderAdr);
		} else if (p.type.equals("success") || p.type.equals("no match") || p.type.equals("failure")) {
			handleReply(p, senderAdr);
		} else if (p.type.equals("join")) {
			handleJoin(p, senderAdr);
		} else if (p.type.equals("update")) {
			handleUpdate(p, senderAdr);
		} else if (p.type.equals("leave")) {
			handleLeave(p, senderAdr);
		}
	}

	/**
	 * Add an entry to the route tabe.
	 * 
	 * @param newRoute is a pair (addr,hash) where addr is the socket address for
	 *                 some server and hash is the first hash in that server's range
	 *
	 *                 If the number of entries in the table exceeds the max number
	 *                 allowed, the first entry that does not refer to the successor
	 *                 of this server, is removed. If debug is true and the set of
	 *                 stored routes does change, print the string "rteTbl=" +
	 *                 rteTbl. (IMPORTANT)
	 */
	public static void addRoute(Pair<InetSocketAddress, Integer> newRoute) {
		// your code here
		if (!rteTbl.contains(newRoute)) {
			if (rteTbl.size() >= numRoutes) {
				for (Iterator<Pair<InetSocketAddress, Integer>> it = rteTbl.iterator(); it.hasNext();) {
				    if (!it.next().equals(succInfo)) {
				    	it.remove();
				    	break;
				    }
				}
			}
			if(rteTbl.size() < numRoutes) {
				rteTbl.add(newRoute);
				if (debug) {
					System.out.println("rteTbl=" + rteTbl + "\n");
				}
			}
		}
	}

	/**
	 * Remove an entry from the route tabe.
	 * 
	 * @param rmRoute is the route information for some server need to be removed
	 *                from route table
	 *
	 *                If the route information exists in current entries, remove it.
	 *                Otherwise, do nothing. If debug is true and the set of stored
	 *                routes does change, print the string "rteTbl=" + rteTbl.
	 *                (IMPORTANT)
	 */
	public static void removeRoute(Pair<InetSocketAddress, Integer> rmRoute) {
		// your code here
		if (rteTbl.contains(rmRoute)) {
			rteTbl.remove(rmRoute);
			if (debug) {
				System.out.println("rteTbl=" + rteTbl + "\n");
			}
		}
	}

	/**
	 * Forward a packet using the local routing table.
	 * 
	 * @param p    is a packet to be forwarded
	 * @param hash is the hash of the packet's key field
	 *
	 *             This method selects a server from its route table that is
	 *             "closest" to the target of this packet (based on hash). If
	 *             firstHash is the first hash in a server's range, then we seek to
	 *             minimize the difference hash-firstHash, where the difference is
	 *             interpreted modulo the range of hash values. IMPORTANT POINT -
	 *             handle "wrap-around" correctly. Once a server is selected, p is
	 *             sent to that server.
	 */
	public static void forward(Packet p, int hash) {
		// your code here
		int minDiff = Integer.MAX_VALUE;
		InetSocketAddress fwdAdr = null;
		for (Iterator<Pair<InetSocketAddress, Integer>> it = rteTbl.iterator(); it.hasNext();) {
			Pair<InetSocketAddress, Integer> srvInfo = it.next();
			int firstHash = srvInfo.right.intValue();
			int diff = hash - firstHash;
			int mod = Math.floorMod(diff,Integer.MAX_VALUE);
		    if (mod < minDiff) {
		    	minDiff = mod;
		    	fwdAdr = srvInfo.left;
		    }
		}
		p.send(sock,fwdAdr,debug);
	}
}
