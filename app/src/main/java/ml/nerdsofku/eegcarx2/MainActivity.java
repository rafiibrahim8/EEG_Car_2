package ml.nerdsofku.eegcarx2;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import com.github.pwittchen.neurosky.library.NeuroSky;
import com.github.pwittchen.neurosky.library.exception.BluetoothNotEnabledException;
import com.github.pwittchen.neurosky.library.listener.ExtendedDeviceMessageListener;
import com.github.pwittchen.neurosky.library.message.enums.BrainWave;
import com.github.pwittchen.neurosky.library.message.enums.Signal;
import com.github.pwittchen.neurosky.library.message.enums.State;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.util.Log.d;
import static android.util.Log.e;

public class MainActivity extends AppCompatActivity {

    private final static String LOG_TAG = "NeuroSky";
    private NeuroSky neuroSky;
    private CarController carController;

    @BindView(R.id.tvState0)
    TextView stateMWM;
    @BindView(R.id.tvAttentionLvl0)
    TextView attentionLevel;
    @BindView(R.id.tvSignalQ0)
    TextView signalQuality;
    @BindView(R.id.tvBlinkStrength0)
    TextView blinkStrength;
    @BindView(R.id.briefCarConnection)
    TextView briefCarConnection;
    @BindView(R.id.briefMWMConnection)
    TextView briefMWMConnection;
    @BindView(R.id.signalLayout)
    LinearLayout signalLayout;
    @BindView(R.id.direction)
    ImageView imgDir;
    @BindView(R.id.proximity_layout)
    LinearLayout proximityLayout;
    @BindView(R.id.timer_layout)
    LinearLayout timerLayout;
    @BindView(R.id.fallSwitch)
    Switch fallSwitch;
    @BindView(R.id.proximitySwitch)
    Switch proximitySwitch;
    @BindView(R.id.fireSwitch)
    Switch fireSwitch;
    @BindView(R.id.btnConnectCar0)
    Button btnCar;
    @BindView(R.id.btnConnectMWM0)
    Button btnMWM;
    @BindView(R.id.proximity)
    TextView distance;
    @BindView(R.id.timer)
    TextView timer;

    @BindView(R.id.testBlink)
    Button testBlink;


    public static final String PING_CHAR= "Z";
    public static final char FIRE_ALERT = 'J';
    public static final char FALL_ALERT = 'K';
    public static final char FALL_ENABLE = 'E';
    public static final char FALL_DISABLE = 'D';
    public static final char FIRE_ENABLE = 'X';
    public static final char FIRE_DISABLE = 'Y';
    public static final char PROX_ENABLE = 'H';
    public static final char PROX_DISABLE = 'I';
    public static final char FORWORD = 'F';
    public static final char BACKWORD = 'B';
    public static final char LEFT = 'L';
    public static final char RIGHT = 'R';
    public static final char STOP = 'S';

    private static final int NSMW2 = 1;
    private static final int MAX_PROXIMITY = 1000; //cm
    private static final int MIN_SAFE_DISTANCE = 10;  //cm
    private static final long MIN_FIRE_INTERVAL_SECS = 60;
    private static final int HC05 = 0;
    private long lastFireAlert = 0;
    private Vibrator vibrator;
    private BluetoothAdapter bluetoothAdapter;
    private InputStream btInput;
    private OutputStream btOutput;
    private String[] btDevices;
    private String remoteAddr, remoteName;
    private BluetoothSocket bluetoothSocket;
    private AtomicBoolean[] isConnected;
    private String phoneNumber;
    private final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //uuid for hc-05

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        phoneNumber = getIntent().getStringExtra("eeg_2_ph_number");
        neuroSky = createNeuroSky();
        carController = new CarController(this);
        isConnected = new AtomicBoolean[]{new AtomicBoolean(false), new AtomicBoolean(false)};

        signalLayout.setVisibility(View.GONE);
        proximityLayout.setVisibility(View.INVISIBLE);
        timerLayout.setVisibility(View.INVISIBLE);
        btInput = null;
        btOutput = null;
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (bluetoothAdapter == null) {
            Toast.makeText(this, R.string.blueUnav, Toast.LENGTH_LONG).show();
            return;
        }
        doBluetoothStuffs();
        initBTReceiver();
        registerBroadcasts();
        getPermission();
        initSwitches();
        enableSwitches(false);
        runPingThread();

        testBlink.setVisibility(View.GONE);

        d("PhoneNumber",phoneNumber);
    }
    
    private void toaster(String msg,int duration){
        Intent intent = new Intent("toaster");
        intent.putExtra("msg", msg);
        intent.putExtra("duration",duration);
        this.sendBroadcast(intent);
    }
    private void toaster(String msg){
        toaster(msg,Toast.LENGTH_SHORT);
    }

    private void enableSwitches(boolean b) {
        if(b){
            fallSwitch.setEnabled(true);
            fireSwitch.setEnabled(true);
            proximitySwitch.setEnabled(true);
            return;
        }
        fallSwitch.setChecked(false);
        fallSwitch.setEnabled(false);
        fireSwitch.setChecked(false);
        fireSwitch.setEnabled(false);
        proximitySwitch.setChecked(false);
        proximitySwitch.setEnabled(false);
    }

    private void initSwitches() {
        fallSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked) sendSignalToCar(""+FALL_ENABLE);
            else sendSignalToCar(""+FALL_DISABLE);
        });

        fireSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked) sendSignalToCar(""+FIRE_ENABLE);
            else sendSignalToCar(""+FIRE_DISABLE);
        });

        proximitySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if(isChecked){
                sendSignalToCar(""+PROX_ENABLE);
                proximityLayout.setVisibility(View.VISIBLE);
            }
            else{
                sendSignalToCar(""+PROX_DISABLE);
                proximityLayout.setVisibility(View.INVISIBLE);
            }
        });
    }

    void handleDistance(int distance){
        Intent intent = new Intent("proximity");
        if(distance>MAX_PROXIMITY)
            intent.putExtra("distance", "NIR");
        else
            intent.putExtra("distance", String.valueOf(distance)+"CM");
        if(distance<MIN_SAFE_DISTANCE && carController.getCurrentState() == CarController.STATE_RUNNING && carController.getCurrentPointedDirection()!=CarController.BACKWARD){
            intent.putExtra("alert",true);
            carController.setCurrentState(CarController.STATE_IDLE);
            sendSignalToCar("S");
        }

        else intent.putExtra("alert",false);
        this.sendBroadcast(intent);

    }
    void sendAlert(char type){
        if(type==FALL_ALERT){
            sendSms(phoneNumber,getResources().getString(R.string.fallAlertMsg));
            return;
        }
        if(System.currentTimeMillis()-lastFireAlert > MIN_FIRE_INTERVAL_SECS*1000){
            lastFireAlert = System.currentTimeMillis();
            sendSms(phoneNumber,getResources().getString(R.string.fireAlertMsg));
        }
    }
    void handleRecvChar(char c){
        d("BTRs","T: "+c);
        switch (c){
            case FIRE_ALERT:
                sendAlert(FIRE_ALERT);
                break;
            case FALL_ALERT:
                carController.setCurrentState(CarController.STATE_IDLE);
                sendSignalToCar("S");
                sendAlert(FALL_ALERT);
                break;
            case FALL_ENABLE:
                toaster("Fall alert enabled.");
                d("BTRsE","T: "+c);
                break;
            case FALL_DISABLE:
                toaster("Fall alert disabled.");
                break;
            case FIRE_ENABLE:
                toaster("Fire alert enabled. ");
                break;
            case FIRE_DISABLE:
                toaster("Fire alert disabled. ");
                break;
            case PROX_DISABLE:
                toaster("Proximity alert disabled");
                break;
            case PROX_ENABLE:
                toaster("Proximity alert enabled.");
                break;
            case STOP:
                toaster("Wheelchair is stopped.");
                break;
            case LEFT:
                toaster("Wheelchair is going left.");
                break;
            case RIGHT:
                toaster("Wheelchair is going right.");
                break;
            case FORWORD:
                toaster("Wheelchair is going forword.");
                break;
            case BACKWORD:
                toaster("Wheelchair is going backword.");
                break;

        }
    }
    private void initBTReceiver() {
        new Thread(() -> {
            Looper.prepare();
            String distance = "";
            while (true) {
                try {
                    if (isConnected[HC05].get() && btInput != null && btInput.available() > 0) {


                        char recv = (char)(btInput.read()&0xFF);
                        d("BTRecv","RE: "+recv);
                        if(Character.isDigit(recv)){
                            distance+=recv;
                        }
                        else if(recv == 'P'){
                            handleDistance(Integer.valueOf(distance,10));
                            distance = "";
                        }
                        else{
                            handleRecvChar(recv);
                        }

                    }
                } catch (Exception e) {
                    //e.printStackTrace();
                    d("HHer","JJ");
                }
            }
        }).start();
    }

    private void registerBroadcasts() {

        BroadcastReceiver brArrow = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int uiCode = intent.getIntExtra("arrows", CarController.NONE);
                switch (uiCode) {
                    case CarController.FORWARD:
                        imgDir.setImageDrawable(getResources().getDrawable(R.drawable.ic_arrow_up));
                        break;
                    case CarController.BACKWARD:
                        imgDir.setImageDrawable(getResources().getDrawable(R.drawable.ic_arrow_down));
                        break;
                    case CarController.LEFT:
                        imgDir.setImageDrawable(getResources().getDrawable(R.drawable.ic_arrow_left));
                        break;
                    case CarController.RIGHT:
                        imgDir.setImageDrawable(getResources().getDrawable(R.drawable.ic_arrow_right));
                        break;
                    case CarController.NONE:
                        imgDir.setImageDrawable(getResources().getDrawable(R.drawable.ic_stop_black_24dp));
                        break;
                }
            }
        };

        BroadcastReceiver brTimer = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                timer.setText(intent.getStringExtra("leftTime"));
            }
        };

        BroadcastReceiver brCar = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                sendSignalToCar(intent.getStringExtra("direction"));
            }
        };

        BroadcastReceiver brState = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int state = intent.getIntExtra("state",CarController.STATE_IDLE);
                if(state == CarController.STATE_CIRCLING_ARROWS) timerLayout.setVisibility(View.VISIBLE);
                else timerLayout.setVisibility(View.INVISIBLE);
                if(state == CarController.STATE_RUNNING) imgDir.setColorFilter(Color.GREEN, PorterDuff.Mode.SRC_IN);
                else imgDir.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
            }
        };

        BroadcastReceiver btProximity = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String distanceFromCar = intent.getStringExtra("distance");
                //proximityLayout.setVisibility(View.VISIBLE);
                distance.setText(distanceFromCar);
                if(intent.getBooleanExtra("alert",false)){
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(200);
                    }
                }
            }
        };

        BroadcastReceiver brToaster = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Toast.makeText(MainActivity.this,intent.getStringExtra("msg"),intent.getIntExtra("duration",Toast.LENGTH_SHORT)).show();
            }
        };

        BroadcastReceiver brHC05disconnect = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                initDisconnect();
            }
        };

        registerReceiver(brHC05disconnect,new IntentFilter("eeg_x_disconnect_hc05"));
        registerReceiver(brToaster,new IntentFilter("toaster"));
        registerReceiver(brTimer, new IntentFilter("updateTimer"));
        registerReceiver(brArrow, new IntentFilter("updateArrows"));
        registerReceiver(brCar, new IntentFilter("sendToCar"));
        registerReceiver(btProximity, new IntentFilter("proximity"));
        registerReceiver(brState,new IntentFilter("updateState"));
    }

    @OnClick(R.id.btnConnectCar0)
    void onBtnCarClick() {
        if (bluetoothSocket == null) {
            if (!bluetoothAdapter.isEnabled()) {
                showBTAlert();
                return;
            }

            showBTSelect();
        } else {
            initDisconnect();
        }
    }

    @OnClick(R.id.btnConnectMWM0)
    void onBtnMWClick() {
        if (neuroSky != null && neuroSky.isConnected()) {
            neuroSky.disconnect();
            return;
        }
        try {
            neuroSky.connect();
        } catch (BluetoothNotEnabledException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            d(LOG_TAG, e.getMessage());
        }
    }


    @OnClick(R.id.testBlink)
    void onTestBlink() {
        carController.registerBlink(100);
    }


    @Override
    protected void onResume() {
        super.onResume();
        if (neuroSky != null && neuroSky.isConnected()) {
            neuroSky.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (neuroSky != null && neuroSky.isConnected()) {
            neuroSky.stop();
        }
    }

    @NonNull
    private NeuroSky createNeuroSky() {
        return new NeuroSky(new ExtendedDeviceMessageListener() {
            @Override
            public void onStateChange(State state) {
                handleStateChange(state);
            }

            @Override
            public void onSignalChange(Signal signal) {
                handleSignalChange(signal);
            }

            @Override
            public void onBrainWavesChange(Set<BrainWave> brainWaves) {
                handleBrainWavesChange(brainWaves);
            }
        });
    }

    private boolean sendSignalToCar(String signal) {
        if (!isConnected[HC05].get()) {
            Toast.makeText(this, R.string.carNotConnMsg, Toast.LENGTH_LONG).show();
            return false;
        }
        try {
            btOutput.write(signal.getBytes());
            d("SignalCar",signal);
            return true;
        } catch (Exception ex) {
            Toast.makeText(this, R.string.errOccered, Toast.LENGTH_LONG).show();
            return false;
        }
    }

    private void handleStateChange(final State state) {
        if (neuroSky == null) {
            Toast.makeText(this, "Something went wrong.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!state.equals(State.CONNECTED)) {
            btnMWM.setText(R.string.connectMWM);
            isConnected[NSMW2].set(false);
        }

        switch (state) {
            case CONNECTED:
                briefMWMConnection.setText(R.string.mwmConnectedHint);
                briefMWMConnection.setTextColor(Color.GREEN);

                btnMWM.setText(R.string.conOK);
                isConnected[NSMW2].set(true);
                neuroSky.start();
                break;
            case NOT_FOUND:
                briefMWMConnection.setText(R.string.mwmNotFound);
                briefMWMConnection.setTextColor(Color.RED);
                break;
            case CONNECTING:
                briefMWMConnection.setText(R.string.connectingMWM);
                briefMWMConnection.setTextColor(Color.rgb(255, 150, 0));
                break;
            case DISCONNECTED:
                briefMWMConnection.setText(R.string.mwmNotConnectedHint);
                briefMWMConnection.setTextColor(Color.RED);
                break;
            case NOT_PAIRED:
                briefMWMConnection.setText(R.string.mwmNotPairedHint);
                briefMWMConnection.setTextColor(Color.BLUE);
                break;
        }


        stateMWM.setText(state.toString());
        d(LOG_TAG, state.toString());
    }

    private void handleSignalChange(final Signal signal) {
        switch (signal) {
            case ATTENTION:
                attentionLevel.setText(String.valueOf(signal.getValue()) + "%");
                carController.registerAttention(signal.getValue());
                break;
            case MEDITATION:
                //tvMeditation.setText(getFormattedMessage("meditation: %d", signal));
                break;
            case BLINK:
                blinkStrength.setText(String.valueOf(signal.getValue()) + "%");
                carController.registerBlink(signal.getValue());
                break;
            case POOR_SIGNAL:
                // seems something is wrong with the MWM Signal Quality library. Will code it later.
                break;
        }

        d(LOG_TAG, String.format("SigX: %s: %d", signal.toString(), signal.getValue()));
    }

    private void handleBrainWavesChange(final Set<BrainWave> brainWaves) {
        for (BrainWave brainWave : brainWaves) {
            d(LOG_TAG, String.format("WebX: %s: %d", brainWave.toString(), brainWave.getValue()));
        }
    }

    private void showBTAlert() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setPositiveButton("Yes", (dialog, which) -> doBluetoothStuffs());
        alertDialog.setNegativeButton("No", null);
        alertDialog.setMessage(R.string.blueNotOnPromt);
    }

    private void doBluetoothStuffs() {
        if (!bluetoothAdapter.isEnabled()) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
        }

        if (!bluetoothAdapter.isEnabled()) {
            showBTAlert();
            //Log.e("dsff","fewf");
            return;
        }

        Set<BluetoothDevice> pairedDevices;

        pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() < 1) {
            Toast.makeText(this, R.string.noPaired, Toast.LENGTH_LONG).show();
        }

        ArrayList<String> deviceNames = new ArrayList<>();
        for (BluetoothDevice dev : pairedDevices) {
            deviceNames.add(dev.getName() + "\n" + dev.getAddress());
        }
        Object[] objects = deviceNames.toArray();
        btDevices = Arrays.copyOf(objects, objects.length, String[].class);

    }

    private void showBTSelect() {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.bt_select_dialog_layout);
        TextView dialogHead = dialog.findViewById(R.id.dialog_head);
        ListView listView = dialog.findViewById(R.id.dialog_listView);
        ArrayAdapter arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_1, btDevices);
        listView.setOnItemClickListener((parent, view, i, id) -> {
            dialog.dismiss();
            connectBluetooth(btDevices[i].substring(0, btDevices[i].length() - 18), btDevices[i].substring(btDevices[i].length() - 17));
        });
        listView.setAdapter(arrayAdapter);
        dialogHead.setText(R.string.selectCar);
        dialog.show();
    }

    private void connectBluetooth(String name, String addr) {
        remoteAddr = addr;
        remoteName = name;
        new ConnectBT().execute();
    }

    public static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            d("ERROR", "Thread Sleep");
        }
    }

    private void getPermission(){
        if (!hasSmsPermission()) {

            // Permission is not granted
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

                // Show an explanation to the user *asynchronously* -- don't block
                // this thread waiting for the user's response! After the user
                // sees the explanation, try again to request the permission.

            } else {

                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.SEND_SMS},
                        1);

                // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                // app-defined int constant. The callback method gets the
                // result of the request.
            }
        } else {
            ///mkAppDir();
        }
    }

    private boolean hasSmsPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
    }

    private void sendSms(String number, String text){
        getPermission();
        if(hasSmsPermission()){
            SmsManager.getDefault().sendTextMessage(number,null,text,null,null);
            Toast.makeText(this,"SMS Sent",Toast.LENGTH_SHORT).show();
        }
        else{
            Toast.makeText(this,"Unable to SMS Sent. Please grant permission.",Toast.LENGTH_LONG).show();
            getPermission();
        }
    }

    private void runPingThread(){
        new Thread(()->{
            Looper.prepare();
            int failCount = 0;
            boolean delayed = false;
            int dt = 0;
            while (true){
                if(btOutput!=null){
                    if(sendSignalToCar(PING_CHAR))
                        failCount=0;
                    else failCount++;

                    sleep(300);
                    if(failCount>2){
                        toaster("Wheelchair connection lost.");
                        broadcastDisconnect();
                    }
                }
            }
        }).start();
    }

    private void broadcastDisconnect() {
        Intent intent = new Intent("eeg_x_disconnect_hc05");
        this.sendBroadcast(intent);
    }

    private void initDisconnect() {
        d("InitDisc","In");
        try {
            isConnected[HC05].set(false);
            btOutput = null;
            btInput = null;

            briefCarConnection.setText(R.string.carNotConnectedHint);
            briefCarConnection.setTextColor(Color.RED);
            btnCar.setText(R.string.connectCar);
            carController.setCurrentState(CarController.STATE_IDLE);
            enableSwitches(false);

            bluetoothSocket.close();
            bluetoothSocket = null;
        } catch (Exception e) {
        }
    }


    //this class is for connecting HC-05 only
    private class ConnectBT extends AsyncTask<Void, Void, Void> {

        private boolean success;
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            success = true;

            progressDialog = ProgressDialog.show(MainActivity.this, "Connecting...", "Please Wait...");
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (bluetoothSocket == null || !isConnected[HC05].get()) {
                try {
                    BluetoothDevice remoteDev = bluetoothAdapter.getRemoteDevice(remoteAddr);
                    bluetoothSocket = remoteDev.createInsecureRfcommSocketToServiceRecord(uuid);
                    //bluetoothAdapter.cancelDiscovery();
                    bluetoothSocket.connect();
                    //btInput = bluetoothSocket.getInputStream();
                } catch (Exception ex) {
                    d("EXE",ex.getLocalizedMessage());
                    success = false;
                }

            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressDialog.dismiss();
            if (!success) {
                Toast.makeText(getApplicationContext(), R.string.conFailed, Toast.LENGTH_LONG).show();
            } else {

                isConnected[HC05].set(true);
                enableSwitches(true);
                btnCar.setText(R.string.conOK);
                carController.setCarConnected(true);
                briefCarConnection.setTextColor(Color.GREEN);
                briefCarConnection.setText(R.string.carConnectedHint);
                Toast.makeText(getApplicationContext(), R.string.conOK, Toast.LENGTH_SHORT).show();
            }
            progressDialog.dismiss();
            try {
                btInput = bluetoothSocket.getInputStream();
                btOutput = bluetoothSocket.getOutputStream();
                if(btInput == null){
                    e("BTSoc","Null");
                }
            } catch (IOException e) {
                e("BTSoc","Failed");
            }

        }
    }

    @Override
    public void onBackPressed() {
        finish();
        System.exit(0);
    }
}
