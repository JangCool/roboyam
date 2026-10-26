/**
 * 감사 로그 (hamonica3 D-89 에서 옮김). 키는 `audit.무엇` 두 마디 — 화면 이름을 넣지 않는다. `en/audit.ts` 와 같은 키, 같은 차례.
 * hamonica3 의 "워크스페이스" 칸 문구는 뺐다(이 제품에는 워크스페이스가 없다).
 * 작업·대상 종류·결과의 이름과 실패·거부 사유는 서버가 요청 언어로 주므로 여기에 없다(`succeeded`·`failed`·`denied` 는 결과 목록을 못 받았을 때의 대체 문구).
 */
export default {
    title: '감사 로그',
    subtitle: '누가 언제 무엇을 했는지 남은 기록입니다.',

    // hamonica3 의 공통 문구 "조회"·"조건 지우기" — 이 앱 공통 문구(`@roboyam/i18n`)에 아직 없어 여기 둔다
    search: '조회',
    clearFilter: '조건 지우기',

    from: '시작 시간',
    to: '종료 시간',
    fromPlaceholder: '이 시각 이후',
    toPlaceholder: '이 시각 이전',
    actor: '사용자',
    actorPlaceholder: '아이디 · 이름',
    action: '작업',
    targetType: '대상 종류',
    target: '대상',
    targetPlaceholder: '아이디 · 이름',
    result: '결과',

    occurredAt: '발생 시간',
    changes: '내용',
    changeDetail: '변경 내용',
    succeeded: '성공',
    failed: '실패',
    denied: '거부',
    removed: '(없어짐)',
    none: '(없음)',

    empty: '아직 남은 기록이 없습니다',
    emptyHint: '관리 화면에서 무언가 바꾸면 여기에 쌓입니다.',
    loadFailed: '감사 로그를 불러오지 못했어요',
    noChange: '기록된 변경 내용이 없어요.',
    filterInvalid: '조건을 확인해 주세요',
    filterInvalidHint: '칸 아래의 안내를 보고 고친 뒤 다시 조회해 주세요.',
    actorSystem: '시스템',
    actorAnonymous: '로그인하지 않은 사용자',
    filterAfter: '{at} 이후',
    filterBefore: '{at} 이전',
    filterActor: '사용자 「{name}」',
    filterTarget: '대상 「{name}」',

    // ── 보관 기간(대화상자 — commerce.md 13-4) ─
    retentionButton: '보관 기간',
    retentionTitle: '감사 기록 보관 기간',
    retentionLead: '감사 기록에는 행동마다 보관 분류가 붙어요. 매일 03:30 정리 배치(audit-log-retention)가 분류별 보관 기간이 지난 기록을 지워요.',
    retentionSuperOnly: '최고 관리자만 바꿀 수 있어요.',
    retentionName: {
        GENERAL: '일반 운영',
        PERSONAL_DATA: '개인정보 열람·내려받기',
        MONEY: '돈 관련',
    },
    retentionExample: {
        GENERAL: '예: 설정 변경, 게시판·메뉴 관리, 로그인',
        PERSONAL_DATA: '예: 회원 상세·이메일 전체 보기, 배송지 열람, 배송 명단 내려받기',
        MONEY: '예: 환불 실행, 포인트 수동 조정, 대사 불일치 처리, 판매 켜기/끄기',
    },
    retentionUnitDays: '일',
    retentionAboutYears: '약 {years}년',
    retentionAboutMonths: '약 {months}개월',
    retentionDays: '{days}일',
    retentionBounds: '하한 {min}일 · 기본 {def}일',
    retentionRequired: '일수를 입력해 주세요.',
    retentionNotInteger: '일수는 정수로 입력해 주세요.',
    retentionBelowMin: '하한({days}일)보다 짧게 할 수 없어요.',
    retentionAboveMax: '{days}일보다 길게 할 수 없어요.',
    retentionLastSaved: '마지막 저장: {at} · {by}',
    retentionNeverSaved: '저장한 적 없음 — 기본값이에요.',
    retentionLoadFailed: '보관 기간을 불러오지 못했어요.',
    retentionShrinkTitle: '보관 기간을 줄일까요?',
    retentionShrinkMessage: '줄이면 다음 정리부터 그보다 오래된 기록이 지워지고 되돌릴 수 없어요. 줄어드는 분류: {names}',
    retentionShrinkConfirm: '줄이고 저장',
    retentionSaved: '보관 기간을 저장했어요.',
    retentionFieldsRejected: '받아들여지지 않은 칸이 있어요. 칸 아래 문구를 확인해 주세요.',
};
