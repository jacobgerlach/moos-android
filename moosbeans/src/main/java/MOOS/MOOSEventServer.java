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
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.TreeMap;
import java.io.Serializable;
import java.util.Properties;


import static MOOS.MOOSMsg.*;

/**
 *
 * @author Benjamin C. Davis
 */
public class MOOSEventServer extends MOOSCommClient implements Serializable {

    public double statusTime;
    public static final double STATUS_PERIOD = 2.0;
    /**
     * holds information about the map between message varNames and registered listeners
     */
    protected TreeMap<String, ArrayList<MOOSMsgEventListener>> msgListenerMap;

    public MOOSEventServer() {
        super("127.0.0.1", 9000); // defaults
        msgListenerMap = new TreeMap<String, ArrayList<MOOSMsgEventListener>>();
        statusTime = MOOSMsg.moosTimeNow();
    }

    /**
     * Here we override the parent iterate method so that we can intercept the new messages and fire them out to the registered listeners.
     */
    @Override
    public void iterate() throws IOException {
        super.iterate();
        ArrayList<MOOSMsg> tempInbox;
        synchronized (this) {
            tempInbox = new ArrayList<MOOSMsg>(inboxList); // create a clone which won't alter.
        }
        TreeMap<String, ArrayList<MOOSMsg>> msgMap = new TreeMap<String, ArrayList<MOOSMsg>>();

        // clear the inbox
        inboxList.clear();


        // sort inbox into individual message lists
        for (MOOSMsg m : tempInbox) {
            String varName = m.getKey();


            ArrayList<MOOSMsg> msgVarList = msgMap.get(varName);
            if (msgVarList != null) {
                // add to list
                msgVarList.add(m);
            } else {
                // create mew list for variable
                msgVarList = new ArrayList<MOOSMsg>();
                // add to list
                msgVarList.add(m);
                // add to treemap
                msgMap.put(varName, msgVarList);
            }
        }

        // process the correct message lists by the correct listeners
        for (String key : msgMap.keySet()) {
            // send message to all receivers

            ArrayList<MOOSMsgEventListener> list = null;
            synchronized (this) {
                list = msgListenerMap.get(key);
            }
            if (list != null) {
                ArrayList<MOOSMsg> homogenousMsgs = msgMap.get(key);
                try {
                    for (MOOSMsgEventListener c : list) {
                        c.processMOOSMsg(homogenousMsgs);
                    }
                } catch (Exception e) { // we don't want to crash the MOOS thread is a process goes wrong, so we just report it.
                    e.printStackTrace();
                }
            }
        }

        iteratePrivate();

    }

    protected void iteratePrivate() {
        if (MOOSMsg.moosTimeNow() - statusTime > STATUS_PERIOD) {
            String sStatus = this.name.toUpperCase() + "_STATUS";
            notify(sStatus, makeStatusString(), -1);
            statusTime = MOOSMsg.moosTimeNow();
        }


    }

    protected String makeStatusString() {


        String pubList = "";
        String subList = "";


        for (String p : this.publishingList) {
            pubList += p + ",";
        }
        for (String p : this.subscribingList.keySet()) {
            subList += p + ",";
        }

        String ssStatus = "AppErrorFlag=" + false + "," + "Uptime=" + (double) ((MOOSMsg.moosTimeNow() - startTime)) + ","
                + "MOOSName=" + this.name + ","
                + "Publishing=\"" + pubList + "\","
                + "Subscribing=\"" + subList + "\"";



        return ssStatus;
    }

    public boolean registerLists(Iterator<String> varNames, Iterator<Double> intervals) {
        while (varNames.hasNext()) {
            if (intervals.hasNext()) {
                if (!register(varNames.next(), intervals.next())) {  // add register the variables and freqency intervals with the super class and MOOSDB.
                    // System.out.println("MOOSEventServer: Registration Failed!");
                    //  return false;
                }
            } else {
                System.out.println("MOOSEventServer: Moos Variable has no associated interval! Client subclass not returning interval. See Stack Trace below:");
                StackTraceElement[] e = Thread.currentThread().getStackTrace();
                for (StackTraceElement t : e) {
                    System.err.println(t);
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Here we register all the messages we are interested in by looking at the event listeners of this class and calling the super resgister() method if the super.handshake() is successful.
     * @return weather handshake was successful.
     */
   /* @Override
    public boolean handshake() throws IOException {
        if (super.handshake()) {
            boolean result = true;

            //make sure all listeners are registered... 
            if (mOOSMsgEventListenerList != null) {
                for (MOOSMsgEventListener listener : mOOSMsgEventListenerList) {
                    result &= this.register(listener);
                }
            }
            
            if (!result) {
                System.out.println("MOOSEventServer: failed to register lists of variables on Handshake!");
            }
            return true; // fail silently if just registration error.
        } else {
            return false;
        }
    }*/
    @Override
     protected void sendRegistrationPackets() {
          boolean result = true;

            //make sure all listeners are registered...
            if (mOOSMsgEventListenerList != null) {
                for (MOOSMsgEventListener listener : mOOSMsgEventListenerList) {
                    result &= this.register(listener);
                }
            }

        //    if (!result) {
        //        System.out.println("MOOSEventServer: failed to register lists of variables after Handshake!");
        //    }
            //return true; // fail silently if just registration error.

    }

    public synchronized boolean unRegister(MOOSMsgEventListener client) {
        // remove from treemap
        boolean success = true;

        // this way we know we got rid of all old vars from that client, just in case they changed. A bit slow but shouldn't happen too often.
        ArrayList<String> registeredVars = new ArrayList<String>();

        for (String var : msgListenerMap.keySet()) {
            for (MOOSMsgEventListener theClient : msgListenerMap.get(var)) {
                if (theClient == client) {
                    if (!super.unRegister(var)) {
                        success = false;
                    }
                    msgListenerMap.get(var).remove(client);
                    if (msgListenerMap.get(var).isEmpty()) {
                        msgListenerMap.remove(var);
                    }
                    break;
                }
            }
        }

        /*
        for (String var : client.getVariableNames()) {
        if (msgListenerMap.containsKey(var)) {
        msgListenerMap.get(var).remove(client);
        if (msgListenerMap.get(var).isEmpty()) {
        msgListenerMap.remove(var);
        }
        }
        if (!super.unRegister(var)) {
        success = false;
        }
        }
         */
        return success;

    }

    public synchronized boolean register(MOOSMsgEventListener client) {
        // Here gather information from the requesting client about what message types etc it is interested in receiving.

        //build a tree map for associating listeners with MOOS Message variables
        for (String var : client.getVariableNames()) {
            if (msgListenerMap.containsKey(var) && !msgListenerMap.get(var).contains(client)) {
                msgListenerMap.get(var).add(client);
            } else if (!msgListenerMap.containsKey(var)) {
                ArrayList<MOOSMsgEventListener> l = new ArrayList<MOOSMsgEventListener>();
                l.add(client);
                msgListenerMap.put(var, l);
            }
        }


        return this.registerLists(client.getVariableNames().iterator(), client.getRequiredMsgIntervals().iterator());
    }
    ////////////////////////////////////////////////////////////////////////
    // Event Listener stuff for registering the MOOSMsgEventListener etc
    ////////////////////////////////////////////////////////////////////////
    /**
     * Utility field holding list of MOOSMsgEventListeners.
     */
    protected java.util.ArrayList<MOOSMsgEventListener> mOOSMsgEventListenerList;

    /**
     * Registers MOOSMsgEventListener to receive events.
     * @param listener The listener to register.
     */
    public synchronized void addMOOSMsgEventListener(MOOSMsgEventListener listener) {
        if (mOOSMsgEventListenerList == null) {
            mOOSMsgEventListenerList = new java.util.ArrayList<MOOSMsgEventListener>();
        }
        if (!mOOSMsgEventListenerList.contains(listener)) {
            mOOSMsgEventListenerList.add(listener);
        }
        register(listener);
    }

    /**
     * Removes MOOSMsgEventListener from the list of listeners.
     * @param listener The listener to remove.
     */
    public synchronized void removeMOOSMsgEventListener(MOOSMsgEventListener listener) {
        if (mOOSMsgEventListenerList != null) {
            mOOSMsgEventListenerList.remove(listener);
        }
        unRegister(listener);
    }

    public java.util.ArrayList<MOOSMsgEventListener> getMOOSMsgEventListenerList() {
        return mOOSMsgEventListenerList;
    }

    public void setMOOSMsgEventListenerList(java.util.ArrayList<MOOSMsgEventListener> mOOSMsgEventListenerList) {
        this.mOOSMsgEventListenerList = mOOSMsgEventListenerList;
    }

    /**
     * Called if the bean has been serialized from disk
     */
    public void initiate() {
        if (this.mOOSMsgEventListenerList != null) {
            for (Object client : this.mOOSMsgEventListenerList) {
                this.register((MOOSMsgEventListener) client);
            }
        }
        if (this.enable && theThread != null && !theThread.isAlive()) {
            //start thread in same state as when it was serialised to a bean
            this.enable = false;
            this.setEnable(true);
        }

    }

    public static void main(String[] args) {
        try {
            Properties properties = new Properties();
            try {
                properties.load(new FileInputStream("config.properties"));
            } catch (IOException e) {
                System.out.println("Cannot open config.properties file.");
                System.exit(0);
            }

            // test code for packets and msgs
            moosTrace("Executing a few tests... \n");
            String moosHost = properties.getProperty("moos_host");
            int moosPort = new Integer(properties.getProperty("moos_port"));
            MOOSMsg smsg = new MOOSMsg(MOOS_DATA, "TestVariable", "test");
            MOOSMsg smsg1 = new MOOSMsg(MOOS_DATA, "TestVariable", "test", 12345);
            MOOSMsg smsg2 = new MOOSMsg(MOOS_DATA, "TestVariable", 3.145d, 54321);
            smsg.setSource("MeJavaMan");
            MOOSCommPkt pkt = new MOOSCommPkt();
            ArrayList<MOOSMsg> l = new ArrayList<MOOSMsg>();
            l.add(smsg);
            l.add(smsg1);
            l.add(smsg2);
            ByteBuffer b = MOOSMsg.allocate(smsg.getSizeInBytesWhenSerialised());

            // System.out.println(System.currentTimeMillis());
            pkt.serialize(l, true);
            b = pkt.getBytes();
            ArrayList<MOOSMsg> l2 = new ArrayList<MOOSMsg>();
            MOOSCommPkt pkt2 = new MOOSCommPkt();
            pkt2.packetData = b; // need setter method
            pkt2.fill();
            pkt2.serialize(l2, false);
            for (MOOSMsg m : l2) {
                m.trace();
            }
            moosTrace("Tests Finished \n");

            // end of test code



            MOOSEventServer client = new MOOSEventServer();
            client.setHostname("localhost");//"129.67.95.225");
            client.setVerbose(true);
            client.setEnable(true);

            try {
                Thread.sleep(3000);
                client.setEnable(false);
                System.out.println(client.socket.socket().isInputShutdown());
                Thread.sleep(3000);
                client.setEnable(true);
                System.out.println(client.socket.socket().isInputShutdown());

                Thread.sleep(3000);
                client.addMOOSMsgEventListener(new MOOSClient());
                Thread.sleep(3000);

                client.setEnable(false);
                System.out.println(client.socket.socket().isInputShutdown());

                Thread.sleep(3000);
                System.out.println(client.socket.socket().isInputShutdown());

                client.setEnable(true);
                Thread.sleep(3000);
                System.out.println(client.socket.socket().isInputShutdown());

                client.setEnable(false);
                Thread.sleep(3000);
                client.setEnable(true);
            } catch (InterruptedException ex) {
            }

        } catch (NoJavaZipCompressionSupportYetException ex) {
            ex.printStackTrace();
        }

    }
}
