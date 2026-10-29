/**
 * Shared address input (`AddressInput`), built-in address search (`AddressSearchPanel`) and the dev demo (`/dev/address`). Same keys, same order as `ko/address.ts`.
 */
export default {
    postcode: 'Postcode',
    search: 'Find postcode',
    line1: 'Address',
    line1Placeholder: 'Filled in by Find postcode',
    line2: 'Address line 2',
    line2Placeholder: 'Building, unit, etc.',
    manualHelp: 'Enter the postcode and address yourself.',
    searchUnavailable: "Couldn't open postcode search. Enter the postcode and address yourself.",
    fallbackNotice: "Couldn't open the Daum postcode service, so we're searching this site's address list instead.",
    manualEntry: 'Enter manually',

    keywordLabel: 'Address search',
    keywordPlaceholder: 'Search by street, building or lot number',
    searchIntro: 'Enter a street address or building name. e.g. Teheran-ro 152',
    keywordTooShort: 'Enter at least {min} characters.',
    noResults: 'No matching address. Try other words or enter it manually.',
    resultCount: '{count} addresses',
    jibunPrefix: 'Lot {address}',
    searchFailed: "Couldn't search addresses. Please try again shortly.",

    devBadge: 'Dev-only page',
    devTitle: 'Address input demo',
    devIntro: 'Try the shared address input — how the Admin › Address search provider looks on the member site.',
    devCaseTitle: '1. Current settings',
    devCaseIntro: 'Provider and Daum script URL from the server. Find postcode fetches them again each time it opens.',
    devReload: 'Reload settings',
    devInputTitle: '2. New address',
    devShowErrors: 'Show / hide field errors',
    devClear: 'Clear',
    devValue: 'Current value',
    devManualTitle: '3. Edit an existing address',
    devManualIntro: 'When editing a saved address, manual entry is open from the start (Find postcode still works).',
};
