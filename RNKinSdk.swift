import Foundation
import KinSDK

@objc(RNKinSdk)
class RNKinSdk: RCTEventEmitter {
    private var client: KinClient? = nil
    private var account: KinAccount? = nil
    private var blockchainEndpoint: String? = nil
    private var watch: BalanceWatch?
    private var backupRestoreManager = KinBackupRestoreManager()

    private var hasListeners: Bool = false

    private var defaultFee: UInt32 = 1;
  
    private var url: String? = nil
    private var appId: String? = nil

    @objc func initialize(
        _ options: [AnyHashable : Any],
        resolver resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
        ) -> Void {

        let network = options["network"] as! String;
        let appId = options["appId"] as! String;
        let url = network == "testNet"
            ? "https://horizon.kinfederation.com"
            : "https://horizon.kinfederation.com" // Add prod here too
        self.url = url
        self.appId = appId

        guard let providerUrl = URL(string: url) else { return () }

        self.blockchainEndpoint = network == "testNet"
            ? "endpoint to backend account creator"
            : "endpoint to backend account creator" // Prod

        do {
            let appId = try AppId(appId)
            self.client = KinClient(
                with: providerUrl,
                network: network == "testNet" ? .mainNet : .mainNet, // Prod
                appId: appId
            )
        }
        catch let error {
            return self.rejectError(reject: reject, message: "Error \(error)")
        }
      
        backupRestoreManager.delegate = self

        resolve(true);
    }

    // we need to override this method and
    // return an array of event names that we can listen to
    override func supportedEvents() -> [String]! {
        return ["updateBalance", "backupComplete", "restoreSuccess"]
    }

    override func startObserving() {
        self.hasListeners = true
    }

    override func stopObserving() {
        self.hasListeners = false
    }

    @objc override func constantsToExport() -> [AnyHashable : Any]! {
        // expose some variables, if necessary
        return [
            "ENVIRONMENT_BETA": "beta",
            "ENVIRONMENT_PRODUCTION": "production",
        ]
    }

    @objc override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    override func sendEvent(withName name: String!, body: Any!) {
        if hasListeners {
            super.sendEvent(withName: name, body: body)
        }
    }

    private func rejectError(
        reject: RCTPromiseRejectBlock,
        message: String? = "unexpected error",
        code: String? = "500"
        ) {
        reject(code, message, NSError(domain: "", code: Int(code!) ?? 500, userInfo: nil))
    }

    private func getFirstAccount() -> KinAccount? {
        return self.client!.accounts.last
    }

    private func createLocalAccount() -> KinAccount? {
        do {
            let account = try self.client!.addAccount()
            return account
        }
        catch let error {
            print("Error creating an account \(error)")
        }
        return nil
    }

    private func initBalanceEventEmitter() {
      let ac = self.account!
      self.watch = try? ac.watchBalance(nil)
      self.watch?.emitter.on(queue: .main, next: { balance in
          if self.bridge != nil {
            self.sendEvent(withName: "updateBalance", body: balance)
          } else {
            print("initBalanceEventEmitter: bridge is nil") // this happens when you reload the RN app withouth fresh start
          }
      })
    }

    private func initEventEmitters() {
        self.initBalanceEventEmitter()
    }

    private func createAccountOnBlockchain(_ completionHandler: @escaping ((String?) -> ())) {
        let createUrlString = "\(self.blockchainEndpoint!)\(self.account!.publicAddress)"

        guard let createUrl = URL(string: createUrlString) else { return }

        let request = URLRequest(url: createUrl)

        let task = URLSession.shared.dataTask(with: request) { (data: Data?, response: URLResponse?, error: Error?) in
            if let error = error {
                print("Account creation on playground blockchain failed with error: \(error)")
                completionHandler(nil)
                return
            }

            self.initEventEmitters();
            completionHandler(self.account!.publicAddress)
        }

        task.resume()
    }

    @objc func createAccount(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
        ) -> Void {

        // Load account
        if let existingAccount = getFirstAccount() {
            self.account = existingAccount
        } else if let newAccount = createLocalAccount() {
            self.account = newAccount
        } else {
            return self.rejectError(reject: reject, message: "Account loading failed");
        }


        // If not registered on the blockchain, do that
        self.account!.status { (status: AccountStatus?, error: Error?) in
            guard let status = status else { return }

            if status == .notCreated {
                return self.createAccountOnBlockchain(resolve)
            }

            self.initEventEmitters();
            return resolve(self.account!.publicAddress)
        }
    }

    @objc func getBalance(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        self.account!.balance { (balance: Kin?, error: Error?) in
            if error != nil || balance == nil {
              print(error)
                return self.rejectError(reject: reject, message: "Cannot get balance")
            }
            resolve(balance)
        }
    }
  
    @objc func updateAccount(
      _ resolve: @escaping RCTPromiseResolveBlock,
      rejecter reject: @escaping RCTPromiseRejectBlock
      ) -> Void {
      guard let providerUrl = URL(string: self.url!) else { return () }
      do {
        let appId = try AppId(self.appId!)
        self.client = KinClient(
          with: providerUrl,
          network: .mainNet,
          appId: appId
        )
        self.account = self.client!.accounts.last
        resolve(self.account!.publicAddress)
      }
      catch let error {
        return self.rejectError(reject: reject, message: "Error \(error)")
      }
    }
  
    @objc func openBackup() -> Void {
      DispatchQueue.main.async {
        let app = UIApplication.shared.delegate as! AppDelegate
        self.backupRestoreManager.backup(self.account!, presentedOnto: app.window!.rootViewController!)
      }
    }
  
    @objc func openRestore() -> Void {
      DispatchQueue.main.async {
        let app = UIApplication.shared.delegate as! AppDelegate
        self.backupRestoreManager.restore(self.client!, presentedOnto: app.window!.rootViewController!)
      }
    }

    /**
    Sends a transaction to the given account.
    */
    private func send(fromAccount account: KinAccount,
                        kinAmount kin: Kin,
                        memo memo: String,
                        to to: String,
                        completionHandler: ((String?) -> ())?) {
        // Get a transaction envelope object
        account.generateTransaction(to: to, kin: kin, memo: memo, fee: self.defaultFee) { (envelope, error) in
            if error != nil || envelope == nil {
                print("Could not generate the transaction")
                if let error = error { print("with error: \(error)")}
                completionHandler?(nil)
                return
            }

            // Sends the transaction
            account.sendTransaction(envelope!) { (txId, error) in
                if error != nil || txId == nil {
                    print("Error send transaction")
                    if let error = error {
                        print("with error: \(error)")
                    }
                    completionHandler?(nil)
                    return
                }
                print("Transaction was sent successfully for \(kin) Kins - id: \(txId!)")
                completionHandler?(txId!)
            }
        }
    }

    @objc func sendTransaction(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {

        return self.send(
            fromAccount: self.account!,
            kinAmount: Decimal(floatLiteral: options["amount"] as! Double),
            memo: options["memo"] as! String,
            to: options["to"] as! String,
            completionHandler: resolve
        )
    }
}

extension RNKinSdk: KinBackupRestoreManagerDelegate {
  func kinBackupRestoreManagerDidComplete(_ manager: KinBackupRestoreManager, kinAccount: KinAccount?) {
    if kinAccount == nil {
      self.sendEvent(withName: "backupComplete", body: "")
    } else {
      self.sendEvent(withName: "restoreSuccess", body: "")
    }
  }
  
  func kinBackupRestoreManagerDidCancel(_ manager: KinBackupRestoreManager) {}
  
  func kinBackupRestoreManager(_ manager: KinBackupRestoreManager, error: Error) {}
}
