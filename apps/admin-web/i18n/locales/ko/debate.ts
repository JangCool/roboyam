/**
 * 글 찬반 토론 — 글 번호로 쓰는 재사용 부품(토론 설정 대화창 `DebateSettingsDialog` · 의견 관리 서랍 `DebateOpinionsDrawer`) 문구.
 * 하이라이트 화면과 (다음 작업) 게시글 관리가 같이 쓴다. `en/debate.ts` 와 같은 키, 같은 차례. ⚠ 문구 안의 `@`·`|` 는 `{'@'}`·`{'|'}` 로 감싼다.
 */
export default {
    // ── 토론 설정 ──
    settingsTitle: '찬반 토론 설정',
    settingsLoadFailed: '토론 설정을 불러오지 못했어요',
    settingsSaveFailed: '토론 설정을 저장하지 못했어요.',
    settingsReadOnly: '토론을 바꿀 권한이 없어 볼 수만 있어요.',
    debateNone: '토론 없음',
    noneHint: '이 글에는 지금 찬반 토론이 없어요. 열면 글 화면에서 찬성·반대 투표와 의견을 받아요.',
    openedBy: {
        MEMBER: '글쓴이가 연 토론',
        OPERATOR: '운영자가 연 토론',
    },
    /** `{stances}` 자리에는 화면이 입장 색을 입힌 `stanceTab` 글자(투표 수)를 ` · ` 로 이어 넣는다 */
    summaryLine: '참여 {total}명({stances}) · 의견 {opinions} · 대기 고정 요청 {pending}',
    neutralLabel: '중립도 받기',
    neutralHelp: '끄면 공개 토론에 찬성·반대만 보이고 새 중립 선택을 막아요. 이미 중립을 고른 표와 의견은 남아요.',
    stanceChangeLabel: '입장 바꾸기 허용',
    stanceChangeHelp: '회원이 고른 입장을 나중에 바꿀 수 있어요. 바꾸면 쓴 의견도 함께 옮겨 가요.',
    stanceChangeOff: '한 번 고른 입장은 바꿀 수 없어요. 이미 고른 입장·의견은 그대로예요.',
    open: '토론 열기',
    opened: '찬반 토론을 열었어요.',
    saved: '토론 설정을 저장했어요.',
    close: '토론 닫기',
    closeTitle: '토론을 닫을까요?',
    closeMessage: '투표가 있어도 닫혀요. 이미 고른 입장·쓴 의견·고정은 지우지 않고, 다시 열면 그대로 돌아와요.',
    closed: '토론을 닫았어요.',
    counter: '{n}/{max}',
    tooLong: '{max}자까지 쓸 수 있어요.',

    // ── 의견 관리 ──
    opinionsTitle: '의견 관리',
    opinionsLead: '찬성·반대·중립 입장이 붙은 원댓글이에요. 고정한 것 먼저, 추천 많은 순.',
    debateOpen: '토론 열림',
    debateClosed: '토론 닫힘',
    opinionsReadOnly: '의견을 고정하거나 숨길 권한이 없어 볼 수만 있어요.',
    opinionsTabs: '의견 입장',
    stanceTab: {
        PRO: '찬성 {n}',
        CON: '반대 {n}',
        NEUTRAL: '중립 {n}',
    },
    stance: {
        PRO: '찬성',
        CON: '반대',
        NEUTRAL: '중립',
    },
    opinionsLoadFailed: '의견을 불러오지 못했어요',
    opinionsEmpty: '이 입장의 의견이 없어요',
    opinionStats: '추천 {likes} · 비추천 {dislikes} · 답글 {replies}',
    pinnedTag: '고정',
    pinRequestPendingTag: '고정 요청 대기',
    hiddenReason: {
        STAFF: '운영자 숨김',
        REPORTED: '신고로 숨김',
        ILLEGAL: '신고로 숨김(불법)',
        RIGHTS: '신고로 숨김(권리 침해)',
    },
    pin: '고정',
    unpin: '고정 풀기',
    hideOpinion: '숨기기',
    unhideOpinion: '숨김 풀기',
    reportHiddenNote: '신고로 숨긴 의견은 신고 처리에서 풀어요.',
    opinionsPage: '{page}쪽 · 전체 {total}개',
    opinionDone: {
        pinOn: '의견을 고정했어요.',
        pinOff: '고정을 풀었어요.',
        hideOn: '의견을 숨겼어요. 글쓴이에게는 알리지 않아요.',
        hideOff: '숨김을 풀었어요.',
    },
    opinionActionFailed: '처리하지 못했어요.',

    // ── 의견 고정 요청 ──
    pinRequestsTab: '고정 요청 {n}',
    pinRequestsLead: '의견을 쓴 회원이 보낸 고정 요청이에요(오래된 것부터). 고정하면 공개 토론 의견 맨 위에 올라가요. 결과는 요청한 회원에게 알려요.',
    pinRequestsLoadFailed: '고정 요청을 불러오지 못했어요',
    pinRequestsEmpty: '대기 중인 고정 요청이 없어요',
    pinRequestedAt: '요청 {at}',
    opinionGone: '지운 의견',
    pinRequestReject: '거절',
    pinRequestApproveOf: '{name} 님의 의견 고정',
    pinRequestRejectOf: '{name} 님의 고정 요청 거절',
    rejectReasonLabel: '거절 사유(선택)',
    rejectReasonHelp: '요청한 회원에게 알림으로 가요. 비워도 돼요.',
    rejectConfirm: '거절하기',
    pinRequestDone: {
        approve: '요청을 승인해 의견을 고정했어요.',
        reject: '고정 요청을 거절했어요.',
    },
};
