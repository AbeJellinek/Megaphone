package im.abe.megaphone.app;

import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import io.realm.Realm;

import java.util.Date;
import java.util.UUID;

public class MessageEditActivity extends BaseActivity {

    private Realm realm;

    @Override
    protected int getMainView() {
        return R.layout.activity_message_edit;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        realm = Realm.getInstance(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_message_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_done) {
            realm.beginTransaction();

            Message message = realm.createObject(Message.class);
            message.setId(UUID.randomUUID().toString());
            message.setDate(new Date());
            message.setTitle(((EditText) findViewById(R.id.message_title)).getText().toString());
            message.setText(((EditText) findViewById(R.id.message_text)).getText().toString());

            realm.commitTransaction();

            ActivityCompat.finishAfterTransition(this);
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
