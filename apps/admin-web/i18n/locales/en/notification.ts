/**
 * Admin notification drawer. Notification titles and bodies come from the server in the request language, so they are not here.
 * Keys are two parts: `notification.what`. Same keys and order as `ko/notification.ts`.
 */
export default {
    title: 'Notifications',
    unreadSummary: '{count} unread',
    allReadSummary: 'All caught up',
    markAllRead: 'Mark all as read',
    unreadMark: 'Unread',
    emptyTitle: 'No new notifications',
    emptyBody: 'Notifications will show up here.',
    loadFailed: 'Could not load notifications',
    toastTitle: '{n} new notifications',
    toastMore: '{n} more',
    toastOpen: 'Open notification history',
    osTitle: '{product}: {n} new notifications',
    readFailed: 'Could not mark as read. Please try again later.',
};
