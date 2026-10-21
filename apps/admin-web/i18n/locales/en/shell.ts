/**
 * Admin shell (top bar, sidebar, search, notification/display-settings drawers, profile, mobile menu, sign-out confirmation).
 * Keys are two parts: `shell.what` — no screen names. Same keys and order as `ko/shell.ts`.
 */
export default {
    // Top bar
    adminBadge: 'Admin',
    homeLink: '{site} admin home',
    openMenu: 'Open menu',
    accountInfo: 'Signed-in admin',
    collapseSidebar: 'Collapse sidebar',
    expandSidebar: 'Expand sidebar',
    toDark: 'Switch to dark mode',
    toLight: 'Switch to light mode',
    notifications: 'Notifications',
    notificationsUnread: 'Notifications, {count} unread',
    displaySettings: 'Display settings',
    profileMenu: 'Account menu',
    myProfile: 'My profile',
    settings: 'Settings',
    backToMenu: 'Back to menu',

    // Menu search
    searchPlaceholder: 'Search menu',
    searchOpen: 'Open menu search',
    searchClose: 'Close menu search',
    searchEmpty: 'No matching menu',

    // Menu
    mainNav: 'Admin menu',
    settingsNav: 'Settings menu',
    menu: 'Menu',

    // Accessibility skip link
    skipToContent: 'Skip to content',

    // Screen without permission
    forbiddenTitle: 'You do not have permission to view this screen',
    forbiddenBody: 'Ask the super administrator for a role if you need access.',
    goHome: 'Go home',

    // Roles — administrator (ADMIN) · staff (STAFF)
    roleAdmin: 'Administrator',
    roleStaff: 'Staff',

    // Display settings drawer
    settingsTitle: 'Display settings',
    settingsPrimary: 'Accent color',
    settingsBarTint: 'Apply accent color to top bar',
    barTintHint: 'When off, the top bar is not tinted and uses the plain surface color.',
    settingsTextSize: 'Text size',
    textSmaller: 'Smaller text',
    textLarger: 'Larger text',
    settingsDark: 'Dark mode',
    settingsWidth: 'Content width',
    widthFull: 'Full width',
    widthBoxed: 'Centered',
    settingsHoverExpand: 'Expand sidebar on hover',
    hoverExpandHint: 'Hover over the collapsed sidebar to expand it temporarily.',

    settingsSurface: 'Background color',
    settingsDensity: 'Density',
    densityNormal: 'Normal',
    densityCompact: 'Compact',
    settingsMenuPosition: 'Menu position',
    menuSidebar: 'Sidebar',
    menuTop: 'Top',
    menuPositionMobileHint: 'On narrow (mobile) screens the hamburger menu is always used.',
    settingsDrawerPosition: 'Drawer position',
    settingsSidebarPosition: 'Sidebar position',
    sideLeft: 'Left',
    sideRight: 'Right',
    settingsNotify: 'Notifications',
    notifyToast: 'Toast for new notifications',
    notifyToastHint: 'Shown under the bell while you are looking at the screen.',
    notifyOs: 'OS notifications',
    notifyOsHint: 'Sent as a system notification while the window is minimized or another window has focus.',
    notifyOsDenied: 'Your browser is blocking notifications for this site. Allow them in the site settings next to the address bar.',
    notifyOsUnsupported: 'This browser does not support OS notifications.',

    // Background color names
    surfaceDefault: 'Default',
    surfaceWhite: 'White',
    surfaceSlate: 'Slate',
    surfaceGray: 'Gray',
    surfaceZinc: 'Zinc',
    surfaceNeutral: 'Neutral',
    surfaceStone: 'Stone',

    // Accent color names
    colorBlue: 'Blue',
    colorIndigo: 'Indigo',
    colorViolet: 'Violet',
    colorPink: 'Pink',
    colorRed: 'Red',
    colorOrange: 'Orange',
    colorAmber: 'Amber',
    colorGreen: 'Green',
    colorTeal: 'Teal',
    colorCyan: 'Cyan',

    // Sign-out confirmation
    logoutTitle: 'Sign out?',
    logoutMessage: 'You will be signed out of the admin screen on this device. Sign in again to come back.',
    loggedOut: 'Signed out',
};
