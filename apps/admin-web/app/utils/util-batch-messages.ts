import type { Translate } from '#layers/i18n/utils/util-i18n';

/** 배치 화면이 함께 쓰는 계산이 낀 문구. 문구 본문은 `i18n/locales/{ko,en}/batch.ts`. */
export default class UtilBatchMessages {
    /**
     * 실행 결과 코드를 말로 푼다 — `SKIP` 을 「할 일 없음」으로 풀어 쓴다(코드 값만 보이면 실패로 읽힌다). 모르는 코드는 그대로, 없으면 「—」.
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} status - 실행 결과 코드(`OK` · `FAIL` · `SKIP`).
     * @returns {string} 딱지에 보일 글자.
     */
    static statusLabel(t: Translate, status: string | null | undefined): string {
        if (status === 'OK') return t('batch.statusOk');
        if (status === 'FAIL') return t('batch.statusFail');
        if (status === 'SKIP') return t('batch.statusSkip');
        return status || '—';
    }
}
