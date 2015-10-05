package com.ramimartin.multibluetooth.bluetooth.manager;

import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.ramimartin.multibluetooth.activity.EnableBluetoothDiscoveryActivity;
import com.ramimartin.multibluetooth.bluetooth.client.BluetoothClient;
import com.ramimartin.multibluetooth.bluetooth.server.BluetoothServer;
import com.ramimartin.multibluetooth.bus.*;

import java.lang.reflect.Method;
import java.util.*;

import de.greenrobot.event.EventBus;

/**
 * Created by Rami MARTIN on 13/04/2014.
 * Edited by Utsav Drolia
 */
public class BluetoothManager extends BroadcastReceiver {

    private static final String SERVER_NAME = "HyraxServer";
    private static final String CLIENT_NAME = "HyraxClient";
    private static final int CLIENT_HOLDOFF = 5000;
    private static final long SCAN_DELAY = 15000;
    private static final long SCAN_INTERVAL = 30000;

    public boolean isConnected()
    {
        return isConnected;
    }

    public enum TypeBluetooth{
        Client,
        Server,
        None;
    }

    public static final int REQUEST_DISCOVERABLE_CODE = 114;

    public static int BLUETOOTH_REQUEST_ACCEPTED;
    public static final int BLUETOOTH_REQUEST_REFUSED = 0; // NE PAS MODIFIER LA VALEUR

    public static final int BLUETOOTH_TIME_DICOVERY_60_SEC = 60;
    public static final int BLUETOOTH_TIME_DICOVERY_120_SEC = 120;
    public static final int BLUETOOTH_TIME_DICOVERY_300_SEC = 300;
    public static final int BLUETOOTH_TIME_DICOVERY_600_SEC = 600;
    public static final int BLUETOOTH_TIME_DICOVERY_900_SEC = 900;
    public static final int BLUETOOTH_TIME_DICOVERY_1200_SEC = 1200;
    public static final int BLUETOOTH_TIME_DICOVERY_3600_SEC = 3600;

    private static int BLUETOOTH_NBR_CLIENT_MAX = 7;

    private Activity mActivity;
    private Service mService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothClient mBluetoothClient;

    private ArrayList<String> mAdressListServerWaitingConnection;
    private HashMap<String, BluetoothServer> mServeurWaitingConnectionList;
    private ArrayList<BluetoothServer> mServeurConnectedList;
    private HashMap<String, Thread> mServeurThreadList;
    private int mNbrClientConnection;
    public TypeBluetooth mType;
    private int mTimeDiscoverable;
    private boolean isConnected;
    private boolean mBluetoothIsEnableOnStart;
    private String mBluetoothNameSaved;
    private Timer mScanTimer;

    public BluetoothManager(Activity activity) {
        mService = null;
        mActivity = activity;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothNameSaved = mBluetoothAdapter.getName();
        mBluetoothIsEnableOnStart = mBluetoothAdapter.isEnabled();
        mType = TypeBluetooth.None;
        isConnected = false;
        mScanTimer = new Timer();
        mNbrClientConnection = 0;
        mAdressListServerWaitingConnection = new ArrayList<String>();
        mServeurWaitingConnectionList = new HashMap<String, BluetoothServer>();
        mServeurConnectedList = new ArrayList<BluetoothServer>();
        mServeurThreadList = new HashMap<String, Thread>();
        EventBus.getDefault().register(this);
        //setTimeDiscoverable(BLUETOOTH_TIME_DICOVERY_300_SEC);
    }


    public BluetoothManager(Service service) {
        mService = service;
        mActivity = null;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothNameSaved = mBluetoothAdapter.getName();
        mBluetoothIsEnableOnStart = mBluetoothAdapter.isEnabled();
        mType = TypeBluetooth.None;
        isConnected = false;
        mNbrClientConnection = 0;
        mAdressListServerWaitingConnection = new ArrayList<String>();
        mServeurWaitingConnectionList = new HashMap<String, BluetoothServer>();
        mServeurConnectedList = new ArrayList<BluetoothServer>();
        mServeurThreadList = new HashMap<String, Thread>();
        EventBus.getDefault().register(this);
        //setTimeDiscoverable(BLUETOOTH_TIME_DICOVERY_300_SEC);
    }

    /**
     * Become the server and start scanning for clients immediately
     */
    public void selectServerMode(){
        mType = TypeBluetooth.Server;
        setTimeDiscoverable(BLUETOOTH_TIME_DICOVERY_3600_SEC);
        setServerBluetoothName();
        startDiscovery();
        scanAllBluetoothDevice();
    }

    private void setServerBluetoothName(){
        mBluetoothAdapter.setName(SERVER_NAME);
    }

    /**
    * Become the client and start scanning for server after few seconds
    */
    public void selectClientMode(){
        mType = TypeBluetooth.Client;
        setTimeDiscoverable(BLUETOOTH_TIME_DICOVERY_3600_SEC);
        mBluetoothAdapter.setName(CLIENT_NAME);
        startDiscovery();

        Timer t = new Timer();
        t.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                scanAllBluetoothDevice();
            }
        }, CLIENT_HOLDOFF);
    }

    public String getYourBtMacAddress(){
        if(mBluetoothAdapter != null){
            return mBluetoothAdapter.getAddress();
        }
        return null;
    }

    public void setNbrClientMax(int nbrClientMax){
        if(nbrClientMax <= BLUETOOTH_NBR_CLIENT_MAX){
            BLUETOOTH_NBR_CLIENT_MAX = nbrClientMax;
        }
    }

    private int getNbrClientMax(){
        return BLUETOOTH_NBR_CLIENT_MAX;
    }

    private boolean isNbrMaxReached(){
        return mNbrClientConnection == getNbrClientMax();
    }

    private void setServerWaitingConnection(String address, BluetoothServer bluetoothServer, Thread threadServer){
        mAdressListServerWaitingConnection.add(address);
        mServeurWaitingConnectionList.put(address, bluetoothServer);
        mServeurThreadList.put(address, threadServer);
    }

    private void incrementNbrConnection(){
        mNbrClientConnection = mNbrClientConnection +1;
        setServerBluetoothName();
        if(mNbrClientConnection == getNbrClientMax()){
            //resetWaitingThreadServer();
        }
        Log.e("", "===> incrementNbrConnection mNbrClientConnection : "+mNbrClientConnection);
    }

    private void resetWaitingThreadServer(){
        for(Map.Entry<String, Thread> bluetoothThreadServerMap : mServeurThreadList.entrySet()){
            if(mAdressListServerWaitingConnection.contains(bluetoothThreadServerMap.getKey())){
                Log.e("", "===> resetWaitingThreadServer Thread : "+bluetoothThreadServerMap.getKey());
                bluetoothThreadServerMap.getValue().interrupt();
            }
        }
        for(Map.Entry<String, BluetoothServer> bluetoothServerMap : mServeurWaitingConnectionList.entrySet()){
            Log.e("", "===> resetWaitingThreadServer BluetoothServer : " + bluetoothServerMap.getKey());
            bluetoothServerMap.getValue().closeConnection();
            //mServeurThreadList.remove(bluetoothServerMap.getKey());
        }
        mAdressListServerWaitingConnection.clear();
        mServeurWaitingConnectionList.clear();
    }

    private void decrementNbrConnection(){
        if(mNbrClientConnection ==0){
            return;
        }
        mNbrClientConnection = mNbrClientConnection -1;
        if(mNbrClientConnection ==0){
            isConnected = false;
        }
        Log.e("", "===> decrementNbrConnection mNbrClientConnection : "+mNbrClientConnection);
        setServerBluetoothName();
    }

    private void setTimeDiscoverable(int timeInSec){
        mTimeDiscoverable = timeInSec;
        BLUETOOTH_REQUEST_ACCEPTED = mTimeDiscoverable;
    }

    public boolean checkBluetoothAviability(){
        if (mBluetoothAdapter == null) {
            return false;
        }else{
            return true;
        }
    }

    public void cancelDiscovery(){
        if(isDiscovering()) {
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    public boolean isDiscovering(){
        return mBluetoothAdapter.isDiscovering();
    }

    public void startDiscovery() {
        if (mBluetoothAdapter == null) {
            return;
        } else {
            if (mBluetoothAdapter.isEnabled() && isDiscovering()) {
                Log.e("", "===> mBluetoothAdapter.isDiscovering()");
                return;
            } else {
                Log.e("", "===> startDiscovery");
                if (mActivity != null) {
                    Method method;
                    try {
                        method = mBluetoothAdapter.getClass().getMethod("setScanMode", int.class, int.class);
                        method.invoke(mBluetoothAdapter,BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, mTimeDiscoverable);
                        Log.e("invoke","method invoke successfully");
                    }
                    catch (Exception e){
                        e.printStackTrace();
                    }
//                    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, mTimeDiscoverable);
//                    mActivity.startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_CODE);
                }
                else {
                    Intent intent = new Intent(mService.getApplicationContext(), EnableBluetoothDiscoveryActivity.class);
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, mTimeDiscoverable);
                    mService.startActivity(intent);
                }
            }
        }
    }

    /**
     * Start Scanning for devices nearby
     */
    private void startScanning()
    {
        mBluetoothAdapter.startDiscovery();
        mScanTimer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                mBluetoothAdapter.startDiscovery();
            }
        }, SCAN_DELAY, SCAN_INTERVAL);
    }

    /**
     * Stop Scanning for devices nearby
     */
    private void stopScanning()
    {
        mBluetoothAdapter.cancelDiscovery();
        mScanTimer.cancel();
    }

    public void scanAllBluetoothDevice() {
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        if (mActivity != null)
            mActivity.registerReceiver(this, intentFilter);
        else
            mService.registerReceiver(this, intentFilter);
        startScanning();
    }

    private void createClient(String addressMac) {
        if(mType == TypeBluetooth.Client && !isConnected)
        {
            IntentFilter bondStateIntent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            if (mActivity != null)
                mActivity.registerReceiver(this, bondStateIntent);
            else
                mService.registerReceiver(this, bondStateIntent);
            stopScanning();
            mBluetoothClient = new BluetoothClient(mBluetoothAdapter, addressMac);
            new Thread(mBluetoothClient).start();
        }
    }

    private void createServeur(String address){
        if(mType == TypeBluetooth.Server && !mAdressListServerWaitingConnection.contains(address))
        {
            if(!isNbrMaxReached())
            {
                BluetoothServer mBluetoothServer = new BluetoothServer(mBluetoothAdapter, address);
                Thread threadServer = new Thread(mBluetoothServer);
                threadServer.start();
                setServerWaitingConnection(address, mBluetoothServer, threadServer);
                IntentFilter bondStateIntent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                if (mActivity != null)
                    mActivity.registerReceiver(this, bondStateIntent);
                else
                    mService.registerReceiver(this, bondStateIntent);
                Log.e("", "===> createServeur address : " + address);
            }
        }
    }

    /**
     * If Server gets connected to client, add to list and increment number of connected clients.
     * If number of clients reached max, stop scanning.
     * @param addressClientConnected
     */
    private void onServerConnectionSuccess(String addressClientConnected)
    {
        for(Map.Entry<String, BluetoothServer> bluetoothServerMap : mServeurWaitingConnectionList.entrySet())
        {
            if(addressClientConnected.equals(bluetoothServerMap.getValue().getClientAddress())){
                mServeurConnectedList.add(bluetoothServerMap.getValue());
                incrementNbrConnection();
                Log.e("", "===> onServerConnectionSuccess address : "+addressClientConnected);
                return;
            }
        }
        if(isNbrMaxReached())
        {
            Log.e("BlueToothManager","Reached Max. Stopping scans");
            stopScanning();
        }
    }

    private void onServerConnectionFailed(String addressClientConnectionFailed)
    {
        int index = 0;
        for(BluetoothServer bluetoothServer : mServeurConnectedList)
        {
            if(addressClientConnectionFailed.equals(bluetoothServer.getClientAddress()))
            {
                mServeurConnectedList.get(index).closeConnection();
                mServeurConnectedList.remove(index);
                mServeurWaitingConnectionList.get(addressClientConnectionFailed).closeConnection();
                mServeurWaitingConnectionList.remove(addressClientConnectionFailed);
                mServeurThreadList.get(addressClientConnectionFailed).interrupt();
                mServeurThreadList.remove(addressClientConnectionFailed);
                mAdressListServerWaitingConnection.remove(addressClientConnectionFailed);
                decrementNbrConnection();
                Log.e("", "===> onServerConnectionFailed address : "+addressClientConnectionFailed);
                return;
            }
            index++;
        }
        if(!isNbrMaxReached())
            startScanning();
    }

    public void onEvent(ClientConnectionSuccess event)
    {
        isConnected = true;
    }

    public void onEvent(ClientConnectionFail event)
    {
        isConnected = false;
        startScanning();
    }

    public void onEvent(ServeurConnectionSuccess event)
    {
        isConnected = true;
        onServerConnectionSuccess(event.mClientAdressConnected);
    }

    public void onEvent(ServeurConnectionFail event)
    {
        onServerConnectionFailed(event.mClientAdressConnectionFail);
    }

    public void sendMessage(String message) {
        if(mType != null && isConnected){
            if(mServeurConnectedList!= null){
                for (BluetoothServer aMServeurConnectedList : mServeurConnectedList)
                {
                    aMServeurConnectedList.write(message);
                }
            }
            if(mBluetoothClient != null){
                mBluetoothClient.write(message);
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        Log.e("BluetoohManager", "onReceive() " + intent.getAction());

        if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND))
        {
//            if((mType == TypeBluetooth.Client && !isConnected)
//                    || (mType == TypeBluetooth.Server && !mAdressListServerWaitingConnection.contains(device.getAddress()))){

            if(device.getName()!=null)
            {
                EventBus.getDefault().post(device);
                if (mType == TypeBluetooth.Client)
                {
                    if (device.getName().equals(SERVER_NAME))
                    {

                        createClient(device.getAddress());
                    }
                } else if (mType == TypeBluetooth.Server)
                {
                    if (device.getName().equals(CLIENT_NAME))
                    {

                        createServeur(device.getAddress());
                    }
                }
//            }
            }
        }
        if(intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)){
            //Log.e("", "===> ACTION_BOND_STATE_CHANGED");
            int prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
            int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            if (prevBondState == BluetoothDevice.BOND_BONDING)
            {
                // check for both BONDED and NONE here because in some error cases the bonding fails and we need to fail gracefully.
                if (bondState == BluetoothDevice.BOND_BONDED || bondState == BluetoothDevice.BOND_NONE )
                {
                    //Log.e("", "===> BluetoothDevice.BOND_BONDED");
                    EventBus.getDefault().post(new BondedDevice());
                }
            }
        }
    }

    public void disconnectClient(){
        mType = TypeBluetooth.None;
        cancelDiscovery();
        resetClient();
    }

    public void disconnectServer(){
        mType = TypeBluetooth.None;
        cancelDiscovery();
        resetServer();
    }

    public void resetServer(){
        if(mServeurConnectedList != null){
            for (BluetoothServer aMServeurConnectedList : mServeurConnectedList)
            {
                aMServeurConnectedList.closeConnection();
            }
            mServeurConnectedList.clear();
        }
    }

    public void resetClient(){
        if(mBluetoothClient != null){
            mBluetoothClient.closeConnexion();
            mBluetoothClient = null;
        }
    }

    public void closeAllConnexion(){
        mBluetoothAdapter.setName(mBluetoothNameSaved);

        try{
            if (mActivity != null)
                mActivity.unregisterReceiver(this);
            else
                mService.unregisterReceiver(this);
        }catch(Exception e){}

        cancelDiscovery();

        if(!mBluetoothIsEnableOnStart){
            mBluetoothAdapter.disable();
        }

        mBluetoothAdapter = null;

        if(mType != null){
            resetServer();
            resetClient();
        }
    }
}
