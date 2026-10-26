/**
 * Audit log (ported from hamonica3 D-89). Keys are `audit.what` — no screen names. Same keys and order as `ko/audit.ts`.
 * The hamonica3 "Workspace" column text is left out (this product has no workspaces).
 * Action, target type and result names and failure/denial reasons come from the server in the request language, so they are not here (`succeeded`/`failed`/`denied` are fallbacks used only when the result list could not be loaded).
 */
export default {
    title: 'Audit log',
    subtitle: 'A record of who did what, and when.',

    // hamonica3 common texts "Search" / "Clear filters" — not in the app-wide texts (`@roboyam/i18n`) yet, so they live here
    search: 'Search',
    clearFilter: 'Clear filters',

    from: 'From',
    to: 'To',
    fromPlaceholder: 'After this time',
    toPlaceholder: 'Before this time',
    actor: 'User',
    actorPlaceholder: 'ID or name',
    action: 'Action',
    targetType: 'Target type',
    target: 'Target',
    targetPlaceholder: 'ID or name',
    result: 'Result',

    occurredAt: 'When',
    changes: 'What changed',
    changeDetail: 'Change detail',
    succeeded: 'Succeeded',
    failed: 'Failed',
    denied: 'Denied',
    removed: '(gone)',
    none: '(none)',

    empty: 'Nothing recorded yet',
    emptyHint: 'Changes made in the admin screens collect here.',
    loadFailed: 'Could not load the audit log',
    noChange: 'No changes were recorded.',
    filterInvalid: 'Check your filters',
    filterInvalidHint: 'Fix the fields marked below, then search again.',
    actorSystem: 'System',
    actorAnonymous: 'Not signed in',
    filterAfter: 'after {at}',
    filterBefore: 'before {at}',
    filterActor: 'user [{name}]',
    filterTarget: 'target [{name}]',

    // ── Retention (dialog — commerce.md 13-4) ─
    retentionButton: 'Retention',
    retentionTitle: 'Audit log retention',
    retentionLead: 'Each audit entry gets a retention category based on the action. A cleanup job (audit-log-retention) runs daily at 03:30 and deletes entries older than their category’s retention period.',
    retentionSuperOnly: 'Only the super admin can change this.',
    retentionName: {
        GENERAL: 'General operations',
        PERSONAL_DATA: 'Personal data viewing and downloads',
        MONEY: 'Money-related',
    },
    retentionExample: {
        GENERAL: 'e.g. settings changes, board and menu management, sign-ins',
        PERSONAL_DATA: 'e.g. viewing member details or full emails, viewing shipping addresses, downloading shipping lists',
        MONEY: 'e.g. refunds, manual point adjustments, reconciliation fixes, turning sales on/off',
    },
    retentionUnitDays: 'days',
    retentionAboutYears: 'about {years} yr',
    retentionAboutMonths: 'about {months} mo',
    retentionDays: '{days} days',
    retentionBounds: 'Minimum {min} days · default {def} days',
    retentionRequired: 'Enter the number of days.',
    retentionNotInteger: 'Enter a whole number of days.',
    retentionBelowMin: 'Cannot be shorter than the minimum ({days} days).',
    retentionAboveMax: 'Cannot be longer than {days} days.',
    retentionLastSaved: 'Last saved: {at} · {by}',
    retentionNeverSaved: 'Never saved — using defaults.',
    retentionLoadFailed: 'Could not load the retention settings.',
    retentionShrinkTitle: 'Shorten the retention period?',
    retentionShrinkMessage: 'From the next cleanup, entries older than the new period will be deleted, and this cannot be undone. Shortened: {names}',
    retentionShrinkConfirm: 'Shorten and save',
    retentionSaved: 'Retention saved.',
    retentionFieldsRejected: 'Some fields were not accepted. Check the messages under them.',
};
