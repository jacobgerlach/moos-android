package MOOS;

import java.io.IOException;
import java.util.ArrayList;

/**
 * Java implementation of the CMOOSApp idiom
 * Created by jacob on 6/29/15.
 */
public abstract class MOOSApp implements Runnable {

    private MOOSCommClient mComms;
    private ArrayList<MOOSMsg> mNewMail;

    public MOOSApp(){

        //Configure comms client name
    }

    @Override
    public void run(){
        try {
            onStartupPrivate();
            onStartup();
            connect();
        }
        catch (Exception e){
            throw e;
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
