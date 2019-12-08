import java.io.*;
import java.net.*;
import java.util.*;

/** Class for working with ring packets. */
public class Packet {
	// packet fields - note: all are public
	public String type;		// packet type
	public int sender;		// sender's ring id
	public InetAddress succIp;	// IP of successor
	public int succPort;		// port number of successor

	/** Constructor, initializes fields to default values. */
	public Packet() { clear(); }

	/** Initialize all packet fields.
	 *  Initializes all fields to an undefined value.
 	 */
	public void clear() {
		type = null; sender = -1; succIp = null; succPort = -1;
	}

	/** Copy the copy of one packet to another.
	 *  @param p is a packet whose contents is copied to this Packet
	 */
	public void copyFrom(Packet p) {
		type = p.type; sender = p.sender;
		succIp = p.succIp; succPort = p.succPort;
	}

	/** Pack attributes defining packet fields into buffer.
	 *  Fails if the packet type is undefined or if the resulting
	 *  buffer exceeds the allowed length of 1400 bytes.
	 *  @return null on failure, otherwise a byte array
	 *  containing the packet payload.
	 */
	public byte[] pack() {
		if (type == null)  return null;
		byte[] buf;
		try { buf = toString().getBytes("US-ASCII");
		} catch(Exception e) { return null; }
		if (buf.length > 1400) return null;
		return buf;
	}

	/** Unpack attributes defining packet fields from buffer.
	 *  @param buf is a byte array containing the ring packet
	 *  (or if you like, the payload of a UDP packet).
	 *  @param bufLen is the number of valid bytes in buf
	 */
	public boolean unpack(byte[] buf, int bufLen) {
		// convert buf to a string
		String s; 
		try { s = new String(buf,0,bufLen,"US-ASCII");
		} catch(Exception e) { return false; }

		// divide into lines and process
		String[] lines = s.split("\n");
		for (int i = 0; i < lines.length; i++) {
			String[] chunks = lines[i].split(":",2);
			if (chunks.length != 2) return false;
			// process the line
			String left = chunks[0];
			String right = chunks[1];
			if (left.equals("type")) {
				type = right;
			} else if (left.equals("sender")) {
				sender = Integer.parseInt(right);
			} else if (left.equals("succIp")) {
				try { succIp = InetAddress.getByName(right);
				} catch(Exception e) { return false; }
			} else if (left.equals("succPort")) {
				succPort = Integer.parseInt(right);
			} else {
				// ignore lines that don't match defined field
			}
		}
		return true;
	}

	/** Create String representation of packet.
	 *  The resulting String is produced using the defined
	 *  attributes and is formatted with one field per line,
	 *  allowing it to be used as the actual buffer contents.
	 */
	public String toString() {
		StringBuffer s = new StringBuffer("");
		if (type != null) {
			s.append("type:"); s.append(type); s.append("\n");
		}
		if (sender != -1) {
			s.append("sender:"); s.append(sender); s.append("\n");
		}
		if (succIp != null) {
			s.append("succIp:");
			s.append(succIp.getHostAddress());
			s.append("\n");
		}
		if (succPort != -1) {
			s.append("succPort:"); s.append(succPort);
			s.append("\n");
		}
		return s.toString();
	}
		
	/** Send the packet to a specified destination.
	 *  Packs the various packet fields into a buffer
	 *  before sending. Does no validity checking.
	 *  @param sock is the socket on which the packet is sent
	 *  @param dest is the socket address of the destination
	 *  debug is a flag; if true, the packet is printed before it is sent
	 *  @return true on success, false on failure
	 */
	public boolean send(DatagramSocket sock, InetSocketAddress dest,
			    boolean debug) {
		if (debug) {
			System.out.println("" + sock.getLocalSocketAddress() +
				" sending packet to " + dest + "\n" +
				toString());
			System.out.flush();
		}
		byte[] buf = pack();
		if (buf == null) return false;
        DatagramPacket pkt = new DatagramPacket(buf, buf.length);
		pkt.setSocketAddress(dest);
		try { sock.send(pkt); } catch(Exception e) { return false; }
		return true;
	}
		
	/** Get the next packet on the socket.
	 *
	 * Receives the next datagram from the socket and
	 * unpacks it.
	 * @param sock is the socket on which the packet is received
	 * @param debug is a flag; if it is true, the received
	 * packet is printed
	 * @return the sender's socket address on success and null on failure
	 */
	public InetSocketAddress receive(DatagramSocket sock, boolean debug) {
		clear();
		byte[] buf = new byte[2000];
		DatagramPacket pkt = new DatagramPacket(buf, buf.length);
		try {
			sock.receive(pkt);
		} catch(SocketTimeoutException e) {
			return null;
		} catch(Exception e) {
			System.out.println("receive exception: " + e);
			return null;
		}
	
		if (!unpack(buf,pkt.getLength())) {
			System.err.println("error while unpacking packet");
			return null;
		}
		if (debug) {
			System.out.println(sock.getLocalSocketAddress() +
				" received packet from " + 
				pkt.getSocketAddress() + "\n" + toString());
			System.out.flush();
		}
		return (InetSocketAddress) pkt.getSocketAddress();
	}
}
