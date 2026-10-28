/**
 * App shell (top bar, hamburger menu, bottom tabs, footer, write button, log-out confirmation) and shared parts (grade badge, error state). Same keys and order as `ko/shell.ts`.
 */
export default {
    // Top bar
    openMenu: 'Open menu',
    homeLink: '{site} home',
    accountNav: 'Account',
    accountMenu: 'My account menu',
    // Theme shell top menus (Daum-style sub menu, Naver-style top links)
    subMenuLabel: '{title} menu',
    quickLinks: 'Quick links',
    // Arrow buttons at both ends of an overflowing menu row (`AppScrollRow`)
    scrollMenuLeft: 'Scroll menu left',
    scrollMenuRight: 'Scroll menu right',

    // Menu rows
    menu: 'Menu',
    myInfo: 'My profile',
    messageBox: 'Messages',
    messageBoxUnread: 'Messages · {count} unread',
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
    tabGroups: 'Clubs',
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
};
