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

    // Sign-in details — email, verification, password, and the rows to change email or password
    methodsTitle: 'Sign-in details',
    emailRow: 'Email',
    noEmail: 'No email registered',
    verified: 'Verified',
    unverified: 'Not verified',
    resendDone: 'Verification email sent again',
    resendWait: 'You can send again in {time}',
    checkSpam: 'If you cannot find the email, check your spam folder.',
    emailChange: 'Change email',
    emailChangeHint: 'We send a verification email to the new address',
    passwordChange: 'Change password',
    passwordChangeHint: 'Only for accounts created with email',
    noEmailChangeReason: 'Accounts created with a social login cannot change their email',
    noPasswordReason: 'Not available for accounts created with a social login',
    notAvailable: 'Unavailable',
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

    // Change email (`/settings/account/email`) — includes words shared with the change-password screen
    backToAccount: 'Back to account',
    accountLoading: 'Loading your account',
    accountLoadFailed: 'Could not load your account',
    emailSeoDescription: 'Change the email you use to log in to {site}.',
    currentEmail: 'Current email',
    newEmail: 'New email',
    newEmailHelp: 'We send a verification email to the new address. The change finishes when you open its link.',
    passwordHelp: 'Enter your current password once more to confirm it is you',
    resendPasswordHelp: 'To send again, enter your current password once more to confirm it is you',
    emailSend: 'Send verification email',
    emailResend: 'Send verification email again',
    sending: 'Sending…',
    sendFailed: 'Could not send the verification email.',
    sentTitle: 'Verification email sent',
    sentText: 'The change finishes when you open the link in the email. Until then, log in with your current email.',
    wrongEmailNote: 'If you typed the email wrong, enter it again from the start.',
    retype: 'Enter a different email',
    pendingNote: 'A change request sent to {email} is not finished yet. If you send a new one, the earlier link stops working.',
};
