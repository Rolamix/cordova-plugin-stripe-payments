import Alamofire
import Stripe

class StripeAPIClient: NSObject, STPCustomerEphemeralKeyProvider {

    static let shared = StripeAPIClient()

    var ephemeralKeyUrl = ""

    // MARK: STPCustomerEphemeralKeyProvider
    enum CustomerKeyError: Error {
        case ephemeralKeyUrl
        case invalidResponse
    }

    func createCustomerKey(withAPIVersion apiVersion: String, completion: @escaping STPJSONResponseCompletionBlock) {
        let endpoint = ephemeralKeyUrl // "/api/passengers/me/ephemeral_keys"

        guard let url = URL(string: endpoint) else {
                completion(nil, CustomerKeyError.ephemeralKeyUrl)
                return
        }

        let parameters: [String: Any] = ["api_version": apiVersion]
        let headers: HTTPHeaders = [
            // "Authorization": "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ==",
            "Accept": "application/json"
        ]

        // TODO need xcode for this.
        if PluginConfig.extraHTTPHeaders.count > 0 {
            // for each HTTPHeader in extraHTTPHeaders
            // headers.add(header)
        }

        Alamofire.request(url, method: .post, parameters: parameters, headers: headers)
            .validate(statusCode: 200..<300)
            .responseJSON { responseJSON in
                switch responseJSON.result {
                case .success(let json):
                    guard let data = json as? [String: AnyObject] else {
                        completion(nil, CustomerKeyError.invalidResponse)
                        return
                    }
                    completion(data, nil)
                case .failure(let error):
                    completion(nil, error)
                }
                // The docs also have this approach, not sure which is correct:
                // guard let json = responseJSON.result.value as? [AnyHashable: Any] else {
                //     completion(nil, CustomerKeyError.invalidResponse)
                //     return
                // }
                // completion(json, nil)
        }
    }

}
