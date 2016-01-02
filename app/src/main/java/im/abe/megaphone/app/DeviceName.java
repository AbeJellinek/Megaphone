package im.abe.megaphone.app;

import android.bluetooth.BluetoothDevice;
import android.support.annotation.NonNull;

public class DeviceName implements CharSequence {
    private CharSequence name;
    private BluetoothDevice device;

    public DeviceName(CharSequence name, BluetoothDevice device) {
        this.name = name;
        this.device = device;
    }

    public BluetoothDevice getDevice() {
        return device;
    }

    public void setDevice(BluetoothDevice device) {
        this.device = device;
    }

    @Override
    public int length() {
        return name.length();
    }

    @Override
    public char charAt(int index) {
        return name.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return name.subSequence(start, end);
    }

    @NonNull
    @Override
    public String toString() {
        return name.toString();
    }
}
