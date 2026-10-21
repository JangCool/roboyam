/**
 * Install wizard (`/install`): step titles and help, check results, input errors, review and done screens. Same keys and order as `ko/install.ts`.
 */
export default {
    // Around the screen (title, progress)
    pageTitle: 'Setup',
    pageDescription: 'Setup wizard for {site}.',
    progressLabel: 'Setup progress',
    progress: '{current} / {total} · {label}',
    installButton: 'Install',
    installing: 'Installing. Do not close this page.',

    // Step names, titles and descriptions
    stepTokenLabel: 'Setup check',
    stepTokenTitle: 'Setup check',
    stepTokenDescription: 'Enter the setup token shown in the log when the server first started. If you set the ROBOYAM_INSTALL_TOKEN environment variable, use that value.',
    stepRootLabel: 'Root account',
    stepRootTitle: 'Root account',
    stepRootDescription: 'Create the first Administrator account for this site. Email verification is skipped and the account is created as verified.',
    stepSiteLabel: 'Site',
    stepSiteTitle: 'Site',
    stepSiteDescription: 'Set the site name, an optional tagline, and the public URL. You can change them later in the Admin app settings.',
    stepMailLabel: 'Mail server',
    stepMailTitle: 'Mail server',
    stepMailDescription: 'The server that sends sign-up verification and password reset emails. You can skip this and add it later in the Admin app settings.',
    stepSocialLabel: 'Social login',
    stepSocialTitle: 'Social login keys',
    stepSocialDescription: 'The client ID and secret for Naver, Kakao and Google login. You can skip this and add them later in the Admin app social login settings.',
    stepBoardsLabel: 'Board setup',
    stepBoardsTitle: 'Default board setup',
    stepBoardsDescription: 'Choose the boards to create first. After setup you can change boards and tags anytime in the Admin app board settings.',
    stepReviewLabel: 'Review',
    stepReviewTitle: 'Review',
    stepReviewDescription: 'Check what you entered, then install. Passwords and secrets are hidden.',

    // Step 1: setup token and checks
    tokenLabel: 'Setup token',
    tokenRequired: 'Enter the setup token.',
    tokenOk: 'Setup token verified.',
    tokenLocked: 'The setup token was wrong too many times, so it is locked for now. You can try again in {remaining}.',
    sessionExpired: 'Your setup authorization has expired. Enter the setup token again. What you entered is still here.',
    checksLoading: 'Running setup checks',
    checksList: 'Setup check results',
    checksFailed: 'Could not load the setup check results.',
    recheck: 'Check again',
    checkBlockingNote: 'Something is blocking setup, so you cannot continue. Fix the server settings, restart the server, and check again.',
    checkCryptoNote: 'There is no encryption key, so the mail password and social login keys cannot be saved now. You can still continue. Set the key, then add them in the Admin app settings.',
    checkLineOk: '{label}: Passed',
    checkLineWarn: '{label}: Warning',
    checkLineFail: '{label}: Failed',

    // When there is no encryption key
    cryptoLockMail: 'There is no encryption key, so the username and password cannot be saved. You can only add a mail server that needs no authentication. If yours does, skip this step.',
    cryptoLockSocial: 'There is no encryption key, so secrets cannot be saved. Skip this step. Set the key, then add them in the Admin app social login settings.',
    cryptoDropped: 'There is no encryption key, so the mail username and password and the social login keys cannot be saved. We cleared those entries and marked the steps as skipped. Review them, then install again.',

    // Step 2: root account
    emailLabel: 'Email',
    nicknameLabel: 'Nickname',
    nicknameHint: '{min}–{max} characters',
    nicknameHelp: 'You can use Korean and English letters, numbers, underscores, and spaces between characters. Names like “Operator” are fine too.',
    passwordLabel: 'Password',
    passwordConfirmLabel: 'Confirm password',
    passwordMatch: 'Passwords match.',
    passwordMismatch: 'Passwords do not match.',
    passwordRequired: 'Enter a password.',
    passwordRule: 'Use {min}–{max} characters, with both letters and numbers.',
    passwordTooLong: 'The password is too long. Use {max} bytes or fewer in UTF-8.',
    passwordConfirmRequired: 'Enter the password again.',

    // Step 4: mail server
    mailSkippedTitle: 'Mail server skipped',
    mailSet: 'Set up mail server',

    // Step 5: social login keys
    socialSkippedTitle: 'Social login keys skipped',
    socialSkippedBody: 'No social login buttons will appear on the log in and sign up pages. You can add them later in the Admin app social login settings.',
    socialSet: 'Enter social login keys',
    socialIntro: 'Turn on only the providers you want to use, then enter the client ID and secret. Providers left off are not used.',

    // Step 6: default board setup
    boardsLoading: 'Loading the available setups.',
    boardsFailed: 'Could not load the default board setups. If you install now, the server default (neutral) will be used.',
    boardsDefaultMark: 'Default',
    boardsNote: 'The Hot deals and Self-promotion boards are feature boards, so they are created with every setup. This choice applies only once, at setup.',
    boardsServerDefault: 'Server default',
    boardsSummary: 'Show boards ({count})',

    // Step 7: review
    filled: 'Entered',
    skipped: 'Skipped',
    reviewServer: 'Server',
    reviewSecurity: 'Security',
    reviewNoAuth: 'None (no authentication)',
    reviewTagline: 'Tagline',
    reviewPreset: 'Preset',
    reviewSocialClientId: '{provider} client ID',
    reviewSocialSecret: '{provider} secret',
    reviewFill: 'Enter',
    reviewEditLabel: 'Edit {title}',
    reviewFillLabel: 'Enter {title}',
    installNote: 'Installing creates the root account and closes this page. Right after installing you go to the log in page and log in with the account you just created.',

    // Done
    doneTitle: 'Setup is complete',
    doneText: 'Log in with {email} to get started. After you log in, you can change the site, mail and social login settings in the Administrator tools under My profile.',
    goLogin: 'Log in now',

    // Could not get the setup status
    statusFailedTitle: 'Could not check the setup status',
    statusFailedDescription: 'Could not connect to the server. Make sure the server is running, then try again.',
    statusBanner: 'Could not check the server setup status. Some pages may not work.',
    statusRecheck: 'Check again',
};
