var exec = require('cordova/exec');

var StripePaymentsPlugin = function () { };

StripePaymentsPlugin._paymentStatusObserverList = [];

StripePaymentsPlugin._processFunctionList = function (array, param) {
  for (var i = 0; i < array.length; i++) {
    array[i](param);
  }
};

var paymentStatusCallbackProcessor = function (state) {
  StripePaymentsPlugin._processFunctionList(StripePaymentsPlugin._paymentStatusObserverList, state);
};

/**
 * Set the high level plugin config.
 * THIS METHOD MUST BE CALLED BEFORE ANY OTHER METHODS ON THE PLUGIN.
 * @param {object} config {publishableKey, ephemeralKeyUrl, appleMerchantId, companyName}
 */
StripePaymentsPlugin.prototype.init = function (config, successCallback, errorCallback) {
  exec(successCallback, errorCallback, 'StripePaymentsPlugin', 'beginStripe', [config]);
};

/**
 * Adds an observer to the stream of events returned while the payment request windows are open.
 */
StripePaymentsPlugin.prototype.addPaymentStatusObserver = function (callback) {
  StripePaymentsPlugin._paymentStatusObserverList.push(callback);
  exec(paymentStatusCallbackProcessor, function () { }, 'StripePaymentsPlugin', 'addPaymentStatusObserver', []);
};

/**
 * Prompts user for Media Library permissions, or returns immediately with their existing
 * permission if the dialog has already been shown in the lifetime of the app
 * This will prompt AGAIN if the user has changed permissions outside the app and returned
 * to the app.
 * @param {object} paymentOptions Options for the payment to collect, in the format
 *  { price, currency, country }. Price must be in the smallest unit of currency, e.g.
 *  1000 for $10.00 USD; currency must be the 3-letter currency code, uppercase, e.g. 'USD';
 *  country must be the ISO 2-letter country code e.g. 'US'.
 * @param {function} successCallback Success callback
 * @param {function} errorCallback Error callback
 */
StripePaymentsPlugin.prototype.showPaymentDialog = function (paymentOptions, successCallback, errorCallback) {
  if (!paymentOptions) {
    return errorCallback({ status: "PAYMENT_ERROR", error: '[CONFIG]: Payment options are required ' });
  }
  exec(successCallback, errorCallback, 'StripePaymentsPlugin', 'showPaymentDialog', [paymentOptions]);
};

/**
 * Finalize the payment. If this requires additional user input, Stripe will take care of it.
 * @param {function} successCallback Success callback
 * @param {function} errorCallback Error callback
 */
StripePaymentsPlugin.prototype.requestPayment = function (successCallback, errorCallback) {
  exec(successCallback, errorCallback, 'StripePaymentsPlugin', 'requestPayment', []);
};


//-------------------------------------------------------------------
var instance = new StripePaymentsPlugin();

if (!window.plugins) {
  window.plugins = {};
}

if (!window.plugins.StripePaymentsPlugin) {
  window.plugins.StripePaymentsPlugin = instance;
}

if (typeof module != 'undefined' && module.exports) {
  module.exports = instance;
}
