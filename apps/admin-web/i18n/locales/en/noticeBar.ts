/**
 * Notice bar settings (Settings > General "Notice bar") — same keys and order as `ko/noticeBar.ts`.
 */
export default {
    title: 'Notice bar',
    intro: 'Shows a one-line notice below the menu on every public page. Visitors can hide it with X for a day, a week, a month or forever; it comes back for them when the bar content changes.',
    enabledLabel: 'Show notice bar',
    enabledHelp: 'Turn off to remove the bar from every page.',
    sourceLabel: 'Content',
    sourceBoard: 'Latest notice posts',
    sourceCustom: 'Custom text',
    boardHelp: 'Rotates the latest posts of the “{name}” board, one line every 5 seconds. Clicking opens the post.',
    boardMissing: 'There is no notice board (address notice), so the bar has nothing to show. Create one in board management or choose custom text.',
    countLabel: 'Posts to show',
    countOption: '{count}',
    textLabel: 'Notice text',
    textHint: '1–{max} characters',
    linkLabel: 'Link (optional)',
    linkHelp: 'A site path (/boards/notice) or an https:// address. Leave empty for text only.',
    previewLabel: 'Lines shown now (saved values)',
    previewEmpty: 'Nothing to show, so the bar is hidden.',
    previewOff: 'Turned off, so the bar is hidden.',
    loadFailed: 'Couldn’t load the notice bar settings',
    saved: 'Notice bar settings saved',

    textRequired: 'Enter the notice text.',
    textLength: 'The notice text must be {max} characters or fewer.',
    textChars: 'The notice text cannot contain "<" or ">".',
    linkLength: 'The link must be {max} characters or fewer.',
    linkShape: 'Enter a site path starting with / or an http(s):// address.',
};
