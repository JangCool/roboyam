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
    // Right side of the My info shortcut — adds the reward while today’s check-in is open
    entryHint: '{balance} · check-in +{amount}P',

    // Points screen (`/me/points`) and the points summary on My page
    title: 'Points',
    seoDescription: 'Your {site} points balance and history.',
    history: 'Points history',
    loading: 'Loading points',
    loadFailed: 'Couldn’t load points',
    balance: 'Balance',
    // Charged/earned split (only once charging opens — commerce D13), expiring and unrecovered — {paid}, {free}, {points} look like "1,280P"
    splitLine: 'Charged {paid} · Earned {free}',
    expiringLine: '{points} expire on {date}',
    expiryNote: 'Points expire 1 year after you receive them. Points expiring soonest are used first.',
    expiryNoteSplit: 'Earned points expire 1 year after you receive them (charged points never expire). Earned points expiring soonest are used first.',
    debtTitle: 'Unrecovered {points} — deducted from your next earnings',
    debtText: 'Points from posts or comments you deleted should have been taken back, but your balance was too low to cover all of it.',
    todayTitle: 'Earned today',
    todayPost: 'Posts',
    todayComment: 'Comments',
    todayLikeBonus: 'Like bonus',
    countOfCap: '{count}/{cap}',
    capReached: 'Daily limit reached',
    todayTotal: 'Total earned today',
    totalOfCap: '{amount} / {cap}',
    totalCapReached: 'Today’s limit reached',
    dayBasis: 'Each day starts at midnight Korea time.',

    // Earning rules
    rulesTitle: 'How you earn',
    ruleAttendance: 'Check-in',
    rulePost: 'Writing a post',
    ruleComment: 'Writing a comment',
    ruleLikeBonus: 'Getting {threshold} likes',
    rulePerDay: '+{amount}P · {cap} times a day',
    ruleOff: 'Not awarded',
    ruleSignup: 'Identity verification bonus +{amount}P (once)',
    signupReceived: 'Received',
    signupVerify: 'Verify identity',
    conditionsTitle: 'Conditions',
    condPost: 'Posts need at least {n} characters in the body (may differ by board)',
    condComment: 'Comments: {rules}',
    condCommentMin: 'at least {n} characters',
    condCommentOwnPost: 'not on your own posts',
    condCommentOnce: 'once per post',
    condInterval: 'Nothing for posts or comments written within {seconds} seconds of one that earned points',
    condDailyTotal: 'Activity points are capped at {cap} a day — anything over is not paid',
    rulesRevokeNote: 'If you delete a post or comment, the points it earned are taken back. Your balance never goes below 0P — any shortfall is deducted from your next earnings.',
    ruleAttendanceStreak: 'Every {days}-day check-in streak',

    // Check-in (commerce 1-3) — calendar and today's check-in
    attendanceTitle: 'Check-in',
    todayAttendance: 'Check-in',
    attendDone: 'Done',
    attending: 'Checking in…',
    attended: 'Checked in today',
    attendHelp: 'Once a day. Days start at midnight Korea time.',
    attendedHelp: 'You can check in again tomorrow.',
    calPrev: 'Previous month',
    calNext: 'Next month',
    calMonth: '{monthName} {year}',
    // Starting on Sunday — split by commas
    calWeekdays: 'Sun,Mon,Tue,Wed,Thu,Fri,Sat',
    calLabel: 'Check-in calendar',
    calLoading: 'Loading the check-in calendar',
    calLoadFailed: 'Couldn’t load the check-in calendar',
    calDay: '{monthName} {day}',
    calDayAttended: '{date}, checked in',
    calDayToday: '{label} · today',
    calLegend: 'Blue days are check-ins; the bold outline is today.',
    calEmptyMonth: 'No check-ins this month · before you joined or a month you didn’t check in.',
    monthCount: '{count} days this month',
    streak: '{count}-day streak',
    streakNext: '+{bonus}P after {days} more days',
    streakNextToday: 'Check in today for a +{bonus}P streak bonus',
    attendedToast: 'Checked in! +{amount}P',
    attendedToastStreak: 'Checked in! +{amount}P · {streak}-day streak bonus +{bonus}P',
    attendedToastNoPoints: 'Checked in',
    alreadyAttendedToast: 'You’ve already checked in today',
    attendFailedTitle: 'Check-in failed.',
    attendFailedText: 'No points were given. Please try again shortly.',
    attendUnknownTitle: 'Couldn’t confirm your check-in.',
    attendUnknownText: 'We’ll reload the calendar and your balance to check. If it went through, you don’t need to press again.',
    attendRateLimitedTitle: 'Please try again shortly',
    attendRateLimitedText: 'Too many requests. Wait a moment and press again.',
    blockedSuspendedTitle: 'You can’t check in while suspended',
    blockedSuspendedText: 'You can check in again once the suspension ends. No points are given while suspended.',
    blockedEmailTitle: 'Verify your email to check in',
    blockedEmailText: 'Open the link in the verification email and you can check in right away.',
    blockedEmailLink: 'See email verification',
    blockedSanctionLink: 'See sanction details',

    // History
    kindFilter: 'Filter history',
    kindEarn: 'Earned',
    kindRevoke: 'Deducted',
    kindExpire: 'Expired',
    kindCancelUse: 'Use canceled',
    restoredUntil: 'These points had already expired, so we returned them usable until {date}.',
    historyLoadFailed: 'Couldn’t load history',
    historyEmpty: 'No points history yet',
    historyEmptyFiltered: 'Nothing matches this filter',
    targetUnavailable: 'Post not available',
    balanceAfter: 'Balance {points}',

    // Business-only accounts
    businessTitle: 'Business accounts don’t use points',
    businessText: 'Points and history are available on regular member accounts.',
};
