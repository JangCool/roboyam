/**
 * 배치 관리·배치 로그 (hamonica3 D-89 에서 옮김). 두 화면이 결과 딱지를 함께 쓴다. 키는 `batch.무엇` 두 마디 — 화면 이름을 넣지 않는다.
 * `en/batch.ts` 와 같은 키, 같은 차례.
 * ⚠ 배치 이름·설명·마지막 실행 메시지는 서버가 요청 언어로 풀어 준 글이라 여기에 두지 않는다.
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
    schedule: '주기',
    lastRun: '마지막 실행',
    nextRun: '다음 실행',
    neverRan: '아직 안 돌았습니다',
    nextRunOff: '꺼져 있어 예정 없음',
    nextRunNone: '예정 없음',
    enabled: '켜짐',
    toggleLabel: '{name} 켜기·끄기',
    runNow: '지금 실행',
    running: '실행 중',
    viewLogs: '로그 보기',
    viewLogsLabel: '{name} 실행 로그 보기',
    noManageReason: '배치를 켜고 끌 권한이 없어요.',
    noRunReason: '배치를 지금 실행할 권한이 없어요.',
    turnedOn: '켰습니다',
    turnedOff: '껐습니다',
    runNowNote: '「지금 실행」은 꺼져 있어도 돕니다.',
    cronZoneNote: '주기는 서버 시간대(Asia/Seoul) 기준입니다.',

    runConfirmTitle: '배치를 지금 실행할까요?',
    runConfirmMessage: '「{name}」 — 끝날 때까지 기다리며, 꺼져 있어도 실행됩니다. 처리 결과는 되돌릴 수 없고 배치 로그에 남습니다.',
    runConfirmLabel: '지금 실행',

    emptyJobs: '도는 배치가 없습니다',
    emptyJobsHint: '서버에 등록된 배치가 있으면 여기에 나옵니다.',
    emptyLogs: '아직 남은 기록이 없습니다',
    emptyLogsHint: '배치가 한 번이라도 돌면 여기에 쌓입니다.',
    jobsLoadFailed: '배치 목록을 불러오지 못했어요',
    logsLoadFailed: '배치 로그를 불러오지 못했어요',

    startedAt: '시작',
    result: '결과',
    trigger: '실행 방식',
    elapsed: '걸린 시간',
    affected: '건수',
    message: '메시지',
    countCases: '{n}건',

    statusRunning: '실행 중',
    statusOk: '성공',
    statusFail: '실패',
    statusSkip: '할 일 없음',
    triggerScheduled: '예약',
    triggerManual: '수동',
    runBySeconds: '{n}초',
    manualRunNote: '사람이 그 자리에서 부른 것이라 시각표와 상관이 없습니다. 실행 결과는',
    batchLogsLink: '배치 로그',
    remainsIn: '에 남습니다.',

    // ── 주기(cron)를 사람 말로 ──
    cronDaily: '매일 {time}',
    cronWeekly: '매주 {day}요일 {time}',
    cronMonthly: '매월 {day}일 {time}',
    cronHourly: '매시 {minute}분',
    cronEveryMinutes: '{n}분마다',
    cronDow0: '일',
    cronDow1: '월',
    cronDow2: '화',
    cronDow3: '수',
    cronDow4: '목',
    cronDow5: '금',
    cronDow6: '토',
};
