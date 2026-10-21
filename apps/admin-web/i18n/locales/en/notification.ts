/**
 * Admin notifications — the bell drawer and the notification events screen (`/settings/notifications`). Notification titles and bodies come from the server in the request language, so they are not here.
 * Area names reuse the permission areas (`permission.area.*`); only `security`, which permissions do not have, is here (`areaSecurity`). Event names, descriptions and recipient text are also resolved by the server in the request language, so they are not here.
 * Keys are two parts: `notification.what` (severity `severity.*` has three). Same keys and order as `ko/notification.ts`.
 */
export default {
    // ── Bell drawer ──
    title: 'Notifications',
    unreadSummary: '{count} unread',
    allReadSummary: 'All caught up',
    markAllRead: 'Mark all as read',
    unreadMark: 'Unread',
    emptyTitle: 'No new notifications',
    emptyBody: 'Notifications will show up here.',
    loadFailed: 'Could not load notifications',
    loadMore: 'Show more',
    loadMoreFailed: 'Could not load earlier notifications. Please try again later.',
    toastTitle: '{n} new notifications',
    toastMore: '{n} more',
    toastOpen: 'Open notification history',
    osTitle: '{product}: {n} new notifications',
    readFailed: 'Could not mark as read. Please try again later.',

    // ── Severity (drawer, event management, profile) ──
    severity: {
        info: 'Info',
        success: 'Done',
        warning: 'Warning',
        critical: 'Critical',
    },
    areaSecurity: 'Security',

    // ── Notification events (/settings/notifications) ──
    adminTitle: 'Notification events',
    adminSubtitle: 'Choose, for each thing that happens in the admin app, whether it sends a notification and whether it is also emailed.',
    adminReload: 'Reload',
    adminLoadFailed: 'Could not load notification events',
    adminEmptyTitle: 'No notification events',
    adminEmptyBody: 'Events are defined by the system and show up here once their features exist.',
    adminReadOnly: 'You do not have permission to change notification events, so this is view-only.',
    adminNote: 'Switches save as soon as you press them. In-app notifications (the bell) always go out for events that are on, and who receives each event is fixed per event, so it cannot be changed here.',
    colEvent: 'Event',
    colRecipient: 'Recipients',
    colSeverity: 'Severity',
    colEnabled: 'On',
    colEmail: 'Email',
    colSent: 'Sent (30 days)',
    mandatory: 'Required',
    mandatoryHint: 'Required events cannot be turned off.',
    notReady: 'Coming soon',
    notReadyHint: 'The feature that sends this notification does not exist yet. You can turn it on once it does.',
    emailNotAvailable: 'This event is not sent by email.',
    noManageReason: 'You do not have permission to change notification events.',
    enabledLabel: '{name}: on',
    emailLabel: '{name}: send by email',
    sentCount: '{n}',
    adminEnabledOn: '{name}: turned on',
    adminEnabledOff: '{name}: turned off',
    adminEmailOn: '{name}: also sent by email',
    adminEmailOff: '{name}: no longer sent by email',
};
