package com.kinfit;

import android.app.Application;

import com.facebook.react.ReactApplication;
import com.ybrain.rn.googlefitness.GoogleFitPackage;
import com.kinfit.RNKinSdkPackage;
import com.kevinejohn.RNMixpanel.RNMixpanel;
import com.bugsnag.BugsnagReactNative;
import com.swmansion.gesturehandler.react.RNGestureHandlerPackage;
import com.amazonaws.RNAWSCognitoPackage;
import com.oblador.vectoricons.VectorIconsPackage;
import com.emesonsantana.BMDPedometer.BMDPedometer;
import com.facebook.react.ReactNativeHost;
import com.facebook.react.ReactPackage;
import com.facebook.react.shell.MainReactPackage;
import com.facebook.soloader.SoLoader;

import java.util.Arrays;
import java.util.List;

public class MainApplication extends Application implements ReactApplication {

  private final ReactNativeHost mReactNativeHost = new ReactNativeHost(this) {
    @Override
    public boolean getUseDeveloperSupport() {
      return BuildConfig.DEBUG;
    }

    @Override
    protected List<ReactPackage> getPackages() {
      return Arrays.<ReactPackage>asList(
          new MainReactPackage(),
            new GoogleFitPackage(BuildConfig.APPLICATION_ID),
            new RNMixpanel(),
            BugsnagReactNative.getPackage(),
            new RNGestureHandlerPackage(),
            new RNAWSCognitoPackage(),
            new VectorIconsPackage(),
            new BMDPedometer(),
            new RNKinSdkPackage()
      );
    }

    @Override
    protected String getJSMainModuleName() {
      return "index";
    }
  };

  @Override
  public ReactNativeHost getReactNativeHost() {
    return mReactNativeHost;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    SoLoader.init(this, /* native exopackage */ false);
  }
}
