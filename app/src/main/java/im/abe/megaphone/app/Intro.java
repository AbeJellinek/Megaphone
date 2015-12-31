package im.abe.megaphone.app;

import android.os.Bundle;
import android.support.annotation.Nullable;
import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;

public class Intro extends AppIntro {
    @Override
    public void init(@Nullable Bundle bundle) {
        addSlide(AppIntroFragment.newInstance("Test", "Testing!", R.drawable.ic_image_white_24dp, 0xFF03A9F4));
    }

    @Override
    public void onSkipPressed() {

    }

    @Override
    public void onNextPressed() {

    }

    @Override
    public void onDonePressed() {
        finish();
    }

    @Override
    public void onSlideChanged() {

    }
}
