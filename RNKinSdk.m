
#import "React/RCTBridgeModule.h"
#import "React/RCTEventEmitter.h"

@interface RCT_EXTERN_MODULE(RNKinSdk, RCTEventEmitter)

RCT_EXTERN_METHOD(
                  initialize:
                  (NSDictionary *)options
                  resolver: (RCTPromiseResolveBlock)resolve
                  rejecter: (RCTPromiseRejectBlock)reject
                  )
RCT_EXTERN_METHOD(
                  createAccount:
                  (RCTPromiseResolveBlock)resolve
                  rejecter: (RCTPromiseRejectBlock)reject
                  )
RCT_EXTERN_METHOD(
                  getBalance:
                  (RCTPromiseResolveBlock)resolve
                  rejecter: (RCTPromiseRejectBlock)reject
                  )
RCT_EXTERN_METHOD(
                  sendTransaction:
                  (NSDictionary *)options
                  resolver: (RCTPromiseResolveBlock)resolve
                  rejecter: (RCTPromiseRejectBlock)reject
                  )
RCT_EXTERN_METHOD(openBackup)
RCT_EXTERN_METHOD(openRestore)
RCT_EXTERN_METHOD(
                  updateAccount:
                  (RCTPromiseResolveBlock)resolve
                  rejecter: (RCTPromiseRejectBlock)reject
                  )
@end
