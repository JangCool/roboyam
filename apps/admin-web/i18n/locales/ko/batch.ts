/**
 * 배치 관리·배치 로그 (hamonica3 D-89 에서 옮김). 두 화면이 결과 딱지를 함께 쓴다. 키는 `batch.무엇` 두 마디 — 화면 이름을 넣지 않는다.
 * `en/batch.ts` 와 같은 키, 같은 차례.
 * ⚠ `auditRetention` 같은 "자바 배치" 이름·설명은 서버가 문구 열쇠(`i18n`·`descI18n`)로 내려 주는 배치의 문구다 — 열쇠가 없는 배치(고객사가 만든 것)는 서버가 적은 글자가 그대로 나온다.
 */
export default {
    title: '배치 관리',
    subtitle: '정해진 시각에 도는 일들을 보고 관리합니다.',
    logsTitle: '배치 로그',
    logsSubtitle: '배치가 언제 돌았고 어떻게 끝났는지 봅니다.',

    // hamonica3 의 공통 문구 "다시 조회"·"조건 지우기" — 이 앱 공통 문구(`@roboyam/i18n`)에 아직 없어 여기 둔다
    reload: '다시 조회',
    clearFilter: '조건 지우기',

    batch: '배치',
    where: '어디서',
    whereApp: '애플리케이션',
    whereDb: 'DB',
    schedule: '주기',
    lastRun: '마지막 실행',
    neverRan: '아직 안 돌았습니다',
    enabled: '켜짐',
    toggleLabel: '{name} 켜기·끄기',
    runNow: '지금 실행',
    dbRunsTooltip: 'pg_cron 의 명령은 DB 가 직접 돌립니다',
    turnedOn: '켰습니다',
    turnedOff: '껐습니다',
    runNowNote: '「지금 실행」은 꺼져 있어도 돕니다.',

    emptyJobs: '도는 배치가 없습니다',
    emptyJobsHint: "코드에 {'@'}Scheduled 를 붙이거나 pg_cron 에 등록하면 여기에 나옵니다.",
    emptyLogs: '아직 남은 기록이 없습니다',
    emptyLogsHint: '배치가 한 번이라도 돌면 여기에 쌓입니다.',
    jobsLoadFailed: '배치 목록을 불러오지 못했어요',
    logsLoadFailed: '배치 로그를 불러오지 못했어요',

    startedAt: '시작',
    result: '결과',
    elapsed: '걸린 시간',
    affected: '건수',
    message: '메시지',

    statusOk: '성공',
    statusFail: '실패',
    statusSkip: '할 일 없음',
    dbBadge: '앱',
    dbBadgeDb: 'DB',
    runBySeconds: '{n}초',
    runByDb: 'DB 가 돌립니다',
    manualRunNote: '사람이 그 자리에서 부른 것이라 시각표와 상관이 없습니다. 실행 결과는',
    batchLogsLink: '배치 로그',
    remainsIn: '에 남습니다.',

    // ── 자바 배치 — 서버가 열쇠로 내려 준다 ──
    auditRetention: '감사 로그 정리',
    auditRetentionDesc: '보관 기간이 지난 감사 로그를 조각째 치운다.',
    accountExpiry: '계정 기간 만료',
    accountExpiryDesc: '사용 기간이 지난 계정에 만료 표시를 세운다. 막는 것은 로그인이 직접 한다.',
};
