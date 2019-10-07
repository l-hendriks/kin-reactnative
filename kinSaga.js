import {
    call,
    fork,
    put,
    select,
    take,
    takeEvery,
} from 'redux-saga/effects';
import { eventChannel } from 'redux-saga';

import * as kinActions from '../actions/kin';
import * as userActions from '../actions/user';

import { getDeviceId } from '../selectors/user';

import { getBackupStatus } from './init';

import env from '../../env';
import Kin from './kinSdk';

const { listeners, kin } = Kin;

function* openAppsDiscovery() {
    yield call([kin, 'openAppsDiscoveryIntent']);
}

function* openBackup() {
    yield call([kin, 'openBackup']);
}

function* openRestore() {
    yield call([kin, 'openRestore']);
}

function* updateBalance() {
    const balance = yield call([kin, 'getBalance']);
    yield put(kinActions.setBalance(balance));
}

function* backupDoneWatcher() {
    const channel = eventChannel((emitter) => {
        listeners.addListener('backupComplete', () => {
            emitter(true);
        });

        return () => {};
    });

    while (true) {
        yield take(channel);
        const deviceId = yield select(getDeviceId);
        yield call(fetch, `${env.ENDPOINT}/setBackupStatus`, {
            body: JSON.stringify({ deviceId }),
            method: 'POST',
        });
        yield call(getBackupStatus, deviceId);
    }
}

function* restoreDoneWatcher() {
    const channel = eventChannel((emitter) => {
        listeners.addListener('restoreSuccess', () => {
            emitter(true);
        });

        return () => {};
    });

    while (true) {
        yield take(channel);

        const deviceId = yield select(getDeviceId);
        const wallet = yield call([kin, 'updateAccount']);
        yield put(userActions.updateAddress(wallet));
        yield call(fetch, `${env.ENDPOINT}/registerWallet`, {
            body: JSON.stringify({ wallet, deviceId, overwrite: true }),
            method: 'POST',
        });
        yield call(updateBalance);
    }
}

function* balanceWatcher() {
    const channel = eventChannel((emitter) => {
        listeners.addListener('updateBalance', () => {
            console.log('UPDATE BALANCE CALLED!');
            emitter(true);
        });

        return () => {};
    });

    while (true) {
        yield take(channel);
        yield call(updateBalance);
    }
}

function* sendKin({ payload }) {
    let tx;
    try {
        tx = yield call(
            [kin, 'sendTransaction'],
            { amount: parseInt(payload.amount, 10), memo: '', to: payload.address },
        );

        if (!tx) {
            yield put(kinActions.sendKinError('Something went wrong with the transactions. Please check the amount and address, then try again.'));
        } else {
            yield put(kinActions.sendKinComplete(tx));
        }
    } catch (e) {
        yield put(kinActions.sendKinError(e.message));
    }
}

export default function* () {
    yield fork(balanceWatcher);
    yield fork(backupDoneWatcher);
    yield fork(restoreDoneWatcher);

    yield takeEvery(kinActions.OPEN_APPS_DISCOVERY, openAppsDiscovery);
    yield takeEvery(kinActions.OPEN_BACKUP, openBackup);
    yield takeEvery(kinActions.OPEN_RESTORE, openRestore);
    yield takeEvery(kinActions.SEND, sendKin);
}
