/**
 * 게시판 업무의 고정 문구 — 게시판 목록 화면(`pages/boards/[slug].vue`)·목록 모양 컴포넌트·`utils/util-board.ts`·`util-board-messages.ts` 가 쓴다.
 * 게시판 **이름**은 운영자가 정하는 동적 이름이다(`nameEn` 규칙). `en/board.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 게시판을 한 묶음으로 모을 때의 기본 묶음 이름(메뉴를 못 받았을 때·메뉴에 없는 게시판)
    bundleLabel: '게시판',

    // 작성자·글 줄 표시
    guestBadge: '비회원',
    guestIp: '작성 IP {ip}',
    pinned: '고정 공지',
    adultMark: '19금 글',
    hasImage: '이미지 있음',
    commentCountLabel: '댓글',
    newMark: '새 글',
    hiddenPost: '신고로 숨겨진 글이에요',
    viewsShort: '조회 {n}',
    likesShort: '추천 {n}',

    // 목록 표
    listCaption: '글 목록',
    colTitle: '제목',
    colAuthor: '작성자',
    colDate: '날짜',
    colViews: '조회',
    colLikes: '추천',

    // 쪽 번호
    pagerLabel: '쪽 이동',
    pagePrev: '이전 쪽',
    pageNext: '다음 쪽',
    pageN: '{n}쪽',

    // 투자 고지
    investmentTitle: '투자 유의',
    investmentBoard: '이 게시판의 글은 개인 의견이며 투자 권유가 아니에요. 투자 판단과 결과의 책임은 본인에게 있어요.',
    investmentPost: '이 글은 작성자의 개인 의견이며 투자 권유가 아니에요. 투자 판단과 결과의 책임은 본인에게 있어요.',

    // 게시판 상태
    notFoundTitle: '게시판을 찾을 수 없어요',
    notFoundText: '주소가 잘못됐거나 지금은 쓰지 않는 게시판이에요.',
    goHome: '홈으로',
    inactiveTitle: '지금은 운영하지 않는 게시판이에요',
    loadFailed: '게시판을 불러오지 못했어요',
    loadingName: '게시판 이름을 불러오는 중',
    adultNotice: '19금 글이 올라올 수 있는 게시판이에요. 19금 글은 성인 확인 뒤에 볼 수 있어요.',

    // 말머리·정렬·보기·검색
    prefixNav: '말머리',
    sortLabel: '정렬',
    sortLatest: '최신순',
    sortOldest: '오래된순',
    viewLabel: '보기 방식',
    viewList: '게시판형',
    viewGallery: '갤러리형',
    searchPlaceholder: '제목·내용·작성자',
    searchLabel: '이 게시판에서 검색',
    searchSummary: '「{q}」 검색 결과 {n}건',
    searchClear: '검색 지우기',

    // 목록 상태
    loadingList: '글 목록을 불러오는 중',
    listFailed: '글 목록을 불러오지 못했어요',
    searchEmptyTitle: '검색 결과가 없어요',
    searchEmptyText: '다른 낱말로 찾아보세요.',
    pageEmptyTitle: '이 쪽에는 글이 없어요',
    firstPage: '첫 쪽으로',
    prefixEmptyTitle: '[{name}] 말머리 글이 아직 없어요',
    prefixEmptyText: '다른 말머리를 고르거나 전체 글을 보세요.',
    allPosts: '전체 글 보기',
    emptyTitle: '아직 글이 없어요',
    emptyText: '이 게시판의 첫 글을 남겨 보세요.',
    emptyReadOnly: '지금은 이 게시판에 새 글을 쓸 수 없어요.',

    // 글쓰기가 막힌 까닭
    writeLogin: '로그인하면 이 게시판에 글을 쓸 수 있어요.',
    writeStaffOnly: '이 게시판에는 운영진만 글을 써요.',
    writeLocked: '지금은 이 게시판에 새 글을 쓸 수 없어요.',
    writeGrade: '{grade} 등급부터 글을 쓸 수 있어요.',
    writeGradeUnknown: '등급이 올라가면 글을 쓸 수 있어요.',
    writeEmail: '이메일 인증을 마치면 글과 댓글을 쓸 수 있어요.',
    writeRestricted: '이용이 제한된 계정이라 글과 댓글을 쓸 수 없어요.',
    writeBlocked: '지금은 글을 쓸 수 없어요.',

    // 댓글이 막힌 까닭
    commentOff: '댓글을 받지 않는 게시판이에요.',
    commentLogin: '로그인하면 댓글을 쓸 수 있어요.',
    commentStaffOnly: '이 게시판에는 운영진만 댓글을 써요.',
    commentLocked: '지금은 이 게시판에 새 댓글을 쓸 수 없어요.',
    commentGrade: '{grade} 등급부터 댓글을 쓸 수 있어요.',
    commentGradeUnknown: '등급이 올라가면 댓글을 쓸 수 있어요.',
    commentBlocked: '지금은 댓글을 쓸 수 없어요.',

    // 쓰기 잠금
    lockPost: '읽기 전용 게시판이에요. 새 글은 쓸 수 없고 댓글은 달 수 있어요.',
    lockPostComment: '읽기 전용 게시판이에요. 새 글과 새 댓글을 쓸 수 없어요.',
};
