/**
 * 감사 로그 (hamonica3 D-89 에서 옮김). 키는 `audit.무엇` 두 마디 — 화면 이름을 넣지 않는다. `en/audit.ts` 와 같은 키, 같은 차례.
 * hamonica3 의 "워크스페이스" 칸 문구는 뺐다(이 제품에는 워크스페이스가 없다).
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
    actorPlaceholder: '아이디 · 이메일',
    action: '작업',
    targetType: '대상 종류',
    target: '대상',
    targetPlaceholder: '아이디 · 이름',
    result: '결과',
    resultOk: '성공만',
    resultFail: '실패만',

    occurredAt: '발생 시간',
    changes: '내용',
    changeDetail: '변경 내용',
    succeeded: '성공',
    failed: '실패',
    removed: '(없어짐)',
    none: '(없음)',

    empty: '아직 남은 기록이 없습니다',
    emptyHint: '관리 화면에서 무언가 바꾸면 여기에 쌓입니다.',
    loadFailed: '감사 로그를 불러오지 못했어요',
    noChangeReadOnly: '변경된 값이 없습니다 — 조회 기록입니다.',
    filterAfter: '{at} 이후',
    filterBefore: '{at} 이전',
    filterActor: '사용자 「{name}」',
    filterTarget: '대상 「{name}」',
};
