package com.kinfit;

import android.content.Intent;
import android.app.Activity;

import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactActivity;
import com.facebook.react.ReactActivityDelegate;
import com.facebook.react.ReactRootView;
import com.swmansion.gesturehandler.react.RNGestureHandlerEnabledRootView;

public class MainActivity extends ReactActivity {

    private ReactInstanceManager mReactInstanceManager;

    /**
     * Returns the name of the main component registered from JavaScript.
     * This is used to schedule rendering of the component.
     */
    @Override
    protected String getMainComponentName() {
        return "kinfit";
    }

    @Override
    protected ReactActivityDelegate createReactActivityDelegate() {
        return new ReactActivityDelegate(this, getMainComponentName()) {
            @Override
            protected ReactRootView createRootView() {
            return new RNGestureHandlerEnabledRootView(MainActivity.this);
            }
        };
    }

    public void onActivityResult(Activity m, int requestCode, int resultCode, Intent data) {
        mReactInstanceManager.onActivityResult(m, requestCode, resultCode, data);
    }

}
