/**
 * Staff action oversight messages — the staff action oversight screen (`/staff-actions`), its drawer and the restore dialog (contract `docs/features/board-staff-action-contract.md`).
 * Same keys, same order as `ko/staffAction.ts`. Messages shared with sanctions (refresh, search, period, totals) use `sanction.*`; manager/staff role names use `boardSanction.role.*`.
 * Restore reasons and server error messages are not here (they come from the server).
 */
export default {
    title: 'Staff action oversight',
    subtitle: 'See posts hidden or deleted and comments deleted by board managers and staff across all boards, and undo them when needed. Who handled it is shown only here and to that board’s manager.',
    readOnly: 'View only — restoring needs the Restore staff actions (keeper-action.restore) permission.',

    // Search
    searchLabel: 'Search staff actions',
    searchPlaceholder: 'Handler nickname',
    filterRestored: 'Restored',
    restoredFilter: {
        ALL: 'All',
        OPEN: 'Not restored',
        RESTORED: 'Restored',
    },
    filterBoard: 'Board',
    filterBoardAll: 'All boards',
    filterKind: 'Type',
    filterAll: 'All',
    filterPeriod: 'Handled on',
    sortNote: 'Newest first',
    loadFailed: 'Couldn’t load staff actions',
    empty: 'No staff actions yet',
    emptyHint: 'Posts and comments that board managers and staff hide or delete from their menus show up here.',

    // Table
    col: {
        board: 'Board',
        kind: 'Type',
        target: 'Target',
        reason: 'Reason',
        actor: 'Handled by',
        at: 'Time',
        restored: 'Restored',
        actions: 'Actions',
    },
    kind: {
        POST_HIDE: 'Hide post',
        POST_DELETE: 'Delete post',
        COMMENT_DELETE: 'Delete comment',
    },
    reason: {
        SPAM: 'Spam / advertising',
        ABUSE: 'Insults / personal attacks',
        INVESTMENT: 'Investment solicitation',
        OFF_TOPIC: 'Off-topic for the board',
    },
    state: {
        PUBLIC: 'Public',
        HIDDEN: 'Hidden',
        DELETED: 'Deleted',
    },
    restoredTag: 'Restored',
    openTag: 'Not restored',
    restoredBy: {
        MANAGER: 'Manager',
        OPERATOR: 'Operator',
    },
    restoredByValue: '{name} ({who})',
    actorValue: '{name} · {role}',
    actorOperatorValue: '{name} ({role})',
    actorGone: '(unknown)',
    authorGuest: '{name} (guest)',
    authorGone: '(unknown)',
    titleNone: '(no title)',
    excerptNone: '(no content)',

    // Drawer
    drawerTitle: '{kind}',
    drawerTitleEmpty: 'Staff action',
    prev: 'Previous action',
    next: 'Next action',
    detailFailed: 'Couldn’t load the action',
    infoTitle: 'Action',
    targetTitle: 'Target',
    openPostAdmin: 'View in Posts',
    commentOf: 'Comment on “{title}”',
    authorLabel: 'By {name}',
    stateLabel: 'Now',
    snapshotNote: 'The title and comment preview were saved when it was handled. “Now” is the target’s current state.',
    restoredTitle: 'Restore record',
    restoredByLabel: 'Restored by',
    restoredAtLabel: 'Restored at',
    restoreReasonNone: 'No reason',
    needsRestore: 'Restoring needs the Restore staff actions (keeper-action.restore) permission.',

    // Restore
    restore: 'Restore',
    restoreRow: 'Restore {kind}',
    restoreTitle: 'Restore staff action',
    restoreReasonLabel: 'Reason for restoring',
    restoreNotice: {
        POST_HIDE: 'Unhides the post. The author isn’t notified, and it’s kept in the audit log. The handler’s daily limit isn’t given back.',
        POST_DELETE: 'Brings the post back and returns the reclaimed points. The author isn’t notified, and it’s kept in the audit log. The handler’s daily limit isn’t given back.',
        COMMENT_DELETE: 'Brings the comment back. The author isn’t notified, and it’s kept in the audit log. The handler’s daily limit isn’t given back.',
    },
    restored: {
        POST_HIDE: 'Post unhidden',
        POST_DELETE: 'Post restored',
        COMMENT_DELETE: 'Comment restored',
    },
    error: {
        BOARD_STAFF_ACTION_RESTORED: 'This action was already restored. The list has been refreshed.',
        POST_STATE_INVALID: 'The post changed in the meantime, so it can’t be restored. Check it in Posts.',
        POST_PURGED: 'The post’s original was purged, so it can’t be restored.',
    },
};
