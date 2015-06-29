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

import java.lang.String;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 *  This Class is a Java representation of the C++ (Double plus, increment, 
 * whatever ...) MOOSMsg.cpp class. The C++ source was originally written by
 * Paul Newman.  This uses ByteBuffers to serialize and de-serialize data. These
 * buffers can then be written directly to a Socket.
 * 
 * This class extends the java.util.EventObject merely so that it is recognised
 * as a java Event. It doesn't need to all, but this lets us use it with the
 * java event passing system quite nicely.
 *
 * @author Benjamin C. Davis
 *
 */
public class MOOSMsg extends java.util.EventObject implements java.io.Serializable {

    private static final long serialVersionUID = -3282121626645069536L; // needed for serializable interface - ignore.
    public static final ByteOrder MOOS_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;
    // Primitive type sizes in bytes.
    public static final int INT_SIZE_IN_BYTES = (Integer.SIZE / 8);
    public static final int BYTE_SIZE_IN_BYTES = (Byte.SIZE / 8);
    public static final int DOUBLE_SIZE_IN_BYTES = (Double.SIZE / 8);
    // MESSAGE TYPES
    public static final char MOOS_NOTIFY = 'N';
    public static final char MOOS_REGISTER = 'R';
    public static final char MOOS_UNREGISTER = 'U';
    public static final char MOOS_NOT_SET = '~';
    public static final char MOOS_COMMAND = 'C';
    public static final char MOOS_ANONYMOUS = 'A';
    public static final char MOOS_NULL_MSG = '.';
    public static final char MOOS_DATA = 'i';
    public static final char MOOS_POISON = 'K';
    public static final char MOOS_WELCOME = 'W';
    public static final char MOOS_SERVER_REQUEST = 'Q';
    // MESSAGE DATA TYPES
    public static final char MOOS_DOUBLE = 'D';
    public static final char MOOS_STRING = 'S';
    public static final char MOOS_BINARY_STRING = 'B';
    // EXTRA FIELDS
    public static boolean DISABLE_AUX_SOURCE = false;
    //Allowable Skew tolerance between MOOSDB time and local time.
    public static final int SKEW_TOLERANCE = 5;
    // is MOOS in playback mode, i.e. is this a playback message
    protected boolean playback = false;
    ////////////////////////
    // more fields from c++ with camel case name adjustment
    ////////////////////////
    /**what type of message is this? Notification,Command,Register etc*/
    protected char msgType;
    /**what kind of data is this? String,Double,Array?*/
    protected char dataType;
    /**what is the variable name?*/
    protected String varName;
    /**ID of message*/
    protected int msgID;
    /** double precision time stamp (UNIX time)*/
    protected double time;
    //DATA VARIABLES
    //a) numeric
    protected double doubleData;
    protected double m_dfVal2; // dunno, didn't seem to be used at time of port.
    //b) string
    protected String stringData;
    // binary data
    protected byte[] binaryData;
    //who sent this message?
    protected String source;
    //extra info on source (optional payload)
    protected String sourceAuxInfo;
    //what community did it originate in?
    protected String community;
    // message length in bytes
    protected int msgLength;

    //Tag for Android Logger
    protected static final String TAG = "MOOS";

    //////////////////////////////////////////////////////////////////////
    // Construction/Destruction
    //////////////////////////////////////////////////////////////////////
    /**
     *  Default: Assumes MOOS_NULL_MSG, MOOS_DOUBLE, etc. Empty message.
     * param src The source of this event, can be null
     */
    public MOOSMsg() {
        super("MOOSMsg: The source is null! "); // call super(null) so that there is no event source to this message.
        msgType = MOOS_NULL_MSG;
        dataType = MOOS_DOUBLE;
        time = -1;
        doubleData = -1;
        m_dfVal2 = -1;
        msgID = -1;
        source = "";
        sourceAuxInfo = "";
    }

    public MOOSMsg(char cMsgType, String varName, double doubleData) {
        this(cMsgType, varName, doubleData, -1);
    }

    public MOOSMsg(char cMsgType, String varName, double doubleData, double dfTime) {
        super("MOOSMsg: The source is null! ");
        msgType = cMsgType;
        this.doubleData = doubleData;
        m_dfVal2 = -1;
        dataType = MOOS_DOUBLE;
        this.varName = varName;
        time = dfTime;
        msgID = -1;

        if (dfTime == -1) {
            time = moosTimeNow();
        } else {
            time = dfTime;
        }
    }

    public MOOSMsg(char cMsgType, String varName, String stringData) {
        this(cMsgType, varName, stringData, -1);
    }

    public MOOSMsg(char cMsgType, String varName, String stringData, double dfTime) {
        super("MOOSMsg: The source is null! ");
        msgType = cMsgType;
        doubleData = -1;
        m_dfVal2 = -1;
        dataType = MOOS_STRING;
        this.varName = varName;
        this.stringData = stringData;
        time = dfTime;
        msgID = -1;

        if (dfTime == -1) {
            time = moosTimeNow();
        } else {
            time = dfTime;
        }
    }

    public MOOSMsg(char cMsgType, String varName, byte[] binaryData) {
        this(cMsgType, varName, binaryData, -1);
    }

    public MOOSMsg(char cMsgType, String varName, byte[] binaryData, double dfTime) {
        super("MOOSMsg: The source is null! ");
        msgType = cMsgType;
        doubleData = -1;
        m_dfVal2 = -1;
        dataType = MOOS_BINARY_STRING;
        this.varName = varName;
        this.binaryData = binaryData;
        time = dfTime;
        msgID = -1;

        if (dfTime == -1) {
            time = moosTimeNow();
        } else {
            time = dfTime;
        }
    }

    /**
     * This is a helper function to ensure that we always read in the correct byte order. By default ByteBuffer is Big Endian which is contrary to Moos, so we always swap the buffer to little endian so we always read and transmit the correct data.
     * @param size of buffer in bytes
     * @return
     */
    public static ByteBuffer allocate(int size) {
        return ByteBuffer.allocate(size).order(MOOS_BYTE_ORDER);
    }

    /**
     * This takes the java epox time..
     * @return unix time in seconds.
     */
    public static double moosTimeNow() {
        return ((double) System.currentTimeMillis()) / 1000.0d;
    }

    /**
     *
     * @param playback sets whether this message is a playback message, rather than a real one. default is false.
     */
    public void setPlayback(boolean playback) {
        this.playback = playback;
    }

    public boolean isPlayback() {
        return playback;
    }

    /**
     * Whether Auxilary Src Data will be included in MOOSMsg. This is a Compile time flag in C++ and is usually set to false.
     */
    public static void disableAUXSourceData() {
        DISABLE_AUX_SOURCE = true;
    }

    public static void enableAUXSourceData() {
        DISABLE_AUX_SOURCE = false;
    }

    public void setEventSource(Object src) {
        super.source = src;
    }

    /* Methods from C++
     *
     */
    /**
     * check data type (MOOS_STRING or MOOS_DOUBLE)
     */
    public boolean isDataType(char cDataType) {
        return cDataType == dataType;
    }

    /**check data type is double*/
    public boolean isDouble() {
        return isDataType(MOOS_DOUBLE);
    }

    /**check data type is string*/
    public boolean isString() {
        return isDataType(MOOS_STRING);
    }

    /**check message type MOOS_NOTIFY, REGISTER etc*/
    public boolean isType(char cType) {
        return this.msgType == cType;
    }

    /**return time stamp of message*/
    public double getTime() {
        return time;
    }

    /**return double val of message*/
    public double getDoubleData() {
        return doubleData;
    }

    /**return string value of message*/
    public String getStringData() {
        return stringData;
    }

    /**return the name of the message*/
    public String getKey() {
        return varName;
    }

    /**return the name of the process (as registered with the DB) which
    posted this notification*/
    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSourceAux() {
        return sourceAuxInfo;
    }

    public void getSourceAux(String sSrcAux) {
        sourceAuxInfo = sSrcAux;
    }

    /**return the name of the MOOS community in which the originator lives*/
    public String getCommunity() {
        return community;
    }

    /** set the Double value */
    public void setDoubleData(double dfD) {
        doubleData = dfD;
    }

    /**
     *
     * @return length in bytes if were serialised
     */
    public int getSizeInBytesWhenSerialised() {
        int sourceLength = 0;
        if (source != null) {
            sourceLength = source.getBytes().length;
        }
        int communityLength = 0;
        if (community != null) {
            communityLength = community.getBytes().length;
        }
        int varNameLength = 0;
        if (varName != null) {
            varNameLength = varName.getBytes().length;
        }
        int stringDataLength = 0;
        if (this.dataType == this.MOOS_BINARY_STRING && binaryData != null) {
            stringDataLength = binaryData.length;
        } else if (stringData != null) {
            stringDataLength = stringData.getBytes().length;
        }
        int sourceAuxInfoLength = 0;
        if (sourceAuxInfo != null) {
            sourceAuxInfoLength = sourceAuxInfo.getBytes().length;
        }

        int nInt = 2 * INT_SIZE_IN_BYTES;
        int nChar = 2 * BYTE_SIZE_IN_BYTES;
        int nString = INT_SIZE_IN_BYTES + sourceLength
                + INT_SIZE_IN_BYTES + communityLength
                + INT_SIZE_IN_BYTES + varNameLength
                + INT_SIZE_IN_BYTES + stringDataLength;
        if (!DISABLE_AUX_SOURCE) {
            nString += INT_SIZE_IN_BYTES + sourceAuxInfoLength;
        }

        int nDouble = 3 * DOUBLE_SIZE_IN_BYTES;

        return nInt + nChar + nString + nDouble;

    }

    /**
     * This function writes a string with an integer representing its length preceeding the string bytes to the supplied ByteBuffer
     * @param msgBuffer
     * @param s
     */
    protected void putString(ByteBuffer msgBuffer, String s) {
        if (s == null || s.length() <= 0) {
            msgBuffer.putInt(0);
        } else {
            msgBuffer.putInt(s.length());
            msgBuffer.put(s.getBytes());
        }
    }

    /**
     * This reads the integer length value and then reads the remaining length n bytes into a String object
     * @param msgBuffer
     * @return Returns the next String from the ByteBuffer
     */
    protected String getString(ByteBuffer msgBuffer) {
        int length = msgBuffer.getInt();
        if (length > 0) {
            byte[] charData = new byte[length];
            binaryData = charData; // always update the binaryData - ends up being the last bit of stringData if it is binary anyway.
            msgBuffer.get(charData);
            return new String(charData);
        } else {
            return new String(); // decided that returning the empty string may cause problems...
        }
    }

    /**
     * This method never calls rewind on the passed ByteBuffer, only reads or writes to it from its current position. This method assumes that the buffer is big enough and has been creates as such by a moos packet.
     * @param msgBuffer The ByteBuffer to serialise the message to/from
     * @param toStream whether to serialise the contents of this message into the supplied ByteBuffer or to populate this message from the supplied ByteBuffer
     * @return
     */
    public int serialize(ByteBuffer msgBuffer, boolean toStream) {

        if (toStream) {
            msgLength = this.getSizeInBytesWhenSerialised();
            msgBuffer.putInt(msgLength);
            msgBuffer.putInt(this.msgID);
            msgBuffer.put(String.valueOf(this.msgType).getBytes()[0]);
            msgBuffer.put(String.valueOf(this.dataType).getBytes()[0]);

            putString(msgBuffer, source);

            if (!DISABLE_AUX_SOURCE) {
                putString(msgBuffer, sourceAuxInfo);
            }

            putString(msgBuffer, community);
            putString(msgBuffer, varName);

            msgBuffer.putDouble(time);
            msgBuffer.putDouble(doubleData);
            msgBuffer.putDouble(m_dfVal2);

            if (this.dataType == this.MOOS_BINARY_STRING) {
                msgBuffer.putInt(binaryData.length).put(binaryData);
            } else {
                putString(msgBuffer, stringData);
            }


        } else {
            this.msgLength = msgBuffer.getInt();
            this.msgID = msgBuffer.getInt();
            this.msgType = new String(new byte[]{msgBuffer.get()}).charAt(0);
            this.dataType = new String(new byte[]{msgBuffer.get()}).charAt(0);

            this.source = this.getString(msgBuffer);

            if (!DISABLE_AUX_SOURCE) {
                this.sourceAuxInfo = this.getString(msgBuffer);
            }

            this.community = this.getString(msgBuffer);
            this.varName = this.getString(msgBuffer);

            this.time = msgBuffer.getDouble();
            this.doubleData = msgBuffer.getDouble();
            this.m_dfVal2 = msgBuffer.getDouble();

            this.stringData = getString(msgBuffer); // this will populate the binary data field also.

        }
        return msgLength;
    }

    public int getLength() {
        return msgLength;
    }

    /**
     * Originally an overload of System.printf(...). Modified to be overload of Android Log.d
     * @param s
     * @param variables
     */
    public static void moosTrace(String s, Object... variables) {

//        Log.d(TAG,String.format(s,variables));
        System.out.printf(s, variables);
    }

    /**
     *  This prints to the standard out the contents of the message
     */
    public void trace() {

        moosTrace("Type=%c DataType=%c Key=%s ", msgType, dataType, varName);

        switch (dataType) {
            case MOOS_DOUBLE:
                moosTrace("Data=%f ", doubleData);
                break;
            case MOOS_STRING:
                moosTrace("Data=%s ", stringData);
                break;
        }


        moosTrace("Source=%s Time =%10.3f\n", source, time);
    }

    public boolean isYoungerThan(double dfAge) {
        return time >= dfAge;
    }

    /**
     *A method to check the timestamping of a MOOSMsg.
     *Does so by checking the <code>TimeNow</code> passed to it, and gives the
     *requesting class an idea about how out of sync this message is by comparing the
     *MOOSMsg's time stamp (<code>m_dfTime</code>) to SKEW_TOLERANCE.
     *@return true if a MOOSMsg's time stamp is either SKEW_TOLERANCE seconds ahead or
     *behind the MOOSDB clock.  Will also pass you the <code>pdfSkew</code>, or amount of
     *time difference between the MOOSDB and MOOSMsg timestamp if desired.
     */
    public boolean isSkewed(double dfTimeNow, Double pdfSkew) {
        //if we are in playback mode (a global app wide flag)
        //then skew may not mean anything.. (we can stop and start at will)
        if (isPlayback()) {
            dfTimeNow = time;
        }

        double dfSkew = Math.abs(dfTimeNow - time);
        pdfSkew = dfSkew;
        return (dfSkew > SKEW_TOLERANCE) ? true : false;
    }

    public String getAsString(int nFieldWidth/*=12*/, int nNumDP/*=5*/) {
        if (getTime() != -1) {
            if (isDataType(MOOS_DOUBLE)) {

                return "" + doubleData;
            } else {
                return stringData;
            }
        } else {
            return "NotSet";
        }
    }

    /**
     * @return the msgID
     */
    public int getMsgID() {
        return msgID;
    }

    /**
     * @param msgID the msgID to set
     */
    public void setMsgID(int msgID) {
        this.msgID = msgID;
    }

    /**
     * @return the sourceAuxInfo
     */
    public String getSourceAuxInfo() {
        return sourceAuxInfo;
    }

    /**
     * @param sourceAuxInfo the sourceAuxInfo to set
     */
    public void setSourceAuxInfo(String sourceAuxInfo) {
        this.sourceAuxInfo = sourceAuxInfo;
    }

    /**
     * @return the binaryData
     */
    public byte[] getBinaryData() {
        return binaryData;
    }

    /**
     * @param binaryData the binaryData to set
     */
    public void setBinaryData(byte[] binaryData) {
        this.binaryData = binaryData;
    }

    public String strTrace() {

        String trace=new String();

        trace+=String.format("Type=%c DataType=%c Key=%s ", msgType, dataType, varName);

        switch (dataType) {
            case MOOS_DOUBLE:
                trace+=String.format("Data=%f ", doubleData);
                break;
            case MOOS_STRING:
                trace+=String.format("Data=%s ", stringData);
                break;
        }


        trace+=String.format("Source=%s Time =%10.3f\n", source, time);

        return(trace);
    }
}
