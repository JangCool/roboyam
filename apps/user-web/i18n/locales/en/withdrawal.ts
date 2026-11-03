/**
 * Member withdrawal: the "Delete account" row on the account screen, the withdrawal screen (`/settings/account/withdraw`: facts, choices, verification, in-progress state), and the done screen (`/withdrawn`).
 * Same keys and order as `ko/withdrawal.ts`.
 */
export default {
    title: 'Delete account',
    rowHint: 'Delete your account and leave the service',
    seoDescription: 'Delete your {site} account.',
    loading: 'Loading withdrawal details',
    loadFailed: 'Could not load withdrawal details',
    intro: 'Please check what happens when you delete your account. Once you press the button, it is processed right away.',

    // What you lose
    lossesTitle: 'What disappears',
    lossFreePoints: 'Earned points {points}',
    lossFreePointsHelp: 'They expire when you leave and cannot be restored.',
    lossItems: '{count} point shop items',
    lossItemsHelp: 'Purchased items and equipped decorations are all removed.',
    lossItemsRefundable: '{count} of them were bought within 7 days and are unused, so you can request a refund.',
    lossItemsRefundLink: 'Request a refund in My items',
    lossGrade: 'Grade and attendance',
    lossGradeHelp: 'Your {grade} grade and {days}-day attendance streak are removed. A new account starts from the beginning.',
    lossGradeHelpNoGrade: 'Your grade and attendance record are removed. A new account starts from the beginning.',

    // What stays
    remainsTitle: 'What stays',
    remainPosts: '{posts} posts · {comments} comments',
    remainPostsHelp: 'They stay with the author shown as "Withdrawn member". Delete any posts you want gone before you leave.',
    remainPostsLink: 'View my posts',
    remainTrades: 'Transaction records',
    remainTradesHelp: 'Payment, order, refund and point records are kept for the legally required period ({years} years). Only amounts and dates remain; they cannot identify you.',
    remainContact: 'Contact email',
    remainContactHelp: 'Because you have a payment history, one email address is kept encrypted and separate for {years} years so we can reach you about disputes or tax matters. It is separated from other data and not visible on normal screens.',

    // Pending
    pendingTitle: 'Things that must finish first',
    pendingIntro: 'Your account is deleted automatically once these finish. If you apply now, your account is "being deleted" until then.',
    pendingKind: {
        GROUP_BUY_ORDER: 'Group buy order in progress',
        RETURN_REQUEST: 'Cancellation or return request in progress',
        PAYMENT_CANCEL: 'Refund in progress',
        POINT_CHARGE_REFUND: 'Charged point refund',
        POINT_SHOP_REFUND: 'Point shop refund request under review',
        BIZ_APPLICATION: 'Ad or hot deal in progress',
        DELIVERED_RETURN_WINDOW: 'Order waiting for its return window',
    },
    pendingWaitUntil: 'Expected to finish around {date}',
    pendingWaitUnknown: 'When it finishes depends on the outcome',
    pendingOpen: 'Open',

    // Choice: delivered within 7 days
    choicesTitle: 'Your choices',
    deliveredTitle: '{count} orders delivered less than 7 days ago',
    deliveredHelp: 'A change-of-mind return is possible up to 7 days after delivery. Choose what to do.',
    deliveredOrder: 'Order {orderNo} · return by {date}',
    deliveredWait: 'Wait until the return window ends, then delete automatically',
    deliveredWaitHelp: 'Your account is deleted when the window ends. You can still request a return as usual until then.',
    deliveredNow: 'Delete now',
    deliveredNowHelp: 'After deletion, request returns by emailing customer support (handled through your contact email). Defects and wrong deliveries can be reported the same way regardless of the window.',

    // Choice: charged points
    paidTitle: 'Charged points {points}',
    paidHelp: 'Charged points can be refunded. Check the estimate and choose.',
    paidEstimate: 'Estimated refund {amount}',
    paidFeeNone: 'Charged less than 7 days ago, so the full amount is refunded with no fee.',
    paidFee: '{points} charged more than 7 days ago carries a {rate}% fee ({fee}) because the withdrawal-of-offer period has passed. The same rule applies whether or not you leave.',
    paidRefund: 'Get a refund, then delete',
    paidRefundHelp: 'Your account is deleted once the refund finishes. Until then it is "being deleted".',
    paidForfeit: 'Forfeit and delete',
    paidForfeitHelp: 'Deletes right away with no refund. Requires the consent below.',
    paidForfeitConsent: 'I forfeit {points} charged points (estimated refund {amount}).',

    // Choice: ads and hot deals (business)
    bizTitle: '{count} ads or hot deals in progress',
    bizHelp: 'These are your running business items. You can wait for them to finish or forfeit them.',
    bizRunning: '{label} · until {date}',
    bizForfeitTotal: 'Amount lost if forfeited',
    bizRefundWaiting: 'Amount waiting for refund',
    bizRefundWaitingHelp: 'Refunds for items already cancelled. They are refunded either way.',
    bizWait: 'Wait until they finish, then delete automatically',
    bizWaitHelp: 'Your account is deleted when the last item ends.',
    bizForfeit: 'Forfeit and delete now',
    bizForfeitHelp: 'Ends the running items now; the remaining period is not refunded. Requires the consent below.',
    bizForfeitConsent: 'I forfeit the remaining {amount} of my running ads and hot deals. The {waiting} waiting for refund is still refunded.',

    // Choice: groups I lead
    groupsTitle: '{count} groups you lead',
    groupsHelp: 'For each group, choose who takes over or dissolve it. If you choose nothing, it is handled automatically.',
    groupMembers: '{count} members',
    groupAuto: 'Automatic',
    groupAutoDelegate: 'Hands over to {nickname} ({role}) automatically.',
    groupAutoDissolve: 'No members, so the group is dissolved automatically. No dissolution penalty.',
    groupTransfer: 'Hand over',
    groupTransferHelp: 'The chosen member becomes the leader.',
    groupTransferTo: 'Member to hand over to',
    groupTransferPlaceholder: 'Choose a member',
    groupDissolve: 'Dissolve',
    groupDissolveHelp: 'The group closes and all members leave. There is no penalty for dissolving because of withdrawal.',
    role: {
        MANAGER: 'manager',
        MEMBER: 'member',
    },

    // Rejoin
    rejoinTitle: 'Joining again',
    rejoinText: 'You can sign up again with the same email, social account or identity verification from {date}. It is a new account: points, items, grade and posts do not carry over.',
    rejoinExtended: 'Delayed until your restriction ends on {date}. Reason: {reason}',

    // Reason (optional)
    reasonTitle: 'Reason (optional)',
    reasonHelp: 'You can delete your account without choosing one. It helps us improve the service.',
    reasonPlaceholder: 'Choose a reason',
    reason: {
        NOT_USING: 'I rarely use it',
        PRIVACY: 'Privacy concerns',
        INCONVENIENT: 'Hard to use',
        OTHER_SERVICE: 'I use another service',
        REJOIN: 'I want to start over with a new account',
        OTHER: 'Other',
    },
    reasonNote: 'Anything else',
    reasonNotePlaceholder: 'Optional (up to 200 characters)',

    // Verification
    verifyTitle: 'Confirm it is you',
    verifyPasswordHelp: 'Enter your current password once to confirm it is you.',
    verifyRecentOk: 'You logged in recently, so you are confirmed.',
    verifyRecentNeeded: 'This account logs in with a social account. To confirm it is you, log in again within the last 10 minutes.',
    verifyRelogin: 'Log in again',

    // Submit
    submit: 'Delete account',
    submitScheduled: 'Apply to delete account',
    submitting: 'Processing…',
    submitHelpNow: 'Your account is deleted immediately. This cannot be undone.',
    submitHelpScheduled: 'Your account becomes "being deleted" and is deleted automatically once the items above finish. You can cancel until then.',
    submitFailed: 'Could not process the deletion.',
    consentRequired: 'You need to consent to the forfeit to continue.',
    transferTargetRequired: 'Choose a member to hand over to.',
    scheduledToast: 'Deletion requested',

    // In progress
    scheduledTitle: 'Deletion in progress',
    scheduledText: 'Requested on {date}. Your account is deleted automatically once the items below finish, and we will email you.',
    scheduledExpected: 'Expected completion {date}',
    scheduledExpectedUnknown: 'Completion depends on the outcome of the items below',
    scheduledBlocked: 'While in progress, new activity such as posting, joining, paying or charging is blocked, and you are hidden from profiles, search and messages. You can only handle the remaining items or cancel the deletion.',
    scheduledNotify: 'If it does not finish within 10 days, we email you the status.',
    cancel: 'Cancel deletion',
    cancelHelp: 'Cancelling restores your account right away.',
    cancelTitle: 'Cancel the deletion?',
    cancelMessage: 'Your account returns to normal right away and your choices are cleared.',
    cancelConfirm: 'Cancel deletion',
    cancelDone: 'Deletion cancelled',
    cancelFailed: 'Could not cancel the deletion.',

    // Global banner (`components/account/WithdrawalBanner.vue`) — at the top of every page while deletion is in progress
    bannerLabel: 'Account deletion in progress',
    bannerTitle: 'Your account deletion is in progress.',
    bannerText: 'It completes automatically once the remaining items are done.',
    bannerLink: 'View or cancel',

    // Done (`/withdrawn`)
    doneSeoTitle: 'Account deleted',
    doneSeoDescription: 'Your account has been deleted.',
    doneTitle: 'Your account has been deleted',
    doneText: 'Thank you for being with us. Your personal data has been removed; only the items below remain.',
    doneRemainsTitle: 'What stays',
    doneRemainPosts: 'Posts and comments — the author is shown as "Withdrawn member".',
    doneRemainTrades: 'Transaction records — kept for {years} years, then deleted (amounts and dates only; they cannot identify you).',
    doneRemainContact: 'Contact email — because of your payment history, one email is kept encrypted and separate for {years} years.',
    doneRemainHashes: 'Rejoin-restriction hashes — kept only until {date}, then deleted.',
    doneContactTitle: 'Need help?',
    doneContactText: 'For returns, refunds or defects after deletion, and to request that a post be hidden, email customer support. Including the order number or post link speeds things up.',
    doneContactMail: 'Customer support email',
    doneRejoinTitle: 'Joining again',
    doneRejoin: 'From {date} you can sign up again with the same email, social account or identity verification. You start with a new account.',
    doneRejoinUnknown: '30 days after deletion you can sign up again with the same email, social account or identity verification. You start with a new account.',
    doneHome: 'Go to home',
};
