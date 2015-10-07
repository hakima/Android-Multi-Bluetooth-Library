package com.ramimartin.multibluetooth.bluetooth.server;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import com.ramimartin.multibluetooth.bus.BluetoothCommunicator;
import com.ramimartin.multibluetooth.bus.ServeurConnectionFail;
import com.ramimartin.multibluetooth.bus.ServeurConnectionSuccess;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.UUID;

import de.greenrobot.event.EventBus;

/**
 * Created by Rami MARTIN on 13/04/2014.
 */
public class BluetoothServer implements Runnable
{

    private boolean CONTINUE_READ_WRITE = true;

    private UUID mUUID;
    public String mClientAddress;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothServerSocket mServerSocket;
    private BluetoothSocket mSocket;
    private DataOutputStream mDataOutputStream;
    private DataInputStream mDataInputStream;


    public BluetoothServer(BluetoothAdapter bluetoothAdapter, String clientAddress)
    {
        mBluetoothAdapter = bluetoothAdapter;
        mClientAddress = clientAddress;
        mUUID = UUID.fromString("e0917680-d427-11e4-8830-" + mClientAddress.replace(":", ""));
    }

    @Override
    public void run()
    {
        try
        {
            //mServerSocket = mBluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("BLTServer", mUUID);
            mServerSocket = mBluetoothAdapter.listenUsingRfcommWithServiceRecord("BLTServer", mUUID);
            mSocket = mServerSocket.accept();

            //Assign input streams
            mDataInputStream = new DataInputStream(new BufferedInputStream(mSocket.getInputStream()));

            //Assign output streams
            mDataOutputStream = new DataOutputStream(new BufferedOutputStream(mSocket.getOutputStream()));
            int len;

            EventBus.getDefault().post(new ServeurConnectionSuccess(mClientAddress));

            while (CONTINUE_READ_WRITE)
            {
                len = mDataInputStream.readInt();
                byte[] buffer = new byte[len];
                mDataInputStream.read(buffer);
                EventBus.getDefault().post(new BluetoothCommunicator(mClientAddress, buffer));
            }
        } catch (IOException e)
        {
            Log.e("", "ERROR : " + e.getMessage());
            EventBus.getDefault().post(new ServeurConnectionFail(mClientAddress));
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

    public String getClientAddress()
    {
        return mClientAddress;
    }

    public void closeConnection()
    {
        CONTINUE_READ_WRITE = false;

        try
        {
            mDataInputStream.close();
            mDataInputStream = null;
            mDataOutputStream.close();
            mDataOutputStream = null;
            mSocket.close();
            mSocket = null;
            mServerSocket.close();
            mServerSocket = null;
        } catch (Exception e)
        {
        }
    }
}
