/**
 * My profile screen (`/me`). Server error messages are shown as the server sent them, so they are not here.
 * Keys are two parts: `profile.what`. Same keys and order as `ko/profile.ts`.
 */
export default {
    seoTitle: 'My profile',
    title: 'My profile',
    subtitle: 'Review your account, display preferences and sign-in history.',
    menuLabel: 'My profile menu',
    tabGeneral: 'General',
    tabPersonal: 'Preferences',
    tabLogin: 'Sign-in history',
    tabPassword: 'Password',
    tabSecurity: 'Sign-in security',

    // Basic information
    basicInfo: 'Basic information',
    accountId: 'ID',
    joinedAt: 'Joined',
    lastLoginAt: 'Last sign-in',

    // Member information
    memberInfo: 'Member information',
    nickname: 'Nickname',
    nicknameHint: '2–12 characters',
    nicknameHelp: 'Korean, English letters, numbers and underscores (_), with a single space allowed between words.',
    email: 'Email',
    emailHelp: 'Your email cannot be changed.',
    role: 'Role',
    roleNone: 'No roles assigned',

    // Preferences — my formatting (language, time zone, time/date format): product defaults <- my formatting
    displayUseMine: 'Use my own formatting',
    displayMineOn: 'You see it the way you chose.',
    displayMineOnTail: 'These values win over the product defaults.',
    displayMineOff: 'Following the product defaults.',
    displayMineOffTail: 'Turn this on to use the values below — they are kept even while it is off.',
    displayAppliedNow: 'This screen is currently using {from}.',
    displayFromSystem: 'the product defaults',
    displayFromUser: 'your own settings',
    displaySection: 'Language, region, time and date',
    displayMineSaved: 'Using your own formatting',
    displayMineOffSaved: 'Following the product defaults',
    displayLoadFailed: 'Could not load your formatting',

    // Preferences — alerts
    notifications: 'Alerts',
    emailNotification: 'Email alerts',
    emailNotificationHint: 'Only notification emails can be turned off. System emails such as invitations, password resets and verification codes cannot be turned off.',
    emailNotificationOn: 'You will receive notification emails',
    emailNotificationOff: 'You will no longer receive notification emails',
    browserNotification: 'Get notifications in this browser',
    browserNotificationHint: 'Shows new alerts as browser notifications while this window is hidden or in the background. Applies to this browser only.',
    browserNotificationDenied: 'This browser is blocking notifications for this site. Allow them in the site settings next to the address bar.',
    browserNotificationUnsupported: 'This browser does not support notifications.',
    browserNotificationOn: 'You will get notifications in this browser',
    browserNotificationOff: 'You will no longer get notifications in this browser',
    events: 'Alerts by event',
    eventsHint: 'Choose which events send you alerts. A turned-off event no longer notifies you.',
    eventsLoadFailed: 'Could not load the event list.',
    eventsEmpty: 'There are no events you can receive right now.',
    eventMandatory: 'Required',
    eventEmailable: 'Also sent by email',
    eventReceiveOn: 'You will receive {name} alerts',
    eventReceiveOff: 'You will no longer receive {name} alerts',

    // Sign-in history
    loginHistory: 'Recent sign-ins',
    noLoginHistory: 'No history yet',
    noLoginHistoryBody: 'Sign-in records will show up here.',
    loginHistoryLoadFailed: 'Could not load your sign-in history',
    signInOk: 'Success',
    signInFail: 'Failed',
    reasonInvalidCredentials: 'Incorrect email or password',
    reasonLoginLocked: 'Sign-in was temporarily locked',

    // Sign-in history — environment (parsed from the User-Agent)
    agentUnknown: 'Unknown',
    device_desktop: 'Desktop',
    device_mobile: 'Mobile',
    device_tablet: 'Tablet',

    // Input hints
    nicknameRequired: 'Enter a nickname.',
    nicknameLength: 'Enter a nickname of 2–12 characters.',
    nicknameChars: 'Use only Korean, English letters, numbers and underscores (_), with a single space between words.',

    // Change password (shared password fields and rules wording is in `account.ts`)
    passwordTitle: 'Change password',
    passwordIntro: 'Confirm your current password, then choose a new one.',
    passwordChange: 'Change password',
    passwordChanged: 'Your password was changed',
    passwordLogoutNote: 'You are signed out on other devices. This device stays signed in.',
    currentPasswordRequired: 'Enter your current password.',

    // States
    loadFailed: 'Could not load your profile',
    emptyTitle: 'No profile to show',
    emptyBody: 'Please try again in a moment.',
};
