/**
 * Holidays (`/settings/holidays`) — one site-wide table of days off (public data holidays · defaults · company days off). Same keys and order as `ko/holiday.ts`.
 * Day-off marks on calendar cells live in the layer (`holidayMark.*`). **Server `message` comes first when present.**
 */
export default {
    title: 'Holidays',
    subtitle: 'One table for the site’s days off. Every calendar’s holiday marks and group buy business-day counts use it. Public holidays are fetched monthly from public data.',
    readOnly: 'View only. You need the “Change site settings” permission to edit company days off or fetch now.',
    loadFailed: 'Could not load holidays.',

    // Public data fetch
    syncSection: 'Public data fetch',
    syncLead: 'Public holidays come from the KASI special day API. They are fetched every month, and you can fetch them here too.',
    noKeyTitle: 'No public data key',
    noKeyText: 'Add the service key in Public data settings. Until then only the default holidays are used.',
    noKeyLink: 'Go to Public data settings',
    syncNever: 'Never fetched yet.',
    syncRanAt: 'Last fetched',
    syncRanBy: '{at} · {name}',
    syncAuto: '{at} · scheduled',
    syncResult: 'Result',
    syncApplied: 'Years fetched',
    syncChanged: 'Changes',
    syncChangedCount: '{count}',
    syncFailed: 'Years failed',
    syncNone: 'None',
    syncNow: 'Fetch now',
    syncDone: 'Public holidays fetched.',
    syncDoneNoKey: 'Not fetched — no service key.',
    syncDoneFailed: 'Could not fetch public holidays.',
    outcome: {
        OK: 'All fetched',
        PARTIAL: 'Partly fetched',
        NO_KEY: 'No key',
        FAILED: 'Failed',
    },

    // Year list
    listSection: 'Holidays in {year}',
    prevYear: 'Previous year',
    nextYear: 'Next year',
    thisYear: 'This year',
    colDate: 'Date',
    colName: 'Name',
    colSource: 'Source',
    source: {
        PUBLIC_DATA: 'Public data',
        SEED: 'Default',
        OPERATOR: 'Company day off',
    },
    substitute: 'Substitute holiday',
    addedBy: 'Added by {name}',
    empty: 'No holidays in this year.',
    emptyHint: 'Fetch public data or add a company day off.',
    remove: 'Remove',
    removeAria: 'Remove {date} {name}',
    removeTitle: 'Remove this company day off?',
    removeMessage: 'Removes “{name}” on {date}.',
    removed: 'Company day off removed.',
    readOnlyRow: 'Public data and default holidays can’t be changed.',

    // Add company day off
    addSection: 'Add a company day off',
    addLead: 'A company day off that is not a public holiday (founding day, etc.). Shown only on admin calendars (member calendars show public holidays only).',
    addDate: 'Date',
    addName: 'Name',
    addNameHint: '1–30 chars',
    addNamePlaceholder: 'e.g. Founding day',
    addSubmit: 'Add',
    added: 'Company day off added.',
    dateRequired: 'Pick a date.',
    nameRequired: 'Enter a name.',
    nameTooLong: 'Names can be up to 30 characters.',
};
