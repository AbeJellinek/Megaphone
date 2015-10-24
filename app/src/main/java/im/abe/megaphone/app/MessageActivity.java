package im.abe.megaphone.app;

import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import io.realm.Realm;

public class MessageActivity extends BaseActivity {

    public static final String EXTRA_MESSAGE = "MessageActivity:message";

    private Realm realm;
    private Message message;

    @Override
    protected int getMainView() {
        return R.layout.activity_message;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        realm = Realm.getDefaultInstance();
        message = realm.where(Message.class).equalTo("id", getIntent().getStringExtra(EXTRA_MESSAGE)).findFirst();

        ViewCompat.setTransitionName(findViewById(R.id.card), EXTRA_MESSAGE);

        ((TextView) findViewById(R.id.message_title)).setText(message.getTitle());
        ((TextView) findViewById(R.id.message_text)).setText(message.getText());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_message, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_delete) {
            realm.beginTransaction();
            message.removeFromRealm();
            realm.commitTransaction();

            finish();
            return true;
        } else if (id == android.R.id.home) {
            ActivityCompat.finishAfterTransition(this);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        realm.close();
    }
}
