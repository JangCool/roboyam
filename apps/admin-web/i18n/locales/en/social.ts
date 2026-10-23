/**
 * Social login settings screen (`/settings/social-login`). Same keys and order as `ko/social.ts`.
 */
export default {
    title: 'Social login',
    subtitle: 'Register the client IDs and secrets for Kakao, Naver and Google login, and turn their buttons on the public login screen on or off.',
    readOnly: "You don't have permission to change social login keys, so this is read-only.",
    loadFailed: "Couldn't load the social login settings",
    cryptoMissingTitle: "Can't save without an encryption key",
    cryptoMissingText: 'Set the encryption key (ROBOYAM_CRYPTO_KEY) on the server, restart it and reload this page. Until then the public site shows no social login buttons.',
    footnote: 'Saved changes apply to the public login screen right away. Secrets are stored encrypted and never shown again.',
    historyHint: 'Changes made in the install wizard and on this screen. Secrets are recorded only as set, changed or removed, and client IDs show only their last four characters.',
    provider_KAKAO: 'Kakao',
    provider_NAVER: 'Naver',
    provider_GOOGLE: 'Google',

    // Status
    badgeActive: 'On',
    badgeInactive: 'On · hidden',
    badgeOff: 'Off',
    badgeNone: 'Not set',
    statusNone: 'Not registered yet, so the {name} button does not appear on the login and sign-up screens.',
    statusOff: 'Registered but turned off, so the {name} button does not appear on the login and sign-up screens.',
    statusActive: 'The {name} button is showing on the public login and sign-up screens.',
    statusNoCryptoKey: 'Turned on, but the server has no encryption key, so the {name} button does not appear.',
    statusNotConfigured: 'Turned on, but the client ID and secret are incomplete, so the {name} button does not appear.',
    updatedBy: 'Last saved {at} · {name}',
    updatedAt: 'Last saved {at}',
    secretReenterNotice: "The saved secret can't be read with the current server key. Enter the secret again.",

    // Callback URL
    callbackLabel: 'Callback URL',
    callbackHint: 'Register in the {name} developer console',
    copy: 'Copy',
    copyAria: 'Copy the {name} callback URL',
    copied: 'Copied the {name} callback URL',
    copyFailed: "Couldn't copy. Select the URL and copy it yourself.",
    callbackMissing: "There's no public site URL, so the callback URL can't be built. Set the public URL first.",
    callbackMissingLink: 'Site general settings',

    // Fields
    clientIdLabel: 'Client ID',
    clientSecretLabel: 'Client secret',
    clientSecretLabelRequired: 'Client secret (required)',
    secretKeep: 'Leave blank to keep the current value.',
    secretReenter: "The saved value can't be read. Enter the secret again to save.",
    secretNew: "The value issued in each provider's developer console. It is not shown again after saving.",
    enabledLabel: 'Turn on',
    enabledHelp: 'When on, the {name} button appears on the login and sign-up screens.',
    remove: 'Delete settings',

    // Checked on screen
    clientIdRequired: 'Enter the client ID.',
    clientSecretRequired: 'Enter the client secret.',
    clientIdLength: 'Use {max} characters or fewer for the client ID.',
    clientSecretLength: 'Use {max} characters or fewer for the client secret.',
    clientIdSpace: "The client ID can't contain spaces or control characters.",
    clientSecretSpace: "The client secret can't contain spaces or control characters.",

    // Results
    savedActive: 'Saved the {name} login settings. The button now appears on the login screen.',
    savedInactive: "Saved the {name} login settings. It's on, but the button doesn't appear yet.",
    savedOff: "Saved the {name} login settings. It's off, so the button doesn't appear.",
    removeTitle: 'Delete the {name} login settings?',
    removeMessage: 'The saved client ID and secret are deleted and the {name} button disappears from the login and sign-up screens. Members who already signed up with {name} stay linked.',
    removed: 'Deleted the {name} login settings',
    leaveTitle: 'You have unsaved changes',
    leaveMessage: 'If you leave this screen, what you entered will be lost.',
    leaveConfirm: 'Leave without saving',
};
