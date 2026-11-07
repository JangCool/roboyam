/**
 * 팁·강좌·사용기 묶음 화면(`/tips`)의 문구 — 매거진형(이번 주 추천 · 최신 글 · 많이 본 글 · 태그 카드). 칩의 게시판 이름은 운영자가 정하는 값이라 서버가 준다(`useLocalizedName`).
 * 키는 `tips.무엇` 두 마디. `en/tips.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '팁·강좌·사용기',
    subtitle: '따라 하기 좋은 팁과 강좌, 직접 써 본 사용기를 모아 봐요',
    boardTabs: '게시판 고르기',
    headlineTitle: '이번 주 추천',
    latestTitle: '최신 글',
    mostViewedTitle: '많이 본 글',
    mostViewedEmpty: '아직 모을 글이 없어요',
    tagsTitle: '태그',
    tagSearch: '{name} 검색',
    loading: '글을 불러오는 중이에요',
    loadFailed: '글을 불러오지 못했어요',
    emptyTitle: '아직 올라온 글이 없어요',
    emptyText: '첫 글의 주인공이 되어 보세요',
    summary: '최신 글 {count}개',
    seoDescription: '{site}의 팁·강좌와 사용기를 한곳에서 모아 봐요.',
};
