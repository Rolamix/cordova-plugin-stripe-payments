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
    public var maximumKeyRetries: Int = 0
    public var paymentOptionsFooter: String = ""
    public var addCardFooter: String = ""
    public var extraHTTPHeaders: [String:String] = [:]
}

let PluginConfig = StripePaymentsPluginConfig()
