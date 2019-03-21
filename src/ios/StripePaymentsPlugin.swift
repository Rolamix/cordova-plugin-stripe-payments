
import UIKit
import Stripe

// https://stripe.com/docs/apple-pay/apps
// https://stripe.com/docs/mobile/ios/standard
// https://github.com/zyra/cordova-plugin-stripe/blob/v2/src/ios/CordovaStripe.m
// https://github.com/stripe/stripe-connect-rocketrides/blob/master/server/routes/api/rides.js
// https://github.com/stripe/stripe-connect-rocketrides/blob/master/ios/RocketRides/RideRequestViewController.swift
// https://github.com/stripe/stripe-ios/blob/master/Example/Standard%20Integration%20(Swift)/CheckoutViewController.swift
// https://github.com/stripe/stripe-ios/blob/master/Example/Standard%20Integration%20(Swift)/MyAPIClient.swift

@objc(StripePaymentsPlugin) class StripePaymentsPlugin: CDVPlugin, STPPaymentContextDelegate {

    private var paymentStatusCallback: String? = nil
    private let customerContext: STPCustomerContext
    private let paymentContext: STPPaymentContext

    override func pluginInitialize() {
        super.pluginInitialize()
    }

    @objc(addPaymentStatusObserver:)
    func addPaymentStatusObserver(command: CDVInvokedUrlCommand) {
        paymentStatusCallback = command.callbackId
    }

    // MARK: Init Method

    @objc(init:)
    public func init(command: CDVInvokedUrlCommand) {
        let error = "The Stripe Publishable Key and ephemeral key generation URL are required"

        guard let dict = command.arguments[0] as? [String:Any] ?? nil else {
            errorCallback(command.callbackId, [ "status": "INIT_ERROR", "error": error ])
            return
        }

        // Would be nice to figure a way to customize the UI, as Rocket Rides did,
        // https://github.com/stripe/stripe-connect-rocketrides/blob/master/ios/RocketRides/UIColor%2BPalette.swift
        // but this would be alot of work and a clumsy API so put that on hold to come up with a better way.
        PluginConfig.publishableKey = dict["publishableKey"] as? String ?? ""
        PluginConfig.ephemeralKeyUrl = dict["ephemeralKeyUrl"] as? String ?? ""
        PluginConfig.appleMerchantId = dict["appleMerchantId"] as? String ?? ""
        PluginConfig.companyName = dict["companyName"] as? String ?? ""
        PluginConfig.requestPaymentImmediately = dict["requestPaymentImmediately"] as? Boolean ?? true

        if headersDict = dict["extraHTTPHeaders"] as? [String:String] {
            PluginConfig.parseExtraHeaders(headersDict)
        }

        if !self.verifyConfig() {
            errorCallback(command.callbackId, [ "status": "INIT_ERROR", "error": error ])
            return
        }

        APIClient.shared.ephemeralKeyUrl = PluginConfig.ephemeralKeyUrl
        STPPaymentConfiguration.shared().companyName = PluginConfig.companyName
        STPPaymentConfiguration.shared().publishableKey = PluginConfig.publishableKey

        if !PluginConfig.appleMerchantId.isEmpty {
            STPPaymentConfiguration.shared().appleMerchantIdentifier = PluginConfig.appleMerchantId
        }

        customerContext = STPCustomerContext(keyProvider: APIClient.shared)
        paymentContext = STPPaymentContext(customerContext: customerContext)

        paymentContext.delegate = self
        paymentContext.hostViewController = self.viewController

        successCallback(command.callbackId, [ "status": "INIT_SUCCESS" ])
    }


    // MARK: Public plugin API

    @objc(showPaymentDialog:)
    public func showPaymentDialog(command: CDVInvokedUrlCommand) {
        var error = "[CONFIG]: Error parsing payment options or they were not provided"

        // Ensure we have valid config.
        guard let options = command.arguments[0] as? [String:Any] ?? nil else {
            errorCallback(command.callbackId, [ "status": "PAYMENT_DIALOG_ERROR", "error": error ])
            return
        }

        if !self.verifyConfig() {
            error = "[CONFIG]: Config is not set, init() must be called before using plugin"
            errorCallback(command.callbackId, [ "status": "PAYMENT_DIALOG_ERROR", "error": error ])
            return
        }

        let paymentOptions = PaymentOptions(options)
        paymentContext.paymentAmount = paymentOptions.price
        paymentContext.paymentCurrency = paymentOptions.currency
        paymentContext.paymentCountry = paymentOptions.country

        // This dialog collects a payment method from the user. When they close it, you get a context
        // change event with the payment info. NO charge has been created at that point, NO source
        // has been created from the payment method. All that has happened is the user entered
        // payment data and clicked 'ok'. That's all.
        // After that dialog closes - after paymentContextDidChange is called with
        // a selectedPaymentMethod - THEN you want to call requestPayment.
        paymentContext.presentPaymentMethodsViewController()
        successCallback(command.callbackId, [ "status": "PAYMENT_DIALOG_SHOWN" ])
    }

    @objc(requestPayment:)
    public func requestPayment(command: CDVInvokedUrlCommand) {
        // Ensure we have valid config.
        if !self.verifyConfig() {
            let error = "[CONFIG]: Config is not set, init() must be called before using plugin"
            errorCallback(command.callbackId, [ "status": "REQUEST_PAYMENT_ERROR", "error": error ])
            return
        }

        doRequestPayment(command.callbackId)
    }

    func doRequestPayment(_ callbackId: String) {
        paymentContext.requestPayment()
        successCallback(callbackId, [ "status": "REQUEST_PAYMENT_STARTED" ])
    }


    // MARK: STPPaymentContextDelegate

    func paymentContext(_ paymentContext: STPPaymentContext, didFailToLoadWithError error: Error) {
        let alertController = UIAlertController(
            preferredStyle: .alert,
            retryHandler: { (action) in
                // Retry payment context loading
                paymentContext.retryLoading()
            }
        )

        var message = error?.localizedDescription ?? ""
        var callbackMessage: String = ""

        if let customerKeyError = error as? APIClient.CustomerKeyError {
            switch customerKeyError {
            case .ephemeralKeyUrl:
                // Fail silently until base url string is set
                callbackMessage = "[ERROR]: Please assign a value to `APIClient.shared.ephemeralKeyUrl` before continuing. See `StripePaymentsPlugin.swift`."
            case .invalidResponse:
                // Use customer key specific error message
                callbackMessage = "[ERROR]: Missing or malformed response when attempting to call `APIClient.shared.createCustomerKey`. Please check internet connection and backend response."
                message = "Could not retrieve customer information"
            }
        }
        else {
            // Use generic error message
            callbackMessage = "[ERROR]: Unrecognized error while loading payment context: \(error)"
            message = error.localizedDescription ?? "Could not retrieve payment information"
        }

        print(callbackMessage)
        errorCallback(paymentStatusCallback, ["error": callbackMessage], keepCallback: true)

        alertController.setMessage(message) // ??
        self.viewController.present(alertController, animated: true, completion: nil)
    }

    func paymentContextDidChange(_ paymentContext: STPPaymentContext) {
        var isLoading = paymentContext.isLoading
        var isPaymentReady = paymentContext.selectedPaymentMethod != nil
        var label = ""
        var image = ""

        // https://stackoverflow.com/questions/11592313/how-do-i-save-a-uiimage-to-a-file
        if selectedPaymentMethod = paymentContext.selectedPaymentMethod {
            label = selectedPaymentMethod.label
            image = ""
            let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
            if let filePath = paths.first?.appendingPathComponent("StripePaymentMethod.jpg") {
                // Save image.
                do {
                    try UIImageJPEGRepresentation(selectedPaymentMethod.image, 1)?.write(to: filePath, options: .atomic)
                    image = filePath
                }
                catch { }
            }
        }

        let resultMsg: [String : Any] = [
            "status": "PAYMENT_STATUS_CHANGED",
            "isLoading": isLoading,
            "isPaymentReady": isPaymentReady,
            "label": label,
            "image": image
        ]

        successCallback(paymentStatusCallback, resultMsg, keepCallback: true)

        if isPaymentReady && PluginConfig.requestPaymentImmediately {
            doRequestPayment(paymentStatusCallback)
        }
    }

    // This callback is triggered when requestPayment() completes successfully to create a Source.
    // This Source can then be used by the app to process a payment (create a charge, subscription etc.)
    func paymentContext(_ paymentContext: STPPaymentContext, didCreatePaymentResult paymentResult: STPPaymentResult, completion: @escaping STPErrorBlock) {
        // Create charge using payment result
        let resultMsg: [String : Any] = [
            "status": "PAYMENT_CREATED",
            "source": paymentResult.source.stripeID
        ]

        successCallback(paymentStatusCallback, resultMsg, keepCallback: true)
        completion(nil)
    }

    // This callback triggers due to:
    // a) the result of the payment info prompt, if the user cancels payment method selection
    // b) the result of requestPayment, if the user was prompted for more data and cancels
    // c) the result of requestPayment, if they attempt to verify a payment method and it fails
    // d) the output of paymentContext(didCreatePaymentResult:), in our case, always called with success.
    // In a full iOS app, in paymentContext(didCreatePaymentResult:) you would call your backend,
    // and return an appropriate error or success; however for the plugin, we are returning the
    // payment Source to the app, so we don't need paymentContext(didCreatePaymentResult:) to do anything
    // besides return success.
    // In later versions we may add the option for that method to call your backend directly so you
    // don't have to.
    func paymentContext(_ paymentContext: STPPaymentContext, didFinishWith status: STPPaymentStatus, error: Error?) {
        var resultMsg: [String : Any] = [:]

        switch status {
        case .success:
            resultMsg = [ "status": "PAYMENT_COMPLETED_SUCCESS" ]
        case .error:
            // Use generic error message
            print("[ERROR]: Unrecognized error while finishing payment: \(String(describing: error))");
            self.viewController.present(UIAlertController(message: "Could not complete payment"), animated: true)

            resultMsg = [
                "status": "PAYMENT_COMPLETED_ERROR",
                error: "[ERROR]: Unrecognized error while finishing payment: \(String(describing: error))"
            ]

            errorCallback(paymentStatusCallback, resultMsg, keepCallback: true)
            return
        case .userCancellation:
            resultMsg = [ "status": "PAYMENT_CANCELED" ]
        }

        successCallback(paymentStatusCallback, resultMsg, keepCallback: true)
    }

    func successCallback(_ callbackId: String, _ data: [String:Any?], keepCallback: Bool = false) {
        var pluginResult = CDVPluginResult(
            status: .ok,
            messageAs: data
        )
        pluginResult?.setKeepCallbackAs(keepCallback)
        self.commandDelegate!.send(pluginResult, callbackId: callbackId)
    }

    func errorCallback(_ callbackId: String, _ data: [String:Any?], keepCallback: Bool = false) {
        var pluginResult = CDVPluginResult(
            status: .error,
            messageAs: data
        )
        pluginResult?.setKeepCallbackAs(keepCallback)
        self.commandDelegate!.send(pluginResult, callbackId: callbackId)
    }

    func verifyConfig() -> Bool {
        return PluginConfig.publishableKey != nil && !PluginConfig.publishableKey!.isEmpty
            && PluginConfig.ephemeralKeyUrl != nil && !PluginConfig.ephemeralKeyUrl!.isEmpty
    }

}
