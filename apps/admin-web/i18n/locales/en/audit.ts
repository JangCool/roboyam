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
};
