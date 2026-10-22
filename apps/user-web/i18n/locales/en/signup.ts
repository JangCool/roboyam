/**
 * Sign-up screens: finishing a social sign-up (`/signup/social`) and terms agreement (`TermsAgreement`, also for the email sign-up screen).
 * Nickname rule texts are in `auth.ts`. Same keys and order as `ko/signup.ts`.
 */
export default {
    // Finish social sign-up
    socialTitle: 'Finish signing up',
    socialSeoDescription: 'The last step of signing up with a social account. Choose a nickname and agree to the terms.',
    socialLoading: 'Checking your social account…',
    socialLoadFailed: 'Could not load your sign-up details',
    socialChecked: 'Your {provider} account is confirmed.',
    socialRemaining: 'Only a nickname and the terms are left.',
    socialEmail: 'Email received: {email}',
    emailUnverified: 'This email is not verified yet. After you sign up we will send a verification email to it. Open its link to start writing posts and comments.',
    nicknameLabel: 'Nickname',
    nicknameHint: '{min}–{max} characters',
    nicknameHelp: 'No real name or contact details needed. Use Korean or English letters, digits, underscores and single spaces between words.',
    submit: 'Get started',
    submitting: 'Signing up…',
    leave: 'Leave without signing up',
    toLogin: 'Go to log in',
    welcome: 'You are all signed up. Welcome!',

    // Terms agreement
    termsLegend: 'Terms agreement',
    agreeAll: 'Agree to all',
    requiredMark: 'Required',
    view: 'View',
    viewNewTab: '({label}, new tab)',
    ageConfirmedLabel: 'I am 14 or older',
    agreeTermsMissing: 'Please agree to the Terms of Service.',
    agreePrivacyMissing: 'Please agree to the Privacy Policy.',
    ageConfirmedMissing: 'You must be 14 or older to sign up.',
    ageNote: 'Children under 14 cannot sign up. If found, the account may be closed.',
};
