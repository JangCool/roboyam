/**
 * 게시판이 섞인 목록 화면의 문구 — 최신글 전체(`/latest`)·인기글 전체(`/popular`)·게시판 모음(`/boards`)과 글 줄(`components/feed/FeedPostItem.vue`).
 * 화면 제목 「최신글」「인기글」은 서비스 이름(`siteTerms.latest`·`popular` — `useSiteTerms().term`)이고, `{name}` 자리에 들어간다(조사가 이름에 붙지 않게 문장을 만들었다).
 * 키는 `feed.무엇` 두 마디. `en/feed.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 공통
    loading: '{name} 목록을 불러오는 중이에요',
    loadFailed: '{name} 목록을 불러오지 못했어요',
    summaryEmpty: '{name} 글이 없어요',
    summaryCount: '{name} {count}개',
    summaryCountMore: '{name} {count}개 · 더 있어요',
    moreFailed: '더 불러오지 못했어요. {message}',
    browseBoards: '게시판 둘러보기',
    firstPage: '첫 쪽으로',
    rank: '{n}위',
    boardTag: '게시판 {name}',

    // 최신글
    latestSubtitle: '전체 게시판의 새 글을 최신순으로 보여 줘요',
    latestSeoDescription: '{site}의 모든 게시판에 올라온 새 글을 최신순으로 모아 봐요.',
    latestEmptyTitle: '아직 올라온 글이 없어요',
    latestEmptyText: '첫 글의 주인공이 되어 보세요',

    // 인기글
    periodRealtime: '실시간',
    periodYesterday: '어제',
    periodWeek: '이번 주',
    periodTabs: '{name} 기간',
    subtitleRealtime: '오늘 올라온 글 중 지금 반응이 좋은 글을 순위대로 보여 줘요',
    subtitleYesterday: '어제 올라온 글을 반응이 많은 순서대로 보여 줘요',
    subtitleWeek: '이번 주에 올라온 글을 반응이 많은 순서대로 보여 줘요',
    emptyRealtimeTitle: '아직 오늘 순위에 든 글이 없어요',
    emptyRealtimeText: '글에 추천이 쌓이면 여기에 올라와요',
    emptyYesterdayTitle: '어제 순위에 든 글이 없어요',
    emptyYesterdayText: '어제 올라온 글 중 순위에 들 만큼 반응을 받은 글이 없었어요',
    emptyWeekTitle: '이번 주 순위에 든 글이 아직 없어요',
    emptyWeekText: '이번 주 올라온 글 중 순위에 들 만큼 반응을 받은 글이 아직 없어요',
    pageEmptyTitle: '이 쪽에는 글이 없어요',
    pageEmptyText: '순위가 바뀌어 없어진 쪽이에요',
    periodLoadFailed: '{period} {name} 목록을 불러오지 못했어요',
    fillYesterday: '어제',
    fillLastWeek: '지난주',
    fillYesterdayLabel: '어제 올라온 글',
    fillLastWeekLabel: '지난주에 올라온 글',
    fillNoticeYesterday: '오늘 올라온 글이 아직 적어서 어제 글로 채웠어요',
    fillNoticeLastWeek: '이번 주 글이 아직 적어서 지난주 글로 채웠어요',
    rangeNote: '{period} {from} ~ {to}',
    popularSeoTitle: '{name} · {period}',
    popularSeoTitlePage: '{name} · {period} ({page}쪽)',
    seoRealtime: '오늘 올라온 글 중 지금 반응이 좋은 글을 순위대로 모아 봐요.',
    seoYesterday: '어제 올라온 글 중 반응이 많았던 글을 순위대로 모아 봐요.',
    seoWeek: '이번 주에 올라온 글 중 반응이 많은 글을 순위대로 모아 봐요.',

    // 게시판 모음
    hubTitle: '게시판',
    hubSeoDescription: '{site}의 모든 게시판이에요.',
    hubLoading: '게시판 목록을 불러오는 중',
    hubError: '게시판 목록을 불러오지 못했어요',
    hubErrorText: '네트워크를 확인하고 다시 시도해 주세요.',
    recentTitle: '최근 방문',
    recentEmpty: '아직 방문한 게시판이 없어요. 아래에서 골라 보세요.',
};
