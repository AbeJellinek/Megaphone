package im.abe.megaphone.app;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import io.realm.Realm;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

class ConnectThread extends Thread {
    private static final String TAG = "ConnectThread";

    private final BluetoothSocket socket;
    private MainActivity activity;

    ConnectThread(MainActivity activity, BluetoothDevice device) {
        this.activity = activity;

        // Use a temporary object that is later assigned to socket,
        // because socket is final
        BluetoothSocket tmp = null;

        // Get a BluetoothSocket to connect with the given BluetoothDevice
        try {
            tmp = device.createRfcommSocketToServiceRecord(MainActivity.SERVICE_UUID);
        } catch (IOException e) {
            e.printStackTrace();
        }
        socket = tmp;
    }

    public void run() {
        // Cancel discovery because it will slow down the connection
        activity.getBluetooth().cancelDiscovery();

        try {
            // Connect the device through the socket. This will block
            // until it succeeds or throws an exception
            socket.connect();
        } catch (IOException connectException) {
            activity.getSyncDialog().dismiss();

            // Unable to connect; close the socket and get out
            connectException.printStackTrace();
            try {
                socket.close();
            } catch (IOException ignored) {
            }
            return;
        }

        // Do work to manage the connection (in a separate thread)
        manageConnectedSocket(socket);
    }

    private void manageConnectedSocket(final BluetoothSocket socket) {
        new Thread() {
            private Realm realm;

            @Override
            public void run() {
                try {
                    realm = Realm.getInstance(activity);

                    InputStream in = socket.getInputStream();
                    OutputStream out = socket.getOutputStream();
                    DataInputStream dataIn = new DataInputStream(in);
                    DataOutputStream dataOut = new DataOutputStream(out);

                    if (!dataIn.readUTF().equals(MainActivity.HANDSHAKE_MESSAGE)) {
                        Log.d(TAG, "Got wrong handshake. Closing...");
                        socket.close();
                        return;
                    }
                    Log.d(TAG, "Got correct handshake.");
                    dataOut.writeUTF(MainActivity.HANDSHAKE_MESSAGE);
                    Log.d(TAG, "Wrote handshake.");

                    int newIDs = dataIn.readInt();
                    List<String> ids = new ArrayList<>(newIDs);
                    for (int i = 0; i < newIDs; i++) {
                        ids.add(new UUID(dataIn.readLong(), dataIn.readLong()).toString());
                    }
                    Log.d(TAG, "Read " + newIDs + " message IDs.");

                    List<Message> allMessages = realm.allObjects(Message.class);
                    List<Message> newMessages = new ArrayList<>(allMessages);
                    for (ListIterator<Message> iterator = newMessages.listIterator(); iterator.hasNext(); ) {
                        Message newMessage = iterator.next();
                        if (ids.contains(newMessage.getId())) {
                            iterator.remove();
                        }
                    }

                    dataOut.writeInt(newMessages.size());
                    for (Message message : newMessages) {
                        MessageWriter.writeMessage(dataOut, message);
                    }

                    MessageWriter.writeMessageIDs(dataOut, allMessages);

                    int size = dataIn.readInt();
                    List<Message> newMessagesHere = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        newMessagesHere.add(MessageWriter.readMessage(dataIn));
                    }
                    realm.beginTransaction();
                    realm.copyToRealm(newMessagesHere);
                    realm.commitTransaction();

                    realm.close();
                    activity.getSyncDialog().dismiss();
                } catch (IOException e) {
                    activity.getSyncDialog().dismiss();
                    e.printStackTrace();
                }
            }
        }.start();
    }

    /**
     * Will cancel an in-progress connection, and close the socket
     */
    public void cancel() throws IOException {
        socket.close();
    }
}
