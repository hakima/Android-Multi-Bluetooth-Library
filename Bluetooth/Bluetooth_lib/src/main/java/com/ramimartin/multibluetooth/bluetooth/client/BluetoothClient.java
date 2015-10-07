package com.ramimartin.multibluetooth.bluetooth.client;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.ramimartin.multibluetooth.bus.BluetoothCommunicator;
import com.ramimartin.multibluetooth.bus.ClientConnectionFail;
import com.ramimartin.multibluetooth.bus.ClientConnectionSuccess;

import java.io.*;
import java.util.UUID;

import de.greenrobot.event.EventBus;

/**
 * Created by Rami MARTIN on 13/04/2014.
 */
public class BluetoothClient implements Runnable
{
    private final int TRIES = 20;

    private boolean CONTINUE_READ_WRITE = true;

    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mBluetoothDevice;
    private UUID mUuid;
    private String mServerAddress;

    private BluetoothSocket mSocket;
    private DataOutputStream mDataOutputStream;
    private DataInputStream mDataInputStream;

    private BluetoothConnector mBluetoothConnector;

    public BluetoothClient(BluetoothAdapter bluetoothAdapter, String adressMac)
    {
        mBluetoothAdapter = bluetoothAdapter;
        mServerAddress = adressMac;
        mUuid = UUID.fromString("e0917680-d427-11e4-8830-" + bluetoothAdapter.getAddress().replace(":", ""));
    }

    @Override
    public void run()
    {

        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(mServerAddress);
//        List<UUID> uuidCandidates = new ArrayList<UUID>();
//        uuidCandidates.add(mUuid);
        int tries = 0;
        while (mSocket == null && tries<=TRIES)
        {
            tries++;
            try
            {
                Thread.sleep(500);
                mBluetoothConnector = new BluetoothConnector(mBluetoothDevice, true, mBluetoothAdapter, mUuid);
                mSocket = mBluetoothConnector.connect().getUnderlyingSocket();
            } catch (IOException e1)
            {
                Log.e("", "===> mSocket IOException", e1);
                //EventBus.getDefault().post(new ClientConnectionFail());
                e1.printStackTrace();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }

        if (mSocket == null)
        {
            Log.e("", "===> mSocket == Null");
            EventBus.getDefault().post(new ClientConnectionFail());
            return;
        }

        try
        {
            mDataInputStream = new DataInputStream(new BufferedInputStream(mSocket.getInputStream()));
            mDataOutputStream = new DataOutputStream(new BufferedOutputStream(mSocket.getOutputStream()));
            EventBus.getDefault().post(new ClientConnectionSuccess());
            int len;
            while (CONTINUE_READ_WRITE)
            {
                len = mDataInputStream.readInt();
                byte[] buffer = new byte[len];
                mDataInputStream.read(buffer);
                EventBus.getDefault().post(new BluetoothCommunicator(mServerAddress, buffer));
            }

        } catch (IOException e)
        {
            Log.e("", "===> Client run");
            e.printStackTrace();
            EventBus.getDefault().post(new ClientConnectionFail());
        }
    }

    public synchronized void write(byte[] message)
    {
        try
        {
            if (mDataOutputStream != null)
            {
                mDataOutputStream.writeInt(message.length);
                mDataOutputStream.write(message);
                mDataOutputStream.flush();
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public void closeConnexion()
    {
        CONTINUE_READ_WRITE = false;
        if (mSocket != null)
        {
            try
            {
                mDataInputStream.close();
                mDataInputStream = null;
                mDataOutputStream.close();
                mDataOutputStream = null;
                mSocket.close();
                mSocket = null;
                mBluetoothConnector.close();
            } catch (Exception e)
            {
                Log.e("", "===> Client closeConnexion");
            }
        }
    }
}
