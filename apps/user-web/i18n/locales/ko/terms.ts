/**
 * 약관의 문구 — 공개 약관 화면(`/terms`·`/privacy`)·기본 레이아웃의 변경 안내 띠. 종류 이름은 공통 `common.terms`·`common.privacy`.
 * ⚠ 서비스 이름(용어)의 `siteTerms` 와 다른 기능이다. `en/terms.ts` 와 같은 키, 같은 차례.
 */
export default {
    // ── 본문 화면 ──
    loadError: '약관을 불러오지 못했어요',
    versionLine: '버전 {version} · 시행 {date}',
    changeNote: '변경 요지',
    emptyTitle: {
        TERMS: '아직 게시된 이용약관이 없어요',
        PRIVACY: '아직 게시된 개인정보처리방침이 없어요',
    },
    upcomingLabel: '시행 예정 안내',
    upcomingBox: {
        TERMS: '{date}부터 바뀐 이용약관이 시행돼요',
        PRIVACY: '{date}부터 바뀐 개인정보처리방침이 시행돼요',
    },
    viewUpcoming: '바뀐 약관 보기',
    viewingUpcoming: '{date}부터 시행되는 버전이에요.',
    viewingPast: '지난 버전이에요.',
    viewCurrent: '지금 약관 보기',
    versionMissing: '그 버전을 찾을 수 없어요',
    historyTitle: '이전 버전',
    status: {
        CURRENT: '지금',
        UPCOMING: '시행 예정',
        PAST: '지난',
    },

    // ── 변경 안내 띠 ──
    noticeLabel: '약관 변경 안내',
    noticeUpcoming: {
        TERMS: '이용약관이 바뀌어요 · 시행 {date}',
        PRIVACY: '개인정보처리방침이 바뀌어요 · 시행 {date}',
    },
    noticeRecent: {
        TERMS: '이용약관이 바뀌었어요 · 시행 {date}',
        PRIVACY: '개인정보처리방침이 바뀌었어요 · 시행 {date}',
    },
    noticeView: '보기',
    noticeClose: '{name} 변경 안내 닫기',
};
