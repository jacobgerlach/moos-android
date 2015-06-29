/*   MOOS - Mission Oriented Operating Suite 
*  
*   A suit of Applications and Libraries for Mobile Robotics Research 
*   Copyright (C) 2001-2005 Massachusetts Institute of Technology and 
*   Oxford University. 
*
*   The original C++ version of this software was written by Paul Newman
*   at MIT 2001-2002 and Oxford University 2003-2005. 
*   email: pnewman@robots.ox.ac.uk. 
*	  
*   This Java version of MOOSClient is part of the MOOSBeans for Java
*   package written by Benjamin C. Davis at Oxford University 2010-2011  
*   email: ben@robots.ox.ac.uk
*
*   This file is part of the MOOSBeans for Java package.
*		
*   This program is free software; you can redistribute it and/or 
*   modify it under the terms of the GNU General Public License as 
*   published by the Free Software Foundation; either version 2 of the 
*   License, or (at your option) any later version. 
*		  
*   This program is distributed in the hope that it will be useful, 
*   but WITHOUT ANY WARRANTY; without even the implied warranty of 
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
*   General Public License for more details. 
*			
*   You should have received a copy of the GNU General Public License 
*   along with this program; if not, write to the Free Software 
*   Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 
*   02111-1307, USA. 
*
*                      END_GPL   
*/

package MOOS;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * Currently no Zip compression is supported. It will throw an exception if a compressed packet is tried to decode.
 *  This is not a like for like C++ port. This uses java naming conventions. However, the methods for decoding are still serialize() in lower case.
 *
 * packet layout
 * int [pktLenghtInt][pktLenghtInt][pktLenghtInt][pktLenghtInt]
 * int [numberMessages][numberMessages][numberMessages][numberMessages]
 * byte [compressed?]
 * //next packetLengthInBytes worth of packet payload:
 * //Message 0 from byte 0+4 onwards.
 * int  [msgLengthInt][msgLengthInt][msgLengthInt][msgLengthInt]
 * int  [msgIDInt][msgIDInt][msgIDInt][msgIDInt]
 * byte [msgType]
 * byte [dataType]
 * int  [srcLengthInt][srcLengthInt][srcLengthInt][srcLengthInt]
 * string       [String * n Bytes]
 * ? optional int [SrcAuxInt][SrcAuxInt][SrcAuxInt][SrcAuxInt]
 * ? optional        [String * n Bytes]
 * int  [originCommunityLengthInt][originCommunityLengthInt][originCommunityLengthInt][originCommunityLengthInt]
 * string        [String * n Bytes]
 * int  [m_sKeyInt][m_sKeyInt][m_sKeyInt][m_sKeyInt]
 * string        [String * n Bytes]
 * double time [time][time][time][time][time][time][time][time]
 * double m_dfVal [m_dfVal][m_dfVal][m_dfVal][m_dfVal][m_dfVal][m_dfVal][m_dfVal][m_dfVal]
 * double m_dfVal [m_dfVal2][m_dfVal2][m_dfVal2][m_dfVal2][m_dfVal2][m_dfVal2][m_dfVal2][m_dfVal2]
 * int  [m_sValInt][m_sValInt][m_sValInt][m_sValInt]
 * string        [String * n Bytes]
 * ... next message
 *
 * @author Benjamin C. Davis
 */
public class MOOSCommPkt {

    public static final String MOOS_PROTOCOL_STRING = "ELKS CAN'T DANCE 2/8/10";
    public static final int DEFAULT_ASSUMMED_MAX_MOOS_MSG_SIZE = 10000000;
    public static final int PACKET_HEADER_SIZE = 2 * MOOSMsg.INT_SIZE_IN_BYTES + MOOSMsg.BYTE_SIZE_IN_BYTES;
    protected int packetLengthInBytes = 0; // 1st 4 bytes contain length of packet in bytes. This is set once header is read.
    protected int msgCount;
    protected boolean isCompressed;
    protected ArrayList<MOOSMsg> msgList;
    protected int bytesRequired;// = MOOSMsg.INT_SIZE_IN_BYTES; // 1st 4 bytes contain length of packet in bytes
    // protected int currentSize = 0;
    protected ByteBuffer packetData;

    public MOOSCommPkt() {
        resetFill();
    }

    public void resetFill() {
        bytesRequired = MOOSMsg.INT_SIZE_IN_BYTES; // 1st 4 bytes contain length of packet in bytes
        packetData = MOOSMsg.allocate(bytesRequired); // ready for first data

    }

    protected void fillWholePacket() throws NoJavaZipCompressionSupportYetException {
        // got a full packet so will decode other header data
        this.packetLengthInBytes = packetData.getInt(); // have got anyway, but get again...
        //number of messages we are expecting
        msgCount = packetData.getInt();
        // is it compressed?
        isCompressed = packetData.get() != 0;
        if (isCompressed) {
            // Zip compresses to be completed
            throw new NoJavaZipCompressionSupportYetException();
        } else {
            bytesRequired = 0;
        }

    }

    /**
     * Once this method has been called, call serialise(list, false) or deSerialize() to use internal message list.
     * @param packetData
     * @return size of data still required
     * @throws NoJavaZipCompressionSupportYetException
     */
    public int fill() throws NoJavaZipCompressionSupportYetException {

        if (packetData.order().equals(ByteOrder.BIG_ENDIAN)) { // sanity check to make sure no one has been fiddling with our ByteBuffer
            packetData.order(ByteOrder.LITTLE_ENDIAN); // convert to little endian
        }

        if (this.packetLengthInBytes == 0) {
            // still need to read header
            if (packetData.limit() > 3) {
                
                int length = packetData.getInt();
                packetData.rewind(); // may read same data again depending on outcome
                if (length > 0) {
                    this.packetLengthInBytes = length;
                    if (packetData.limit() == this.packetLengthInBytes) {
                        this.fillWholePacket();
                        bytesRequired = 0;
                    } else {
                        if (this.packetLengthInBytes > MOOSCommPkt.DEFAULT_ASSUMMED_MAX_MOOS_MSG_SIZE) {
                                System.err.println("MOOSCommPkt: Error! Packet Size > DEFAULT_ASSUMMED_MAX_MOOS_MSG_SIZE" + this.packetLengthInBytes + " -> If this is a size error it could crash us!!!");
                        }
                        this.packetData = MOOSMsg.allocate(this.packetLengthInBytes).put(packetData); // create new buffer of correct length for full packet and add already collected data
                        this.bytesRequired = this.packetLengthInBytes - packetData.position();
                    }
                }
            } else { // haven't got enough bytes for length yet.
                packetData.position(packetData.limit());
                return bytesRequired -= packetData.limit();
            }

        } else { // already got the header
            // wait until got all the data required.
            if (packetData.limit() == this.packetLengthInBytes) { // got full data
                this.fillWholePacket();
                bytesRequired = 0;

            } else { // still waiting for data, calculate how much is left
                this.bytesRequired = this.packetLengthInBytes - packetData.limit(); // how many bytes are still required... why did it not get them all the 1st time
                this.packetData.position(packetData.limit()); // put current position to write bytes to where it got to before
                this.packetData.limit(packetData.capacity()); // reset limit to end
            }

        }
        return bytesRequired;

    }

    /**
     * Convenience method to serialize from the list of messages held by this packet, or use deSerialize() to deserialise to the list of messages held by this packet.
     * @param toStream
     * @return whether it succeeded
     */
    public boolean serialize() {
        if (msgList == null || msgList.isEmpty()) {
            return false;
        } else {
            return serialize(this.msgList, true);
        }
    }

    public boolean deSerialize() {
        if (msgList == null) {
            msgList = new ArrayList<MOOSMsg>();
        } else {
            msgList.clear(); // empty current messages
        }
        if (this.packetData == null || this.packetData.remaining() == 0) {
            return false;
        } else {
            return serialize(this.msgList, false);
        }


    }

    /**
     *  This method will either serialize the data in the messages list ready for sending, or it will decode the byte[] which backs this buffer, supplied by the fill() method, and add all the messages contained in this packet to he supplied message list.
     * @param messages the list of messages to be added to or to be serialized into this packets backing byte[]. This list should be non-null and should be empty if this method is to add messages from this packet to the list.
     * @param toStream whether this should serialize the supplied messages to the backing byte[] or whether it should read data and populate the message list with the new messages.
     * @return whether it managed to complete the operation
     */
    public boolean serialize(ArrayList<MOOSMsg> messages, boolean toStream) {
        this.msgList = messages;
        // int [pktLenghtInt][pktLenghtInt][pktLenghtInt][pktLenghtInt]
        // int [numberMessages][numberMessages][numberMessages][numberMessages]
        // byte [compressed?]
        //note +1 is for indicator regarding compressed or not compressed
        if (toStream) {
            // find out total length of packet
            this.packetLengthInBytes = PACKET_HEADER_SIZE;
            for (MOOSMsg msg : messages) {
                this.packetLengthInBytes += msg.getSizeInBytesWhenSerialised();
            }
            this.msgCount = messages.size();

            // resize the ByteBuffer
            packetData = MOOSMsg.allocate(this.packetLengthInBytes);

            // create header
            packetData.putInt(this.packetLengthInBytes);
            packetData.putInt(msgCount);
            if (isCompressed) {
                packetData.put((byte) 1);
            } else {
                packetData.put((byte) 0);
            }

            // now add serialize the messages to the ByteBuffer
            for (MOOSMsg msg : messages) {
                msg.serialize(packetData, toStream);
            }
            this.packetData.flip(); // sets the limit to here and the position to zero, ready for writing to stream from
        } else {
            // here we read from the buffer into this packet and add all messages to the supplied list
            // we assume fill has already been called and the packet header has been read and thus incremented the mark on the supplied ByteBuffer
            if (packetData.position() != this.PACKET_HEADER_SIZE) {
                packetData.position(this.PACKET_HEADER_SIZE); // if for some reason not in correct place to start reading messages from
            }
            for (int i = 0; i < msgCount; i++) {
                MOOSMsg msg = new MOOSMsg();
                msg.serialize(packetData, toStream);
                messages.add(msg);
            }
            packetData.rewind(); //rewind
        }


        return true;
    }

    /**
     *
     * @return returns the ByteBuffer created from the messages. Returns null is serialize(..) hasn't been called, and a such the packet is empty.
     */
    public ByteBuffer getBytes() {
        return this.packetData;
    }

    /**
     *  Replace the backing ByteBuffer with the selected one. Fill() should be called followed by deSerialize or serialize(messages, false)
     * @param the ByteBuffer full of MOOS packet data.
     */
    public void setBytes(ByteBuffer data) {
        this.resetFill();
        this.packetData = data;
    }

    /**
     *
     * @return the List<MOOSMsg> which this packet holds.
     */
    public ArrayList<MOOSMsg> getMsgList() {
        return msgList;
    }

    /**
     * Serialize() should be called after this, serialize(List<MOOSMsg> list, true) instead.
     * @param list of messages to put in this packet
     */
    public void setMsgList(ArrayList<MOOSMsg> list) {
        this.msgList = list;
    }

    /**
     *
     * @return length of packet in bytes. Will only return header length if serialize(..) hasn't been called yet.
     */
    public int getPacketLengthInBytes() {
        return this.packetLengthInBytes;
    }
}
