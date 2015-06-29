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

import java.util.TreeMap;
import java.util.Collection;
import java.util.Stack;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

import static MOOS.MOOSMsg.*;

public class MOOSCommClient extends MOOSCommObject implements Runnable {

    private static MOOSCommClient singletonInstance = null;
  
    public static final int MOOS_SERVER_REQUEST_ID = -2;
    protected SocketChannel socket;
    protected int port;
    protected String hostname;
    protected String name;
    protected Stack<MOOSMsg> outboxList;
    protected ArrayList<MOOSMsg> inboxList;
    protected ArrayList<String> publishingList;
    protected TreeMap<String, Double> subscribingList;
    protected Thread theThread;
    protected boolean enable; // whether this thread is running or not.
    protected int MAX_INBOX_MESSAGES = 1000;
    protected int MAX_OUTBOX_MESSAGES = 500;
    protected double fundamentalFrequency = 5; // 200ms
    protected long keepAliveTime = 1000; // 1000ms
    protected long lastSentMsgTime = 0; // first msg will fire a null msg straight away if outbox is empty. This is keep it ticking over.
    protected double startTime;
    protected boolean doLocalTimeCorrection = false;
    protected boolean useNameAsSrc = true;
    protected int nextMsgID = 0; // start IDs at zero.

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


            /* test stopping and starting the client */
            MOOSCommClient client = new MOOSCommClient(moosHost, moosPort);
            client.setVerbose(true);
            client.setEnable(true);
            try {
                Thread.sleep(3000);
                client.setEnable(false);
                Thread.sleep(3000);
                client.setEnable(true);
                Thread.sleep(3000);
                client.setEnable(false);
                Thread.sleep(3000);
                client.setEnable(true);
                Thread.sleep(3000);
                client.setEnable(false);
                Thread.sleep(3000);
                client.setEnable(true);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                //     Logger.getLogger(MOOSCommClient.class.getName())..log(Level.SEVERE, null, ex);

            }

        } catch (NoJavaZipCompressionSupportYetException ex) {
            ex.printStackTrace();
        }
    }

    
  
    /**
     *
     * @param hostname MOOSDB hostname or IP address, i.e. localhost
     * @param port of MOOSBD default 9000
     */
    public MOOSCommClient(String hostname, int port) {

        this.hostname = hostname;
        this.port = port;


        // Initialise message boxes
        outboxList = new Stack<MOOSMsg>();
        inboxList = new ArrayList<MOOSMsg>();

        // current not editing the publishingList.
        publishingList = new ArrayList<String>();
        subscribingList = new TreeMap<String, Double>();

        name = "JavaMOOSConnector";

    }
  
    /**
     * Lets us obtain a singleton instance of the class, if required
     * @param hostname MOOSDB hostname or IP address, i.e. localhost
     * @param port of MOOSBD default 9000
     */ 
    public synchronized static MOOSCommClient getInstance(String hostname, int port) {
        if (singletonInstance == null) {
            singletonInstance = new MOOSCommClient(hostname, port);
        }
        return singletonInstance;
    }
                                                  
  
    /** Used to indicate to the run() loop that we have already established the connection so it doesn't need to.
     *
     */
    protected boolean manualConnect = false;
    /**
     * If this is set to true the auto reconnection is automatically attempted continuously after 1st initial successful connection. If using tryToConnect() this will throw an exception if 1st connection attempt fails. if autoReconnect == true then this will still only later try and reconnect if the 1st connection was succesfull and moos has been running for a while.
     */
    protected boolean autoReconnect = true;

    /**
     *  Automatically calls setEnable(true) to start the server monitoring process if this succeeds. This will throw an exception if cannot connect.
     * @throws IOException
     */
    public void tryToConnect() throws IOException {
          if (!this.enable) { // don't want to try and call if already running
            if (connectToServer()) {
                if (handshake()) {
                    manualConnect = true; // we managed to connect
                    this.setEnable(true); // starts the server loop thread.
                    return;
                }
                this.disconnectFromServer(); // only if tried part handshake()
                throw (new IOException("Couldn't Handshake with Server, no reason given...!"));
            }
            // if get to here then something failed quietly.
            throw (new IOException("Couldn't connect to Server, no reason given...!"));
        }
    }

    /**
     * The main loop
     * - Create a socket connecting to the server.
     * - handshake. Exchange protocol details and wait for a welcome message.
     * - Run round a loop pausing for the fundamental frequency
     * - call iterate() on every iteration. This sends messages in outbox and receives all new MOOSPackets containing messages, and orders then in the inbox in newest 1st.
     */
    @Override
    public void run() {
        while (enable) {
            try {
                if (manualConnect || connectToServer()) { // should only call connectToServer() if manualConnect is false. (i.e. user didn't call tryToConnect())
                    // this.enable = false;
                    //  return;
                    if (manualConnect || handshake()) {
                        sendRegistrationPackets();
                        while (enable && socket.isConnected()) {
                            try {
                                Thread.sleep((int) Math.floor((1000.0 / fundamentalFrequency)));
                                if (enable && socket.isConnected()) {
                                    iterate();

                                } else {
                                    if (enable)  moosTrace("MOOSDB disconnected us :-( \n");
                                    moosTrace("Requesting Disconnect from the MOOSDB... \n");
                                    break;
                                }

                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        //  disconnectFromServer();
                        //  return;
                    }// else {
                    // disconnectFromServer();
                    // return;
                    //  }
                }

            } catch (Exception e) {
                System.out.println("CAUGHT EXCEPTION: ");
                e.printStackTrace();

                closeConnection();
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            manualConnect = false;
            if (!isAutoReconnect()) {
                enable = false;
                break; // don't want to reconnect on disconnect.
            }
        }
        this.disconnectFromServer();

    }

    public synchronized ArrayList<MOOSMsg> getNewMsgs() {
        ArrayList<MOOSMsg> in = (ArrayList<MOOSMsg>)inboxList.clone();
        inboxList.clear();
        return in;
    }
    public static MOOSMsg findNewestMsg(Iterable<MOOSMsg> msgList, String varName) {
        for (MOOSMsg m: msgList) {
            if (m.getKey().equals(varName)) return m;
        }
        return null;
    }


    public synchronized void readNewMessages() throws IOException {
        moosTrace("Reading new messages from socket...\n");
        MOOSCommPkt pktRx = new MOOSCommPkt();
        if (inboxList.size() > this.MAX_INBOX_MESSAGES)  {
            //System.out.println("INBOX OVERFLOWING: CLEARING!");
            inboxList.clear();
        } // always empty the mail box.
        while (readPkt(socket, pktRx)) { // only add messages if managed to read a packet. Also the latest packet will be the newest so that gets added to the top of the list.
            ArrayList<MOOSMsg> tempList = new ArrayList<MOOSMsg>();
            pktRx.serialize(tempList, false); // read messages into temp list
            inboxList.addAll(0, tempList); // add temp list to front of message queue as last packet received will contain the newest messages
            //debug
            if (verbose) {
                for (MOOSMsg m : tempList) {
                    System.out.print("I:");
                    m.trace();
                }
            }
            pktRx = new MOOSCommPkt();
        }
    }

    /**
     * This will also clear the message list once sent
     * @param messages
     */
    public synchronized void sendMessages(Collection<MOOSMsg> messages) {
        // Send the whole list
        if (!messages.isEmpty()) {
            MOOSCommPkt PktTx = new MOOSCommPkt();
            PktTx.serialize(new ArrayList(messages), true); // convert from Stack to ArrayList.
            sendPkt(socket, PktTx);
        }
    }

    /**
     *  Read incoming messages and send outgoing ones. This is called at the rate of the thread loop
     */
    public void iterate() throws IOException {

        long timeNow = System.currentTimeMillis();

        if (outboxList.isEmpty()) { // if empty we just send an NULL message to keep things ticking over. Sending at fundamental frequency seems a bit high
            if (timeNow - this.lastSentMsgTime > this.keepAliveTime) { // only if has been a while since last message
                this.lastSentMsgTime = timeNow;
                MOOSMsg msg = new MOOSMsg();
                //outboxList.add(msg);
                post(msg);
            }
        } else {
            this.lastSentMsgTime = timeNow;
        }

        sendMessages(outboxList); // send the outbox
        outboxList.clear(); // clear the outbox

        //  try {
        readNewMessages();
        /*  } catch (IOException ex) {
        ex.printStackTrace();
        this.setEnable(false);
        //Logger.getLogger(MOOSCommClient.class.getName()).log(Level.SEVERE, null, ex);
        }*/
    }

    public boolean connectToServer() throws IOException {
        if (socket != null && socket.isConnected()) {
            moosTrace("Client is already connected! ... disconnecting first\n");
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }
        try {
            moosTrace("Trying " + this.hostname + ":" + this.port + "...\n");
            socket = SocketChannel.open();
            socket.socket().setReuseAddress(enable); // allows immediate reuse
            socket.socket().setSendBufferSize(4000000);
             socket.socket().setReceiveBufferSize(4000000);
             socket.socket().setPerformancePreferences(0, 1, 2); // we need low latency and bandwidth
            socket.connect(new InetSocketAddress(hostname, port));

            socket.configureBlocking(false); // We want this to be non-blocking.... so false?
           // socket.socket().setSoTimeout(10);
            
            if (socket.isConnected() && socket.socket().isConnected() && socket.socket().isBound()) {
                moosTrace("Connected to Server.");
                return true;

            }

            return false;
        } catch (IOException ex) {
            // ex.printStackTrace();
            moosTrace("Unable to connect to " + hostname + " :" + port + "\n");
            throw(ex);
        //    return false;
        }



    }

    protected boolean closeConnection() {
        if (socket != null && socket.isConnected()) {
            moosTrace("Socket is still open, closing.....");
            try {
                socket.socket().close();
                socket.close();
                moosTrace("CLOSED!\n");
                return true;
            } catch (IOException ex) {
                ex.printStackTrace();
                return true;
            }
        } else {
            moosTrace("Client is NOT connected! \n");
            return false;
        }
    }

    public synchronized boolean disconnectFromServer() {
        enable = false;
        this.inboxList.clear();
        this.outboxList.clear();
        return closeConnection();
    }

    public boolean handshake() throws IOException {
        try {
            moosTrace("  Handshaking as \"%s\" ... ", name);
            if (doLocalTimeCorrection) {
                //SetMOOSSkew(0);
            } // put in MOOSMsg Little Endian bytebuffer so that it is correct ordering.
            ByteBuffer buf = MOOSMsg.allocate(32);
            buf.put((MOOSCommPkt.MOOS_PROTOCOL_STRING).getBytes());
            buf.put(new byte[32 - MOOSCommPkt.MOOS_PROTOCOL_STRING.length()]); // padding
            buf.flip();
            this.socket.write(buf);
            // Send a blank message
            MOOSMsg msg = new MOOSMsg(MOOS_DATA, "", name);
            sendMsg(socket, msg);
            for (int i = 0; i < 50; i++) { // try to read 10 times of 3 seconds. Should be enough time to see if we receive a welcome

                try {
                    Thread.sleep(300);
                } catch (InterruptedException ex) {
                    //  Logger.getLogger(MOOSCommClient.class.getName()).log(Level.SEVERE, null, ex);
                }

                this.readNewMessages();
                for (MOOSMsg welcomeMsg : inboxList) {
                    if (welcomeMsg != null) {
                        if (welcomeMsg.isType(MOOSMsg.MOOS_WELCOME)) {
                            moosTrace(" Success! " + welcomeMsg.getStringData() + "\n");
                            if (verbose) {
                                System.out.print("I:");
                                welcomeMsg.trace();
                            }
                            double skew = welcomeMsg.getDoubleData();
                            if (this.doLocalTimeCorrection) {
                                // SetMOOSSkew(skew);
                                if (verbose) {
                                    moosTrace("MOOSCommClient: Not implemented skew yet \n");
                                }
                            }
                            inboxList.remove(welcomeMsg);
                            return true;
                        } else if (welcomeMsg.isType(MOOSMsg.MOOS_POISON)) {
                            // filthy MOOS
                            moosTrace("MOOSDB Poisoned us, handshake() fail, why oh why?! : " + welcomeMsg.getStringData() + "\n");
                            return false;
                        } else {
                            moosTrace("MOOSDB Handshake - Not welcome message?! : " + welcomeMsg.getStringData() + "\n continuing to wait for welcome... \n");
                        }
                    }
                }
            }
            moosTrace("MOOSDB Handshake Failed - no data?! : This probably means you are using an OLD MOOSDB! which doesn't accept the protocol string:" + MOOSCommPkt.MOOS_PROTOCOL_STRING);
            return false;

        } catch (IOException ex) {
          //  ex.printStackTrace();
            throw (ex);
          //  return false;
        }
    }

    // Functions from C++ port
    /**
     *  check to see if we are registered to receive a variable
     * @param variable
     * @return
     */
    public boolean isRegisteredFor(String variable) {
        return this.subscribingList.containsKey(variable);
    }

    /**
     * 
     * @param msg The MOOSMsg created by the user.
     * @return whether this post was successful or not.
     */
    public boolean notify(MOOSMsg msg) {
        if (!this.publishingList.contains(msg.getKey())) {
            this.publishingList.add(msg.getKey());
        }
        return post(msg);
    }

    /**
     * notify the MOOS community that something has changed (double)
     * @param var
     * @param dfVal
     * @param dfTime
     * @return
     */
    public boolean notify(String var, double dfVal, double dfTime) {
        if (!this.publishingList.contains(var)) {
            this.publishingList.add(var);
        }
        return post(new MOOSMsg(MOOS_NOTIFY, var, dfVal, dfTime));

    }

    /**
     * notify the MOOS community that something has changed (double)
     * @param var The variable you are sending
     * @param dfVal the value
     * @param srcAux extra source info
     * @param dfTime the timestamp for the data
     * @return
     */
    public boolean notify(String var, double dfVal, String srcAux, double dfTime) {

        MOOSMsg msg = new MOOSMsg(MOOS_NOTIFY, var, dfVal, dfTime);
        msg.setSourceAuxInfo(srcAux);

        if (!this.publishingList.contains(var)) {
            this.publishingList.add(var);
        }

        return post(msg);
    }

    /**
     * notify the MOOS community that something has changed (String)
     * @param var The variable you are sending
     * @param sVal the value
     * @param dfTime the timestamp for the data
     * @return
     */
    public boolean notify(String var, String sVal, double dfTime) {
        if (!this.publishingList.contains(var)) {
            this.publishingList.add(var);
        }
        return post(new MOOSMsg(MOOS_NOTIFY, var, sVal, dfTime));

    }

    /**
     * notify the MOOS community that something has changed (String)
     * @param var The variable you are sending
     * @param sVal the value
     * @param srcAux extra source info
     * @param dfTime the timestamp for the data
     * @return
     */
    public boolean notify(String var, String sVal, String srcAux, double dfTime) {
        MOOSMsg msg = new MOOSMsg(MOOS_NOTIFY, var, sVal, dfTime);
        msg.setSourceAuxInfo(srcAux);

        if (!this.publishingList.contains(var)) {
            this.publishingList.add(var);
        }

        return post(msg);
    }

    /**
     * notify the MOOS community that something has changed (Binary)
     * @param var The variable you are sending
     * @param binary the value
     * @param dfTime the timestamp for the data
     * @return
     */
    public boolean notify(String var, byte[] binary, double dfTime) {
        if (!this.publishingList.contains(var)) {
            this.publishingList.add(var);
        }
        return post(new MOOSMsg(MOOS_NOTIFY, var, binary, dfTime));

    }

    /**
     * notify the MOOS community that something has changed (Binary)
     * @param var The variable you are sending
     * @param binary the value
     * @param srcAux
     * @param dfTime the timestamp for the data
     * @return
     */
    public boolean notify(String var, byte[] binary, String srcAux, double dfTime) {
        if (!this.publishingList.contains(var)) {
            this.publishingList.add(var);
        }
        MOOSMsg msg = new MOOSMsg(MOOS_NOTIFY, var, binary, dfTime);
        msg.setSourceAuxInfo(srcAux);

        return post(msg);
    }
    /*
    bool CMOOSCommClient::ServerRequest(const string &sWhat,MOOSMSG_LIST  & MsgList, double dfTimeOut, bool bClear)
    {
    if(!IsConnected())
    return false;
    
    CMOOSMsg Msg(MOOS_SERVER_REQUEST,sWhat.c_str(),"");
    Post(Msg);

    if(Msg.m_nID != MOOS_SERVER_REQUEST_ID)
    {
    return MOOSFail("Logical Error in ::ServerRequest");
    }

    int nSleep = 100;

    double dfWaited = 0.0;

    while(dfWaited<dfTimeOut)
    {
    if (Peek(MsgList, MOOS_SERVER_REQUEST_ID, bClear))
    {
    //OK we have our reply...
    return true;
    }
    else
    {
    MOOSPause(nSleep);
    dfWaited+=((double)nSleep)/1000.0;

    }
    }

    return false;
    }

    bool CMOOSCommClient::Peek(MOOSMSG_LIST & MsgList, int nIDRequired,bool bClear)
    {
    MsgList.clear();

    m_InLock.Lock();

    MOOSMSG_LIST::iterator p,q;

    p=m_InBox.begin();
    while(p!=m_InBox.end())
    {
    if(!p->IsType(MOOS_NULL_MSG))
    {
    //only give client non NULL Msgs
    if(p->m_nID==nIDRequired)
    {
    //this is the correct ID!
    MsgList.push_front(*p);
    q=p++;
    m_InBox.erase(q);
    continue;
    }
    }
    p++;
    }

    //conditionally (ex MIT suggestion 2006) remove all elements
    if(bClear)
    m_InBox.clear();


    m_InLock.UnLock();

    return !MsgList.empty();
    }

    //a static helper function
    bool CMOOSCommClient::PeekMail(MOOSMSG_LIST &Mail,
    const string &sKey,
    CMOOSMsg &Msg,
    bool bRemove,
    bool bFindYoungest )
    {
    MOOSMSG_LIST::iterator p;
    MOOSMSG_LIST::iterator q =Mail.end();

    double dfYoungest = -1;

    for(p = Mail.begin();p!=Mail.end();p++)
    {
    if(p->m_sKey==sKey)
    {
    //might want to consider more than one msg....

    if(bFindYoungest)
    {
    if(p->m_dfTime>dfYoungest)
    {
    dfYoungest=p->m_dfTime;
    q = p;
    }
    }
    else
    {
    //simply take first
    q=p;
    break;
    }

    }
    }

    if(q!=Mail.end())
    {
    Msg=*q;

    if(bRemove)
    {
    //Mail.erase(p);
    Mail.erase(q);
    }
    return true;

    }

    return false;
    }



    bool CMOOSCommClient::PeekAndCheckMail(MOOSMSG_LIST &Mail, const std::string &sKey, CMOOSMsg &Msg,bool bErase , bool bFindYoungest)
    {
    if(PeekMail(Mail,sKey,Msg,bErase,bFindYoungest))
    return(!Msg.IsSkewed(MOOSTime()-5.0));
    else
    return false;
    }



     */

    protected void sendRegistrationPackets() {
        for (String var : this.subscribingList.keySet()) {
            register(var, this.subscribingList.get(var));
        }
    }

    public boolean register(String var, double interval) {
        MOOSMsg MsgR = new MOOSMsg(MOOS_REGISTER, var, interval, 1.0);
        boolean bSuccess = post(MsgR);
        if (bSuccess || (this.socket == null || this.socket.isConnected())) {
            if (!subscribingList.containsKey(var)) {
                subscribingList.put(var, interval);
            }

            // subscribingList.add(var);
        }
        return bSuccess;
    }

    protected boolean unRegister(String var) {
        if (this.subscribingList.containsKey(var)) {
            MOOSMsg MsgUR = new MOOSMsg(MOOS_UNREGISTER, var, 0.0, 0.0);
            if (post(MsgUR)) {
                subscribingList.remove(var);
                return true;
            } else {
                return false;
            }

        } else {
            return true;
        }
    }

    public synchronized boolean post(MOOSMsg msg) { // we don't need to lock in Java if all the methods which deal with the inbox/outbox are synchronized
        if (socket == null || !this.socket.isConnected()) {
            return false;
        }

        //stuff our name in here  - prevent client from having to worry about
        //it...
        if (useNameAsSrc) {
            msg.setSource(name);
        } else if (!msg.isType(MOOS_NOTIFY)) {
            msg.setSource(name);
        }

        if (msg.isType(MOOS_SERVER_REQUEST)) {
            msg.setMsgID(MOOS_SERVER_REQUEST_ID);
        } else {
            //set up Message ID;
            msg.setMsgID(nextMsgID++);
        }

        outboxList.push(msg);

        if (outboxList.size() > MAX_OUTBOX_MESSAGES) {
            moosTrace("\nThe outbox is very full. This is suspicious and dangerous.\n");
            moosTrace("\nRemoving old unsent messages as new ones are added\n");
            if (verbose) {
                moosTrace("mOutbox size is " + outboxList.size() + "\n");
            }
            outboxList.remove(outboxList.lastElement());
        }
        if (verbose) {
            System.out.print("O:");
            msg.trace();
        }

        return true;
    }

    /**
     * This fails silently. Must satisfy: 0 < frequency <= 100
     * @param frequency
     */
    public void setFundamentalFrequency(double frequency) {
        if (frequency < 0.0) {
            fundamentalFrequency = 1;
        } else if (frequency > 100) {
            fundamentalFrequency = 100;
        } else {
            fundamentalFrequency = frequency;
        }
        // this is how often we call into the MOOSDB to get messages!
        this.keepAliveTime = (long) (1000.0/frequency);
        return;
    }

    /**
     *
     * @return the fundamental frequency
     */
    public double getFundamentalFrequency() {
        return this.fundamentalFrequency;
    }

    /**
     * @return the enable
     */
    public boolean isEnable() {
        return enable;
    }

    /** Set to true to start the Thread
     * @param enable the enable to set
     */
    public void setEnable(boolean enable) {
        if (!this.enable && enable) {
            // switch on
            if (theThread != null && theThread.isAlive()) {
                theThread.stop(); // This is the most retarded peace of code ever, should never get called, also its dirty. Sanity check...
            }            // Set up the thread
            theThread = new Thread(this);
            this.enable = enable;
            this.startTime = MOOSMsg.moosTimeNow();
            theThread.start();

        } else if (this.enable && !enable) {
            // switch off
            this.enable = enable;
        }
        //all other cases can be ignored - idempotent


    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /** Set the name of this MOOSclient for identification purposes on the MOOSDB
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return the hostname
     */
    public String getHostname() {
        return hostname;
    }

    /**
     * @param hostname the hostname to set
     */
    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    /**
     * @return the keepAliveTime
     */
    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    /**
     * @param keepAliveTime the keepAliveTime to set
     */
    public void setKeepAliveTime(long keepAliveTime) {
        this.keepAliveTime = keepAliveTime;
    }

    /**
     * @return the useNameAsSrc
     */
    public boolean isUseNameAsSrc() {
        return useNameAsSrc;
    }

    /**
     * @param useNameAsSrc the useNameAsSrc to set
     */
    public void setUseNameAsSrc(boolean useNameAsSrc) {
        this.useNameAsSrc = useNameAsSrc;
    }

    /**
     * @return the MAX_INBOX_MESSAGES
     */
    public int getMAX_INBOX_MESSAGES() {
        return MAX_INBOX_MESSAGES;
    }

    /**
     * @param MAX_INBOX_MESSAGES the MAX_INBOX_MESSAGES to set
     */
    public synchronized void setMAX_INBOX_MESSAGES(int MAX_INBOX_MESSAGES) {
        this.MAX_INBOX_MESSAGES = MAX_INBOX_MESSAGES;
    }

    /**
     * @return the MAX_OUTBOX_MESSAGES
     */
    public int getMAX_OUTBOX_MESSAGES() {
        return MAX_OUTBOX_MESSAGES;
    }

    /**
     * @param MAX_OUTBOX_MESSAGES the MAX_OUTBOX_MESSAGES to set
     */
    public synchronized void setMAX_OUTBOX_MESSAGES(int MAX_OUTBOX_MESSAGES) {
        this.MAX_OUTBOX_MESSAGES = MAX_OUTBOX_MESSAGES;
    }

    /**
     * @return the autoReconnect
     */
    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    /**
     * @param autoReconnect the autoReconnect to set
     */
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public boolean outboxIsEmpty() {
        return(this.outboxList.empty());
    }
}
