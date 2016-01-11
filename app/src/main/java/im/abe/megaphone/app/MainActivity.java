package im.abe.megaphone.app;

import android.app.DialogFragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
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

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends BaseActivity implements NfcAdapter.OnNdefPushCompleteCallback {

    static final String HANDSHAKE_MESSAGE = "HEY, THIS IS MEGAPHONE";
    private static final int REQUEST_SELECT_PHOTO = 100;
    private static final int REQUEST_ENABLE_BT = 101;
    static final String NAME = "MegaphoneApp";
    static final UUID SERVICE_UUID = UUID.fromString("5e20de22-44e0-4be1-a027-1795fc55ee3f");
    private static final String TAG = "MainActivity";
    private static final String MEGAPHONE_MIME = "application/x-megaphone";
    private Realm realm;
    private RealmResults<Message> messages;
    private MessageAdapter adapter;
    private BluetoothAdapter bluetooth;
    private MaterialDialog btDialog;
    private MaterialDialog syncingDialog;
    private boolean enableBluetooth = false;
    private DialogFragment syncDialog;

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

            final AcceptThread accepter = new AcceptThread(this);
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
                                new ConnectThread(MainActivity.this, device).start();

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

    void showSyncingDialog() {
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
        new AcceptThread(this).start();
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
                    new ConnectThread(this, device).start();
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

    BluetoothAdapter getBluetooth() {
        return bluetooth;
    }

    DialogFragment getSyncDialog() {
        return syncDialog;
    }

    MaterialDialog getBluetoothDialog() {
        return btDialog;
    }

    RecyclerView.Adapter<RecyclerView.ViewHolder> getAdapter() {
        return adapter;
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

}
