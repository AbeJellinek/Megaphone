package im.abe.megaphone.app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.simplelist.MaterialSimpleListAdapter;
import com.afollestad.materialdialogs.simplelist.MaterialSimpleListItem;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.squareup.picasso.Picasso;
import io.realm.*;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class MainActivity extends BaseActivity implements NfcAdapter.OnNdefPushCompleteCallback {

    private static final String HANDSHAKE_MESSAGE = "HEY, THIS IS MEGAPHONE";
    private static final int REQUEST_SELECT_PHOTO = 100;
    private static final int REQUEST_ENABLE_BT = 101;
    private static final String NAME = "MegaphoneApp";
    private static final UUID SERVICE_UUID = UUID.fromString("5e20de22-44e0-4be1-a027-1795fc55ee3f");
    private static final String TAG = "MainActivity";
    private static final String MEGAPHONE_MIME = "application/x-megaphone";
    private Realm realm;
    private RealmResults<Message> messages;
    private MessageAdapter adapter;
    private BluetoothAdapter bluetooth;
    private MaterialDialog btDialog;
    private MaterialDialog syncingDialog;
    private boolean enableBluetooth = false;

    @Override
    protected int getMainView() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Realm.setDefaultConfiguration(new RealmConfiguration.Builder(this).build());

        realm = Realm.getDefaultInstance();
        messages = realm.where(Message.class).findAllSortedAsync("date", Sort.DESCENDING);

        initUI();

        if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean("pref_enable_bluetooth", true)) {
            enableBluetooth = true;
            initBluetooth();
        }
    }

    private void initBluetooth() {
        bluetooth = BluetoothAdapter.getDefaultAdapter();
        if (bluetooth == null || !bluetooth.isEnabled()) {
            enableBluetooth = false;
            return; // Device does not support Bluetooth
        }

        if (!bluetooth.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            initNFC();
        }
    }

    private void initUI() {
        final FloatingActionMenu fam = (FloatingActionMenu) findViewById(R.id.fam);
        FloatingActionButton textFab = (FloatingActionButton) findViewById(R.id.add_text_fab);
        FloatingActionButton imageFab = (FloatingActionButton) findViewById(R.id.add_image_fab);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.main_list);
        adapter = new MessageAdapter();
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        textFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fam.close(false);

                Intent intent = new Intent(MainActivity.this, MessageEditActivity.class);
                startActivity(intent);
            }
        });

        imageFab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fam.close(false);

                Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
                photoPickerIntent.setType("image/*");
                startActivityForResult(photoPickerIntent, REQUEST_SELECT_PHOTO);
            }
        });

        messages.addChangeListener(new RealmChangeListener() {
            @Override
            public void onChange() {
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQUEST_ENABLE_BT:
                if (resultCode == RESULT_OK) {
                    initNFC();
                }
                break;
            case REQUEST_SELECT_PHOTO:
                if (resultCode == RESULT_OK) {
                    Uri selectedImage = data.getData();
                    String[] filePathColumn = {MediaStore.Images.Media.DATA};

                    Cursor cursor = getContentResolver().query(
                            selectedImage, filePathColumn, null, null, null);

                    if (cursor != null) {
                        cursor.moveToFirst();

                        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                        if (cursor.isAfterLast()) {
                            cursor.close();
                            break;
                        }

                        String filePath = cursor.getString(columnIndex);
                        cursor.close();

                        realm.beginTransaction();

                        Message message = realm.createObject(Message.class);
                        message.setId(UUID.randomUUID().toString());
                        message.setDate(new Date());
                        message.setTitle("Image");
                        message.setText(filePath);
                        message.setImage(true);

                        realm.commitTransaction();
                    }
                }
                break;
        }
    }

    private void initNFC() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter == null) return; // NFC not available on this device
        try {
            nfcAdapter.setOnNdefPushCompleteCallback(this, this);
            nfcAdapter.setNdefPushMessage(new NdefMessage(new NdefRecord[]{
                    NdefRecord.createApplicationRecord("im.abe.megaphone.app"),
                    createMime(MEGAPHONE_MIME, bluetooth.getAddress().getBytes("UTF-8"))}), this);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    /**
     * Copied from API 16.
     *
     * @param mimeType The MIME type.
     * @param mimeData The data to include in the record.
     * @return The new record.
     */
    private static NdefRecord createMime(String mimeType, byte[] mimeData) {
        if (mimeType == null) throw new NullPointerException("mimeType is null");

        // We only do basic MIME type validation: trying to follow the
        // RFCs strictly only ends in tears, since there are lots of MIME
        // types in common use that are not strictly valid as per RFC rules
        if (mimeType.length() == 0) throw new IllegalArgumentException("mimeType is empty");
        int slashIndex = mimeType.indexOf('/');
        if (slashIndex == 0) throw new IllegalArgumentException("mimeType must have major type");
        if (slashIndex == mimeType.length() - 1) {
            throw new IllegalArgumentException("mimeType must have minor type");
        }
        // missing '/' is allowed

        // MIME RFCs suggest ASCII encoding for content-type
        byte[] typeBytes = mimeType.getBytes(Charset.forName("US-ASCII"));
        return new NdefRecord(NdefRecord.TNF_MIME_MEDIA, typeBytes, null, mimeData);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_sync).setVisible(enableBluetooth = PreferenceManager.getDefaultSharedPreferences(this)
                .getBoolean("pref_enable_bluetooth", true));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.action_sync) {
            if (!enableBluetooth) {
                return false;
            }

            final AcceptThread accepter = new AcceptThread();
            accepter.start();

            Set<BluetoothDevice> pairedDevices = bluetooth.getBondedDevices();
            final MaterialSimpleListAdapter devicesList = new MaterialSimpleListAdapter(this);

            for (BluetoothDevice device : pairedDevices) {
                devicesList.add(
                        new MaterialSimpleListItem.Builder(MainActivity.this)
                                .content(new DeviceName(device.getName(), device))
                                .icon(R.drawable.ic_smartphone_black_24dp)
                                .build());
            }

            btDialog = new MaterialDialog.Builder(this)
                    .title(R.string.choose_device)
                    .adapter(devicesList, new MaterialDialog.ListCallback() {
                        @Override
                        public void onSelection(final MaterialDialog dialog, View itemView, int which, CharSequence text) {
                            try {
                                accepter.cancel();
                                BluetoothDevice device = ((DeviceName) devicesList.getItem(which).getContent())
                                        .getDevice();
                                new ConnectThread(device).start();

                                showSyncingDialog();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            btDialog = null;
                            dialog.dismiss();
                        }
                    })
                    .dismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            btDialog = null;
                        }
                    })
                    .autoDismiss(true)
                    .progress(true, 0)
                    .neutralText(R.string.settings)
                    .callback(new MaterialDialog.ButtonCallback() {
                        @Override
                        public void onNeutral(MaterialDialog dialog) {
                            Intent intentOpenBluetoothSettings = new Intent();
                            intentOpenBluetoothSettings.setAction(Settings.ACTION_BLUETOOTH_SETTINGS);
                            startActivity(intentOpenBluetoothSettings);
                        }
                    })
                    .show();
        }

        return super.onOptionsItemSelected(item);
    }

    private void showSyncingDialog() {
        syncingDialog = new MaterialDialog.Builder(MainActivity.this)
                .title(R.string.syncing_dialog)
                .content(R.string.please_wait)
                .progress(true, 0)
                .cancelable(false)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();

        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }

    @Override
    public void onNdefPushComplete(NfcEvent event) {
        new AcceptThread().start();
    }

    @Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }

    private void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        for (NdefRecord record : msg.getRecords()) {
            try {
                if (new String(record.getType(), "US-ASCII").equals(MEGAPHONE_MIME)) {
                    BluetoothDevice device = bluetooth.getRemoteDevice(new String(record.getPayload(), "UTF-8"));
                    new ConnectThread(device).start();
                    showSyncingDialog();
                    return;
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        realm.close();
    }

    private Message readMessage(DataInputStream dataIn) throws IOException {
        Message message = new Message();

        message.setId(dataIn.readUTF());
        message.setDate(new Date(dataIn.readLong()));
        message.setTitle(dataIn.readUTF());

        if (dataIn.readBoolean()) {
            message.setImage(true);

            String filename = dataIn.readUTF();
            int totalSize = dataIn.readInt();
            byte[] gzipFile = new byte[dataIn.readInt()];
            dataIn.readFully(gzipFile);

            byte[] file = new byte[totalSize];
            new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(gzipFile))).readFully(file);

            File dir = new File(Environment.getExternalStorageDirectory(), "Megaphone/downloaded/");
            if (!dir.isDirectory())
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
            File newFile = new File(dir, filename);
            int fileIndex = 1;
            while (newFile.exists())
                newFile = new File(dir, filename.replaceFirst(
                        "(\\.[^\\.]+)$", "_ " + fileIndex++ + "$1"));

            OutputStream fileOut = new FileOutputStream(newFile);
            fileOut.write(file);
            fileOut.close();

            message.setText(newFile.getAbsolutePath());

            Log.d(TAG, "Read image message " + message.getId() + ".");
        } else {
            message.setImage(false);
            message.setText(dataIn.readUTF());

            Log.d(TAG, "Read text message " + message.getId() + ".");
        }

        return message;
    }

    private void writeMessageIDs(DataOutputStream dataOut, List<Message> allMessages) throws IOException {
        dataOut.writeInt(allMessages.size());
        for (Message message : allMessages) {
            UUID uuid = UUID.fromString(message.getId());
            dataOut.writeLong(uuid.getMostSignificantBits());
            dataOut.writeLong(uuid.getLeastSignificantBits());
        }

        Log.d(TAG, "Wrote " + allMessages.size() + " message IDs.");
    }

    private void writeMessage(DataOutputStream dataOut, Message message) throws IOException {
        dataOut.writeUTF(message.getId());
        dataOut.writeLong(message.getDate().getTime());
        dataOut.writeUTF(message.getTitle());

        if (message.isImage()) {
            dataOut.writeBoolean(true);

            File file = new File(message.getText());
            InputStream fileIn = new FileInputStream(file);
            ByteArrayOutputStream fileOut = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(fileOut);
            byte[] buffer = new byte[1024 * 25];
            int total = 0;
            int len;
            while ((len = fileIn.read(buffer)) != -1) {
                gzip.write(buffer, 0, len);
                total += len;
            }
            gzip.close();
            fileIn.close();

            dataOut.writeUTF(file.getName());
            dataOut.writeInt(total);
            dataOut.writeInt(fileOut.size());
            dataOut.write(fileOut.toByteArray());

            Log.d(TAG, "Wrote image message " + message.getId() + ".");
        } else {
            dataOut.writeBoolean(false);
            dataOut.writeUTF(message.getText());

            Log.d(TAG, "Wrote text message " + message.getId() + ".");
        }
    }

    private class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        MessageAdapter() {
            registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    if (getItemCount() == 0) {
                        findViewById(R.id.empty_view).setVisibility(View.VISIBLE);
                    } else {
                        findViewById(R.id.empty_view).setVisibility(View.GONE);
                    }
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    onChanged();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    onChanged();
                }
            });
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            switch (viewType) {
                case 0:
                    return new TextViewHolder(getLayoutInflater().inflate(R.layout.message_card, parent, false));
                case 1:
                    return new ImageViewHolder(getLayoutInflater().inflate(R.layout.message_card_image, parent, false));
                default:
                    throw new IllegalArgumentException();
            }
        }

        @Override
        public void onBindViewHolder(final RecyclerView.ViewHolder holder, final int position) {
            final Message message = messages.get(position);

            holder.itemView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {
                @Override
                public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
                    MenuInflater inflater = getMenuInflater();
                    inflater.inflate(R.menu.menu_message_context, menu);
                    MenuItem item = menu.findItem(R.id.info_time);
                    item.setTitle(DateUtils.getRelativeTimeSpanString(message.getDate().getTime()));

                    menu.findItem(R.id.action_delete).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            realm.beginTransaction();
                            messages.remove(holder.getAdapterPosition());
                            realm.commitTransaction();

                            notifyItemRemoved(holder.getAdapterPosition());

                            return true;
                        }
                    });
                }
            });

            if (holder instanceof MessageViewHolder) {
                ((MessageViewHolder) holder).init(position, message);
            }
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).isImage() ? 1 : 0;
        }
    }

    private abstract class MessageViewHolder extends RecyclerView.ViewHolder {
        MessageViewHolder(View itemView) {
            super(itemView);
        }

        public abstract void init(int position, Message message);
    }

    private class TextViewHolder extends MessageViewHolder {
        private TextView title;
        private TextView text;
        private TextView time;

        TextViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.message_title);
            text = (TextView) itemView.findViewById(R.id.message_text);
            time = (TextView) itemView.findViewById(R.id.message_time);
        }

        @Override
        public void init(int position, final Message message) {
            title.setText(message.getTitle());
            text.setText(message.getText());
            time.setText(DateUtils.getRelativeTimeSpanString(message.getDate().getTime()));

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ActivityOptionsCompat options =
                            ActivityOptionsCompat.makeSceneTransitionAnimation(
                                    MainActivity.this, itemView, MessageActivity.EXTRA_MESSAGE);
                    Intent intent = new Intent(MainActivity.this, MessageActivity.class);
                    intent.putExtra(MessageActivity.EXTRA_MESSAGE, message.getId());
                    ActivityCompat.startActivity(MainActivity.this, intent, options.toBundle());
                }
            });
        }
    }

    private class ImageViewHolder extends MessageViewHolder {
        private ImageView image;

        ImageViewHolder(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.message_image);
        }

        @Override
        public void init(int position, final Message message) {
            Picasso.with(MainActivity.this)
                    .load(new File(message.getText()))
                    .resize(1080, 0)
                    .onlyScaleDown()
                    .into(image);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    intent.setDataAndType(Uri.parse("file://" + message.getText()), "image/*");
                    startActivity(intent);
                }
            });
        }
    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        AcceptThread() {
            try {
                serverSocket = bluetooth.listenUsingRfcommWithServiceRecord(NAME, SERVICE_UUID);
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
                    if (btDialog != null)
                        btDialog.dismiss();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showSyncingDialog();
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
                        realm = Realm.getInstance(MainActivity.this);

                        InputStream in = socket.getInputStream();
                        OutputStream out = socket.getOutputStream();
                        DataInputStream dataIn = new DataInputStream(in);
                        DataOutputStream dataOut = new DataOutputStream(out);

                        dataOut.writeUTF(HANDSHAKE_MESSAGE);
                        Log.d(TAG, "Wrote handshake.");
                        if (!dataIn.readUTF().equals(HANDSHAKE_MESSAGE)) {
                            Log.d(TAG, "Got wrong handshake. Closing...");
                            socket.close();
                            return;
                        }
                        Log.d(TAG, "Got correct handshake.");

                        List<Message> allMessages = realm.allObjects(Message.class);
                        writeMessageIDs(dataOut, allMessages);

                        int size = dataIn.readInt();
                        List<Message> newMessages = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            newMessages.add(readMessage(dataIn));
                        }

                        realm.beginTransaction();
                        realm.copyToRealm(newMessages);
                        realm.commitTransaction();

                        Log.d(TAG, "Updated messages.");

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                adapter.notifyDataSetChanged();
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
                            writeMessage(dataOut, message);
                        }

                        realm.close();
                        syncingDialog.dismiss();
                    } catch (IOException e) {
                        syncingDialog.dismiss();
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

    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;

        ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to socket,
            // because socket is final
            BluetoothSocket tmp = null;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                tmp = device.createRfcommSocketToServiceRecord(SERVICE_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            bluetooth.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                socket.connect();
            } catch (IOException connectException) {
                syncingDialog.dismiss();

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
                        realm = Realm.getInstance(MainActivity.this);

                        InputStream in = socket.getInputStream();
                        OutputStream out = socket.getOutputStream();
                        DataInputStream dataIn = new DataInputStream(in);
                        DataOutputStream dataOut = new DataOutputStream(out);

                        if (!dataIn.readUTF().equals(HANDSHAKE_MESSAGE)) {
                            Log.d(TAG, "Got wrong handshake. Closing...");
                            socket.close();
                            return;
                        }
                        Log.d(TAG, "Got correct handshake.");
                        dataOut.writeUTF(HANDSHAKE_MESSAGE);
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
                            writeMessage(dataOut, message);
                        }

                        writeMessageIDs(dataOut, allMessages);

                        int size = dataIn.readInt();
                        List<Message> newMessagesHere = new ArrayList<>(size);
                        for (int i = 0; i < size; i++) {
                            newMessagesHere.add(readMessage(dataIn));
                        }
                        realm.beginTransaction();
                        realm.copyToRealm(newMessagesHere);
                        realm.commitTransaction();

                        realm.close();
                        syncingDialog.dismiss();
                    } catch (IOException e) {
                        syncingDialog.dismiss();
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
}
