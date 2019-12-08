import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;

/** Class for working with lab5 packets. */

public class Packet {
	// packet fields - note: all are public
	public byte ttl;		// # of hops before packet expires
	public int srcAdr;		// IP source address in overlay net
	public int destAdr;		// IP destination address in overlay net
	public String payload;		// application payload

	/** Constructor, initializes fields to default values. */
	public Packet() { clear(); }

	/** Initialize all packet fields.
	 *  Initializes all fields to an undefined value.
 	 */
	public void clear() {
		ttl = 0; srcAdr = 0; destAdr = 0; payload = "";
	}

	/** Pack attributes defining packet fields into buffer.
	 *  Fails if the packet type is undefined or if the resulting
	 *  buffer exceeds the allowed length of 1400 bytes.
	 *  @return null on failure, otherwise a byte array
	 *  containing the packet payload.
	 */
	public byte[] pack() {
		byte[] pbuf;
		try { pbuf = payload.getBytes("US-ASCII");
		} catch(Exception e) { return null; }
		if (pbuf.length > 1400 - 9) return null;
		ByteBuffer bbuf = ByteBuffer.allocate(9 + pbuf.length);
		bbuf.order(ByteOrder.BIG_ENDIAN);
		bbuf.put(ttl);
		bbuf.putInt(srcAdr); bbuf.putInt(destAdr);
		bbuf.put(pbuf);
		return bbuf.array();
	}

	/** Unpack attributes defining packet fields from buffer.
	 *  @param buf is a byte array containing the packet
	 *  (or if you like, the payload of a UDP packet).
	 *  @param bufLen is the number of valid bytes in buf
	 */
	public boolean unpack(byte[] buf, int bufLen) {
		if (bufLen < 9) return false;
		ByteBuffer bbuf = ByteBuffer.wrap(buf);
		bbuf.order(ByteOrder.BIG_ENDIAN);
		ttl = bbuf.get();
		srcAdr = bbuf.getInt(); destAdr = bbuf.getInt();
		try { payload = new String(buf,9,bufLen-9,"US-ASCII");
		} catch(Exception e) { return false; }
		return true;
	}

	/** Create String representation of packet.
	 *  The resulting String is produced using the defined
	 *  attributes and is formatted with one field per line,
	 *  allowing it to be used as the actual buffer contents.
	 */
	public String toString() {
		return " ttl=" + ttl
			+ " srcAdr="  + Util.ip2string(srcAdr)
			+ " destAdr=" + Util.ip2string(destAdr)
			+ "\n" + payload;
	}
}
