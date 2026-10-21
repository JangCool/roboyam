/**
 * Admin sign-in screen — email + password only (no social sign-in, sign-up or password recovery), session expiry, lockout.
 * Keys are two parts: `login.what`. Same keys and order as `ko/login.ts`.
 */
export default {
    // Screen
    title: 'Admin sign-in',
    subtitle: 'Sign in with a staff or administrator account.',
    seoTitle: 'Sign in',

    // Form
    emailLabel: 'Email',
    passwordLabel: 'Password',
    showPassword: 'Show password',
    hidePassword: 'Hide password',
    passwordRequired: 'Enter your password.',
    emailRequired: 'Enter your email.',
    emailLength: 'Email must be 254 characters or fewer.',
    emailFormat: 'Check the email address format.',
    submitting: 'Signing in…',
    submitWait: 'Please wait. Do not close this screen.',
    lockWarning: 'Too many wrong passwords will lock sign-in for a while. (10 minutes after 5 failures in a row)',

    // Returned after the session ended
    expiredTitle: 'Your session has ended.',
    expiredBody: 'For security you are signed out after a period of inactivity. Sign in again to return to where you were.',

    // Lockout
    lockedTitle: 'Sign-in is temporarily restricted.',
    lockedBody: 'Too many wrong passwords, so the account was locked to keep it safe.',
    lockedUntil: 'You can try again in',
    remainingTime: 'Time left {time}',
    lockedButton: 'You can try again shortly',
};
