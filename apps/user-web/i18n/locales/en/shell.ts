/**
 * App shell (top bar, hamburger menu, bottom tabs, footer, write button, log-out confirmation) and shared parts (grade badge, error state). Same keys and order as `ko/shell.ts`.
 */
export default {
    // Top bar
    openMenu: 'Open menu',
    homeLink: '{site} home',
    accountNav: 'Account',
    accountMenu: 'My account menu',

    // Menu rows
    menu: 'Menu',
    myInfo: 'My profile',
    adminApp: 'Admin app',
    adminAppNewTab: 'Admin app (opens in a new tab)',

    // Prompt before logging in (hamburger menu)
    loginInvite: 'Log in to join the conversation',
    loginInviteHint: 'You can write posts and comments after logging in',

    // Accessibility skip link
    skipToContent: 'Skip to main content',

    // Bottom tabs
    bottomTabs: 'Bottom tabs',
    tabHome: 'Home',
    tabBoards: 'Boards',
    tabGroups: 'Groups',
    tabNotifications: 'Notifications',

    // Write button
    write: 'Write',

    // Log-out confirmation
    logoutTitle: 'Log out?',
    logoutMessage: 'You will be logged out on this device. Log in again to pick up where you left off.',
    loggedOut: 'You have been logged out',

    // Grade badge (when the server sends no name)
    gradeSeedling: 'Seedling',
    gradeRegular: 'Regular',
    gradeLoyal: 'Loyal',
    gradeVeteran: 'Veteran',

    // Placeholder home
    homePlaceholderTitle: 'The home screen is not built yet',
    homePlaceholderBody: 'This is a placeholder screen for checking the default layout.',
};
