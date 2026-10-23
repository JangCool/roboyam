/**
 * 통합 검색 화면(`/search`)의 문구 — `pages/search.vue`·`utils/util-search-messages.ts` 가 쓴다. 서버 오류 문구는 서버 몫이라 여기 없다.
 * 키는 `search.무엇` 두 마디. `en/search.ts` 와 같은 키, 같은 차례.
 */
export default {
    title: '검색',
    resultTitle: '‘{q}’ 검색 결과',
    seoDescription: '{site}의 글을 제목·본문·작성자로 검색해요.',

    // 검색 막대
    formLabel: '글 검색',
    inputLabel: '검색어',
    placeholder: '제목·본문·작성자 검색',
    placeholderBoard: '{board} 게시판에서 검색',
    clear: '검색어 지우기',
    back: '뒤로 가기',

    // 범위·게시판·정렬
    scopeLabel: '검색 범위',
    scopeAll: '전체',
    scopeTitle: '제목',
    scopeBody: '본문',
    scopeAuthor: '작성자',
    boardLabel: '게시판',
    allBoards: '전체 게시판',
    orderLabel: '정렬',
    orderLatest: '최신순',
    orderOldest: '오래된순',

    // 검색 진입 · 최근 검색어
    entryHint: '제목·본문·작성자를 검색해요',
    recentTitle: '최근 검색어',
    recentClear: '전체 삭제',
    recentRemove: '최근 검색어 ‘{q}’ 지우기',
    recentEmptyTitle: '최근 검색어가 없어요',
    recentEmptyText: '궁금한 글이나 닉네임을 검색해 보세요. 검색어는 2글자 이상 입력해 주세요.',
    recentNote: '최근 검색어는 이 기기에만 저장돼요',

    // 검색어 검사
    tooShort: '검색어는 2글자 이상 입력해 주세요',
    tooLong: '검색어는 50자 이하로 입력해 주세요',
    shortWord: '각 단어는 2글자 이상이어야 해요',

    // 결과 안내(스크린리더·결과 위 한 줄)
    searching: '‘{q}’ 검색하는 중이에요',
    summary: '‘{q}’ 검색 결과 · {count}개',
    summaryPage: '‘{q}’ 검색 결과 · {page}쪽 · {count}개',
    summaryMore: '{text} · 더 있어요',

    // 결과 상태
    timeoutHint: '더 구체적인 검색어로 다시 검색해 주세요. 단어를 하나 더 붙이거나 긴 단어로 바꾸고, 범위를 “제목”이나 “본문”으로 좁히면 더 빨리 찾아요.',
    boardInvalidTitle: '이 게시판에서는 검색할 수 없어요',
    boardInvalidText: '게시판 주소가 잘못됐거나 지금은 쓰지 않는 게시판이에요.',
    searchAllBoards: '전체 게시판에서 검색',
    errorTitle: '검색하지 못했어요',
    pageEmptyTitle: '이 쪽에는 결과가 없어요',
    pageEmptyText: '글이 지워졌거나 없는 쪽이에요.',
    emptyTitle: '‘{q}’ 검색 결과가 없어요',
    emptyText: '철자를 확인하거나 더 짧은 단어로 검색해 보세요.',
    suggestWords: '이런 검색어는 어때요?',
    suggestRelax: '조건을 넓혀 볼까요?',
    relaxScope: '범위를 ‘전체’로',
    relaxBoard: '전체 게시판에서 찾기',
    more: '더보기',
    hiddenNote: '삭제·숨김 글은 결과에 나오지 않아요',
};
