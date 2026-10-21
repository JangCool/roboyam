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
}
