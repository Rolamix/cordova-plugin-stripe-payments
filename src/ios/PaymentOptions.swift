public struct PaymentOptions {

    // must be in smallest unit e.g. 1000 for $10.00
    public var price: UInt32 = 0
    // 'USD', 'MXN', 'JPY', 'GBP' etc. uppercase.
    public var currency: String = "USD"
    // 'US', 'PH', the ISO 2-letter code, uppercase.
    public var country: String = "US"

    init(dict: [String:Any]) {
        price = dict["price"] as? UInt32 ?? 0
        currency = dict["currency"] as? String ?? "USD"
        country = dict["country"] as? String ?? "US"
    }
}