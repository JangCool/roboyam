/**
 * Payment texts — the fake checkout (`PaymentFakeCheckoutDialog`) and the development test payment screen (`/dev/payment`). Same keys and order as `ko/payment.ts`.
 */
export default {
    // Common
    orderName: 'Order',
    amount: 'Amount',
    paidAmount: 'Paid amount',
    paidAt: 'Paid at',
    expiresAt: 'Valid until',
    paymentId: 'Payment ID',
    gateway: 'Checkout',
    gatewayName: {
        FAKE: 'Fake (development)',
        PORTONE: 'PortOne',
    },
    won: '₩{amount}',
    wonSuffix: ' KRW',
    minuteSuffix: ' min',
    autoCancel: 'Automatic cancel',
    status: {
        READY: 'Awaiting payment',
        PAID: 'Paid',
        PARTIAL_CANCELED: 'Partly canceled',
        CANCELED: 'Canceled',
        EXPIRED: 'Expired',
    },
    cancelStatus: {
        PENDING: 'Cancel waiting',
        SENDING: 'Cancel sending',
        SUCCEEDED: 'Canceled',
        GAVE_UP: 'Cancel failed (operator checking)',
        MANUAL: 'Handled by an operator',
    },

    // Fake checkout
    fakeTitle: 'Fake checkout (development)',
    fakeNotice: 'A checkout for development and tests. No real money moves. Pick an outcome to test the payment flow.',
    fakeOutcome: 'Outcome',
    fakeOption: {
        PAID: 'Pay normally',
        FAILED: 'Payment fails',
        AMOUNT: 'Pay a different amount',
        LATE: 'Pay late',
    },
    fakeOptionHelp: {
        PAID: 'Pays the order amount.',
        FAILED: 'The payment does not go through, like a declined card.',
        AMOUNT: 'Pays an amount different from the order — the server should refuse it and cancel automatically.',
        LATE: 'Treated as paid after the validity ended — the server should refuse it and cancel automatically.',
    },
    fakeAmount: 'Amount to pay',
    fakeAmountHelp: 'The order amount is ₩{amount}.',
    fakeAmountRequired: 'Enter the amount to pay.',
    fakeAmountSame: 'Enter an amount different from the order amount.',
    fakeClose: 'Close',
    fakePay: 'Pay',

    // Test payment screen (/dev/payment)
    devBadge: 'Development only',
    devTitle: 'Test payment',
    devIntro: 'Test the payment module end to end before the charge and group buy screens exist. Create a test order, open the checkout and pick an outcome. The same payment appears in the admin payment orders.',
    devCreateTitle: '1. Create a test order',
    devCreateIntro: 'Choose the amount and how long the payment stays valid. A payment after that is refused (late payment).',
    devAmount: 'Amount',
    devAmountHint: '₩100 – ₩1,000,000',
    devValidMinutes: 'Valid for',
    devValidMinutesHint: '1 – 60 min',
    devCreate: 'Create test order',
    devCreateAgain: 'Create a new test order',
    devCheckoutTitle: '2. Checkout',
    devPortoneLater: 'The server is set to use the PortOne checkout, which is connected in the next stage. For now only the fake checkout (local) can be tested end to end.',
    devOpenCheckout: 'Open checkout',
    devNote: {
        FAILED: 'The payment did not go through, so no completion request was sent. The checkout can’t be reopened — create a new test order.',
        CLOSED: 'You closed the checkout. You can open it again.',
    },
    devResultTitle: '3. Result',
    devReload: 'Reload result',
    devAccepted: 'The payment was accepted. The test order is complete.',
    devAnomaly: {
        AMOUNT_MISMATCH: 'The paid amount differs from the order, so it was refused. The paid amount is fully canceled automatically.',
        DOMAIN_REJECTED: 'The order could not accept the payment, so it was refused. It is fully canceled automatically.',
        LATE_PAYMENT: 'The payment came after the validity ended, so it was refused. It is fully canceled automatically.',
    },
    devNotAccepted: 'The payment is not confirmed yet.',
    devAdminHint: 'Use “Reload result” to see whether the automatic cancel finished. Admin › Payments › Payment orders and Payment issues show the same payment’s history.',
};
