/**
 * 배치 API 가 주고받는 모양 — 서버 DTO(`com.community.batch.dto.response.*` · `request.BatchRunRequestBody`·`BatchToggleRequest`·`BatchScheduleRequest`)와 같은 칸이다.
 * 서버 이름과 다른 것: `BatchParamDef` = 서버 `BatchParamDefView` · `BatchStepDef` = `BatchStepDefView` · `BatchRunRequest` = `BatchRunRequestBody`.
 * 시각은 UTC ISO(`Instant`)다 — 화면은 `useDisplayTime()` 으로 그린다. 규칙은 `docs/domain/commerce.md` 15-2·15-3, `docs/features/batch-contract.md` 「서버 구현 결과」.
 */

/** 어디서 도는 배치인가 — `JAVA` 관리자 서버의 스케줄러(cron 은 Spring 6칸) · `POSTGRESQL` DB 의 저장 프로시저를 pg_cron 이 부른다(cron 은 5칸) */
export type BatchSource = 'JAVA' | 'POSTGRESQL';

/** 배치가 속한 묶음 — `MEMBER` 회원 관련 · `SYSTEM` 시스템 관리 */
export type BatchCategory = 'MEMBER' | 'SYSTEM';

/** 실행 결과 — `RUNNING` 실행 중 · `OK` 성공 · `FAIL` 실패 · `SKIP` 할 일 없음(처리 건수 0) */
export type BatchStatus = 'RUNNING' | 'OK' | 'FAIL' | 'SKIP';

/** 실행을 시작한 방식 — `SCHEDULED` 예약(cron) · `MANUAL` 운영자가 「지금 실행」 */
export type BatchTrigger = 'SCHEDULED' | 'MANUAL';

/** 요청의 종류 — `NOW` 지금 실행(파라미터 지정 실행 포함) · `RESUME` 실패 지점부터 다시 · `RETRY_FAILED` 실패 건만 다시 */
export type BatchRunKind = 'NOW' | 'RESUME' | 'RETRY_FAILED';

/** 요청의 상태 — `PENDING` 대기(배치를 켠 서버가 아직 안 집음) · `RUNNING` 실행 중 · `DONE` 끝남(결과는 그 실행 로그) · `REJECTED` 거절(예: 집을 때 이미 실행 중) */
export type BatchRequestStatus = 'PENDING' | 'RUNNING' | 'DONE' | 'REJECTED';

/** 한 실행이 어떻게 시작됐나 — `NORMAL` 보통(예약·지금 실행) · `RESUME` 실패 지점부터 다시 · `RETRY_FAILED` 실패 건만 다시 */
export type BatchRunMode = 'NORMAL' | 'RESUME' | 'RETRY_FAILED';

/** 단계의 상태 — `PENDING` 안 돎 · `RUNNING` 실행 중 · `OK` 끝남 · `FAIL` 실패 · `SKIP` 건너뜀(고르지 않았거나, 이어 받은 실행에서 원래 실행이 이미 끝낸 단계) */
export type BatchStepStatus = 'PENDING' | 'RUNNING' | 'OK' | 'FAIL' | 'SKIP';

/** 실행 파라미터의 값 꼴 — `DATE` 달력 날짜 `YYYY-MM-DD` · `LONG` 정수 · `STRING` 글. 값은 모두 글자로 보낸다 */
export type BatchParamType = 'DATE' | 'LONG' | 'STRING';

/**
 * 「지금 실행」·「실패 지점부터 다시」·「실패 건만 다시」가 넣은 요청 한 줄(`batch_run_requests`) — 배치를 켠 서버(`roboyam.batch.enabled`)가 집어 실행한다.
 * 화면은 요청 뒤 이 줄을 다시 읽어 상태를 따라간다(`GET /batch-requests/{requestId}`).
 */
export interface BatchRunRequestView {
    requestId: string;
    jobName: string;
    kind: BatchRunKind;
    status: BatchRequestStatus;
    /** 실행이 시작되면 그 실행 로그 번호(`logId`). 아직 안 집었거나 거절이면 null */
    runId: string | null;
    /** 요청에 실은 실행 파라미터 — 없으면 빈 객체 */
    params: Record<string, string>;
    /** 고른 단계 키 — 모두 돌리면 null */
    steps: string[] | null;
    /** 요청한 시각 — UTC ISO */
    requestedAt: string;
    /** 배치 서버가 집은 시각 — UTC ISO. 아직이면 null */
    startedAt: string | null;
    /** 끝나거나 거절된 시각 — UTC ISO. 아직이면 null */
    finishedAt: string | null;
    /** 거절 사유 등 — 서버가 요청 언어로 푼 글. 없으면 null */
    message: string | null;
    /** 요청한 운영자 이름 */
    operatorName: string | null;
}

/** 배치가 받을 수 있는 실행 파라미터 하나(작업이 정의한다) */
export interface BatchParamDef {
    /** 키 — 요청의 `params` 이름이자 오류 `fields.params.<이름>` */
    name: string;
    /** 서버가 요청 언어로 푼 이름 */
    label: string;
    type: BatchParamType;
    required: boolean;
    /** 입력칸 아래 안내 — 서버 글. 없으면 null */
    help: string | null;
}

/** 배치의 단계 하나(작업이 정의한 차례대로) — 한 단계짜리 배치도 1개가 있다 */
export interface BatchStepDef {
    /** 키 — 요청의 `steps` 값 */
    name: string;
    /** 서버가 요청 언어로 푼 이름 */
    label: string;
    /** 이 단계만 골라 돌릴 수 있나. false 인 단계는 고르지 못하고 늘 돈다(확정) */
    selectable: boolean;
}

export interface BatchJobView {
    /** 배치 키 — 예: `grade-promotion` */
    jobName: string;
    /** 서버가 요청 언어로 풀어 준 이름 */
    displayName: string;
    /** 서버가 요청 언어로 풀어 준 설명 */
    description: string | null;
    /** 항상 null — 이름이 이미 번역돼 있다 */
    i18n: string | null;
    /** 항상 null — 설명이 이미 번역돼 있다 */
    descI18n: string | null;
    category: BatchCategory;
    source: BatchSource;
    /** 지금 쓰는 cron — `JAVA` 는 Spring 6칸(초 분 시 일 월 요일), `POSTGRESQL` 은 pg_cron 5칸(분 시 일 월 요일). 시간대는 `scheduleZone`. 쓸 수 없는 DB 배치는 기본 cron(모르면 빈 글) */
    schedule: string;
    /** 처음(코드) 값 — `schedule` 과 같은 문법 */
    defaultSchedule: string;
    enabled: boolean;
    /** 다음 실행 예정 — UTC ISO. 꺼져 있거나 쓸 수 없거나 더는 실행되지 않으면 null */
    nextRunAt: string | null;
    /** 마지막 실행 시작 시각 — UTC ISO. 한 번도 안 돌았으면 null */
    lastRunAt: string | null;
    /** `RUNNING` · `OK` · `FAIL` · `SKIP`. 한 번도 안 돌았으면 null */
    lastStatus: string | null;
    /** 마지막 실행의 처리 건수. 모르면 null */
    lastAffectedCount: number | null;
    /** 마지막 실행의 요약(실패면 사유) — 서버가 요청 언어로 풀어 준다 */
    lastMessage: string | null;
    /** 지금 실행 중인가 */
    running: boolean;
    /** 지금 실행할 수 있는가 — `JAVA` 는 항상 true, `POSTGRESQL` 은 `available` 과 같다 */
    runnable: boolean;
    /** 쓸 수 있는가 — `JAVA` 는 항상 true. `POSTGRESQL` 은 pg_cron 을 쓸 수 없거나 DB 에 등록돼 있지 않으면 false(이때 켜기/끄기·주기 변경·지금 실행은 409 `BATCH_UNAVAILABLE`) */
    available: boolean;
    /** `available=false` 인 이유 — 서버가 요청 언어로 풀어 준 글. 쓸 수 있으면 null */
    unavailableReason: string | null;
    /** `schedule` 을 해석하는 시간대(IANA 이름) — 예: `Asia/Seoul`. `JAVA` 는 서버 설정, `POSTGRESQL` 은 pg_cron 의 `cron.timezone`(설정이 없으면 `GMT`) */
    scheduleZone: string | null;
    /** 마지막 실행(지금 도는 중이면 그 실행)의 로그 번호 — 「상세 보기」가 간다. 한 번도 안 돌았으면 null */
    lastRunId: string | null;
    /** 아직 끝나지 않은 요청(`PENDING`·`RUNNING`) — 없으면 null. 있는 동안 지금 실행은 409 `BATCH_RUNNING` */
    pendingRequest: BatchRunRequestView | null;
    /** 받을 수 있는 실행 파라미터 — 없으면 빈 배열 */
    params: BatchParamDef[];
    /** 단계 목록(차례대로) — 한 단계짜리도 1개 */
    steps: BatchStepDef[];
    /** 체크포인트 재시작(「실패 지점부터 다시」)을 지원하는가 — `JAVA` 작업만 true 일 수 있다 */
    resumable: boolean;
}

export interface BatchLogView {
    logId: string;
    jobName: string;
    /** 배치 이름 — 카탈로그에서 사라진 배치는 키 그대로 */
    displayName: string;
    /** 그 줄을 만든 쪽 — `JAVA` 관리자 서버 · `POSTGRESQL` DB(저장 프로시저) */
    source: BatchSource;
    trigger: BatchTrigger;
    /** 수동 실행한 운영자 이름. 예약이거나 운영자가 지워졌으면 null(DB 가 돌린 줄은 수동이어도 비어 있을 수 있다) */
    operatorName: string | null;
    /** 시작 시각 — UTC ISO */
    startedAt: string;
    /** 끝난 시각 — UTC ISO. 아직 안 끝났으면 null */
    finishedAt: string | null;
    status: string;
    /** 몇 건을 다뤘나(처리 건수). 끝나지 않았거나 센 값이 없으면 null — 0 과 "안 셈"은 다르다. 실패한 DB 배치 줄은 실패하기 전까지 처리한 건수일 수 있다 */
    affectedCount: number | null;
    /** 성공·건너뜀이면 결과 요약, 실패면 사유 */
    message: string | null;
    /** 보통 실행인지 다시 실행인지 */
    runMode: BatchRunMode;
    /** 건별 실패 건수 — 건별 실패를 기록하지 않는 배치(DB 배치)이거나 아직 안 셌으면 null */
    failedCount: number | null;
}

/** 실행 상세의 단계 한 줄(`batch_run_steps`) */
export interface BatchRunStepView {
    name: string;
    /** 서버가 요청 언어로 푼 이름 */
    label: string;
    /** 1 부터 */
    order: number;
    status: BatchStepStatus;
    readCount: number | null;
    processedCount: number | null;
    failedCount: number | null;
    skippedCount: number | null;
    startedAt: string | null;
    finishedAt: string | null;
    /** 그 단계가 마지막으로 끝낸 덩어리의 정렬 키 — 없으면 null */
    checkpoint: string | null;
}

/** 실행 상세 — `GET /batch-logs/{logId}`. 로그 한 줄(`BatchLogView`)에 칸을 더한 모양 */
export interface BatchRunDetailView extends BatchLogView {
    /** 예약 실행의 예정 시각 — UTC ISO. 수동이면 null */
    scheduledAt: string | null;
    /** 결과 요약 — 서버 글 */
    summary: string | null;
    /** 실패 사유 — 서버가 비밀값·개인정보를 가린 글 */
    errorMessage: string | null;
    /** 돌린 서버 — DB 배치는 null */
    nodeId: string | null;
    /** 실행 파라미터 — 없으면 빈 객체 */
    params: Record<string, string>;
    /** 읽은 건수 — 주 단계의 건수(확정, 「실패 건만 다시」 실행은 돈 단계의 합). DB 배치는 null(처리 건수만 센다). ※ 실패 건수(`failedCount`)는 단계 합이다(확정) */
    readCount: number | null;
    /** 처리 건수 — 주 단계의 건수(확정) */
    processedCount: number | null;
    /** 건너뛴 건수 — 주 단계의 건수(확정). DB 배치는 null */
    skippedCount: number | null;
    /** 마지막으로 끝낸 덩어리의 정렬 키(글) — 없으면 null */
    checkpoint: string | null;
    /** 이 실행이 이어 받은(다시 돌린) 원래 실행 — 없으면 null */
    resumedFromRunId: string | null;
    /** 이 실행을 이어 받은 실행들 */
    resumedByRunIds: string[];
    /** 이 실행을 만든 요청 번호 — 예약 실행은 null */
    requestId: string | null;
    steps: BatchRunStepView[];
    /** 아직 해결하지 않은 실패 건수 */
    unresolvedFailureCount: number;
    /** 「실패 지점부터 다시」를 할 수 있나 — 실패로 끝났고 체크포인트가 있고 작업이 지원하며, 이어 받은(RESUME) 실행이 아직 없을 때(두 번 이어 받지 않는다 — 확정) */
    canResume: boolean;
    /** 「실패 건만 다시」를 할 수 있나 — 미해결 실패 건이 있고 작업이 지원 */
    canRetryFailures: boolean;
    /** 이 실행에 대한 다시 실행 요청이 대기·진행 중이면 그 요청 */
    pendingRequest: BatchRunRequestView | null;
}

/** 실행의 건별 실패 한 줄(`batch_run_failures`) */
export interface BatchRunFailureView {
    failureId: string;
    stepName: string;
    stepLabel: string;
    /** 대상 종류 코드 — 예: `POINT_LOT` */
    targetType: string;
    /** 대상 종류 이름 — 서버 글(예: 「포인트 묶음」) */
    targetLabel: string;
    targetId: string;
    errorCode: string;
    /** 서버가 개인정보·비밀값을 가린 글 */
    message: string;
    /** 시도 횟수 — 「실패 건만 다시」에서 또 실패하면 원래 줄은 미해결인 채 +1, 새 실행에는 새 줄(원래 + 1)이 생긴다(확정) */
    attempts: number;
    resolved: boolean;
    /** 해결한 실행 — 해결 안 됐으면 null */
    resolvedByRunId: string | null;
    occurredAt: string;
}

export interface BatchFailurePage {
    items: BatchRunFailureView[];
    total: number;
    /** 0 부터 */
    page: number;
    size: number;
}

export interface BatchFailureQuery {
    /** true 해결한 것만 · false 미해결만 · 비우면 전체 */
    resolved?: boolean;
    /** 0 부터 */
    page?: number;
    /** 1~100 */
    size?: number;
}

/** 지금 실행 요청 — 파라미터·단계를 안 주면 기본(파라미터 없음 · 모든 단계) */
export interface BatchRunRequest {
    /** 이름 → 값(글자). 빈 칸은 보내지 않는다 */
    params?: Record<string, string>;
    /** 돌릴 단계 키 — 안 주면 모두 */
    steps?: string[];
}

export interface BatchLogPage {
    items: BatchLogView[];
    total: number;
    /** 0 부터 */
    page: number;
    size: number;
}

export interface BatchLogQuery {
    jobName?: string;
    status?: string;
    trigger?: string;
    /** 시작 시각의 하한 — UTC ISO 순간 */
    from?: string;
    /** 시작 시각의 상한 — UTC ISO 순간 */
    to?: string;
    /** 0 부터 */
    page?: number;
    /** 1~100 */
    size?: number;
}

/** 켜고 끄기 요청 */
export interface BatchToggleRequest {
    enabled: boolean;
}

/** 주기(cron) 변경 요청 — 문법은 배치의 `source` 가 정한다(`JAVA` 6칸 · `POSTGRESQL` 5칸). 판정은 서버가 하고, 틀리면 400 `fields.schedule` */
export interface BatchScheduleRequest {
    schedule: string;
}
