/**
 * Log-in and authentication screens: login form, social result notices, lockout, email verification banner, password field and rules.
 * Texts that include a provider name are in `provider.ts`. Same keys and order as `ko/login.ts`.
 */
export default {
    // Shared
    seoDescription: 'Log in to {site} with your email, or with a Kakao, Naver or Google account.',
    homeLabel: '{site} home',
    socialAccounts: 'Social accounts',
    socialDivider: 'Or continue with a social account',
    browseWithout: 'Browse without logging in',
    guestNote: 'Guests can only read',

    // Form
    emailLabel: 'Email',
    forgotPassword: 'Forgot password',
    passwordRequired: 'Please enter your password.',
    submitting: 'Logging in…',
    submitWait: 'Please wait. Do not close this screen.',
    lockWarning: 'Too many wrong passwords will lock log-in for a while. (10 minutes after 5 failures in a row)',
    expiredTitle: 'Your session has expired.',
    expiredBody: 'Log in again and you will return to where you were.',

    // Lockout
    lockedTitle: 'Log-in is temporarily locked.',
    lockedBody: 'We locked it to protect your account after several wrong passwords.',
    lockedUntil: 'You can try again in',
    remainingTime: 'Time left {time}',
    lockedButton: 'You can try again shortly',
    lockedHelpLead: 'To log in right now,',
    lockedHelpAction: '{reset} or use a social account',
    passwordReset: 'reset your password',

    // Social login results
    emailExistsTitle: 'An account with this email already exists',
    emailExistsBody: 'The email from {provider} is already used by a {site} account.',
    emailExistsHint: 'Log in with your email, then link {provider} under {where} to log in with it next time.',
    emailExistsWhere: 'Settings › Account',
    noAutoMerge: 'We never merge accounts automatically without verifying it is you.',
    loginWithEmail: 'Log in with email',
    signupOther: 'Sign up with another account',
    socialFailedTitle: 'Logging in with {provider} was not completed',
    socialFailedBody: 'You cancelled or the connection failed. Try again or choose another way.',
    socialExpired: 'Your social sign-up has timed out. Please start again.',

    // Email verification banner
    verifyLabel: 'Email verification notice',
    verifyPending: 'Your email is not verified yet.',
    verifyBenefit: 'Once verified, you can write posts and comments, vote and send messages.',
    verifySentTo: 'Open the link in the email we sent to {email}.',
    verifyGuide: 'View guide',
    resendLabel: 'Resend verification email',
    resendCooling: 'Resend (available in {time})',
    resendSent: 'We sent the verification email again. If you cannot find it, check your spam folder.',
    resendAlready: 'Your email is already verified.',

    // Password field and rules
    passwordLabel: 'Password',
    showPassword: 'Show password',
    hidePassword: 'Hide password',
    rulesLabel: 'Password rules',
    ruleLength: '{min} to {max} characters',
    ruleLengthNow: '{min} to {max} characters ({count} now)',
    ruleBytes: '{max} bytes or fewer in UTF-8 (about {hangul} Korean letters)',
    ruleBytesNow: '{max} bytes or fewer in UTF-8 ({count} now)',
    ruleMix: 'Use both letters and numbers',
    ruleIdle: '(not entered yet)',
    ruleOk: '(met)',
    ruleNo: '(not met)',
};
