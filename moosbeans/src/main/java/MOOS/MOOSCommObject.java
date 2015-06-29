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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class simple read and write methods for MOOS packets and messages to a pipe. This is similar to the C++ version.
 * @author Benjamin C. Davis
 */
public class MOOSCommObject {

    protected boolean verbose;
    protected MOOSMsg lastRead;

    public MOOSCommObject() {
    }

    public boolean sendPkt(SocketChannel sock, MOOSCommPkt pkt) {
        ByteBuffer bytes = pkt.getBytes();


        long written = 0;
        try {

            while (written >= 0 && bytes.position() < bytes.limit()) {
                written += sock.write(bytes);
                
            }
           // bytes.rewind(); // want to reset the position since we this might get called again. Always leave buffer ready to read data from...
        } catch (IOException ex) {
            // Logger.getLogger(MOOSCommObject.class.getName()).log(Level.SEVERE, null, ex);
            ex.printStackTrace();
        }
        if (written > 0) {
            return true;
        }

        /*   if (verbose) {
        MOOSMsg.moosTrace("Bytes Sent: " +  bytes.length +"\n");
        }*/


        return false;
    }

    public boolean readPkt(SocketChannel sock, MOOSCommPkt pkt) throws IOException {
        int required = 0;
       // int iterations = 0;
        try {
           

            while ((required = pkt.fill()) > 0) {
                ByteBuffer theBuffer = pkt.getBytes();
                int count = sock.read(theBuffer);
                if (count <= 0) {
                   if (required > count && theBuffer.position() == 0) return false;// don't want to block indefinately in a hot loop so return if nothing to be read.
                    try {
                        Thread.sleep(1);
                        /*else {
                        System.out.println("ERROR:ReadPkt, read " + count + " bytes but need " +required + " current length " + theBuffer.position());
                        iterations++;
                        }*/
                    } catch (InterruptedException ex) {
                        Logger.getLogger(MOOSCommObject.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
               
               theBuffer.flip(); // this is read on the next iteration of pkt.fill()
             
            }
           
        } catch (NoJavaZipCompressionSupportYetException e2) {
            e2.printStackTrace();
            return false;
        }
//System.out.println("total iterations " + iterations + " required " + required);
        return true;
    }

    //Send a single message to the server
    public boolean sendMsg(SocketChannel sock, MOOSMsg msg) {
        //Create a packet with a single message
        MOOSCommPkt pkt = new MOOSCommPkt();

        ArrayList<MOOSMsg> msgList = new ArrayList<MOOSMsg>();
        msgList.add(msg);

        if (pkt.serialize(msgList, true)) {
            if (sendPkt(sock, pkt)) {
                return true;
            }
        }

        return false;
    }

    //Read a single message from the server
    public ArrayList<MOOSMsg> readMsgs(SocketChannel sock) throws IOException {
        MOOSCommPkt pkt = new MOOSCommPkt();

        if (readPkt(sock, pkt)) {
            pkt.deSerialize();
            ArrayList<MOOSMsg> msgList = pkt.getMsgList();
            if (msgList != null && !msgList.isEmpty()) {
                lastRead = pkt.getMsgList().get(0);
                // System.out.println("readMsgs:" + lastRead);
            } // else the last read stays the same as for some reason this was an empty packet.
            return msgList;
        }
        return new ArrayList<MOOSMsg>();
    }

    /**
     * @return the lastRead
     */
    public MOOSMsg getLastRead() {
        return lastRead;
    }

    /**
     * @return the verbose
     */
    public boolean isVerbose() {
        return verbose;
    }

    /**
     * @param verbose the verbose to set
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }
}
