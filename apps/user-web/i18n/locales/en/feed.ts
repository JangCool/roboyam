/**
 * Mixed-board list screens — all latest posts (`/latest`), popular posts (`/popular`), board hub (`/boards`) and the post row.
 * `{name}` is the service name ("Latest", "Popular" — `useSiteTerms().term`). Same keys and order as `ko/feed.ts`.
 */
export default {
    // Common
    loading: 'Loading {name}',
    loadFailed: "Couldn't load {name}",
    summaryEmpty: 'No {name} posts',
    summaryCount: '{name}: {count}',
    summaryCountMore: '{name}: {count} · more available',
    moreFailed: "Couldn't load more. {message}",
    browseBoards: 'Browse boards',
    firstPage: 'Go to the first page',
    rank: 'Rank {n}',
    boardTag: 'Board {name}',

    // Latest
    latestSubtitle: 'New posts from every board, newest first',
    latestSeoDescription: 'Browse new posts from every board on {site}, newest first.',
    latestEmptyTitle: 'No posts yet',
    latestEmptyText: 'Be the first to post',

    // Popular
    periodRealtime: 'Now',
    periodYesterday: 'Yesterday',
    periodWeek: 'This week',
    periodTabs: '{name} period',
    subtitleRealtime: "Today's posts ranked by how well they're doing right now",
    subtitleYesterday: "Yesterday's posts ranked by reactions",
    subtitleWeek: "This week's posts ranked by reactions",
    emptyRealtimeTitle: 'No ranked posts today yet',
    emptyRealtimeText: 'Posts show up here as they collect likes',
    emptyYesterdayTitle: 'No ranked posts yesterday',
    emptyYesterdayText: "None of yesterday's posts got enough reactions to rank",
    emptyWeekTitle: 'No ranked posts this week yet',
    emptyWeekText: "None of this week's posts have enough reactions to rank yet",
    pageEmptyTitle: 'No posts on this page',
    pageEmptyText: 'The ranking changed and this page no longer exists',
    periodLoadFailed: "Couldn't load {period} {name}",
    fillYesterday: 'Yesterday',
    fillLastWeek: 'Last week',
    fillYesterdayLabel: 'Posted yesterday',
    fillLastWeekLabel: 'Posted last week',
    fillNoticeYesterday: "There aren't many posts today yet, so we filled in with yesterday's",
    fillNoticeLastWeek: "There aren't many posts this week yet, so we filled in with last week's",
    rangeNote: '{period} {from} – {to}',
    popularSeoTitle: '{name} · {period}',
    popularSeoTitlePage: '{name} · {period} (page {page})',
    seoRealtime: "Today's posts ranked by how well they're doing right now.",
    seoYesterday: "Yesterday's posts that got the most reactions.",
    seoWeek: "This week's posts that got the most reactions.",

    // Board hub
    hubTitle: 'Boards',
    hubSeoDescription: 'Every board on {site}.',
    hubLoading: 'Loading boards',
    hubError: "Couldn't load boards",
    hubErrorText: 'Check your connection and try again.',
    recentTitle: 'Recently visited',
    recentEmpty: "You haven't visited any boards yet. Pick one below.",
};
