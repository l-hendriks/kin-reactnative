package com.kinfit.kindiscover;

import kin.sdk.KinAccount;
import kin.sdk.KinClient;
import kin.sdk.Environment;

import org.kinecosystem.transfer.receiver.view.AccountInfoActivityBase;

public class AccountInfoActivity extends AccountInfoActivityBase {

    @Override
    public String getData() {
        KinClient client = new KinClient(getApplicationContext(), Environment.PRODUCTION, "QG32", "user1");
        KinAccount account = client.getAccount(0);
        return account.getPublicAddress();
    }

}