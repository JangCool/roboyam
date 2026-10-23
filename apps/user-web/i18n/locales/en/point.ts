/**
 * Points text used by the app shell (header menu, hamburger menu). Same keys and order as `ko/point.ts`.
 */
export default {
    // Entry to the points screen
    entry: 'Points & check-in',
    entryWithReward: 'Points & check-in · +{amount}P',
    // When check-in doesn't exist (`attendanceAvailable=false`) or before the summary arrives
    entryNoAttendance: 'Points',

    // Balance line — {points} looks like "1,280P"
    balanceLine: 'Points {points}',

    // Check-in button
    attend: 'Check in today',
    attendWithReward: 'Check in today +{amount}P',

    // Balance + check-in hint
    summaryWithAttend: '{balance} · {attend}',

    // Points screen (`/me/points`) and the points summary on My page
    title: 'Points',
    seoDescription: 'Your {site} points balance and history.',
    history: 'Points history',
    loading: 'Loading points',
    loadFailed: 'Couldn’t load points',
    balance: 'Balance',
    todayTitle: 'Earned today',
    todayPost: 'Posts',
    todayComment: 'Comments',
    todayLikeBonus: 'Like bonus',
    countOfCap: '{count}/{cap}',
    capReached: 'Daily limit reached',
    dayBasis: 'Each day starts at midnight Korea time.',

    // Earning rules
    rulesTitle: 'How you earn',
    ruleAttendance: 'Check-in',
    rulePost: 'Writing a post',
    ruleComment: 'Writing a comment',
    ruleLikeBonus: 'Getting {threshold} likes',
    rulePerDay: '+{amount}P · {cap} times a day',
    ruleOff: 'Not awarded',
    rulesRevokeNote: 'If you delete a post or comment, the points it earned are taken back. Your balance never goes below 0P.',

    // History
    kindFilter: 'Filter history',
    kindEarn: 'Earned',
    kindRevoke: 'Deducted',
    historyLoadFailed: 'Couldn’t load history',
    historyEmpty: 'No points history yet',
    historyEmptyFiltered: 'Nothing matches this filter',
    targetUnavailable: 'Post not available',
    balanceAfter: 'Balance {points}',

    // Business-only accounts
    businessTitle: 'Business accounts don’t use points',
    businessText: 'Points and history are available on regular member accounts.',
};
