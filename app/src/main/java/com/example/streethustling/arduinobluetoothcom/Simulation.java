package com.example.streethustling.arduinobluetoothcom;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.AsyncTask;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.UUID;

public class Simulation extends AppCompatActivity {

    Button start;
    Button disconnect;
    Button capture;
    TextView response;
    BluetoothAdapter myBluetooth = null;
    private ProgressDialog progress;
    BluetoothSocket btSocket = null;
    private boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private String deviceMAC = null;
    ConnectBT connection ;
    Timer timer;
    private OutputStream outStream = null;
    private InputStream in = null;
    private Handler handler = new Handler();
    BluetoothSocketListener bsl = null;
    Thread messageListener = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_simulation);


        start = (Button)findViewById(R.id.startBtn);
        disconnect = (Button)findViewById(R.id.disBtn);
        response = (TextView)findViewById(R.id.arduResponseTxt);
        capture = (Button)findViewById(R.id.captureBtn);

        //receive the address of the bluetooth device
        Bundle extras = getIntent().getExtras();
        if(extras != null){
            deviceMAC = (String) extras.getString("EXTRA_ADDRESS");
            connection = new ConnectBT();
            connection.execute();
        }

        start.setOnClickListener(new View.OnClickListener() {


            @Override
            public void onClick(View v) {

                startSimulation();
            }
        });


        capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startCamera();
            }
        });

        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Disconnect();
            }
        });
    }


    private void startCamera(){
            // Make an intent to start next activity.
            Intent i = new Intent(Simulation.this, CameraActivity.class);
            startActivity(i);
    }

    private void stopSimulation() throws InterruptedException {
        if (btSocket!=null)
        {
            try
            {
                outStream.write("0".getBytes());
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }


    private void startSimulation()
    {
        if (btSocket!=null)
        {
            try
            {
                outStream.write("1".getBytes());

            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }


    private void readFromArduino(){
        if (btSocket!=null)
        {
            try
            {
                byte[] buffer = new byte[1024];
                int bytes;

                bytes = in.read(buffer);
                String readMessage = new String(buffer, 0, bytes);
                System.out.println("From Arduino: "+ readMessage);
                response.append("Message: "+ readMessage + " \n");
            }
            catch (IOException e)
            {
                msg("Error");
            }
        }
    }

    private class MessagePoster implements Runnable {
        private TextView textView;
        private String message;

        public MessagePoster(TextView textView, String message) {
            this.textView = textView;
            this.message = message;
        }

        public void run() {
            textView.setText(message + "\n");
            if(message.matches("ON") || message.matches(" ON") || message.matches(" ON ")){
                startCamera();
            }

        }

    }

    private class BluetoothSocketListener implements Runnable {

        private BluetoothSocket socket;
        private TextView textView;
        private Handler handler;

        public BluetoothSocketListener(BluetoothSocket socket,
                                       Handler handler, TextView textView) {
            this.socket = socket;
            this.textView = textView;
            this.handler = handler;
        }

        public void run() {
            int bufferSize = 1024;
            byte[] buffer = new byte[bufferSize];
            try {
                InputStream in = socket.getInputStream();
                int bytesRead = -1;
                String message = "";
                while (true) {
                    message = "";
                    bytesRead = in.read(buffer);
                    if (bytesRead != -1) {
                        while ((bytesRead==bufferSize)&&(buffer[bufferSize-1] != 0)) {
                            message = message + new String(buffer, 0, bytesRead);
                            bytesRead = in.read(buffer);
                        }
                        message = message + new String(buffer, 0, bytesRead - 1);
                        handler.post(new MessagePoster(textView, message));
                        socket.getInputStream();
                    }
                }
            } catch (IOException e) {
                Log.d("BLUETOOTH_COMMS", e.getMessage());
            }
        }
    }



    private class ConnectBT extends AsyncTask<Void, Void, Void>  // UI thread
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute()
        {
            progress = ProgressDialog.show(Simulation.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try
            {
                if (btSocket == null || !isBtConnected)
                {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice dispositivo = myBluetooth.getRemoteDevice(deviceMAC);//connects to the device's address and checks if it's available
                    btSocket = dispositivo.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RFCOMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                    outStream = btSocket.getOutputStream();
                    in = btSocket.getInputStream();
                }
            }
            catch (IOException e)
            {
                ConnectSuccess = false;//if the try failed, you can check the exception here
            }
            return null;
        }
        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess)
            {
                Toast.makeText(getApplicationContext(), "Connection Failed. Is it a SPP Bluetooth? Try again.", Toast.LENGTH_LONG).show();
                response.append("Connection Failed \n");
                finish();
            }
            else
            {
                Toast.makeText(getApplicationContext(), "Connected.", Toast.LENGTH_LONG).show();
                response.append("Connected \n");
                isBtConnected = true;
                bsl = new BluetoothSocketListener(btSocket, handler, response);
                messageListener = new Thread(bsl);
                messageListener.start();
            }
            progress.dismiss();
        }
    }


    private void msg(String s)
    {
        Toast.makeText(getApplicationContext(),s,Toast.LENGTH_LONG).show();
    }


    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
            }
            catch (IOException e)
            { msg("Error");}
        }
        finish(); //return to the first layout
    }
}
