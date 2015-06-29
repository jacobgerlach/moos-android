package org.moos_ivp.udroidpoke;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


import java.util.ArrayList;

import MOOS.MOOSCommClient;
import MOOS.MOOSMsg;



public class MainActivity extends Activity
        implements View.OnClickListener {

    private static final String TAG="uDroidPoke";

    Button pokeButton;
    TextView resultView;

    //Configure MOOS Event Server
//    MOOSEventServer m_server = new MOOSEventServer();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Get Members
        pokeButton = (Button) findViewById(R.id.pokeButton);
        resultView = (TextView) findViewById(R.id.pokeResults);

        //Configure Listeners
        pokeButton.setOnClickListener(this);

        // Set the MOOS server address and port.
//        m_server.setHostname("10.0.0.235");
//        m_server.setPort(9000);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        //Clear any old error messages
        resultView.setText("");

        //Hide Keyboard. From: http://stackoverflow.com/a/7696791/3371623
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager inputManager = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }

        EditText etVar, etVal, etServer, etPort;
        etVar = (EditText) findViewById(R.id.MOOSVar);
        etVal = (EditText) findViewById(R.id.MOOSVarVal);
        etServer = (EditText) findViewById(R.id.MOOSDBIP);
        etPort = (EditText) findViewById(R.id.MOOSDBPort);

        String var, val, server, port, poke;

        var = etVar.getText().toString();
        val = etVal.getText().toString();
        server = etServer.getText().toString();
        port = etPort.getText().toString();
        int iport;

        //Do all input checking here. Any problems thrown as an exception and then displayed in red
        try {
            if (port.isEmpty() || var.isEmpty() || val.isEmpty() || server.isEmpty()){
                throw(new Exception("All fields must be provided."));
            }
            try {
                iport = Integer.parseInt(port);
                //According to http://www.jguru.com/faq/view.jsp?EID=17521, ports below 1025
                //require root access.
                if (iport < 1025 || iport > 65535) {
                    //Not really a number format exception, but allows handling it with the
                    // same catch block...
                    throw (new NumberFormatException());
                }
            } catch (NumberFormatException e) {
                String msg = port + " is not a valid port";
                throw(new Exception(msg));
            }


            if (var.contains(" ")) {
                throw(new Exception("Variable name contains whitespace"));
            }

        } catch (Exception e){
            //See: http://www.chrisumbel.com/article/android_textview_rich_text_spannablestring
            SpannableString err = new SpannableString(e.getMessage());
            err.setSpan(new ForegroundColorSpan(Color.RED), 0, err.length(), 0);
            resultView.setText(err, TextView.BufferType.SPANNABLE);
            return;
        }

//        poke = "Poking " + server + ":" + port + " with " + var + "=" + val;

        resultView.append("Trying to connect to " + server + ":" + port + "...");

        new sendPoke(server, iport, var, val).execute();
    }


    private class sendPoke extends AsyncTask<Void, Void, Boolean> {
        protected String m_srv;
        protected int m_port;
        protected String m_MOOSVar;
        protected String m_VarValue;
        protected String m_prevVal;
        MOOSCommClient client;
        int m_period;
//        MOOSEventServer moossrv = new MOOSEventServer();


        public sendPoke(String srv, int port, String MOOSVar, String VarValue) {
            this.m_srv = srv;
            this.m_port = port;
            this.m_MOOSVar = MOOSVar;
            this.m_VarValue = VarValue;

            client = new MOOSCommClient(m_srv, m_port);
        }

        @Override
        protected Boolean doInBackground(Void... params) {

            try {
                client.setName("uDroidPoke");
                client.setVerbose(true);
                m_period = (int) (1000/client.getFundamentalFrequency());

                client.tryToConnect();

//            moossrv.setEnable(true);
//            Thread.sleep(2000);

            } catch (Exception e) {
                e.printStackTrace();
                return (false);
            }


            try {
                //Register for the var and wait one apptick to see if it's in the DB
                client.register(m_MOOSVar, 0);
                Thread.sleep(3*m_period);
                m_prevVal="n/a";
                ArrayList<MOOSMsg> mail = client.getNewMsgs();
                Log.d(TAG, String.format("%d new messages",mail.size()));
                for (MOOSMsg msg : mail) {
                    String log = "Msg key: " + msg.getKey();
                    Log.d(TAG,log);
                    Log.d(TAG,msg.strTrace());
                    if(msg.getKey().equals(m_MOOSVar)) {
                        if (msg.isString()) {
                            m_prevVal = msg.getStringData();
                        } else if (msg.isDouble()) {
                            m_prevVal = String.format("%.5f", msg.getDoubleData());
                        } else {
                            m_prevVal = "binary data";
                        }
                    }
                }

                client.notify(m_MOOSVar, m_VarValue, MOOSMsg.moosTimeNow());

                //Since mail is sent from the outbox on a separate thread, wait for at least
                //an AppTick to be sure it had a chance to go. Better would probably be to monitor
                //the outbox and wait until it's empty
                    while(!client.outboxIsEmpty()) {
                        try {
                            Log.d(TAG, "Outbox still not empty.");

                            Thread.sleep(m_period);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            catch (Exception e) {
                    //This is intended to catch exceptions thrown while emptying the outbox,
                    //but those are caught within the Comms Client and won't make it up this far.
                    //TODO: extend the Comms Client with listeners so that the UI can react to
                    //those problems? Alternatively, just leave that in logcat...
                    e.printStackTrace();
                    return (false);
                }
            finally {
                client.setEnable(false);
            }

            return (true);
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                resultView.append("Success\n");
                resultView.append(String.format("Value before poking: %s\n", m_prevVal));
            } else {
                resultView.append("Failure");
            }
        }
    }
}