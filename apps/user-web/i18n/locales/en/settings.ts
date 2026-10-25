/**
 * Settings group (`/settings/*`) — menu names and hints, leave confirmation, time display screen. Same keys and order as `ko/settings.ts`.
 */
export default {
    title: 'Settings',
    seoDescription: 'Your {site} account settings.',

    // Menu
    profile: 'Profile',
    profileHint: 'Nickname and bio',
    notifications: 'Notification settings',
    notificationsHint: 'Choose which notifications you get',
    display: 'Time display',
    displayHint: 'Language, time zone, time and date format',
    accountHint: 'Sign-in methods and linked social accounts',
    identityHint: 'Phone identity check and age 19+',
    nicknameImage: 'Nickname image',
    nicknameImageHint: 'An image in place of your nickname · apply once you qualify',
    adult: 'Age check',
    adultHint: 'Confirm your birth year to see adult posts',
    view: 'View',
    viewHint: 'Dark mode, text size, comment order',
    blocks: 'Block list',
    blocksHint: 'Members and words you blocked',
    memos: 'Member notes',
    memosHint: 'Notes on members only you can see',
    messages: 'Messages',
    messagesHint: 'Whether to accept messages',

    // Leaving without saving
    leaveTitle: 'Leave without saving?',
    leaveMessage: 'Your changes won’t be saved.',
    leaveConfirm: 'Leave',

    // Time display
    displaySeo: 'Choose the language, time zone and format for times on {site}.',
    displayLoading: 'Loading time display settings',
    displayLoadFailed: 'Couldn’t load time display settings',
    displayOwnTag: 'My settings',
    displayDefaultTag: 'Site default',
    displayOwnText: 'Times are shown the way you chose.',
    displayDefaultText: 'You’re following the site default. Saving makes these your own settings.',
    displayReset: 'Reset to default',
    displayResetTitle: 'Reset to the site default?',
    displayResetMessage: 'Your choices are removed and you’ll follow the site default. If the operator changes the default, yours changes too.',
    displayResetDone: 'Reset to the site default',
};
