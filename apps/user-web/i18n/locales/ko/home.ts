/**
 * 공개 홈(`/`)과 홈 가젯 문구 — 템플릿(테마별 겉모양 — 배치는 운영자 격자)·가젯 껍데기·가젯 종류별 조각(`components/home/`·`components/themes/*Home.vue`).
 * 가젯 제목·「더보기」 글자·안내 글은 운영자가 정하는 값이라 서버가 준다(여기 없다). 키는 `home.무엇` 두 마디. `en/home.ts` 와 같은 키, 같은 차례.
 */
export default {
    // 홈 전체
    loading: '홈을 불러오는 중이에요',
    loadFailed: '홈을 불러오지 못했어요',
    investmentNote: '산업·주식 게시판의 글은 개인 의견이며 투자 권유가 아니에요. 투자 판단과 결과의 책임은 본인에게 있어요.',

    // 가젯 껍데기
    more: '더보기',
    foldOpen: '{title} 펼치기',
    foldClose: '{title} 접기',
    emptyDefault: '아직 내용이 없어요',
    emptyPosts: '아직 글이 없어요',
    emptyGallery: '아직 사진이 있는 글이 없어요',
    emptyHighlight: '아직 하이라이트가 없어요',
    emptyGroups: '아직 소개할 소모임이 없어요',
    emptyGroupBuy: '진행 중인 공동구매가 없어요',
    lockedText: '로그인하면 볼 수 있어요',
    lockedLogin: '로그인',

    // 글 줄
    commentCount: '댓글 {n}개',
    newMark: 'NEW',
    newLabel: '새 글',
    hotMark: 'HOT',
    hotLabel: '추천이 많은 글',

    // 갤러리
    galleryLabel: '사진이 있는 글',
    badgeGif: 'GIF',
    badgeVideo: '영상',

    // 게시판 카드
    boardGo: '게시판 가기',
    boardGoLabel: '{name} 게시판 가기',
    boardEmpty: '아직 글이 없어요',
    boardStockNote: '투자 권유가 아니에요',

    // 바로가기
    quickLinks: '바로가기',

    // 공동구매
    price: '{amount}원',
    listPriceLabel: '정가 {amount}',
    discount: '{n}%',
    discountLabel: '{n}% 할인',
    groupBuyDday: 'D-{n}',
    groupBuySoon: '곧 마감',
    groupBuySold: '{n}% 판매됨',
    groupBuyLeft: '남은 수량 {n}개',
    groupBuySalesClosed: '지금은 주문을 받지 않아요',
    groupBuyPrev: '이전 공동구매',
    groupBuyNext: '다음 공동구매',
    groupBuyPick: '{n}번째 공동구매 보기',
    groupBuyPosition: '{total}개 중 {n}번째',
    periodTabs: '인기글 기간',
    periodEmpty: '이 기간의 인기글이 아직 없어요',
    // 한 열(모바일) 가로로 미는 줄의 이름
    scrollRowLabel: '{name} — 옆으로 밀어서 더 보기',
    // 관리자 미리보기 편집(관리자 테마 화면의 실제 화면 — 홈 격자 손잡이)
    editSize: '{w}×{h}',
    editMobileOnly: '모바일만',
    editRemove: '{title} 빼기',
    editSettings: '{title} 설정',
    editResize: '크기 바꾸기',
};
