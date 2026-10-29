/**
 * homeLayout messages — home layout editing on the theme screen (`/settings/theme`). Same keys, same order as `ko/homeLayout.ts`.
 */
export default {
    readOnly: 'You can only view this layout.',
    loadFailed: 'Could not load the home layout.',
    saved: 'Home layout saved.',
    saveFailed: 'Could not save the home layout.',
    lastSaved: 'Last saved {at} · {by}',
    neverSaved: 'Never saved — this is the theme’s default layout.',

    // ── Columns ─
    columns: 'Grid columns',
    columnsOption: '{count} columns',
    liveEditHint: 'In the live view below, drag a gadget to move it, drag its bottom-right corner to resize, and use × at the top right to remove it. Use ⚙ on the group-buy gadget to change its count and order.',

    // ── Gadget settings (⚙ — group buy only for now) ─
    settingsTitle: '"{title}" settings',
    settingsLead: 'Choose how many group buys to show on the home page and in what order. Only open, recruiting group buys appear.',
    settingsCount: 'Number to show',
    settingsCountHint: '{min}–{max}',
    settingsSort: 'Order',
    sort_DEADLINE: 'Ending soonest',
    sort_NEWEST: 'Newest',
    sort_POPULAR: 'Most joined',
    settingsPreviewNote: 'Before you save, the live view only reorders and trims the group buys already shown. Saving picks them again with these settings.',
    settingsApply: 'Apply',
    settingsApplied: 'Changed the "{title}" settings. Save to apply.',

    // ── Editor ─
    announce: '{title}: column {x}, row {y}, width {w}, height {h}',
    removed: 'Removed “{title}”. Use Reset before saving to bring it back.',

    // ── Add · reset ─
    add: 'Add gadget',
    addTitle: 'Add gadget',
    addLead: 'Pick a kind. It goes to the bottom at its default size. Its content (boards, counts …) is created with the kind’s defaults when you save.',
    added: 'Added “{title}” at the bottom.',
    resetDefault: 'Theme default',
    resetTitle: 'Switch to the theme’s default layout?',
    resetMessage: 'The gadgets and positions in the editor become the theme’s default layout. Save to apply.',
    resetConfirm: 'Use default',
    resetDone: 'Loaded the theme’s default layout. Save to apply.',
    switchTitle: 'You have unsaved layout changes',
    switchMessage: 'Moving to another theme discards your changes.',
    switchConfirm: 'Discard and move',
    leaveTitle: 'You have unsaved layout changes',
    leaveMessage: 'Leaving this page discards your changes.',
    leaveConfirm: 'Leave',
    stay: 'Stay',

    // ── Gadget kinds ─
    kind: {
        HIGHLIGHT: 'Highlight',
        POST_LIST: 'Post list',
        BOARD_CARDS: 'Board cards',
        GALLERY: 'Gallery',
        GROUPS: 'Popular groups',
        GROUP_BUY: 'Group buy',
        QUICK_LINKS: 'Quick links',
        NOTICE_TEXT: 'Notice text',
        UNKNOWN: 'Unknown kind',
    },
    kindDesc: {
        HIGHLIGHT: 'Shows posts picked by operators, large.',
        POST_LIST: 'Popular, latest or board posts as rows.',
        BOARD_CARDS: 'A card per board with a lead post and titles.',
        GALLERY: 'Photo and video posts as tiles.',
        GROUPS: 'Groups that are active lately.',
        GROUP_BUY: 'Group buys in progress.',
        QUICK_LINKS: 'A row of buttons to popular places.',
        NOTICE_TEXT: 'A short note written by operators.',
    },
};
