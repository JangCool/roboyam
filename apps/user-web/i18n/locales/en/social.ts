/**
 * Social login key inputs (install wizard step 5). Same keys and order as `ko/social.ts`.
 */
export default {
    // Provider names
    providerNaver: 'Naver',
    providerKakao: 'Kakao',
    providerGoogle: 'Google',

    // Field names
    clientIdLabel: 'Client ID',
    clientSecretLabel: 'Client secret',

    // Toggle switch
    stateOn: 'On',
    stateOff: 'Off',

    // Callback URL
    callbackLabel: 'Callback URL',
    callbackHint: 'Register it in the {provider} developer console',
    callbackMissing: 'Enter a valid public URL (step 3) and the callback URL will appear here.',
    copyCallbackLabel: 'Copy {provider} callback URL',
    copied: '{provider} callback URL copied',
    copyFailed: 'Could not copy. Press and hold the address to copy it yourself.',

    // Checks done on the screen
    clientIdRequired: 'Enter the client ID.',
    clientSecretRequired: 'Enter the client secret.',
    clientIdLength: 'The client ID must be {max} characters or fewer.',
    clientSecretLength: 'The client secret must be {max} characters or fewer.',
    clientIdSpace: 'The client ID cannot contain spaces.',
    clientSecretSpace: 'The client secret cannot contain spaces.',
};
