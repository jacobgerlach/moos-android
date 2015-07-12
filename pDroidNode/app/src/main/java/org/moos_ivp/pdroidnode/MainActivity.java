package org.moos_ivp.pdroidnode;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

import MOOS.MOOSApp;
import MOOS.MOOSMsg;


public class MainActivity extends ActionBarActivity
        implements MOOSApp.MOOSUIListener, View.OnClickListener {

    private final static String TAG = "pDroidNode";
    private pDroidNode mMOOSApp = new pDroidNode();
    private Thread mMoosThread = new Thread(mMOOSApp);


    Button pokeButton;
    TextView resultView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pokeButton = (Button) findViewById(R.id.pokeButton);
        resultView = (TextView) findViewById(R.id.pokeResults);

        mMOOSApp.setIssueListener(this);
        pokeButton.setOnClickListener(this);
    }

    @Override
    protected void onStop(){
        Log.d(TAG,"onStop");
        if(mMOOSApp != null){
            mMOOSApp.requestDisconnect();
        }
        super.onStop();
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
        Log.d(TAG,"onClick");
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

        try {
            int iport=Integer.parseInt(port);
            mMOOSApp.configureServer(server,iport);
            mMOOSApp.setName("Test");
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG,"Launching");
        launchTheMOOS();

    }

    protected void launchTheMOOS(){

        //CONTINUE WORK HERE
        if(mMoosThread.getState() != Thread.State.NEW){
            mMoosThread = new Thread(mMOOSApp);
        }

        mMOOSApp.clearDisconnectRequest();
        mMoosThread.start();
    }

    //Note: see http://stackoverflow.com/questions/4732544/why-are-only-final-variables-accessible-in-anonymous-class
    @Override
    public void reportMOOSProblem(final String issue) {
        Log.d(TAG, "About to run alertdialog on UI thread");

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //Trickiness. A plain "this" here refers to the anonymous Runnable inner class.
                    //There is some risk of memory leaks here that I don't understand in the least.
                    //See: http://stackoverflow.com/q/5796611/3371623
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("MOOS Problem")
                            .setMessage(issue)

                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    //Stop the moos thread?
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                }
            });
        }

    @Override
    public void reportMOOSInfo(final String info) {
//        Context context = getApplicationContext();
//        CharSequence text = "Hello toast!";
//        int duration = Toast.LENGTH_SHORT;
//
//        Toast toast = Toast.makeText(context, text, duration);
//        toast.show();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(),info,Toast.LENGTH_LONG).show();
            }
        });
    }

    //Create moos app inner class
    private class pDroidNode extends MOOSApp {

        @Override
        public void iterate() {
            Log.d(TAG,"Iterating");
        }

        @Override
        public void onNewMail(ArrayList<MOOSMsg> mail){
            Log.d(TAG,"Handling mail");
        }
    }
}
