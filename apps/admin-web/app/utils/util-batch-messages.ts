import type { Translate } from '#layers/i18n/utils/util-i18n';
import UtilBatch from '~/utils/util-batch';

/** 배치 화면이 함께 쓰는 계산이 낀 문구. 문구 본문은 `i18n/locales/{ko,en}/batch.ts`. */
export default class UtilBatchMessages {
    /**
     * 실행 결과 코드를 말로 푼다 — `SKIP` 을 「할 일 없음」으로 풀어 쓴다(코드 값만 보이면 실패로 읽힌다). 모르는 코드는 그대로, 없으면 「—」.
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} status - 실행 결과 코드(`RUNNING` · `OK` · `FAIL` · `SKIP`).
     * @returns {string} 딱지에 보일 글자.
     */
    static statusLabel(t: Translate, status: string | null | undefined): string {
        if (status === 'RUNNING') return t('batch.statusRunning');
        if (status === 'OK') return t('batch.statusOk');
        if (status === 'FAIL') return t('batch.statusFail');
        if (status === 'SKIP') return t('batch.statusSkip');
        return status || '—';
    }

    /**
     * cron 을 사람 말로 — 「매일 04:30」 같은 한 줄. 흔한 꼴이 아니면 null(화면이 원문만 보인다).
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} cron - 6칸(JAVA) 또는 5칸(POSTGRESQL) cron 식.
     * @returns {string | null} 사람이 읽는 설명.
     */
    static scheduleText(t: Translate, cron: string | null | undefined): string | null {
        const shape = UtilBatch.parseCron(cron);
        if (!shape) return null;
        switch (shape.kind) {
            case 'daily':
                return t('batch.cronDaily', { time: `${shape.hh}:${shape.mm}` });
            case 'weekly':
                return t('batch.cronWeekly', { day: t(`batch.cronDow${shape.dow}`), time: `${shape.hh}:${shape.mm}` });
            case 'monthly':
                return t('batch.cronMonthly', { day: shape.day, time: `${shape.hh}:${shape.mm}` });
            case 'hourly':
                return t('batch.cronHourly', { minute: shape.mm });
            case 'everyMinutes':
                return t('batch.cronEveryMinutes', { n: shape.n });
        }
    }

    /**
     * 실행 주체를 말로 — `JAVA` 「앱 서버」 · `POSTGRESQL` 「DB(pg_cron)」. 모르는 값은 그대로, 없으면 「—」.
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} source - 배치(또는 로그 줄)의 `source`.
     * @returns {string} 딱지에 보일 글자.
     */
    static sourceLabel(t: Translate, source: string | null | undefined): string {
        if (source === 'JAVA') return t('batch.sourceJava');
        if (source === 'POSTGRESQL') return t('batch.sourceDb');
        return source || '—';
    }

    /**
     * 주기 입력칸 아래 형식 안내 — source 의 문법(JAVA 6칸 · POSTGRESQL 5칸)과 예시. 판정은 서버가 하므로 안내만 한다.
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} source - 배치의 `source`.
     * @returns {string} 안내 문구.
     */
    static scheduleHelp(t: Translate, source: string | null | undefined): string {
        const example = UtilBatch.scheduleExample(source);
        return source === 'POSTGRESQL' ? t('batch.scheduleHelpDb', { example }) : t('batch.scheduleHelpJava', { example });
    }

    /**
     * 주기를 해석하는 시간대 안내 — 목록 아래 한 줄. 배치마다 다를 수 있어(앱 서버 `Asia/Seoul`, DB 는 pg_cron 설정) 값은 응답의 `scheduleZone` 에서 모은다.
     * 모두 같으면 「주기는 X 시간대 기준입니다」, 다르면 「앱 서버 X · DB(pg_cron) Y」. 시간대를 아는 배치가 없으면 null.
     * @param {Translate} t - 번역 함수.
     * @param {{ source: string; scheduleZone: string | null }[]} jobs - 배치 목록.
     * @returns {string | null} 안내 문구.
     */
    static zoneNote(t: Translate, jobs: { source: string; scheduleZone: string | null }[]): string | null {
        const bySource = new Map<string, Set<string>>();
        for (const job of jobs) {
            if (!job.scheduleZone) continue;
            bySource.set(job.source, (bySource.get(job.source) ?? new Set()).add(job.scheduleZone));
        }
        const zones = new Set([...bySource.values()].flatMap((z) => [...z]));
        if (zones.size === 0) return null;
        if (zones.size === 1) return t('batch.cronZoneNote', { zone: [...zones][0] });
        const list = [...bySource].map(([source, z]) => `${UtilBatchMessages.sourceLabel(t, source)} ${[...z].join(', ')}`).join(' · ');
        return t('batch.cronZoneNoteEach', { list });
    }

    /**
     * 실행 방식(예약·수동)을 말로 — 모르는 값은 그대로, 없으면 「—」.
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} trigger - `SCHEDULED` · `MANUAL`.
     * @returns {string} 딱지에 보일 글자.
     */
    static triggerLabel(t: Translate, trigger: string | null | undefined): string {
        if (trigger === 'SCHEDULED') return t('batch.triggerScheduled');
        if (trigger === 'MANUAL') return t('batch.triggerManual');
        return trigger || '—';
    }

    /**
     * 다시 실행 종류 딱지의 글 — 보통 실행(`NORMAL`)은 딱지가 없다(null).
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} mode - `NORMAL` · `RESUME` · `RETRY_FAILED`.
     * @returns {string | null} 딱지 글자. 보통 실행이면 null.
     */
    static runModeLabel(t: Translate, mode: string | null | undefined): string | null {
        if (mode === 'RESUME') return t('batch.runModeResume');
        if (mode === 'RETRY_FAILED') return t('batch.runModeRetry');
        return null;
    }

    /**
     * 단계 상태를 말로 — 실행 결과(`statusLabel`)와 말이 다르다: 단계의 `SKIP` 은 「건너뜀」(고르지 않았거나 원래 실행이 이미 끝낸 단계), `PENDING` 은 「안 돎」.
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} status - 단계 상태.
     * @returns {string} 딱지에 보일 글자.
     */
    static stepStatusLabel(t: Translate, status: string | null | undefined): string {
        if (status === 'PENDING') return t('batch.stepPending');
        if (status === 'RUNNING') return t('batch.stepRunning');
        if (status === 'OK') return t('batch.stepOk');
        if (status === 'FAIL') return t('batch.stepFail');
        if (status === 'SKIP') return t('batch.stepSkip');
        return status || '—';
    }

    /**
     * 요청 종류를 말로.
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} kind - `NOW` · `RESUME` · `RETRY_FAILED`.
     * @returns {string} 글자.
     */
    static requestKindLabel(t: Translate, kind: string | null | undefined): string {
        if (kind === 'RESUME') return t('batch.kindResume');
        if (kind === 'RETRY_FAILED') return t('batch.kindRetry');
        if (kind === 'NOW') return t('batch.kindNow');
        return kind || '—';
    }

    /**
     * 요청 상태 띠의 한 줄 — 「요청 #12 · 실패 지점부터 다시 — 대기 중 …」.
     * @param {Translate} t - 번역 함수.
     * @param {{ requestId: string; kind: string; status: string }} request - 요청 한 줄.
     * @returns {string} 띠에 보일 글.
     */
    static requestLine(t: Translate, request: { requestId: string; kind: string; status: string }): string {
        const head = t('batch.requestHead', { id: request.requestId, kind: UtilBatchMessages.requestKindLabel(t, request.kind) });
        if (request.status === 'PENDING') return `${head} — ${t('batch.requestPending')}`;
        if (request.status === 'RUNNING') return `${head} — ${t('batch.requestRunning')}`;
        if (request.status === 'DONE') return `${head} — ${t('batch.requestDone')}`;
        if (request.status === 'REJECTED') return `${head} — ${t('batch.requestRejected')}`;
        return head;
    }

    /**
     * 걸린 시간. 초 단위로 반올림하지 않는다 — 배치는 대개 1초 안에 끝나서, 반올림하면 전부 「0초」가 되어 「어느 것이 느린가」를 볼 수 없다. 1분이 넘으면 분·초.
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} startedAt - 시작 UTC ISO.
     * @param {string | null | undefined} finishedAt - 끝 UTC ISO. 없으면 「—」.
     * @returns {string} 「850ms」 · 「3.2초」 · 「2분 05초」.
     */
    static elapsed(t: Translate, startedAt: string | null | undefined, finishedAt: string | null | undefined): string {
        if (!startedAt || !finishedAt) return '—';
        const ms = new Date(finishedAt).getTime() - new Date(startedAt).getTime();
        if (ms < 1000) return `${Math.max(0, ms)}ms`;
        if (ms < 60_000) return t('batch.runBySeconds', { n: (ms / 1000).toFixed(1) });
        const s = Math.round(ms / 1000);
        return t('batch.runByMinutes', { m: Math.floor(s / 60), s: String(s % 60).padStart(2, '0') });
    }

    /**
     * 건수 칸 — 처리 건수에 실패가 있으면 「· 실패 n」을 붙인다. 처리 건수가 없으면 「—」.
     * @param {Translate} t - 번역 함수.
     * @param {number | null} processed - 처리 건수.
     * @param {number | null} failed - 건별 실패 건수(DB 배치는 null).
     * @returns {string} 「1,240」 · 「3,816 · 실패 4」.
     */
    static countText(t: Translate, processed: number | null, failed: number | null): string {
        const head = processed == null ? '—' : processed.toLocaleString();
        return failed ? `${head} · ${t('batch.countFailedN', { n: failed.toLocaleString() })}` : head;
    }

    /**
     * 실행이 끝났을 때의 토스트 한 줄 — 「포인트 소멸 — 성공: 포인트 묶음 980개 소멸」. 결과 요약은 서버 글 그대로.
     * @param {Translate} t - 번역 함수.
     * @param {{ displayName: string; lastStatus: string | null; lastMessage: string | null }} job - 끝난 뒤의 배치 한 줄.
     * @returns {string} 토스트 글.
     */
    static runResult(t: Translate, job: { displayName: string; lastStatus: string | null; lastMessage: string | null }): string {
        const summary = `${job.displayName} — ${UtilBatchMessages.statusLabel(t, job.lastStatus)}`;
        return job.lastMessage ? `${summary}: ${job.lastMessage}` : summary;
    }

    /**
     * 「실패 지점부터 다시」가 잠긴 이유 — 권한 → 진행 중인 요청 → 실행 상태 순. 잠기지 않았으면 undefined.
     * @param {Translate} t - 번역 함수.
     * @param {{ canResume: boolean; status: string; checkpoint: string | null; resumedByRunIds: string[]; pendingRequest: unknown | null }} run - 실행 상세.
     * @param {boolean} allowed - `batch.run` 권한이 있나.
     * @returns {string | undefined} 툴팁 글.
     */
    static resumeLockReason(t: Translate, run: { canResume: boolean; status: string; checkpoint: string | null; resumedByRunIds: string[]; pendingRequest: unknown | null }, allowed: boolean): string | undefined {
        if (!allowed) return t('batch.noRerunReason');
        if (run.pendingRequest) return t('batch.rerunBusyReason');
        if (run.canResume) return undefined;
        if (run.status !== 'FAIL') return t('batch.resumeNotFailed');
        if (!run.checkpoint) return t('batch.resumeNoCheckpoint');
        if (run.resumedByRunIds.length) return t('batch.resumeAlready');
        return t('batch.resumeUnavailable');
    }

    /**
     * 「실패 건만 다시」가 잠긴 이유 — 권한 → 진행 중인 요청 → 미해결 건. 잠기지 않았으면 undefined.
     * @param {Translate} t - 번역 함수.
     * @param {{ canRetryFailures: boolean; unresolvedFailureCount: number; pendingRequest: unknown | null }} run - 실행 상세.
     * @param {boolean} allowed - `batch.run` 권한이 있나.
     * @returns {string | undefined} 툴팁 글.
     */
    static retryLockReason(t: Translate, run: { canRetryFailures: boolean; unresolvedFailureCount: number; pendingRequest: unknown | null }, allowed: boolean): string | undefined {
        if (!allowed) return t('batch.noRerunReason');
        if (run.pendingRequest) return t('batch.rerunBusyReason');
        if (run.canRetryFailures) return undefined;
        if (!run.unresolvedFailureCount) return t('batch.retryNoFailures');
        return t('batch.retryUnavailable');
    }
}
