/**
 * Notification bell, panel and unread badges (notifications and messages) — same keys and order as `ko/notification.ts`.
 */
export default {
    // Names
    title: 'Notifications',
    settings: 'Notification settings',
    panelClose: 'Close notifications panel',
    recentList: 'Recent notifications',
    unread: 'Unread',
    viewAll: 'View all notifications ›',

    // States
    loadFailed: 'Couldn’t load notifications',
    loadingSummary: 'Loading notifications',
    noneSummary: 'No notifications',
    summary: '{total} recent notifications · {unread} unread',
    emptyTitle: 'No new notifications',
    emptyDescription: 'We’ll let you know here when someone comments or replies',

    // Badge names (for screen readers)
    badgeUnread: 'Notifications, {count} unread',
    badgeUnreadOver: 'Notifications, {max}+ unread',
    dmTitle: 'Messages',
    dmBadgeUnread: 'Messages, {count} unread conversations',
    dmBadgeUnreadOver: 'Messages, {max}+ unread conversations',
};
