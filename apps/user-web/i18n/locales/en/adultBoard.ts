/**
 * Adults-only board access and PIN — the "Adults-only boards" section of the identity screen (`/settings/identity`, `AdultBoardSettings`), PIN entry screen (`AdultBoardPinGate`, `AdultBoardPinDialog`).
 * Same keys and order as `ko/adultBoard.ts`.
 */
export default {
    title: 'Adults-only boards',
    loading: 'Loading adults-only board settings',
    loadFailed: 'Could not load adults-only board settings',

    // Not a verified adult (not verified yet, or under 19) — the section shows only this line
    notAdultText: 'Only members verified as 19 or older by identity check can turn on access to adults-only boards.',

    // Access switch
    accessLabel: 'Access to adults-only boards',
    accessOn: 'On',
    accessOff: 'Off',
    offNote: 'While off, adults-only boards are not in the menu and cannot be opened. To turn it on you need your account password and a 6-digit PIN.',
    offNoteSocial: 'While off, adults-only boards are not in the menu and cannot be opened. To turn it on, set a 6-digit PIN while logged in within the last 10 minutes.',
    onNote: 'Adults-only boards are shown in the menu. You enter your PIN to open them.',
    onDone: 'Access to adults-only boards is on',
    offConfirmTitle: 'Turn off access to adults-only boards?',
    offConfirmText: 'Adults-only boards leave the menu and cannot be opened. To turn it back on you set a new PIN with your account password.',
    offConfirmButton: 'Turn off',
    offDone: 'Access to adults-only boards is off',

    // PIN
    pinTitle: 'PIN',
    pinLabel: 'PIN',
    pinSetAt: 'Set on {date}',
    pinSet: 'Set',
    pinChange: 'Change PIN',
    pinHelp: 'Forgot your PIN? Use "Change PIN" to set a new one with your account password.',
    pinHelpSocial: 'Forgot your PIN? Use "Change PIN" to set a new one. You need to have logged in within the last 10 minutes.',
    pinChanged: 'PIN changed',

    // Ask for the PIN again
    relockTitle: 'Ask for the PIN again',
    relockIdle: '1 hour after you last viewed',
    relockIdleHint: 'Not asked while you keep viewing adults-only boards.',
    relockFixed: '1 hour after you entered the PIN',
    relockFixedHint: 'Asked every hour, even while viewing.',
    relockSaved: 'Updated when the PIN is asked again',

    // Lock now
    lockTitle: 'Lock now',
    lockOpen: 'Open on this device',
    lockOpenIdle: 'Asks again in about {minutes} min if you stop viewing.',
    lockOpenFixed: 'Asks again in about {minutes} min.',
    lockClosed: 'Locked on this device',
    lockClosedText: 'You will be asked for your PIN when you open an adults-only board.',
    lockNow: 'Lock',
    lockDone: 'Locked. You will be asked for your PIN next time',
    pinBlocked: 'Too many wrong PINs. You cannot enter it for about {minutes} min. Setting a new one with "Change PIN" unlocks it right away.',

    // Turn on · change PIN dialog
    dialogEnableTitle: 'Turn on access to adults-only boards',
    dialogEnableText: 'Confirm your account password and set the PIN you will enter to open adults-only boards.',
    dialogEnableTextSocial: 'Set the PIN you will enter to open adults-only boards.',
    dialogEnableSubmit: 'Turn on',
    dialogChangeTitle: 'Change PIN',
    dialogChangeText: 'Confirm your account password and set a new PIN. You do not need your current PIN.',
    dialogChangeTextSocial: 'Set a new PIN. You do not need your current PIN.',
    dialogChangeSubmit: 'Change',
    passwordLabel: 'Account password',
    passwordRequired: 'Enter your account password.',
    noPasswordNote: 'Accounts without a password are confirmed by whether you logged in within the last 10 minutes.',
    reauthNeeded: 'It has been more than 10 minutes since you logged in. Log in again, then try once more.',
    reauthLogin: 'Log in again',
    pinNewLabel: 'New PIN',
    pinRule: '6 digits',
    pinConfirmLabel: 'PIN again',
    pinInvalid: 'Enter a 6-digit PIN.',
    pinMismatch: 'The two PINs do not match.',

    // PIN entry screen
    gateTitle: 'Enter your PIN',
    gateText: 'This is an adults-only board. Enter the 6-digit PIN you set in settings to view it.',
    gateChecking: 'Checking',
    gateWrong: 'Wrong PIN. {n} more wrong tries locks it for 10 minutes',
    gateWrongPlain: 'Wrong PIN.',
    gateLocked: 'Locked after too many wrong PINs. Try again in {time}',
    gateForgot: 'I forgot my PIN',
    gateExpired: 'It has been a while since you entered your PIN. Enter it again to continue what you were doing.',
};
