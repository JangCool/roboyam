/**
 * Post pro & con debate — wording for the reusable parts keyed by post ID (debate settings dialog `DebateSettingsDialog`, opinions drawer `DebateOpinionsDrawer`).
 * Shared by the highlights screen and (next task) post management. Same keys and order as `ko/debate.ts`. ⚠ Wrap `@` and `|` as `{'@'}` and `{'|'}`.
 */
export default {
    // ── Debate settings ──
    settingsTitle: 'Pro & con debate settings',
    settingsLoadFailed: 'Could not load the debate settings',
    settingsSaveFailed: 'Could not save the debate settings.',
    settingsReadOnly: "You can view the debate but don't have permission to change it.",
    debateNone: 'No debate',
    noneHint: 'This post has no pro & con debate right now. Opening one lets readers vote pro or con and post opinions on the post page.',
    openedBy: {
        MEMBER: 'Opened by the author',
        OPERATOR: 'Opened by staff',
    },
    /** `{stances}` gets the side-colored `stanceTab` texts (vote counts) joined with ` · ` by the screen */
    summaryLine: '{total} voted ({stances}) · {opinions} opinions · {pending} pending pin requests',
    neutralLabel: 'Take neutral too',
    neutralHelp: 'When off, the public debate shows only pro and con and new neutral picks are blocked. Existing neutral votes and opinions stay.',
    stanceChangeLabel: 'Allow switching sides',
    stanceChangeHelp: 'Members can change their side later, and their opinions move with them.',
    stanceChangeOff: "Once picked, a side can't be changed. Sides and opinions already picked stay as they are.",
    open: 'Open debate',
    opened: 'Debate opened.',
    saved: 'Debate settings saved.',
    close: 'Close debate',
    closeTitle: 'Close the debate?',
    closeMessage: 'It closes even if there are votes. Picked sides, opinions and pins are kept and come back if you open it again.',
    closed: 'Debate closed.',
    counter: '{n}/{max}',
    tooLong: 'Up to {max} characters.',

    // ── Opinions ──
    opinionsTitle: 'Opinions',
    opinionsLead: 'Top-level comments with a pro, con or neutral side. Pinned first, then most liked.',
    debateOpen: 'Debate open',
    debateClosed: 'Debate closed',
    opinionsReadOnly: 'You can read opinions but not pin or hide them.',
    opinionsTabs: 'Opinion side',
    stanceTab: {
        PRO: 'Pro {n}',
        CON: 'Con {n}',
        NEUTRAL: 'Neutral {n}',
    },
    stance: {
        PRO: 'Pro',
        CON: 'Con',
        NEUTRAL: 'Neutral',
    },
    opinionsLoadFailed: 'Could not load opinions',
    opinionsEmpty: 'No opinions on this side',
    opinionStats: '{likes} likes · {dislikes} dislikes · {replies} replies',
    pinnedTag: 'Pinned',
    pinRequestPendingTag: 'Pin requested',
    hiddenReason: {
        STAFF: 'Hidden by staff',
        REPORTED: 'Hidden by report',
        ILLEGAL: 'Hidden by report (illegal)',
        RIGHTS: 'Hidden by report (rights)',
    },
    pin: 'Pin',
    unpin: 'Unpin',
    hideOpinion: 'Hide',
    unhideOpinion: 'Unhide',
    reportHiddenNote: 'Opinions hidden by reports are restored from report handling.',
    opinionsPage: 'Page {page} · {total} total',
    opinionDone: {
        pinOn: 'Opinion pinned.',
        pinOff: 'Unpinned.',
        hideOn: 'Opinion hidden. The author is not notified.',
        hideOff: 'Unhidden.',
    },
    opinionActionFailed: 'Could not do that.',

    // ── Opinion pin requests ──
    pinRequestsTab: 'Pin requests {n}',
    pinRequestsLead: 'Requests from members to pin their opinion (oldest first). Pinned opinions go to the top of the public debate. The member is notified of the result.',
    pinRequestsLoadFailed: 'Could not load pin requests',
    pinRequestsEmpty: 'No pending pin requests',
    pinRequestedAt: 'Requested {at}',
    opinionGone: 'Deleted opinion',
    pinRequestReject: 'Reject',
    pinRequestApproveOf: "Pin {name}'s opinion",
    pinRequestRejectOf: "Reject {name}'s pin request",
    rejectReasonLabel: 'Reason (optional)',
    rejectReasonHelp: 'Sent to the member in the notification. You can leave it empty.',
    rejectConfirm: 'Reject request',
    pinRequestDone: {
        approve: 'Request approved and the opinion pinned.',
        reject: 'Pin request rejected.',
    },
};
