/**
 * Email verification screen (`/verify-email`): results of opening a mail link (verified, email changed, expired, unusable, error) and the "verification email sent" notice.
 * The resend button and its results reuse `login.resend*` from the banner. Same keys and order as `ko/verify.ts`.
 */
export default {
    seoTitle: 'Email verification',
    seoDescription: 'Finish verifying your email.',

    // Opening the link
    verifyingTitle: 'Checking your email',
    doneTitle: 'Your email is verified',
    doneText: 'You can now write posts and comments.',
    start: 'Get started',
    loginToStart: 'Log in',
    otherDevice: 'If you opened the link on another device, log in on your original device.',
    changedTitle: 'Your email was changed',
    changedText: 'Log in with your new email from now on.',
    takenTitle: 'Could not change your email',
    takenText: 'Another member started using this email in the meantime. Please try a different email.',
    retryOtherEmail: 'Try a different email',
    loginAndRetry: 'Log in and try again',
    alreadyTitle: 'This account is already verified',
    alreadyText: 'You can write posts and comments.',
    home: 'Go home',
    expiredTitle: 'The verification link has expired',
    expiredText: 'Links work for 24 hours only. Please get a new verification email.',
    invalidTitle: 'This verification link cannot be used',
    invalidText: 'The link is wrong or was already used. Please get a new verification email.',
    getNew: 'Get a new verification email',
    loginAndGetNew: 'Log in and get a new verification email',
    alreadyHint: 'If you already verified, just log in.',
    errorTitle: 'Could not check your verification',

    // Verification email sent
    sentTitle: 'Verification email sent',
    sentTo: 'Open the link in the email we sent to {email}.',
    validHours: 'The link works for 24 hours.',
    spamNote: 'If you cannot find it, check your spam folder. This is a send-only address, so replies are not received.',
    readOnlyNote: 'Until you verify, you can only read posts.',
    changeEmail: 'Typed the wrong email? Change it',
    browseHome: 'Browse the home page',
};
