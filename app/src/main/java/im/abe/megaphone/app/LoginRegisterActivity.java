package im.abe.megaphone.app;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import io.realm.Realm;
import io.realm.RealmConfiguration;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

public class LoginRegisterActivity extends BaseActivity {
    private EditText passwordField;

    @Override
    protected int getMainView() {
        return R.layout.activity_login_register;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        passwordField = (EditText) findViewById(R.id.password_field);

        if (getSupportActionBar() != null) {
            if (new File(getFilesDir(), "default.realm").exists()) {
                getSupportActionBar().setTitle(R.string.enter_password);
            } else {
                getSupportActionBar().setTitle(R.string.choose_password);
            }
        }

        passwordField.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (passwordField.length() == 0)
                    return true;

                byte[] key = new byte[0];
                try {
                    key = passwordField.getText().toString().getBytes("UTF-8");
                    MessageDigest sha = MessageDigest.getInstance("SHA-1");
                    key = sha.digest(key);
                    key = Arrays.copyOf(key, 64);
                } catch (UnsupportedEncodingException | NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }

                final RealmConfiguration config = new RealmConfiguration.Builder(LoginRegisterActivity.this)
                        .encryptionKey(key)
                        .build();
                Realm.setDefaultConfiguration(config);

                try {
                    Realm.getDefaultInstance().close();
                } catch (Exception e) {
                    incorrectPassword(config);
                    return true;
                }

                Intent intent = new Intent(LoginRegisterActivity.this, MainActivity.class);
                startActivity(intent);
                finish();

                return true;
            }
        });
    }

    private void incorrectPassword(final RealmConfiguration config) {
        passwordField.setText("");
        Snackbar.make(findViewById(R.id.container), R.string.incorrect_password, Snackbar.LENGTH_SHORT)
                .setAction(R.string.reset, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        new AlertDialog.Builder(LoginRegisterActivity.this)
                                .setTitle(getString(R.string.reset_dialog_title))
                                .setMessage(getString(R.string.reset_dialog_message))
                                .setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Realm.deleteRealm(config);
                                        finish();
                                        startActivity(new Intent(
                                                LoginRegisterActivity.this,
                                                LoginRegisterActivity.class));
                                    }
                                }).setNegativeButton(android.R.string.cancel, null).create().show();
                    }
                }).show();
    }
}
