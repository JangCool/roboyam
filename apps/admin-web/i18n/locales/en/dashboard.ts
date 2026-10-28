/**
 * Dashboard screen — the commerce panel for now (commerce.md section 19). Keys are two parts: `dashboard.what`. Same keys and order as `ko/dashboard.ts`.
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
};
