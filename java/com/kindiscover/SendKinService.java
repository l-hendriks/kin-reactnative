package com.kinfit.kindiscover;

import android.support.annotation.NonNull;

import org.kinecosystem.appsdiscovery.service.SendKinServiceBase;

import java.math.BigDecimal;

import kin.sdk.Transaction;
import kin.sdk.TransactionId;
import kin.sdk.KinAccount;
import kin.sdk.KinClient;
import kin.sdk.Environment;

// Discovery module send
public class SendKinService extends SendKinServiceBase {
    @NonNull
    @Override
    public KinTransferComplete transferKin(@NonNull String toAddress, int amount, @NonNull String memo) throws KinTransferException {
        String sourceAddress = "None";

        KinClient client = new KinClient(getApplicationContext(), Environment.PRODUCTION, "QG32", "user1");
        KinAccount account = client.getAccount(0);

        try {
            sourceAddress = account.getPublicAddress();
            int fee = 100;
            Transaction transaction = account.buildTransactionSync(toAddress,
                    new BigDecimal(amount), fee, memo);
            TransactionId transactionId = account.sendTransactionSync(transaction);
            return new KinTransferComplete(sourceAddress, transactionId.id(), transaction.getMemo());
        } catch (Exception e) {
            e.printStackTrace();
            throw new KinTransferException(sourceAddress,
                    "Cannot transfer Kin. Exception " + e + ", with message " + e.getMessage());
        }
    }

    @Override
    public BigDecimal getCurrentBalance() throws BalanceException {
        KinClient client = new KinClient(getApplicationContext(), Environment.PRODUCTION, "QG32", "user1");
        KinAccount account = client.getAccount(0);

        try {
            return account.getBalanceSync().value();
        } catch (Exception e) {
            throw new BalanceException("Unable to retrieve Kin balance. Exception " + e + ", with message " + e.getMessage());
        }
    }
}