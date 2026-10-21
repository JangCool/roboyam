/**
 * Points text used by the app shell (header menu, hamburger menu). Same keys and order as `ko/point.ts`.
 */
export default {
    // Entry to the points screen
    entry: 'Points & check-in',
    entryWithReward: 'Points & check-in · +{amount}P',

    // Balance line — {points} looks like "1,280P"
    balanceLine: 'Points {points}',

    // Check-in button
    attend: 'Check in today',
    attendWithReward: 'Check in today +{amount}P',

    // Balance + check-in hint
    summaryWithAttend: '{balance} · {attend}',
};
