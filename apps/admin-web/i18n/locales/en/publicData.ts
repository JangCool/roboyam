/**
 * Public data portal settings (`/settings/public-data`) — screen `pages/settings/public-data.vue`. Menu label is `menu.publicData`.
 * Same keys and order as `ko/publicData.ts`.
 */
export default {
    title: 'Public data portal',
    subtitle: 'The data.go.kr service key. NTS business status and identity checks share this one key. Saved values are never shown again.',
    readOnly: 'View only. Changing or removing the key needs the secret settings permission.',
    loadFailed: 'Could not load the public data settings',
    cryptoMissingTitle: 'The server has no encryption key',
    cryptoMissingText: 'Without it values cannot be saved (removing still works). Check the server setting ROBOYAM_CRYPTO_KEY first.',

    usageTitle: 'Used by',
    usageNts: 'NTS business check',
    provider: {
        ODCLOUD: 'Public data portal',
        FAKE: 'Fake (development)',
    },
    providerFake: 'A test-only fake check is running, so this key is used only by “Test connection”.',
    noKeyTitle: 'NTS checks are unavailable',
    noKeyText: 'Without a service key every check is “unavailable”. Operators must give a reason to approve.',

    keyTitle: 'Service key',
    keyHelp: 'Paste the “General auth key (Decoding)” of the NTS business registration verification service from data.go.kr My page › Developer account.',
    source: {
        SCREEN: 'Set on screen',
        ENV: 'Using env variable',
        NONE: 'Needs setup',
    },
    field: 'General auth key',
    configured: 'Set',
    notConfigured: 'Not set',
    updatedAt: 'Changed {at}',
    updatedBy: '{at} · {name}',
    envNotice: 'The server env variable ROBOYAM_NTS_SERVICE_KEY is in use. A key saved here takes priority; removing it falls back to the env variable.',
    value: 'Auth key',
    newValue: 'New auth key',
    valueHelp: 'Letters and digits, no spaces',
    keepHint: 'Leave empty to keep the current key.',
    noSpaces: 'Enter it without spaces.',
    changes: 'Unsaved input',
    noChanges: 'No changes',
    saved: 'Service key saved',
    remove: 'Remove',
    removeTitle: 'Remove the service key?',
    removeMessage: 'This removes the key saved on this screen. The env variable is used if present; otherwise NTS checks become “unavailable”.',
    removed: 'Service key removed',

    testTitle: 'Test connection',
    testHelp: 'Calls the real NTS status check once with the saved key, even when the fake implementation is in use.',
    testRun: 'Test connection',
    testSaveFirst: 'Save the key you entered first.',
    testResult: {
        OK: 'NTS answered. The key works.',
        KEY_INVALID: 'The key was rejected. Check the general auth key (Decoding).',
        NO_KEY: 'No key is saved.',
        TIMEOUT: 'NTS did not answer. Try again shortly.',
        ERROR: 'The check failed. Try again shortly.',
    },
    testedAt: 'Tested {at}',
    testedBy: '{at} · tested by {name}',

    leaveTitle: 'Leave without saving?',
    leaveMessage: 'The key you entered will not be saved.',
    leaveConfirm: 'Leave',
};
