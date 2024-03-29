import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Router module for an overlay router.
 *
 * The router sends hello packets every one second to its neighbours.
 * If three consecutive packets have no reply, the link will be marked
 * as invalid. Failure packets will be sent to its neighbour if the 
 * failure affects valid routes. It also sends advertisement packets
 * about its paths every 10 seconds periodically to its neighbours. It
 * will forward any advertisement and failure received if this information
 * causes some change in its routing table.
 */
public class Router implements Runnable {
	private Thread myThread; // thread that executes run() method
	private int myIp; // ip address in the overlay
	private String myIpString; // String representation
	private ArrayList<Prefix> pfxList; // list of prefixes to advertise
	private ArrayList<NborInfo> nborList; // list of info about neighbors

	private class LinkInfo { // class used to record link information
		public int peerIp; // IP address of peer in overlay net
		public double cost; // in seconds
		public boolean gotReply; // flag to detect hello replies
		public int helloState; // set to 3 when hello reply received
		// decremented whenever hello reply
		// is not received; when 0, link is down

		// link cost statistics
		public int count;
		public double totalCost;
		public double minCost;
		public double maxCost;

		LinkInfo() {
			cost = 0;
			gotReply = true;
			helloState = 3;
			count = 0;
			totalCost = 0;
			minCost = 10;
			maxCost = 0;
		}
	}

	private ArrayList<LinkInfo> lnkVec; // indexed by link number

	private class Route { // routing table entry
		public Prefix pfx; // destination prefix for route
		public double timestamp; // time this route was generated
		public double cost; // cost of route in ns
		public LinkedList<Integer> path;// list of router IPs;
		// destination at end of list
		public int outLink; // outgoing link for this route
		public boolean valid; // indicate the valid of the route
	}

	private ArrayList<Route> rteTbl; // routing table

	private Forwarder fwdr; // reference to Forwarder object

	private double now; // current time in ns
	private static final double sec = 1000000000; // ns per sec

	private int debug; // controls debugging output
	private boolean quit; // stop thread when true
	private boolean enFA; // link failure advertisement enable

	/**
	 * Initialize a new Router object.
	 * 
	 * @param myIp     is an integer representing the overlay IP address of this
	 *                 node in the overlay network
	 * @param fwdr     is a reference to the Forwarder object through which the
	 *                 Router sends and receives packets
	 * @param pfxList  is a list of prefixes advertised by this router
	 * @param nborList is a list of neighbors of this node
	 *
	 * @param debug    is an integer that controls the amount of debugging
	 *                 information that is to be printed
	 */

	Router(int myIp, Forwarder fwdr, ArrayList<Prefix> pfxList, ArrayList<NborInfo> nborList, int debug, boolean enFA) {
		this.myIp = myIp;
		this.myIpString = Util.ip2string(myIp);
		this.fwdr = fwdr;
		this.pfxList = pfxList;
		this.nborList = nborList;
		this.debug = debug;
		this.enFA = enFA;

		lnkVec = new ArrayList<LinkInfo>();
		for (NborInfo nbor : nborList) {
			LinkInfo lnk = new LinkInfo();
			lnk.peerIp = nbor.ip;
			lnk.cost = nbor.delay;
			lnkVec.add(lnk);
		}
		rteTbl = new ArrayList<Route>();
		quit = false;
	}

	/** Instantiate and start a thread to execute run(). */
	public void start() {
		myThread = new Thread(this);
		myThread.start();
	}

	/** Terminate the thread. */
	public void stop() throws Exception {
		quit = true;
		myThread.join();
	}

	/**
	 * This is the main thread for the Router object.
	 *
	 * The router thread sends hello and advertisement packets periodically 
	 * (1 second and 10 seconds respectively) by calling specific functions.
	 * It also retrieves incoming packets and processes it.
	 */
	public void run() {
		double t0 = System.nanoTime() / sec;
		now = 0;
		double helloTime, pvSendTime;
		helloTime = pvSendTime = now;
		while (!quit) {
			// TODO
			now = System.nanoTime() / sec - t0;
			double helloDelta = 1;
			double advertDelta = 10;
			// if it's time to send hello packets, do it
			if (now > helloTime + helloDelta && fwdr.ready4pkt()) {
				sendHellos();
				helloTime += helloDelta;
			}
			// else if it's time to send advertisements, do it
			else if (now > pvSendTime + advertDelta && fwdr.ready4pkt()) {
				sendAdverts();
				pvSendTime += advertDelta;
			}
			// else if the forwarder has an incoming packet
			// to be processed, retrieve it and process it
			else if (fwdr.incomingPkt()) {
				handleIncoming();
			}
			// else nothing to do, so take a nap
			else {
				try {
					Thread.sleep(1);
				} catch (Exception e) {
					System.out.println("Router:run: " + "can't sleep" + e);
					System.exit(1);
				}
			}
		}
		String s = String.format("Router link cost statistics\n" + "%8s %8s %8s %8s %8s\n", "peerIp", "count",
				"avgCost", "minCost", "maxCost");
		for (LinkInfo lnk : lnkVec) {
			if (lnk.count == 0)
				continue;
			s += String.format("%8s %8d %8.3f %8.3f %8.3f\n", Util.ip2string(lnk.peerIp), lnk.count,
					lnk.totalCost / lnk.count, lnk.minCost, lnk.maxCost);
		}
		System.out.println(s);
	}

	/**
	 * Lookup route in routing table.
	 *
	 * @param pfx is IP address prefix to be looked up.
	 * @return a reference to the Route that matches the prefix or null
	 */
	private Route lookupRoute(Prefix pfx) {
		// TODO lookup function
		Route mtRte = null;
		for (Route rte : rteTbl) {
			if (rte.pfx.equals(pfx)) {
				mtRte = rte;
				break;
			}
		}
		return mtRte;
	}

	/**
	 * Add a route to the routing table.
	 * 
	 * @param rte is a route to be added to the table; no check is done to make sure
	 *            this route does not conflict with an existing route
	 */
	private void addRoute(Route rte) {
		// TODO add route
		rteTbl.add(rte);
	}

	/**
	 * Update a route in the routing table.
	 *
	 * @param rte   is a reference to a route in the routing table.
	 * @param nuRte is a reference to a new route that has the same prefix as rte
	 * @return true if rte is modified, else false
	 *
	 *         This method replaces certain fields in rte with fields in nuRte.
	 *         Specifically,
	 *
	 *         if nuRte has a link field that refers to a disabled link, ignore it
	 *         and return false
	 *
	 *         else, if the route is invalid, then update the route and return true,
	 *
	 *         else, if both routes have the same path and link, then the timestamp
	 *         and cost fields of rte are updated
	 *
	 *         else, if nuRte has a cost that is less than .9 times the cost of rte,
	 *         then all fields in rte except the prefix fields are replaced with the
	 *         corresponding fields in nuRte
	 *
	 *         else, if nuRte is at least 20 seconds newer than rte (as indicated by
	 *         their timestamps), then all fields of rte except the prefix fields
	 *         are replaced
	 *
	 *         else, if the link field for rte refers to a link that is currently
	 *         disabled, replace all fields in rte but the prefix fields
	 */
	private boolean updateRoute(Route rte, Route nuRte) {
		// TODO update route
		if (lnkVec.get(nuRte.outLink).helloState == 0) {
			return false;
		}
		else if (!rte.valid) {
			rte.cost = nuRte.cost;
			rte.outLink = nuRte.outLink;
			rte.path = nuRte.path;
			rte.timestamp = nuRte.timestamp;
			rte.valid = true;
			return true;
		}
		else if (rte.path.equals(nuRte.path)) {
			rte.cost = nuRte.cost;
			rte.timestamp = nuRte.timestamp;
			return true;
		}
		else if (nuRte.cost < 0.9*rte.cost
				|| nuRte.timestamp >= rte.timestamp + 20
				|| lnkVec.get(rte.outLink).helloState == 0) {
			rte.cost = nuRte.cost;
			rte.outLink = nuRte.outLink;
			rte.path = nuRte.path;
			rte.timestamp = nuRte.timestamp;
			rte.valid = true;
			return true;
		}
		return false;
	}

	/**
	 * Send hello packet to all neighbors.
	 *
	 * First check for replies. If no reply received on some link, update the link
	 * status by subtracting 1. If that makes it 0, the link is considered down, so
	 * we mark all routes using that link as invalid. Also, if certain routes are
	 * marked as invalid, we will need to print the table if debug larger than 1,
	 * and we need to send failure advertisement by calling sendFailureAdvert if
	 * failure advertisement is enable.
	 */
	public void sendHellos() {
		int lnk = 0;
		for (LinkInfo lnkInfo : lnkVec) {
			//TODO
			boolean update = false;
			// if no reply to the last hello, subtract 1 from
			// link status if it's not already 0
			if (!lnkInfo.gotReply && lnkInfo.helloState != 0) {
				lnkInfo.helloState--;
			}
			if (lnkInfo.helloState == 0) {
			// go through the routes to check routes 
			// that contain the failed link
				for (Route rte : rteTbl) {
					if (rte.outLink == lnk && rte.valid) {
						rte.valid = false;
						update = true;
					}
				}
			}
			if (update) {
			// print routing table if debug is enabled 
			// and valid field of route is changed
				if (debug > 1) {
					printTable();
				}
			// send link failure advertisement if enFA is enabled
			// and valid field of route is changed
				if (enFA) {
					sendFailureAdvert(lnk);
				}
			}
			// send new hello, after setting gotReply to false
			lnkInfo.gotReply = false;
			Packet p = new Packet();
			p.protocol = 2; p.ttl = 100;
			p.srcAdr = myIp;
			p.destAdr = lnkInfo.peerIp;
			p.payload = String.format("RPv0\ntype: hello\n"
				+ "timestamp: %.3f\n", now);
			fwdr.sendPkt(p,lnk);
			lnk++;
		}
	}

	/** Send initial path vector to each of our neighbors. */
	public void sendAdverts() {
		// TODO send advertisement
		for (Prefix pfx : pfxList) {
			for (int lnk = 0; lnk < nborList.size(); lnk++) {
				if (lnkVec.get(lnk).helloState == 0)
					continue;
				Packet p = new Packet();
				p.protocol = 2;
				p.ttl = 100;
				p.srcAdr = myIp;
				p.destAdr = lnkVec.get(lnk).peerIp;
				p.payload = String.format("RPv0\ntype: advert\n" +
				"pathvec: %s %.3f %.3f %s\n", pfx.toString(), now, 0.0, 
				myIpString);
				fwdr.sendPkt(p, lnk);
			}
		}
	}

	/**
	 * Send link failure advertisement to all available neighbors
	 *
	 * @param failedLnk is the number of link on which is failed.
	 *
	 */
	public void sendFailureAdvert(int failedLnk) {
		int failIp = lnkVec.get(failedLnk).peerIp;
		String failIpString = Util.ip2string(failIp);

		for (int lnk = 0; lnk < nborList.size(); lnk++) {
			if (lnkVec.get(lnk).helloState == 0)
				continue;
			Packet p = new Packet();
			p.protocol = 2;
			p.ttl = 100;
			p.srcAdr = myIp;
			p.destAdr = lnkVec.get(lnk).peerIp;
			p.payload = String.format("RPv0\ntype: fadvert\n" + "linkfail: %s %s %.3f %s\n", myIpString, failIpString,
					now, myIpString);
			fwdr.sendPkt(p, lnk);
		}
	}

	/**
	 * Retrieve and process packet received from Forwarder.
	 *
	 * For hello packets, we simply echo them back. For replies to our own hello
	 * packets, we update costs. For advertisements, we update routing state and
	 * propagate as appropriate.
	 */
	public void handleIncoming() {
		// parse the packet payload
		Pair<Packet,Integer> pp = fwdr.receivePkt();
		Packet p = pp.left; int lnk = pp.right;

		String[] lines = p.payload.split("\n");
		if (!lines[0].equals("RPv0")) return;

		String[] chunks = lines[1].split(":");
		if (!chunks[0].equals("type")) return;
		String type = chunks[1].trim();

		// TODO
		// if it's an route advert, call handleAdvert
		if (type.equals("advert")) {
			handleAdvert(lines, lnk);
		}
		// if it's a link failure advert, call handleFailureAdvert
		else if (type.equals("fadvert")) {
			handleFailureAdvert(lines, lnk);
		}
		// if it's a hello, echo it back
		else if (type.equals("hello")) {
			p.srcAdr = myIp;
			p.destAdr = lnkVec.get(lnk).peerIp;
			p.ttl--;
			p.payload = String.format("RPv0\ntype: hello2u\n"
					+ lines[2]);
			if (fwdr.ready4pkt()) {
				fwdr.sendPkt(p,lnk);
			}
		}
		// else it's a reply to a hello packet
		else if (type.equals("hello2u")) {
		// use timestamp to determine round-trip delay
			String[] message = lines[2].split(":");
			if (!message[0].equals("timestamp")) return;
			double timestamp = Double.parseDouble(message[1].trim());
			double delay = (now - timestamp)/2;
		// use this to update the link cost using exponential
		// weighted moving average method
			LinkInfo linkInfo = lnkVec.get(lnk);
			final double alpha = 0.1;
			linkInfo.cost = (1-alpha)*linkInfo.cost + alpha*delay;
		// also, update link cost statistics
			linkInfo.count++;
			linkInfo.totalCost += delay;
			linkInfo.minCost = Math.min(linkInfo.minCost,delay);
			linkInfo.maxCost = Math.max(linkInfo.maxCost,delay);
		// also, set gotReply to true
			linkInfo.gotReply = true;
			linkInfo.helloState = 3;
		}
	}

	/**
	 * Handle an advertisement received from another router.
	 *
	 * @param lines is a list of lines that defines the packet; the first two lines
	 *              have already been processed at this point
	 *
	 * @param lnk   is the number of link on which the packet was received
	 */
	private void handleAdvert(String[] lines, int lnk) {
		// example path vector line
		// pathvec: 1.2.0.0/16 345.678 .052 1.2.0.1 1.2.3.4
	        //
		// TODO
        // Parse the path vector line.
		String[] message = lines[2].split(":");
		if (!message[0].equals("pathvec")) return;
		String[] info = message[1].trim().split(" ");
		if (info.length < 4) return;
		Prefix pfx = new Prefix(info[0].trim());
		double timestamp = Double.parseDouble(info[1].trim());
		double cost = Double.parseDouble(info[2].trim());
		LinkedList<Integer> path = new LinkedList<Integer>();
		for (int i = 3; i < info.length; i++) {
			int rteIp = Util.string2ip(info[i].trim());
			path.addLast(rteIp);
		}
        // If there is loop in path vector, ignore this packet.
		if (path.contains(myIp)) return;
        // Form a new route, with cost equal to path vector cost
        // plus the cost of the link on which it arrived.
		Route nuRte = new Route();
		nuRte.pfx = pfx;
		nuRte.timestamp = timestamp;
		nuRte.cost = cost + lnkVec.get(lnk).cost;
		nuRte.path = path;
		nuRte.outLink = lnk;
		nuRte.valid = true;
        // Look for a matching route in the routing table
        // and update as appropriate; whenever an update
        // changes the path, print the table if debug>0;
		boolean addNew = false;
		boolean update = false;
		int oldLnk = -1;
		Route rte = lookupRoute(pfx);
		if (rte == null) {
			addRoute(nuRte);
			addNew = true;
		}
		else {
			oldLnk = rte.outLink;
			update = updateRoute(rte, nuRte);
		}
		if (addNew || update) {
        // whenever an update changes the output link,
        // update the forwarding table as well.
			if (oldLnk != lnk) {
				fwdr.addRoute(pfx, lnk);
			}
        // If the new route changed the routing table,
        // extend the path vector and send it to other neighbors.
			String newPath = myIpString;
			for (int rteIp : path) {
				newPath += " " + Util.ip2string(rteIp);
			}
			for (int lnkNum = 0; lnkNum < nborList.size(); lnkNum++) {
				if (lnk == lnkNum
						|| lnkVec.get(lnkNum).helloState == 0) continue;
				Packet p = new Packet();
				p.protocol = 2; p.ttl = 100;
				p.srcAdr = myIp;
				p.destAdr = lnkVec.get(lnkNum).peerIp;
				p.payload = String.format("RPv0\ntype: advert\n"
					+ "pathvec: %s %.3f %.3f %s\n",
					pfx.toString(), timestamp, nuRte.cost, newPath);
				if (fwdr.ready4pkt()) {
					fwdr.sendPkt(p,lnkNum);
				}
			}
			if (debug > 0) {
				printTable();
			}
		}
	}

	/**
	 * Handle the failure advertisement received from another router.
	 *
	 * @param lines is a list of lines that defines the packet; the first two lines
	 *              have already been processed at this point
	 *
	 * @param lnk   is the number of link on which the packet was received
	 */
	private void handleFailureAdvert(String[] lines, int lnk) {
		// example path vector line
		// fadvert: 1.2.0.1 1.3.0.1 345.678 1.4.0.1 1.2.0.1
		// meaning link 1.2.0.1 to 1.3.0.1 is failed
	        //
		// TODO
        // Parse the path vector line.
		String[] message = lines[2].split(":");
		if (!message[0].equals("linkfail")) return;
		String[] info = message[1].trim().split(" ");
		if (info.length < 4) return;
		int failFrom = Util.string2ip(info[0].trim());
		int failTo = Util.string2ip(info[1].trim());
		double timestamp = Double.parseDouble(info[2].trim());
		LinkedList<Integer> path = new LinkedList<Integer>();
		for (int i = 3; i < info.length; i++) {
			int rteIp = Util.string2ip(info[i].trim());
			path.addLast(rteIp);
		}
        // If there is loop in path vector, ignore this packet.
		if (path.contains(myIp)) return;
        // go through routes to check if it contains the link
		// set the route as invalid (false) if it does
		// update the time stamp if route is changed
		boolean change = false;
		for (Route rte : rteTbl) {
			LinkedList<Integer> rtePath = rte.path;
			for (int i = 0; i < rtePath.size()-1; i++) {
				if (rtePath.get(i) == failFrom 
						&& rtePath.get(i+1) == failTo) {
					rte.valid = false;
					rte.timestamp = timestamp;
					change = true;
					break;
				}
			}
		}
		if (change) {
		// print route table if route is changed and debug is enabled
			if (debug > 0) {
				printTable();
			}
		// If one route is changed, extend the message 
		// and send it to other neighbors.
			String newPath = myIpString;
			for (int rteIp : path) {
				newPath += " " + Util.ip2string(rteIp);
			}
			for (int lnkNum = 0; lnkNum < nborList.size(); lnkNum++) {
				if (lnk == lnkNum
						|| lnkVec.get(lnkNum).helloState == 0) continue; 
				Packet p = new Packet();
				p.protocol = 2; p.ttl = 100;
				p.srcAdr = myIp;
				p.destAdr = lnkVec.get(lnkNum).peerIp;
				p.payload = String.format("RPv0\ntype: fadvert\n"
						+ "linkfail: %s %s %.3f %s\n",
						Util.ip2string(failFrom), Util.ip2string(failTo), timestamp, newPath);
				if (fwdr.ready4pkt()) {
					fwdr.sendPkt(p,lnkNum);
				}
			}
		}
	}

	/** Print the contents of the routing table. */
	public void printTable() {
		String s = String.format("Routing table (%.3f)\n" + "%10s %10s %8s %5s %10s \t path\n", now, "prefix",
				"timestamp", "cost", "link", "VLD/INVLD");
		for (Route rte : rteTbl) {
			s += String.format("%10s %10.3f %8.3f", rte.pfx.toString(), rte.timestamp, rte.cost);

			s += String.format(" %5d", rte.outLink);

			if (rte.valid == true)
				s += String.format(" %10s", "valid");
			else
				s += String.format(" %10s \t", "invalid");

			for (int r : rte.path)
				s += String.format(" %s", Util.ip2string(r));

			if (lnkVec.get(rte.outLink).helloState == 0)
				s += String.format("\t ** disabled link");
			s += "\n";
		}
		System.out.println(s);
	}
}
