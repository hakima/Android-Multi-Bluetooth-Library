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
public class BluetoothManager extends BroadcastReceiver
{

    private static final String SERVER_NAME = "HyraxServer";
    private static final String CLIENT_NAME = "HyraxClient";
    private static final int CLIENT_HOLDOFF = 5000;
    private static final long SCAN_DELAY = 15000;
    private static final long SCAN_INTERVAL = 30000;

    public boolean isConnected()
    {
        return isConnected;
    }

    public enum TypeBluetooth
    {
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

//    private ArrayList<String> mAdressListServerWaitingConnection;
    private HashMap<String, BluetoothServer> mServeurWaitingConnectionMap;
    private HashMap<String, BluetoothServer> mServeurConnectedMap;
    private HashMap<String, Thread> mServeurThreadMap;
    private Thread mClientThread;
    private int mNbrClientConnection;
    public TypeBluetooth mType;
    private int mTimeDiscoverable;
    private boolean isConnected;
    private boolean isConnecting;
    private boolean isDiscovering;
    private boolean mBluetoothIsEnableOnStart;
    private String mBluetoothNameSaved;
    private Timer mScanTimer;

    public BluetoothManager(Activity activity)
    {
        mService = null;
        mActivity = activity;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothNameSaved = mBluetoothAdapter.getName();
        mBluetoothIsEnableOnStart = mBluetoothAdapter.isEnabled();
        mType = TypeBluetooth.None;
        isConnected = false;
        isConnecting = false;
        isDiscovering = false;
        mScanTimer = new Timer();
        mNbrClientConnection = 0;
//        mAdressListServerWaitingConnection = new ArrayList<String>();
        mServeurWaitingConnectionMap = new HashMap<String, BluetoothServer>();
        mServeurConnectedMap = new HashMap<String, BluetoothServer>();
        mServeurThreadMap = new HashMap<String, Thread>();
        EventBus.getDefault().register(this);
        //setTimeDiscoverable(BLUETOOTH_TIME_DICOVERY_300_SEC);
    }


    public BluetoothManager(Service service)
    {
        mService = service;
        mActivity = null;
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothNameSaved = mBluetoothAdapter.getName();
        mBluetoothIsEnableOnStart = mBluetoothAdapter.isEnabled();
        mType = TypeBluetooth.None;
        isConnected = false;
        isDiscovering = false;
        mNbrClientConnection = 0;
//        mAdressListServerWaitingConnection = new ArrayList<String>();
        mServeurWaitingConnectionMap = new HashMap<String, BluetoothServer>();
        mServeurConnectedMap = new HashMap<String, BluetoothServer>();
        mServeurThreadMap = new HashMap<String, Thread>();
        EventBus.getDefault().register(this);
        //setTimeDiscoverable(BLUETOOTH_TIME_DICOVERY_300_SEC);
    }

    /**
     * Become the server and start scanning for clients immediately
     */
    public void selectServerMode()
    {
        mType = TypeBluetooth.Server;
        setTimeDiscoverable(BLUETOOTH_TIME_DICOVERY_3600_SEC);
        setServerBluetoothName();
        startDiscovery();
        scanAllBluetoothDevice();
    }

    private void setServerBluetoothName()
    {
        mBluetoothAdapter.setName(SERVER_NAME);
    }

    /**
     * Become the client and start scanning for server after few seconds
     */
    public void selectClientMode()
    {
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

    public String getYourBtMacAddress()
    {
        if (mBluetoothAdapter != null)
        {
            return mBluetoothAdapter.getAddress();
        }
        return null;
    }

    public void setNbrClientMax(int nbrClientMax)
    {
        if (nbrClientMax <= BLUETOOTH_NBR_CLIENT_MAX)
        {
            BLUETOOTH_NBR_CLIENT_MAX = nbrClientMax;
        }
    }

    private int getNbrClientMax()
    {
        return BLUETOOTH_NBR_CLIENT_MAX;
    }

    private boolean isNbrMaxReached()
    {
        return mNbrClientConnection == getNbrClientMax();
    }

    private void setServerWaitingConnection(String address, BluetoothServer bluetoothServer, Thread threadServer)
    {
//        mAdressListServerWaitingConnection.add(address);
        mServeurWaitingConnectionMap.put(address, bluetoothServer);
        mServeurThreadMap.put(address, threadServer);
    }

    private void incrementNbrConnection()
    {
        mNbrClientConnection = mNbrClientConnection + 1;
        setServerBluetoothName();
        if (mNbrClientConnection == getNbrClientMax())
        {
            //resetWaitingThreadServer();
        }
        Log.e("", "===> incrementNbrConnection mNbrClientConnection : " + mNbrClientConnection);
    }

    private void resetWaitingThreadServer()
    {
        for (Map.Entry<String, Thread> bluetoothThreadServerMap : mServeurThreadMap.entrySet())
        {
//            if (mAdressListServerWaitingConnection.contains(bluetoothThreadServerMap.getKey()))
            if (mServeurWaitingConnectionMap.containsKey(bluetoothThreadServerMap.getKey()))
            {
                Log.e("", "===> resetWaitingThreadServer Thread : " + bluetoothThreadServerMap.getKey());
                bluetoothThreadServerMap.getValue().interrupt();
            }
        }
        for (Map.Entry<String, BluetoothServer> bluetoothServerMap : mServeurWaitingConnectionMap.entrySet())
        {
            Log.e("", "===> resetWaitingThreadServer BluetoothServer : " + bluetoothServerMap.getKey());
            bluetoothServerMap.getValue().closeConnection();
            //mServeurThreadMap.remove(bluetoothServerMap.getKey());
        }
//        mAdressListServerWaitingConnection.clear();
        mServeurWaitingConnectionMap.clear();
    }

    private void decrementNbrConnection()
    {
        if (mNbrClientConnection == 0)
        {
            return;
        }
        mNbrClientConnection = mNbrClientConnection - 1;
        if (mNbrClientConnection == 0)
        {
            isConnected = false;
        }
        Log.e("", "===> decrementNbrConnection mNbrClientConnection : " + mNbrClientConnection);
        setServerBluetoothName();
    }

    private void setTimeDiscoverable(int timeInSec)
    {
        mTimeDiscoverable = timeInSec;
        BLUETOOTH_REQUEST_ACCEPTED = mTimeDiscoverable;
    }

    public boolean checkBluetoothAviability()
    {
        if (mBluetoothAdapter == null)
        {
            return false;
        } else
        {
            return true;
        }
    }

    public void cancelDiscovery()
    {
        if (isDiscovering())
        {
            mBluetoothAdapter.cancelDiscovery();
        }
    }

    public boolean isDiscovering()
    {
        return mBluetoothAdapter.isDiscovering();
    }

    public void startDiscovery()
    {
        if (mBluetoothAdapter == null)
        {
            return;
        } else
        {
            if (mBluetoothAdapter.isEnabled() && isDiscovering())
            {
                Log.e("", "===> mBluetoothAdapter.isDiscovering()");
                return;
            } else
            {
                Log.e("", "===> startDiscovery");
                if (mActivity != null)
                {
                    Method method;
                    try
                    {
                        method = mBluetoothAdapter.getClass().getMethod("setScanMode", int.class, int.class);
                        method.invoke(mBluetoothAdapter, BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE, mTimeDiscoverable);
                        Log.e("invoke", "method invoke successfully");
                    } catch (Exception e)
                    {
                        e.printStackTrace();
                    }
//                    Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
//                    discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, mTimeDiscoverable);
//                    mActivity.startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE_CODE);
                } else
                {
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
    private synchronized void startScanning()
    {
        if(!isDiscovering)
        {
            mBluetoothAdapter.startDiscovery();
            mScanTimer = new Timer();
            mScanTimer.schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    mBluetoothAdapter.startDiscovery();
                }
            }, SCAN_DELAY, SCAN_INTERVAL);
            isDiscovering = true;
        }
    }

    /**
     * Stop Scanning for devices nearby
     */
    private synchronized void stopScanning()
    {
        if(isDiscovering)
        {
            Log.e("", "===> StopScanning ");
            mBluetoothAdapter.cancelDiscovery();
            mScanTimer.cancel();
            mScanTimer = null;
            isDiscovering = false;
        }
    }

    public void scanAllBluetoothDevice()
    {
        IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        if (mActivity != null)
            mActivity.registerReceiver(this, intentFilter);
        else
            mService.registerReceiver(this, intentFilter);
        startScanning();
    }

    private void createClient(String addressMac)
    {
        if (mType == TypeBluetooth.Client && !isConnected && !isConnecting)
        {
            isConnecting = true;
            IntentFilter bondStateIntent = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            if (mActivity != null)
                mActivity.registerReceiver(this, bondStateIntent);
            else
                mService.registerReceiver(this, bondStateIntent);
            mBluetoothClient = new BluetoothClient(mBluetoothAdapter, addressMac);
            mClientThread = new Thread(mBluetoothClient);
            mClientThread.start();
            Log.e("", "===> createClient address : " + addressMac);
        }
    }

    private void createServeur(String address)
    {
//        if (mType == TypeBluetooth.Server && !mAdressListServerWaitingConnection.contains(address))
        if (mType == TypeBluetooth.Server && !mServeurWaitingConnectionMap.containsKey(address) && !mServeurConnectedMap.containsKey(address))
        {
            if (!isNbrMaxReached())
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

    @Override
    public void onReceive(Context context, Intent intent)
    {
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        Log.e("BluetoohManager", "onReceive() " + intent.getAction());

        if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND))
        {
//            if((mType == TypeBluetooth.Client && !isConnected)
//                    || (mType == TypeBluetooth.Server && !mAdressListServerWaitingConnection.contains(device.getAddress()))){

            if (device.getName() != null)
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
        if (intent.getAction().equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        {
            //Log.e("", "===> ACTION_BOND_STATE_CHANGED");
            int prevBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);
            int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            if (prevBondState == BluetoothDevice.BOND_BONDING)
            {
                // check for both BONDED and NONE here because in some error cases the bonding fails and we need to fail gracefully.
                if (bondState == BluetoothDevice.BOND_BONDED || bondState == BluetoothDevice.BOND_NONE)
                {
                    //Log.e("", "===> BluetoothDevice.BOND_BONDED");
                    EventBus.getDefault().post(new BondedDevice());
                }
            }
        }
    }

    /**
     * If Server gets connected to client, add to list and increment number of connected clients.
     * If number of clients reached max, stop scanning.
     *
     * @param addressClientConnected
     */
    private synchronized void onServerConnectionSuccess(String addressClientConnected)
    {
//        for (Map.Entry<String, BluetoothServer> bluetoothServerMap : mServeurWaitingConnectionMap.entrySet())
//        {
//            if (addressClientConnected.equals(bluetoothServerMap.getValue().getClientAddress()))
            if(mServeurWaitingConnectionMap.containsKey(addressClientConnected))
            {
                isConnected = true;
                mServeurConnectedMap.put(addressClientConnected, mServeurWaitingConnectionMap.get(addressClientConnected));
                mServeurWaitingConnectionMap.remove(addressClientConnected);
                incrementNbrConnection();
                Log.e("", "===> onServerConnectionSuccess address : " + addressClientConnected);
//                break;
            }
//        }
        if (isNbrMaxReached())
        {
            Log.e("BlueToothManager", "Reached Max. Stopping scans");
            stopScanning();
        }
    }

    private synchronized void onServerConnectionFailed(String addressClientConnectionFailed)
    {
        int index = 0;

        if(mServeurConnectedMap.containsKey(addressClientConnectionFailed))
        {
            mServeurConnectedMap.get(addressClientConnectionFailed).closeConnection();
            mServeurConnectedMap.remove(addressClientConnectionFailed);
            mServeurThreadMap.remove(addressClientConnectionFailed);
            decrementNbrConnection();
        }
        else if(mServeurWaitingConnectionMap.containsKey(addressClientConnectionFailed))
        {
            mServeurWaitingConnectionMap.get(addressClientConnectionFailed).closeConnection();
            mServeurWaitingConnectionMap.remove(addressClientConnectionFailed);
            mServeurThreadMap.remove(addressClientConnectionFailed);
        }

//        for (BluetoothServer bluetoothServer : mServeurConnectedMap)
//        {
//            if (addressClientConnectionFailed.equals(bluetoothServer.getClientAddress()))
//            {
//                mServeurConnectedMap.get(index).closeConnection();
//                mServeurConnectedMap.remove(index);
//                mServeurWaitingConnectionMap.get(addressClientConnectionFailed).closeConnection();
//                mServeurWaitingConnectionMap.remove(addressClientConnectionFailed);
//                mServeurThreadMap.get(addressClientConnectionFailed).interrupt();
//                mServeurThreadMap.remove(addressClientConnectionFailed);
////                mAdressListServerWaitingConnection.remove(addressClientConnectionFailed);
//                decrementNbrConnection();
//                Log.e("", "===> onServerConnectionFailed address : " + addressClientConnectionFailed);
//                if (!isNbrMaxReached())
//                    startScanning();
//                return;
//            }
//            index++;
//        }

    }

    private synchronized void onClientConnectionSuccess()
    {
        isConnected = true;
        isConnecting = false;
        stopScanning();
    }

    private synchronized void onClientConnectionFail()
    {
        Log.e("BT", "===> ClientConnection failed. Resetting");
        resetClient();
        isConnected = false;
        isConnecting = false;
        startScanning();
    }

    public void onEvent(ClientConnectionSuccess event)
    {
        onClientConnectionSuccess();
    }

    public void onEvent(ClientConnectionFail event)
    {
        onClientConnectionFail();
    }

    public void onEvent(ServeurConnectionSuccess event)
    {
        onServerConnectionSuccess(event.mClientAdressConnected);
    }

    public void onEvent(ServeurConnectionFail event)
    {
        onServerConnectionFailed(event.mClientAdressConnectionFail);
    }

    /**
     * If server, send message to all clients or if client, send message to server
     *
     * @param message
     */
    public void sendMessage(byte[] message)
    {
        if (mType != null && isConnected)
        {
            if (mServeurConnectedMap != null)
            {
                for (BluetoothServer BTServer : mServeurConnectedMap.values())
                {
                    BTServer.write(message);
                }
            }
            if (mBluetoothClient != null)
            {
                mBluetoothClient.write(message);
            }
        }
    }

    /**
     * Send message to specific client
     *
     * @param message
     * @param client
     */
    public void sendToClient(byte[] message, String client)
    {
        if (mType == TypeBluetooth.Server && isConnected)
        {
            if (mServeurConnectedMap != null)
            {
                if(mServeurConnectedMap.containsKey(client))
                    mServeurConnectedMap.get(client).write(message);
//                for (BluetoothServer BTServer : mServeurConnectedMap.values())
//                {
//                    if (BTServer.getClientAddress().equals(client))
//                    {
//                        BTServer.write(message);
//                        break;
//                    }
//                }
            }
        }
    }

    public void disconnectClient()
    {
        mType = TypeBluetooth.None;
        cancelDiscovery();
        resetClient();
    }

    public void disconnectServer()
    {
        mType = TypeBluetooth.None;
        cancelDiscovery();
        resetServer();
    }

    public void resetServer()
    {
        if (mServeurConnectedMap != null)
        {
            for (BluetoothServer aMServeurConnectedList : mServeurConnectedMap.values())
            {
                aMServeurConnectedList.closeConnection();
            }
            mServeurConnectedMap.clear();
        }
    }

    public void resetClient()
    {
        if (mBluetoothClient != null)
        {
            mBluetoothClient.closeConnexion();
            mBluetoothClient = null;
            mClientThread = null;
        }
    }

    public void closeAllConnexion()
    {
        mBluetoothAdapter.setName(mBluetoothNameSaved);

        try
        {
            if (mActivity != null)
                mActivity.unregisterReceiver(this);
            else
                mService.unregisterReceiver(this);
        } catch (Exception e)
        {
        }

        cancelDiscovery();

        if (!mBluetoothIsEnableOnStart)
        {
            mBluetoothAdapter.disable();
        }

        mBluetoothAdapter = null;

        if (mType != null)
        {
            resetServer();
            resetClient();
        }
    }
}
