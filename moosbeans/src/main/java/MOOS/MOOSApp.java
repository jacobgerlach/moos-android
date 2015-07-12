package MOOS;

import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Java implementation of the CMOOSApp idiom
 * Created by jacob on 6/29/15.
 */
public abstract class MOOSApp implements Runnable {

    private static final String TAG = "MOOS";

    private String mName;
    private String mServerHost;
    private int mServerPort;

    private double mIterationStart;

    protected double mAppTick = 5;
    private static final double MAX_APPTICK = 100;
    private long mPeriod;

    private MOOSCommClient mComms;
    private ArrayList<MOOSMsg> mNewMail;

    private boolean mDiscReq = false;


    public MOOSApp(){
        mPeriod = (long) (1000/mAppTick);
    }

    public final void setName(String name) throws Exception {
        if(name.contains(" ")){
            throw(new Exception("Process name can't contain whitespace"));
        }

        mName=name;
    }

    public void setAppTick(double t) {
            if(t<=0 || t>MAX_APPTICK){
                return;
            }

            this.mAppTick = t;
            this.mPeriod = (long) (1000/t);
    }

    public synchronized void requestDisconnect() {
        mDiscReq = true;
    }

    public synchronized void clearDisconnectRequest() {
        mDiscReq=false;
    }

    private synchronized boolean disconnectRequested() {
        return mDiscReq;
    }

    public final void configureServer(String host, int port) throws Exception {

        //According to http://www.jguru.com/faq/view.jsp?EID=17521, ports below 1025
        //require root access.
        if(port < 1025 || port > 65535) {
            String msg = port + " is not a valid port";
            throw(new Exception(msg));
        }

        //I could try to do much fancier hostname validation, but not today...
        if( host.contains(" ")) {
            throw (new Exception("Hostname can't contain whitespace"));
        }

        //Everything's good
        mServerHost=host;
        mServerPort=port;

    }

    @Override
    public void run(){
        Log.d(TAG, "Starting run()");
        try {
            onStartupPrivate();
            onStartup();
            connect();
        }
        catch (Exception e){
            Log.d(TAG, "Sending problem to UI");
            sendProblemToUI(e.getMessage());
            Log.d(TAG, "Problem sent to UI. Returning from run()");
            return;
        }

        //Now we are conncted, so further exceptions should disconnect
        try{
            onConnectToServerPrivate();
            onConnectToServer();
            registerVariables();
        }
        catch (Exception e){
            disconnect();
            sendProblemToUI(e.getMessage());
            return;
        }

        try{
            while(!disconnectRequested()){
                mIterationStart = System.currentTimeMillis();
                onNewMailPrivate();
                onNewMail(mNewMail);
                iterate();
                sleepAsRequired();
            }
        }
        catch(Exception e){
            mUIListener.reportMOOSProblem(e.getMessage());
            return;
        }
            finally {
            Log.d(TAG,"Disconnecting");
            disconnect();
        }


    }

    private void sleepAsRequired() {
        long iterationEnd = System.currentTimeMillis();

        long gap = (long) (mPeriod - (iterationEnd-mIterationStart));

        if(gap<=0){
            //The iterate loop took to long, no time to sleep
            return;
        }
        else{
            try {
                Thread.sleep(gap);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    private void onStartupPrivate() throws Exception {
        //Need to check here that the host/port have been configured
        //before instantiating the Client
        if(mServerHost == null){
            throw(new Exception("Server host must be configured!"));
        }
        mComms=new MOOSCommClient(mServerHost,mServerPort);

        if(mName != null){
            mComms.setName(mName);
        }

    }

    protected void onStartup() {

    }

    //Exceptions thrown in tryToConnect caught by try block in run()
    private void connect() throws Exception {
        Log.d(TAG,"Connecting");

        try {
            mComms.tryToConnect();
        } catch (IOException e) {
            Log.d(TAG,e.getMessage());
            throw(new Exception("Couldn't connect to a MOOSDB at " + mServerHost + ":" + mServerPort));
        }

        sendInfoToUI("Connected to a MOOSDB at " + mServerHost);
    }

    private void onConnectToServerPrivate() {

    }

    public void onConnectToServer() {

    }

    private void registerVariables() {

    }

    private void onNewMailPrivate() {
        mNewMail = mComms.getNewMsgs();
    }

    public abstract void onNewMail (ArrayList<MOOSMsg> mail);

    public abstract void iterate();

    private void disconnect() {

        mComms.disconnectFromServer();
        sendInfoToUI("Disconnected from MOOSDB");
    }

    //I am completely unsure about the access specfiers for either the interface or its functions
    //The whole point of this interface is that the Activity can implement it, so I guess public.
    public interface MOOSUIListener {
        void reportMOOSProblem(String problem);
        void reportMOOSInfo(String info);
    }

    protected MOOSUIListener mUIListener;

    public void setIssueListener(MOOSUIListener l) {
        this.mUIListener = l;
    }

    protected void sendInfoToUI(String info){
        if(mUIListener != null){
            mUIListener.reportMOOSInfo(info);
        }
    }

    protected void sendProblemToUI(String problem){
        if(mUIListener != null){
            mUIListener.reportMOOSProblem(problem);
        }
    }
}
