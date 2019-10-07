package com.kinfit;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import android.support.annotation.NonNull;
import android.util.Log;
import android.content.Intent;
import android.app.Activity;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.WritableMap;

import com.mindorks.nybus.NYBus;
import com.mindorks.nybus.annotation.Subscribe;

import kin.sdk.AccountStatus;
import kin.sdk.Balance;
import kin.sdk.KinAccount;
import kin.sdk.KinClient;
import kin.sdk.Environment;
import kin.sdk.Transaction;
import kin.sdk.TransactionId;
import kin.utils.ResultCallback;
import kin.sdk.exception.CreateAccountException;
import kin.utils.Request;
import kin.sdk.ListenerRegistration;
import kin.sdk.EventListener;

import kin.backupandrestore.BackupAndRestoreManager;
import kin.backupandrestore.BackupCallback;
import kin.backupandrestore.RestoreCallback;
import kin.backupandrestore.exception.BackupAndRestoreException;

import org.kinecosystem.appsdiscovery.view.AppsDiscoveryActivity;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class RNKinSdkModule extends ReactContextBaseJavaModule implements ActivityEventListener {

    private final ReactApplicationContext reactContext;

    private KinClient client;
    private KinAccount account;
    private String blockchainEndpoint;
    private BackupAndRestoreManager backupAndRestoreManager;
    private static final int REQ_CODE_BACKUP = 9000;
    private static final int REQ_CODE_RESTORE = 9001;
    private String appId;

    private Request<Transaction> buildTransactionRequest;
    private Request<TransactionId> sendTransactionRequest;

    private int defaultFee = 100;

    public RNKinSdkModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addActivityEventListener(this);
    }

    @Override
    public String getName() {
        return "RNKinSdk";
    }

    @Override
    public void onNewIntent(Intent intent) {
        // super.onNewIntent(intent);
    }

    @ReactMethod
    public void initialize(
            ReadableMap options,
            Promise promise
    ) {
        try {
            String network = options.getString("network");
            String appId = options.getString("appId");
            this.appId = appId;
            if (Objects.equals(network, "testNet")) {
                this.client = new KinClient(this.reactContext, Environment.TEST, appId, "user1");
                this.blockchainEndpoint = "endpoint to backend account creator";
            } else {
                this.client = new KinClient(this.reactContext, Environment.PRODUCTION, appId, "user1");
                this.blockchainEndpoint = "endpoint to backend account creator";
            }

            // Listen to pubsub events
            NYBus.get().register(this);

            promise.resolve(true);
        } catch (Exception error) {
            promise.reject(error);
        }
    }

    @ReactMethod
    public void updateAccount(
            final Promise promise
    ) {
        this.client = new KinClient(this.reactContext, Environment.PRODUCTION, this.appId, "user1");
        this.account = this.client.getAccount(this.client.getAccountCount() - 1);

        promise.resolve(this.account.getPublicAddress());
    }

    @ReactMethod
    public void createAccount(
            final Promise promise
    ) {
        try {
            if (this.client.hasAccount()) {
                this.account = this.client.getAccount(this.client.getAccountCount() - 1);
            } else {
                this.account = this.client.addAccount();
            }

            ReactApplicationContext ctx = this.reactContext;

            // listen to balance changes
            ListenerRegistration listenerRegistration = this.account.addBalanceListener(new EventListener<Balance>() {
                @Override
                public void onEvent(Balance balance) {
                    WritableMap params = Arguments.createMap();
                    params.putString("update", "yes");


                    Log.d("ch", "balance udated");
                    ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("updateBalance", params);
                }
            });


            Activity mActivity = this.reactContext.getCurrentActivity();
            this.backupAndRestoreManager = new BackupAndRestoreManager(mActivity, REQ_CODE_BACKUP, REQ_CODE_RESTORE);
            this.initializeBackupAndRestore(this.backupAndRestoreManager);

            final String address = this.account.getPublicAddress();
            final String blockchainEndpoint = this.blockchainEndpoint + address;

            OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();

            this.account.getStatus().run(new ResultCallback<Integer>() {
                @Override
                public void onResult(Integer result) {
                    switch (result) {
                        case AccountStatus.CREATED:
                            promise.resolve(address);
                            break;
                        case AccountStatus.NOT_CREATED:
                            try {
                                okhttp3.Request request = new okhttp3.Request.Builder()
                                    .url(String.format(blockchainEndpoint, address))
                                    .get()
                                    .build();

                                okHttpClient.newCall(request)
                                    .enqueue(new okhttp3.Callback() {
                                        @Override
                                        public void onFailure(@NonNull Call call, @NonNull IOException e) {
                                            promise.reject(e);
                                        }

                                        @Override
                                        public void onResponse(@NonNull Call call, @NonNull Response response) {
                                            String body = "";
                                            try {
                                                body = response.body().string();
                                            } catch (Exception error) {
                                                promise.reject(error);
                                            }
                                            int code = response.code();
                                            response.close();
                                            if (code != 200) {
                                                promise.reject(body);
                                            } else {

                                                promise.resolve(address);
                                            }
                                        }
                                    });
                            } catch (Exception error) {
                                promise.reject(error);
                            }
                            break;
                    }
                }

                @Override
                public void onError(Exception e) {
                    promise.reject(e);
                }
            });
        } catch (Exception error) {
            promise.reject(error);
        }
    }

    private void initializeBackupAndRestore(BackupAndRestoreManager backupAndRestoreManager) {
        ReactApplicationContext ctx = this.reactContext;
        WritableMap params = Arguments.createMap();
        params.putString("update", "yes");

        backupAndRestoreManager.registerBackupCallback(new BackupCallback() {
            @Override
            public void onSuccess() {
                Log.d("ch", "backup success");
                ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("backupComplete", params);
            }

            @Override
            public void onCancel() {
                ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("backupCancelled", params);
            }

            @Override
            public void onFailure(BackupAndRestoreException throwable) {
                ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("backupFailed", params);
            }
        });
        backupAndRestoreManager.registerRestoreCallback(new RestoreCallback() {
            @Override
            public void onSuccess(KinClient kinClient, KinAccount kinAccount) {
                Log.d("ch", "restore success");
                ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("restoreSuccess", params);
            }

            @Override
            public void onCancel() {
                ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("restoreCancelled", params);
            }

            @Override
            public void onFailure(BackupAndRestoreException throwable) {
                ctx.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                        .emit("restoreFailed", params);
            }
        });
    }

    @Override
    public void onActivityResult(Activity m, int requestCode, int resultCode, Intent data) {
        // super.onActivityResult(m, requestCode, resultCode, data);
        if (requestCode == REQ_CODE_BACKUP || requestCode == REQ_CODE_RESTORE) {
            backupAndRestoreManager.onActivityResult(requestCode, resultCode, data);
        }
    }

    @ReactMethod
    public void getBalance(final Promise promise) {
        try {
            this.account.getBalance().run(new ResultCallback<Balance>() {
                @Override
                public void onResult(Balance result) {
                    promise.resolve(result.value(2));
                }

                @Override
                public void onError(Exception e) {
                    promise.reject(e);
                }
            });
        } catch (Exception error) {
            promise.reject(error);
        }
    }

    @ReactMethod
    public void sendTransaction(ReadableMap options, final Promise promise) {
        try {
            BigDecimal amount = new BigDecimal(options.getDouble("amount"));
            String memo = options.getString("memo");
            String to = options.getString("to");
            final KinAccount currentAccount = this.account;

            buildTransactionRequest = currentAccount.buildTransaction(to, amount, this.defaultFee, memo);
            buildTransactionRequest.run(new ResultCallback<Transaction>() {
                @Override
                public void onResult(Transaction transaction) {
                    sendTransactionRequest = currentAccount.sendTransaction(transaction);
                    sendTransactionRequest.run(new ResultCallback<TransactionId>() {
                        @Override
                        public void onResult(TransactionId id) {
                            promise.resolve(transaction.getId().id());
                        }

                        @Override
                        public void onError(Exception e) {
                            promise.reject(e);
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    promise.reject(e);
                }
            });
        } catch (Exception error) {
            promise.reject(error);
        }
    }

    @ReactMethod
    public void openAppsDiscoveryIntent() {
        Log.d("ch", "open intent");
        Intent intent = AppsDiscoveryActivity.Companion.getIntent(this.reactContext);
        this.reactContext.startActivity(intent);
    }

    @ReactMethod
    public void openBackup() {
        backupAndRestoreManager.backup(this.client, this.account);
    }

    @ReactMethod
    public void openRestore() {
        backupAndRestoreManager.restore(this.client);
    }
}
