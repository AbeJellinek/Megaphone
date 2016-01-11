package im.abe.megaphone.app;

import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.util.Log;
import io.realm.Realm;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;

class AcceptThread extends Thread {
    private static final String TAG = "AcceptThread";

    private MainActivity activity;
    private final BluetoothServerSocket serverSocket;

    AcceptThread(MainActivity activity) {
        this.activity = activity;
        try {
            serverSocket = activity.getBluetooth().listenUsingRfcommWithServiceRecord(
                    MainActivity.NAME, MainActivity.SERVICE_UUID);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void run() {
        BluetoothSocket socket;
        // Keep listening until exception occurs or a socket is returned
        while (true) {
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                break;
            }
            // If a connection was accepted
            if (socket != null) {
                if (activity.getBluetoothDialog() != null)
                    activity.getBluetoothDialog().dismiss();

                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activity.showSyncingDialog();
                    }
                });

                // Do work to manage the connection (in a separate thread)
                manageConnectedSocket(socket);
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
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

                    dataOut.writeUTF(MainActivity.HANDSHAKE_MESSAGE);
                    Log.d(TAG, "Wrote handshake.");
                    if (!dataIn.readUTF().equals(MainActivity.HANDSHAKE_MESSAGE)) {
                        Log.d(TAG, "Got wrong handshake. Closing...");
                        socket.close();
                        return;
                    }
                    Log.d(TAG, "Got correct handshake.");

                    List<Message> allMessages = realm.allObjects(Message.class);
                    MessageWriter.writeMessageIDs(dataOut, allMessages);

                    int size = dataIn.readInt();
                    List<Message> newMessages = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        newMessages.add(MessageWriter.readMessage(dataIn));
                    }

                    realm.beginTransaction();
                    realm.copyToRealm(newMessages);
                    realm.commitTransaction();

                    Log.d(TAG, "Updated messages.");

                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            activity.getAdapter().notifyDataSetChanged();
                        }
                    });

                    int newIDs = dataIn.readInt();
                    List<String> ids = new ArrayList<>(newIDs);
                    for (int i = 0; i < newIDs; i++) {
                        ids.add(new UUID(dataIn.readLong(), dataIn.readLong()).toString());
                    }
                    Log.d(TAG, "Read " + newIDs + " message IDs.");

                    List<Message> newMessagesHere = new ArrayList<>(allMessages);
                    for (ListIterator<Message> iterator = newMessagesHere.listIterator(); iterator.hasNext(); ) {
                        Message newMessage = iterator.next();
                        if (ids.contains(newMessage.getId())) {
                            iterator.remove();
                        }
                    }

                    dataOut.writeInt(newMessagesHere.size());
                    for (Message message : newMessagesHere) {
                        MessageWriter.writeMessage(dataOut, message);
                    }

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
     * Will cancel the listening socket, and cause the thread to finish
     */
    void cancel() throws IOException {
        serverSocket.close();
    }
}
