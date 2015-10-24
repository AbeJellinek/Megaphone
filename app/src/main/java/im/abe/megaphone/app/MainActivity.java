package im.abe.megaphone.app;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;
import com.squareup.picasso.Picasso;
import io.realm.Realm;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends BaseActivity implements NfcAdapter.CreateNdefMessageCallback {

    private static final int SELECT_PHOTO = 100;
    private final int scrollOffset = 4;

    private Realm realm;
    private RecyclerView recyclerView;
    private List<Message> messages;
    private MessageAdapter adapter;
    private NfcAdapter nfcAdapter;

    @Override
    protected int getMainView() {
        return R.layout.activity_main;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        realm = Realm.getInstance(this);
        messages = realm.allObjectsSorted(Message.class, "date", false);

        final FloatingActionMenu fam = (FloatingActionMenu) findViewById(R.id.fam);
        FloatingActionButton textFab = (FloatingActionButton) findViewById(R.id.add_text_fab);
        FloatingActionButton imageFab = (FloatingActionButton) findViewById(R.id.add_image_fab);
        recyclerView = (RecyclerView) findViewById(R.id.main_list);
        adapter = new MessageAdapter();
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (Math.abs(dy) > scrollOffset) {
                    if (dy > 0) {
                        fam.hideMenu(true);
                    } else {
                        fam.showMenu(true);
                    }
                }
            }
        });

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
                startActivityForResult(photoPickerIntent, SELECT_PHOTO);
            }
        });

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            nfcAdapter.setNdefPushMessageCallback(this, this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent imageReturnedIntent) {
        super.onActivityResult(requestCode, resultCode, imageReturnedIntent);

        switch (requestCode) {
            case SELECT_PHOTO:
                if (resultCode == RESULT_OK) {
                    Uri selectedImage = imageReturnedIntent.getData();
                    String[] filePathColumn = {MediaStore.Images.Media.DATA};

                    Cursor cursor = getContentResolver().query(
                            selectedImage, filePathColumn, null, null, null);
                    cursor.moveToFirst();

                    int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        realm.close();
    }

    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        String text = ("This is a test: " + System.currentTimeMillis());
        return new NdefMessage(
                new NdefRecord[]{createMime(
                        "text/plain", text.getBytes())
                });
    }

    public static String normalizeMimeType(String type) {
        if (type == null) {
            return null;
        }

        type = type.trim().toLowerCase(Locale.ROOT);

        final int semicolonIndex = type.indexOf(';');
        if (semicolonIndex != -1) {
            type = type.substring(0, semicolonIndex);
        }
        return type;
    }

    public static NdefRecord createMime(String mimeType, byte[] mimeData) {
        if (mimeType == null) throw new NullPointerException("mimeType is null");

        // We only do basic MIME type validation: trying to follow the
        // RFCs strictly only ends in tears, since there are lots of MIME
        // types in common use that are not strictly valid as per RFC rules
        mimeType = normalizeMimeType(mimeType);
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

    private class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
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
                    item.setTitle(String.format(String.valueOf(item.getTitle()),
                            DateUtils.getRelativeTimeSpanString(message.getDate().getTime())));

                    menu.findItem(R.id.action_delete).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            realm.beginTransaction();
                            messages.remove(position);
                            notifyItemRemoved(position);
                            realm.commitTransaction();

                            return true;
                        }
                    });
                }
            });

            if (holder instanceof TextViewHolder) {
                TextViewHolder textHolder = (TextViewHolder) holder;
                textHolder.title.setText(message.getTitle());
                textHolder.text.setText(message.getText());

                holder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        ActivityOptionsCompat options =
                                ActivityOptionsCompat.makeSceneTransitionAnimation(
                                        MainActivity.this, holder.itemView, MessageActivity.EXTRA_MESSAGE);
                        Intent intent = new Intent(MainActivity.this, MessageActivity.class);
                        intent.putExtra(MessageActivity.EXTRA_MESSAGE, message.getId());
                        ActivityCompat.startActivity(MainActivity.this, intent, options.toBundle());
                    }
                });
            } else if (holder instanceof ImageViewHolder) {
                final ImageViewHolder imageHolder = (ImageViewHolder) holder;
                Picasso.with(MainActivity.this)
                        .load(new File(message.getText()))
                        .resize(1080, 0)
                        .into(imageHolder.image);

                holder.itemView.setOnClickListener(new View.OnClickListener() {
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

        @Override
        public int getItemCount() {
            return messages.size();
        }

        @Override
        public int getItemViewType(int position) {
            return messages.get(position).isImage() ? 1 : 0;
        }
    }

    private class TextViewHolder extends RecyclerView.ViewHolder {
        private TextView title;
        private TextView text;

        public TextViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.message_title);
            text = (TextView) itemView.findViewById(R.id.message_text);
        }
    }

    private class ImageViewHolder extends RecyclerView.ViewHolder {
        private ImageView image;

        public ImageViewHolder(View itemView) {
            super(itemView);
            image = (ImageView) itemView.findViewById(R.id.message_image);
        }
    }
}
