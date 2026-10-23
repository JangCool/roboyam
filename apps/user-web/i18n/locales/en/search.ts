/**
 * Search screen (`/search`). Server error messages come from the server. Same keys and order as `ko/search.ts`.
 */
export default {
    title: 'Search',
    resultTitle: 'Results for “{q}”',
    seoDescription: 'Search {site} posts by title, body or author.',

    // Search bar
    formLabel: 'Search posts',
    inputLabel: 'Search terms',
    placeholder: 'Search titles, posts and authors',
    placeholderBoard: 'Search in {board}',
    clear: 'Clear search',
    back: 'Go back',

    // Scope · board · order
    scopeLabel: 'Search scope',
    scopeAll: 'All',
    scopeTitle: 'Title',
    scopeBody: 'Body',
    scopeAuthor: 'Author',
    boardLabel: 'Board',
    allBoards: 'All boards',
    orderLabel: 'Sort',
    orderLatest: 'Newest',
    orderOldest: 'Oldest',

    // Entry · recent searches
    entryHint: 'Search titles, posts and authors',
    recentTitle: 'Recent searches',
    recentClear: 'Clear all',
    recentRemove: 'Remove recent search “{q}”',
    recentEmptyTitle: 'No recent searches',
    recentEmptyText: 'Search for a post or a nickname. Use at least 2 characters.',
    recentNote: 'Recent searches are saved only on this device',

    // Checks
    tooShort: 'Enter at least 2 characters',
    tooLong: 'Enter 50 characters or fewer',
    shortWord: 'Each word needs at least 2 characters',

    // Result summary
    searching: 'Searching for “{q}”',
    summary: 'Results for “{q}” · {count}',
    summaryPage: 'Results for “{q}” · page {page} · {count}',
    summaryMore: '{text} · more available',

    // Result states
    timeoutHint: 'Try more specific terms. Add a word, use a longer word, or narrow the scope to “Title” or “Body” to search faster.',
    boardInvalidTitle: "You can't search this board",
    boardInvalidText: 'The board address is wrong or the board is no longer in use.',
    searchAllBoards: 'Search all boards',
    errorTitle: "Couldn't search",
    pageEmptyTitle: 'No results on this page',
    pageEmptyText: 'The posts were removed or this page does not exist.',
    emptyTitle: 'No results for “{q}”',
    emptyText: 'Check the spelling or try shorter words.',
    suggestWords: 'How about these?',
    suggestRelax: 'Broaden your search?',
    relaxScope: 'Search all fields',
    relaxBoard: 'Search all boards',
    more: 'Load more',
    hiddenNote: "Deleted and hidden posts aren't included in results",
};
