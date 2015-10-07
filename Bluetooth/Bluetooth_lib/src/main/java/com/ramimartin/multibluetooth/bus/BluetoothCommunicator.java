package com.ramimartin.multibluetooth.bus;

/**
 * Created by Rami MARTIN on 13/04/2014.
 */
public class BluetoothCommunicator
{

    public byte[] mMessageReceive;
    public String mDevice;

    public BluetoothCommunicator( String mDevice, byte[] mMessageReceive)
    {
        this.mMessageReceive = mMessageReceive;
        this.mDevice = mDevice;
    }

}
