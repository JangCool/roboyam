/**
 * Point game admin settings (the "Point games" section on the point shop settings page) — the "check winning numbers" link on the lotto number picker.
 * Same keys, same order as `ko/pointGame.ts`.
 */
export default {
    section: 'Point games',
    lead: 'The "check winning numbers" link on the member lotto number picker. If the lottery operator changes, update the name and address here.',
    loadFailed: "Couldn't load point game settings.",
    providerName: 'Lottery operator name',
    providerNameHint: '1–{max} characters',
    providerNameHelp: 'Shown in the link text on the member page.',
    checkUrl: 'Winning numbers link',
    checkUrlHint: 'https:// address',
    checkUrlHelp: 'Opens in a new tab when members click it.',
    providerNameRequired: 'Enter the lottery operator name.',
    providerNameTooLong: 'Up to {max} characters.',
    checkUrlRequired: 'Enter the winning numbers link.',
    checkUrlInvalid: "That doesn't look like an address. e.g. https://example.com",
    checkUrlNotHttps: 'Only addresses starting with https:// are allowed.',
    checkUrlTooLong: 'Up to {max} characters.',
    preview: 'Member page',
    previewLabel: 'Check winning numbers · {provider}',
    openLink: 'Open in new tab',
    defaultValue: 'Default {provider} · {url}',
    useDefault: 'Use default',
    noteNoWinning: "The site doesn't make winning numbers or compare them with members' numbers. It only shows one link.",
    noteNoOfficial: "The member page doesn't use the operator's logo or wording that looks like an official service.",
    noteFixedRules: 'Roulette (50P per spin · 5 a day · odds table) and lotto prices (1 set 20P · 5 sets 80P) are fixed for now.',
    saveFailed: "Couldn't save point game settings",
    lastSaved: 'Last saved {at} · {by}',
    neverSaved: 'Never changed (default).',
};
