package MOOS;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Java implementation of the CMOOSApp idiom
 * Created by jacob on 6/29/15.
 */
public abstract class MOOSApp implements Runnable {

    private String mName;
    private String mServerHost;
    private int mServerPort;

    private MOOSCommClient mComms;
    private ArrayList<MOOSMsg> mNewMail;

    public MOOSApp(){
    }

    public final void setName(String name) throws Exception {
        if(name.contains(" ")){
            throw(new Exception("Process name can't contain whitespace"));
        }

        mName=name;
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
        try {
            onStartupPrivate();
            onStartup();
            connect();
        }
        catch (Exception e){
            //throw e;
        }

        //Now we are conncted, so further exceptions should disconnect

        try{
            onConnectToServerPrivate();
            onConnectToServer();
            registerVariables();
        }
        catch (Exception e){
            disconnect();
            throw e;
        }

        try{
            while(true){
                onNewMailPrivate();
                onNewMail(mNewMail);
                iterate();
            }
        }
        catch(Exception e){

        }
        finally {
            disconnect();
        }


    }


    private void onStartupPrivate() {

    }

    protected void onStartup() {

    }

    //Exceptions thrown in tryToConnect caught by try block in run()
    private void connect() throws IOException {
        //Need to check here that the host/port have been configured before trying to connect
        if(mServerHost == null)

        mComms.tryToConnect();
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
    }

}
