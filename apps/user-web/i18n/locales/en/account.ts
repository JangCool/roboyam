/**
 * Account & sign-in screen (`/settings/account`): sign-in methods (password or not), linking and unlinking social accounts, and the result after linking.
 * Failure reasons (`reason=`) use `login.reason*`, shared with the log-in screen. The user menu entry also uses `account.title`.
 * Same keys and order as `ko/account.ts`.
 */
export default {
    title: 'Account & sign-in',
    seoDescription: 'Manage how you sign in to {site} and your linked social accounts.',
    loading: 'Loading linked accounts',
    loadFailed: 'Could not load your linked accounts',

    // Sign-in methods
    methodsTitle: 'Sign-in methods',
    passwordRow: 'Email & password',
    passwordOn: 'You can log in with your email and password.',
    passwordOff: 'This account has no password. You log in only with a linked social account.',

    // Social accounts
    socialTitle: 'Linked social accounts',
    linked: 'Linked',
    notLinked: 'Not linked',
    unavailable: 'Not available right now',
    lastMethod: 'You cannot unlink it because you have no other way to log in',
    lastMethodHelp: 'Link another social account first to unlink this one.',
    link: 'Link',
    unlink: 'Unlink',
    linkLabel: 'Link {provider}',
    unlinkLabel: 'Unlink {provider}',
    unlinkTitle: 'Unlink {provider}?',
    unlinkMessage: 'You will no longer be able to log in with this social account. You can link it again later.',
    unlinked: '{provider} was unlinked',
    linkStartFailed: 'Could not start linking. Please try again later.',

    // Result after linking
    linkedToast: '{provider} account linked',
    linkTakenTitle: 'Could not link.',
    linkTaken: 'This {provider} account is already linked to another member.',
    linkFailedTitle: 'Linking {provider} was not completed.',
};
