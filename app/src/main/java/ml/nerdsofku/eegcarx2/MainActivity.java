package ml.nerdsofku.eegcarx2;

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
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static android.util.Log.d;

public class MainActivity extends AppCompatActivity {

    private final static String LOG_TAG = "NeuroSky";
    private NeuroSky neuroSky;
    private CarController carController;

    @BindView(R.id.tvState0) TextView stateMWM;
    @BindView(R.id.tvAttentionLvl0) TextView attentionLevel;
    @BindView(R.id.tvSignalQ0) TextView signalQuality;
    @BindView(R.id.tvBlinkStrength0) TextView blinkStrength;
    @BindView(R.id.briefCarConnection) TextView briefCarConnection;
    @BindView(R.id.briefMWMConnection) TextView briefMWMConnection;
    @BindView(R.id.signalLayout) LinearLayout signalLayout;


    @BindView(R.id.btnConnectCar0) Button btnCar;
    @BindView(R.id.btnConnectMWM0) Button btnMWM;

    @BindView(R.id.imgDown) ImageView imgBackward;
    @BindView(R.id.imgUP) ImageView imgForward;
    @BindView(R.id.imgLeft) ImageView imgLeft;
    @BindView(R.id.imgRight) ImageView imgRight;


    private static final int HC05 = 0;
    private static final int NSMW2 = 1;
    private BluetoothAdapter bluetoothAdapter;
    private String[] btDevices;
    private String remoteAddr,remoteName;
    private BluetoothSocket bluetoothSocket;
    private boolean[] isConnected;
    private final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //uuid for hc-05

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        neuroSky = createNeuroSky();
        carController = new CarController(this);
        isConnected = new boolean[]{false,false};

        signalLayout.setVisibility(View.GONE);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(bluetoothAdapter == null){
            Toast.makeText(this,R.string.blueUnav,Toast.LENGTH_LONG).show();
            return;
        }
        doBluetoothStuffs();
        registerBroadcasts();
    }

    private void registerBroadcasts() {
        BroadcastReceiver brArrow = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int uiCode = intent.getIntExtra("arrows",CarController.BLANK);
                if(uiCode == CarController.STATE_IDLE){
                    setArrowsVisibility(View.VISIBLE);
                    return;
                }
                setArrowsVisibility(View.INVISIBLE);
                switch (uiCode){
                    case CarController.FORWARD:
                        imgForward.setVisibility(View.VISIBLE);
                        break;
                    case CarController.BACKWARD:
                        imgBackward.setVisibility(View.VISIBLE);
                        break;
                    case CarController.LEFT:
                        imgLeft.setVisibility(View.VISIBLE);
                        break;
                    case CarController.RIGHT:
                        imgRight.setVisibility(View.VISIBLE);
                        break;
                }
            }
        };

        BroadcastReceiver brCar = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                sendSignalToCar(intent.getStringExtra("direction"));
            }
        };

        registerReceiver(brArrow,new IntentFilter("updateUI"));
        registerReceiver(brCar,new IntentFilter("sendToCar"));
    }

    @OnClick(R.id.btnConnectCar0) void onBtnCarClick(){
        if(bluetoothSocket == null){
            if(!bluetoothAdapter.isEnabled()){
                showBTAlert();
                return;
            }

            showBTSelect();
        }
        else{
            try {bluetoothSocket.close();
                bluetoothSocket=null;
                isConnected[HC05] = false;
                briefCarConnection.setText(R.string.carNotConnectedHint);
                briefCarConnection.setTextColor(Color.RED);
                btnCar.setText(R.string.connectCar);
            } catch (IOException e) {}
        }
    }

    @OnClick(R.id.btnConnectMWM0) void onBtnMWClick(){
        if(neuroSky!=null && neuroSky.isConnected()){
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

    private void setArrowsVisibility(int visibility) {
        imgBackward.setVisibility(visibility);
        imgForward.setVisibility(visibility);
        imgRight.setVisibility(visibility);
        imgLeft.setVisibility(visibility);
    }

    @Override protected void onResume() {
        super.onResume();
        if (neuroSky != null && neuroSky.isConnected()) {
            neuroSky.start();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        if (neuroSky != null && neuroSky.isConnected()) {
            neuroSky.stop();
        }
    }

    @NonNull private NeuroSky createNeuroSky() {
        return new NeuroSky(new ExtendedDeviceMessageListener() {
            @Override public void onStateChange(State state) {
                handleStateChange(state);
            }

            @Override public void onSignalChange(Signal signal) {
                handleSignalChange(signal);
            }

            @Override public void onBrainWavesChange(Set<BrainWave> brainWaves) {
                handleBrainWavesChange(brainWaves);
            }
        });
    }

    private void sendSignalToCar(String signal) {
        if(!isConnected[HC05]){
            Toast.makeText(this,R.string.carNotConnMsg,Toast.LENGTH_LONG).show();
            return;
        }
        try{
            bluetoothSocket.getOutputStream().write(signal.getBytes());
        } catch (Exception ex){
            Toast.makeText(this,R.string.errOccered,Toast.LENGTH_LONG).show();
        }
    }

    private void handleStateChange(final State state) {
        if(neuroSky == null){
            Toast.makeText(this,"Something went wrong.",Toast.LENGTH_LONG).show();
            return;
        }
        if(!state.equals(State.CONNECTED)){
            btnMWM.setText(R.string.connectMWM);
            isConnected[NSMW2] = false;
        }

        switch (state){
            case CONNECTED:
                briefMWMConnection.setText(R.string.mwmConnectedHint);
                briefMWMConnection.setTextColor(Color.GREEN);

                btnMWM.setText(R.string.conOK);
                isConnected[NSMW2] = true;
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
                attentionLevel.setText(String.valueOf(signal.getValue())+"%");
                carController.registerAttention(signal.getValue());
                break;
            case MEDITATION:
                //tvMeditation.setText(getFormattedMessage("meditation: %d", signal));
                break;
            case BLINK:
                blinkStrength.setText(String.valueOf(signal.getValue())+"%");
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

    private void showBTAlert(){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(this);
        alertDialog.setPositiveButton("Yes", (dialog, which) -> doBluetoothStuffs());
        alertDialog.setNegativeButton("No",null);
        alertDialog.setMessage(R.string.blueNotOnPromt);
    }

    private void doBluetoothStuffs() {
        if ( !bluetoothAdapter.isEnabled() ) {
            startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 1);
        }

        if(!bluetoothAdapter.isEnabled()){
            showBTAlert();
            //Log.e("dsff","fewf");
            return;
        }

        Set<BluetoothDevice> pairedDevices;

        pairedDevices = bluetoothAdapter.getBondedDevices();

        if(pairedDevices.size() <1){
            Toast.makeText(this, R.string.noPaired,Toast.LENGTH_LONG).show();
        }

        ArrayList<String> deviceNames = new ArrayList<>();
        for(BluetoothDevice dev:pairedDevices){
            deviceNames.add(dev.getName()+"\n"+dev.getAddress());
        }
        Object[] objects = deviceNames.toArray();
        btDevices = Arrays.copyOf(objects,objects.length,String[].class);

    }

    private void showBTSelect() {
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.bt_select_dialog_layout);
        TextView dialogHead = dialog.findViewById(R.id.dialog_head);
        ListView listView = dialog.findViewById(R.id.dialog_listView);
        ArrayAdapter arrayAdapter = new ArrayAdapter(this,android.R.layout.simple_list_item_1,btDevices);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int i, long id) {
                dialog.dismiss();
                connectBluetooth(btDevices[i].substring(0,btDevices[i].length()-18),btDevices[i].substring(btDevices[i].length()-17));
            }
        });
        listView.setAdapter(arrayAdapter);
        dialogHead.setText(R.string.selectCar);
        dialog.show();
    }

    private void connectBluetooth(String name,String addr) {
        remoteAddr= addr;
        remoteName = name;
        new ConnectBT().execute();
    }

    //this class is for connecting HC-05 only
    private class ConnectBT extends AsyncTask<Void,Void,Void> {

        private boolean success;
        private ProgressDialog progressDialog;
        @Override
        protected void onPreExecute() {
            success = true;

            progressDialog= ProgressDialog.show(MainActivity.this,"Connecting...","Please Wait...");
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if(bluetoothSocket ==null || !isConnected[HC05]){
                try{
                    BluetoothDevice remoteDev = bluetoothAdapter.getRemoteDevice(remoteAddr);
                    bluetoothSocket = remoteDev.createInsecureRfcommSocketToServiceRecord(uuid);
                    //bluetoothAdapter.cancelDiscovery();
                    bluetoothSocket.connect();
                }catch (Exception ex){
                    success = false;
                }

            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            progressDialog.dismiss();
            if(!success){
                Toast.makeText(getApplicationContext(),R.string.conFailed,Toast.LENGTH_LONG).show();
            }
            else{
                isConnected[HC05] = true;
                btnCar.setText(R.string.conOK);
                carController.setCarConnected(true);
                briefCarConnection.setTextColor(Color.GREEN);
                briefCarConnection.setText(R.string.carConnectedHint);
                Toast.makeText(getApplicationContext(),R.string.conOK, Toast.LENGTH_SHORT).show();
            }
            progressDialog.dismiss();
        }
    }
}