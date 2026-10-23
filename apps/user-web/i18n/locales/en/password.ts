/**
 * Password screens: forgot password (`/password/forgot`), reset (`/password/reset`), social-account notice (`/password/social`), change password (`/settings/account/password`),
 * and the "confirm password" field shared by sign-up, reset and change. The password input itself (show/hide, rule lines) is `login.*`.
 * Same keys and order as `ko/password.ts`.
 */
export default {
    // Confirm password field (sign-up, reset)
    confirmLabel: 'Confirm password',
    mismatch: 'The passwords do not match.',
    match: 'The passwords match.',

    // Forgot password
    forgotSeoDescription: 'Get a password reset link at the email you signed up with.',
    forgotTitle: 'Enter the email you signed up with',
    forgotSubtitle: 'We will email you a link to set a new password.',
    forgotSubmit: 'Send reset email',
    sending: 'Sending…',
    backToLogin: 'Back to log in',
    socialNote: 'If you signed up with Kakao, Naver or Google, you have no password. Log in with that social account.',
    sentTitle: 'Reset email sent',
    sentText: 'If an account uses this address, we sent it a password reset link. If nothing arrives, check your spam folder.',
    resend: 'Send again',
    resendCooling: 'Send again (in {time})',
    sameNotice: 'We always show the same notice so nobody can tell whether an account exists. The link works for 1 hour.',

    // Reset password
    resetSeoTitle: 'Reset password',
    resetSeoDescription: 'Set a new password.',
    checkingTitle: 'Checking the link',
    expiredTitle: 'The reset link has expired',
    expiredText: 'Links work for a limited time (1 hour). Please get a new reset email.',
    getNewMail: 'Get a new reset email',
    usedNote: 'A link that was already used shows this screen too.',
    checkErrorTitle: 'Could not check the link',
    doneTitle: 'Your password was changed',
    doneText: 'Log in with your new password. For security, you are logged out on other devices.',
    toLogin: 'Log in',
    resetTitle: 'Set a new password',
    newLabel: 'New password',
    newConfirmLabel: 'Confirm new password',
    submit: 'Change password',
    changing: 'Changing…',

    // Social account notice
    socialSeoTitle: 'Social account',
    socialSeoDescription: 'Accounts created with a social login have no password.',
    socialTitleKnown: 'This account was created with {provider}',
    socialTitleUnknown: 'This account was created with a social login',
    socialNoPassword: 'This account has no password.',
    socialLoginKnown: 'Please log in with {provider}.',
    socialLoginUnknown: 'Please log in with the social account you signed up with.',
    socialMailOnly: 'This screen opens only from the link in the email sent to social-login members.',

    // Change password (settings)
    changeSeoDescription: 'Change your {site} password.',
    currentLabel: 'Current password',
    currentRequired: 'Please enter your current password.',
    rulesNewLabel: 'New password conditions',
    ruleDifferent: 'Different from your current password',
    newMismatch: 'The new passwords do not match',
    newMatch: 'Matches the new password',
    changedToast: 'Password changed',
    changedTitle: 'Your password was changed',
    changedText: 'Log in with your new password from now on. This device stays logged in; other devices need to log in again.',
    lockedTitle: 'Too many wrong current passwords.',
    lockedText: 'Please try again in {minutes} min.',
    lockedHelp: 'If you do not remember your current password, log out and use "Forgot password" on the log-in screen.',
    socialAccountTitle: 'This account was created with a social login',
    notSetText: 'This account has no password, so it cannot be changed.',
    socialGuideOne: 'Manage the security of your {provider} account at {provider}.',
    socialGuideMany: 'Manage the security of your social accounts at each service.',
};
