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
};
