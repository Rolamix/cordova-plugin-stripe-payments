import Alamofire

public class StripePaymentsPluginConfig {
  public var publishableKey: String? = nil
  public var ephemeralKeyUrl: String? = nil
  public var appleMerchantId: String? = nil
  public var companyName: String? = nil
  public var requestPaymentImmediately: Boolean? = true
  public var extraHTTPHeaders: [HTTPHeader]? = []
  // TODO:
  // We can add an option to execute the charge API-side, in which case
  // the developer would also need to provide their 'charge' endpoint,
  // meaning that the success/fail return value becomes meaningful.
  // The extraHTTPHeaders now allows us to do that, to be done later..

  // TODO need xcode for this
  func parseExtraHeaders(dict: [String:String]) {
    // extraHTTPHeaders.push(new HTTPHeader(dict[something]))
  }
}

let PluginConfig = StripePaymentsPluginConfig()
