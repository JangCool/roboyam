/** Analytics collection settings (`/settings/analytics`) — turn visit, notification and board statistics on or off per item (screen confirmed 2026-11-04) */
export default {
    title: 'Analytics collection',
    subtitle: 'Turn visit, notification and board statistics on or off one by one. Everything starts off.',
    readOnly: 'You can only view these settings because you do not have permission to change them.',
    forbiddenBody: 'You do not have permission to view analytics collection settings.',
    loadFailed: 'Could not load analytics collection settings.',
    saved: 'Analytics collection settings saved.',
    saveFieldsRejected: 'Some values are not valid. Check the marked fields.',

    // ── How it works ─
    howTitle: 'How collection works',
    howVisitorId: 'Visitors are told apart by a random visitor ID kept in browser storage. IP addresses and raw browser details are not stored.',
    howDelayVisits: 'The server writes visit numbers in batches every minute, so they can be up to 1 minute behind.',
    howDelayBoard: 'Board activity is totaled every 10 minutes, so it can be up to 10 minutes behind.',
    howDefaultOff: 'Collection starts when you turn an item on. There are no numbers for the time before that.',
    updatedBy: 'Last changed: {at} · {by}',
    neverUpdated: 'Not changed yet.',

    // ── Sections ─
    visitSection: 'Visit collection',
    otherSection: 'Notification & board totals',
    visitOffNote: 'Visit collection is off, so the items below do nothing. Your choices are kept.',

    // ── Items ─
    label: {
        enabled: 'Visit collection',
        visits: 'Visitors, page views, online now',
        rawEvents: 'Raw visit records',
        referrers: 'Traffic sources',
        flows: 'Navigation flows',
        environment: 'Environment (device, browser, OS, screen)',
        searchTerms: 'Search terms',
        bots: 'Bots & crawlers',
        notificationSends: 'Notification send log',
        boardActivity: 'Board activity totals',
    },
    help: {
        enabled: 'When off, visitors\' browsers send no visit signals at all. The visit items below only work while this is on.',
        visits: 'Counts visitors, page views and who is online now (last 5 minutes), views per page ("Popular pages", page ranking) and weekly/monthly unique visitors.',
        rawEvents: 'Keeps the raw record of which page was viewed when. It feeds the per-minute page views and recent pages in Visitor stats › Real-time. Page rankings keep counting when this is off.',
        referrers: 'Counts where visits came from (search, social, direct, other sites, campaigns) and the top referring sites.',
        flows: 'Counts which page visitors moved to from which page.',
        environment: 'Counts device, browser, OS and screen-width band per visit (first page of a session). Only the categories are kept, never the raw browser string.',
        searchTerms: 'Counts terms used in this site\'s search and searches with no results. Terms that look like phone numbers, emails or resident registration numbers, shorter than 2 or longer than 50 characters are not stored.',
        bots: 'Counts requests from search engine crawlers and automated tools. Bots are already left out of visit stats.',
        notificationSends: 'Records success and failure per channel each time a notification is sent.',
        boardActivity: 'Totals posts, comments, sign-ups and activity per board.',
    },
    loadLow: 'Low load',
    loadMedium: 'Medium load',
    loadNote: {
        visits: 'Written in batches every minute',
        rawEvents: 'Several rows written in batches every minute',
        referrers: 'Written with visit numbers every minute',
        flows: 'Moves written in batches every minute',
        environment: 'First page of each session, written every minute',
        searchTerms: 'Counted on search, written every minute',
        bots: 'Counted in memory, written every minute',
        notificationSends: 'One row per send',
        boardActivity: 'A database job totals every 10 minutes',
    },
    usedBy: 'Used by',
    usedByPage: '{group} › {page}',
    retentionLabel: 'Raw record retention',
    retentionHelp: 'Keeps the chosen number of months, counting this month, and deletes older months as a whole each month.',
    /** Retention options (months, counting this month) */
    retentionOption: {
        2: '2 months (this and last month)',
        3: '3 months (incl. this month)',
        6: '6 months (incl. this month)',
        12: '12 months (incl. this month)',
    },
    /** Retention period put into the privacy notice */
    retentionPolicy: {
        2: 'up to 2 months — this month and last month',
        3: 'up to 3 months, counting this month',
        6: 'up to 6 months, counting this month',
        12: 'up to 12 months, counting this month',
    },
    /** Short retention text (collection health, settings history) */
    retentionMonths: '{n} months',
    /** Day-based retention in older settings history */
    retentionDays: '{n} days',
    privacyNotice: 'To keep raw visit records (pages viewed, traffic sources), your privacy policy must describe them and how long they are kept ({period}).',
    privacyLink: 'Go to terms & privacy policy',
    searchPrivacyNotice: 'To collect search terms, describe it and how long they are kept in your privacy policy. Terms that look like phone numbers, emails or resident registration numbers are never stored.',

    // ── History & leaving ─
    historyHint: 'Changes to analytics collection settings, newest first.',
    leaveTitle: 'You have unsaved changes',
    leaveMessage: 'If you leave this page, your changes will be lost.',
    leaveConfirm: 'Leave without saving',
};
