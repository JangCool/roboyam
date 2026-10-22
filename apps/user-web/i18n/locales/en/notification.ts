/**
 * Notification bell, panel and unread badge — same keys and order as `ko/notification.ts`. Each notification's sentence comes from the server.
 */
export default {
    // Names
    title: 'Notifications',
    panelClose: 'Close notifications panel',
    recentList: 'Recent notifications',
    unread: 'Unread',
    readAll: 'Mark all as read',

    // States
    loadFailed: 'Couldn’t load notifications',
    loadingSummary: 'Loading notifications',
    noneSummary: 'No notifications',
    summary: '{total} recent notifications · {unread} unread',
    emptyTitle: 'No new notifications',
    emptyDescription: 'We’ll let you know here when someone comments or replies',
    readAllFailed: 'Couldn’t mark everything as read. Please try again shortly.',

    // Badge names (for screen readers)
    badgeUnread: 'Notifications, {count} unread',
    badgeUnreadOver: 'Notifications, {max}+ unread',
};
