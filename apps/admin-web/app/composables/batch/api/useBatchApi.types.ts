/**
 * 배치 API 가 주고받는 모양 — 후보 계약(`GET /api/admin/batch-jobs` 등)이고 확정 전이다. 칸 이름은 hamonica3 `useBatchApi` 의 것을 그대로 쓴다.
 * 시각은 UTC ISO(`Instant`)다 — 화면은 `useDisplayTime()` 으로 그린다.
 */

/** 어디서 도는 배치인가 — `JAVA`(애플리케이션 스케줄러) · `POSTGRESQL`(DB 의 pg_cron) */
export type BatchSource = 'JAVA' | 'POSTGRESQL';

/** 실행 결과 — `OK` 성공 · `FAIL` 실패 · `SKIP` 할 일 없음 */
export type BatchStatus = 'OK' | 'FAIL' | 'SKIP';

export interface BatchJobView {
    jobName: string;
    displayName: string;
    description: string | null;
    /** 이름의 문구 열쇠 — 자바 배치에만 있다. pg_cron 쪽은 비어 있다(DB 에 적힌 명령이라 우리 말이 아니다) */
    i18n: string | null;
    /** 설명의 문구 열쇠 */
    descI18n: string | null;
    source: BatchSource;
    /** 언제 도는가. cron 식 그대로 */
    schedule: string;
    enabled: boolean;
    /** 마지막 실행 시각 — UTC ISO. 한 번도 안 돌았으면 null */
    lastRunAt: string | null;
    /** `OK` · `FAIL` · `SKIP`. 한 번도 안 돌았으면 null */
    lastStatus: string | null;
    lastMessage: string | null;
    /** pg_cron 은 우리가 못 돌린다 — false */
    runnable: boolean;
}

export interface BatchLogView {
    jobName: string;
    displayName: string;
    source: BatchSource;
    /** 시작 시각 — UTC ISO */
    startedAt: string;
    /** 끝난 시각 — UTC ISO. 아직 안 끝났거나 모르면 null */
    finishedAt: string | null;
    status: string;
    /** 몇 건을 다뤘나. 재는 것이 없으면 null — 0 과 "안 셈"은 다르다 */
    affectedCount: number | null;
    message: string | null;
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
    /** 0 부터 */
    page?: number;
    size?: number;
}

/** 켜고 끄기 요청 */
export interface BatchToggleRequest {
    enabled: boolean;
}
