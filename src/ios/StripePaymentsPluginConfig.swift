import Alamofire

// TODO:
// We can add an option to execute the charge API-side, in which case
// the developer would also need to provide their 'charge' endpoint,
// meaning that the success/fail return value becomes meaningful.
// The extraHTTPHeaders now allows us to do that, to be done later..

public class StripePaymentsPluginConfig {
    public var publishableKey: String = ""
    public var ephemeralKeyUrl: String = ""
    public var appleMerchantId: String = ""
    public var companyName: String = ""
    public var requestPaymentImmediately: Bool = true
    public var extraHTTPHeaders: HTTPHeaders = [:]

    // TODO need xcode for this
    func parseExtraHeaders(dict: [String:String]) {
        // extraHTTPHeaders.push(new HTTPHeader(dict[something]))
        // this actually needs to replace them..dunno. I mean they'll just have
        // duplicates and HTTPHeaders should be able to resolve them by updating the header
        // if they're already there, using the latest value (later index in array).
        // must confirm that works.
    }
}

let PluginConfig = StripePaymentsPluginConfig()
