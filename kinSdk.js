import {
    DeviceEventEmitter,
    NativeEventEmitter,
    NativeModules,
    Platform,
} from 'react-native';

const { RNKinSdk } = NativeModules;

const events = Platform.select({
    ios: new NativeEventEmitter(RNKinSdk),
    android: DeviceEventEmitter,
});

export default {
    kin: RNKinSdk,
    listeners: events,
};
