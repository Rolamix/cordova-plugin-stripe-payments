import UIKit
import Stripe

class StripePaymentContextFooterView: UIView {

    var insetMargins: UIEdgeInsets = UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10)

    var text: String = "" {
        didSet {
            textLabel.text = text
        }
    }

    var theme: STPTheme = STPTheme.default() {
        didSet {
            textLabel.font = theme.smallFont
            textLabel.textColor = theme.secondaryForegroundColor
        }
    }

    fileprivate let textLabel = UILabel()

    convenience init(text: String, align: NSTextAlignment = .center) {
        self.init()
        textLabel.numberOfLines = 0
        textLabel.textAlignment = align
        textLabel.adjustsFontSizeToFitWidth = true
        self.addSubview(textLabel)

        self.text = text
        textLabel.text = text

    }

    override func layoutSubviews() {
        textLabel.frame = self.bounds.inset(by: insetMargins)
    }

    override func sizeThatFits(_ size: CGSize) -> CGSize {
        // Add 10 pt border on all sides
        var insetSize = size
        insetSize.width -= (insetMargins.left + insetMargins.right)
        insetSize.height -= (insetMargins.top + insetMargins.bottom)

        var newSize = textLabel.sizeThatFits(insetSize)

        newSize.width += (insetMargins.left + insetMargins.right)
        newSize.height += (insetMargins.top + insetMargins.bottom)

        return newSize
    }


}
