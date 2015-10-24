package im.abe.megaphone.app;

import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.transition.Fade;
import android.transition.Transition;
import android.view.View;

public abstract class BaseActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(getMainView());
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Transition fade = new Fade();
            final View decor = getWindow().getDecorView();
            fade.excludeTarget(android.R.id.statusBarBackground, true);
            fade.excludeTarget(decor, true);
            getWindow().setExitTransition(fade);
            getWindow().setEnterTransition(fade);
        }
    }

    protected abstract int getMainView();
}
