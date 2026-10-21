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
     * @param {string | null | undefined} cron - 6칸 cron 식.
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
