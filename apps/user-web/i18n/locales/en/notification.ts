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
    dmTitle: 'Messages',
    dmBadgeUnread: 'Messages, {count} unread conversations',
    dmBadgeUnreadOver: 'Messages, {max}+ unread conversations',

    // Notifications screen (`/notifications`) and panel links
    seoDescription: 'Notifications you received on {site}.',
    viewAll: 'See all notifications',
    settingsLink: 'Notification settings',
    listLabel: 'Notifications list',
    filterLabel: 'Filter notifications',
    filterComment: 'Comments & replies',
    filterSystem: 'Account',
    unreadOnly: 'Unread only',
    readAllFiltered: 'Mark these as read',
    readAllDone: 'Marked all as read',
    emptyFiltered: 'No notifications in this group',
    unreadNone: 'No unread notifications among those loaded',
    unreadNoneMore: 'Load more to look through older notifications',
    pageSummary: '{total} notifications · {unread} unread',
    retention: 'Notifications are kept for {days} days.',

    // Notification settings (`/settings/notifications`)
    settingsTitle: 'Notification settings',
    settingsSeo: 'Choose which notifications you get on {site}.',
    settingsLoading: 'Loading notification settings',
    settingsLoadFailed: 'Couldn’t load notification settings',
    settingsIntro: 'Turn off notifications you don’t want. The email switch appears only for notifications that are also sent by email.',
    settingsEmpty: 'There are no notifications you can get right now',
    areaContent: 'My posts & comments',
    areaMember: 'My account',
    areaSafety: 'Reports & moderation',
    channelInApp: 'In-app',
    channelEmail: 'Email',
    lockedAccount: 'Account notice — can’t be turned off',
    lockedAction: 'Moderation notice — can’t be turned off',
    emailNotVerified: 'Verify your email to get these by email too.',
    emailNoAddress: 'Your account has no email, so you can’t get these by email.',
    lockedSite: 'Set by the site — can’t be turned off',
};
