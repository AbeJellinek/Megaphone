package im.abe.megaphone.app;

import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.github.clans.fab.FloatingActionButton;
import io.realm.Realm;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements NfcAdapter.CreateNdefMessageCallback {

    private Realm realm;

    private final int scrollOffset = 4;
    private RecyclerView recyclerView;
    private List<Message> messages;
    private MessageAdapter adapter;
    private NfcAdapter nfcAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        realm = Realm.getInstance(this);
        messages = realm.allObjectsSorted(Message.class, "date", false);

        final FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
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
                        fab.hide(true);
                    } else {
                        fab.show(true);
                    }
                }
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MessageEditActivity.class);
                startActivity(intent);
            }
        });

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (nfcAdapter != null) {
            nfcAdapter.setNdefPushMessageCallback(this, this);
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
        NdefMessage msg = new NdefMessage(
                new NdefRecord[]{createMime(
                        "text/plain", text.getBytes())
                });
        return msg;
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

    private class MessageAdapter extends RecyclerView.Adapter<ViewHolder> {
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(getLayoutInflater().inflate(R.layout.message_card, parent, false));
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, final int position) {
            final Message message = messages.get(position);
            holder.title.setText(message.getTitle());
            holder.text.setText(message.getText());
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
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }
    }

    private class ViewHolder extends RecyclerView.ViewHolder {
        private TextView title;
        private TextView text;

        public ViewHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.message_title);
            text = (TextView) itemView.findViewById(R.id.message_text);
        }
    }
}
