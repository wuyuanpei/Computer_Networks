import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;

/** Class for working with studio3 packets. */
public class Packet {
	// packet fields - note: all are public
	public int charCount;		// number of bytes sent by sender
	public int pktCount;		// number of packets sent by sender
	public String payload;		// application payload

	/** Constructor, initializes fields to default values. */
	public Packet() { clear(); }

	/** Initialize all packet fields.
	 *  Initializes all fields to an undefined value.
 	 */
	public void clear() {
		pktCount = 0; charCount = 0; payload = "";
	}

	/** Copy the copy of one packet to another.
	 *  @param p is a packet whose contents is copied to this Packet
	 */
	public void copyFrom(Packet p) {
		pktCount = p.pktCount; charCount = p.charCount;
		payload = p.payload;
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
		if (pbuf.length > 1400 - 8) return null;
		ByteBuffer bbuf = ByteBuffer.allocate(8 + pbuf.length);
		bbuf.order(ByteOrder.BIG_ENDIAN);
		bbuf.putInt(pktCount); bbuf.putInt(charCount);
		bbuf.put(pbuf);
		return bbuf.array();
	}

	/** Unpack attributes defining packet fields from buffer.
	 *  @param buf is a byte array containing the packet
	 *  (or if you like, the payload of a UDP packet).
	 *  @param bufLen is the number of valid bytes in buf
	 */
	public boolean unpack(byte[] buf, int bufLen) {
		if (bufLen < 8) return false;
		ByteBuffer bbuf = ByteBuffer.wrap(buf);
		bbuf.order(ByteOrder.BIG_ENDIAN);
		pktCount = bbuf.getInt(); charCount = bbuf.getInt();
		try { payload = new String(buf,8,bufLen-8,"US-ASCII");
		} catch(Exception e) { return false; }
		return true;
	}

	/** Create String representation of packet.
	 *  The resulting String is produced using the defined
	 *  attributes and is formatted with one field per line,
	 *  allowing it to be used as the actual buffer contents.
	 */
	public String toString() {
		return new String("" + pktCount + ":" + charCount + ":" +
				  payload);
	}
}
