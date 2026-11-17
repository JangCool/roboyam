/**
 * Member titles (member-title-contract) — same keys and order as `ko/memberTitle.ts`. Title names, condition labels and how-to text come from the server in the request language.
 */
export default {
    hallOfFame: 'Hall of Fame',
    myEntry: 'Titles',
    myTitle: 'My titles',
    loading: 'Loading titles',
    loadFailed: "Couldn't load titles",

    // Featured title
    repHeading: 'Featured title',
    repHint: 'Shown next to your name in post lists, posts and comments. Only one.',
    repDefault: 'Default',
    repDefaultHint: 'Your highest writer title',
    repHidden: "Don't show",
    repHiddenHint: 'No title next to your name',
    repSaved: 'Featured title updated.',

    // Owned
    ownedHeading: 'Titles you have ({count})',
    ownedEmpty: 'No titles yet',
    ownedEmptyHint: 'Write great posts and stay active to earn titles.',

    // Progress
    progressHeading: 'Next titles',
    progressCount: '{current} / {target}',
    progressTier: '{label} {tier}',

    // Applicable
    applyHeading: 'Titles you can apply for',
    applyAction: 'Apply',
    applied: "Applied. We'll notify you of the result.",
    conditionMet: 'met',
    conditionNotMet: 'not met yet',
    reapplyAt: 'You can apply again from {date}',

    // Applications
    applicationsHeading: 'Applications',
    rejectReasonLine: 'Reason: {reason}',

    tier: {
        BRONZE: 'Bronze',
        SILVER: 'Silver',
        GOLD: 'Gold',
    },
    method: {
        AUTO: 'Automatic',
        APPLY: 'By application',
        AWARD: 'Awarded',
    },
    status: {
        PENDING: 'In review',
        APPROVED: 'Approved',
        REJECTED: 'Rejected',
    },

    ratio: '{percent}% of members',
    ratioUnder: 'under {percent}% of members',
    periodMonth: '{month}/{year}',
    periodYear: '{year}',
    countLabel: '{label} ×{count}',

    // Public profile
    profileHeading: 'Titles',
    periodsOpen: '{label} ×{count} — show months',
    periodsTitle: '{label} — awarded',

    // Hall of Fame · catalog
    hallLead: 'Writers who made this site shine, and how to earn every title.',
    hallHeading: 'Hall of Fame',
    hallLoadFailed: "Couldn't load the Hall of Fame",
    hallEmpty: 'No one in the Hall of Fame yet',
    hallEmptyHint: 'Writers of the month will be listed here.',
    honoraryHeading: 'Honorary writers',
    yearlyHeading: 'Writers of the year',
    monthlyHeading: 'Writers of the month',
    catalogHeading: 'Title catalog',
    catalogLoadFailed: "Couldn't load the title catalog",
    catalogEmpty: 'No titles yet',
    holders: '{count} members',
    tierLine: '{tier} · {threshold} times · {count}',
    hallSeo: 'Hall of Fame and title catalog of {site} — honorary writers, writers of the year and month, and how to earn titles.',

    // Celebration
    celebrateTitle: 'You earned a new title',
    celebrateText: 'Congratulations! You earned the “{label}” title.',
    celebrateRatio: 'Only {ratio} have it',
    celebrateMore: 'Plus {count} more',
    celebrateUse: 'Use as featured title',
    celebrateHall: 'See the Hall of Fame',

    // Board staff — title applications
    staffTab: 'Title applications',
    staffTitle: 'Review title applications',
    staffHint: "Applications for titles linked to this board. You can't review your own.",
    staffStatusLabel: 'Status',
    staffPending: 'In review',
    staffEmptyPending: 'Nothing to review',
    staffEmpty: 'No title applications',
    staffLoadFailed: "Couldn't load title applications",
    staffOwnApplication: 'This is your own application — another manager or an admin will review it.',
    reviewReasonLine: 'Review note: {reason}',
    approve: 'Approve',
    reject: 'Reject',
    approveTitle: 'Approve application',
    rejectTitle: 'Reject application',
    approveText: 'Give {name} the “{label}” title.',
    rejectText: "Reject {name}'s application for “{label}”.",
    approveReasonLabel: 'Note (optional)',
    rejectReasonLabel: 'Reason',
    rejectNotice: 'The member will see the reason and can apply again after the waiting period.',
    rejectReasonRequired: 'Enter a reason.',
    approvedToast: "Approved {name}'s application.",
    rejectedToast: "Rejected {name}'s application.",
};
