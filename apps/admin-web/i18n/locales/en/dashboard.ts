/**
 * Dashboard screen — the to-do panel (businesses · ads · hot deals) · the commerce panel (commerce.md section 19). Keys are two parts: `dashboard.what`. Same keys and order as `ko/dashboard.ts`.
 */
export default {
    title: 'Dashboard',
    emptyTitle: 'Nothing to show yet',
    emptyBody: 'Metrics and lists will appear here as admin features are built.',

    // ── Commerce panel ─
    commerceTitle: 'Commerce',
    generatedAt: 'As of {at}',
    refresh: 'Refresh',
    loadFailed: 'Could not load the commerce overview.',
    preparing: 'Waiting to prepare',
    preparingNote: 'Payment confirmed · not yet preparing',
    openRequests: 'Open cancel & return requests',
    openRequestsNote: '2-day warning {warn} · 3-day critical {critical}',
    paymentAnomalies: 'Payment issues',
    expiringHolds: 'Holds about to expire',
    expiringHoldsNote: 'Payment time ends within 2 minutes',
    lowStock: 'Group buys low on stock',
    lowStockNote: '10% or 5 or fewer left',
    shipOverdue: 'Unshipped past ship date',
    shipOverdueNote: 'Check whether a delay notice is needed',
    deliveryStale: 'Shipped over {days} days ago',
    deliveryStaleNote: 'Delivery not marked — the withdrawal window has not started',

    // ── To-do panel (04-ia-review Q5 — replaces the combined application queue) ─
    todoTitle: 'To do',
    todoLoadFailed: 'Could not load the to-do list.',
    todoAdReview: 'Ad review',
    todoHotdealReview: 'Hot deal review',
    todoReviewNote: 'Business applications — payment opens after approval',
    todoDeposit: 'Awaiting deposit',
    todoDepositNote: 'Manual payment — confirm after contract copy and deposit',
    todoDepositOverdue: 'Deposit overdue',
    todoDepositOverdueNote: 'Extend the deadline or cancel',
    todoRefund: 'Refund review',
    todoRefundNote: 'Refund requests on running items',
    todoBusinessReview: 'Business verification',
    todoBusinessReviewNote: 'New applications, reapplications and re-reviews',
    todoDomainLink: '{domain} {count}',
};
