import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DashboardLayoutBody } from '~/composables/dashboard/api/useDashboardLayoutApi.types';

/**
 * 대시보드 가젯의 계산이 낀 문구·숫자 글자 — 비교(어제·지난달 대비)·남은 시간·금액·비율. 상수 문구는 컴포넌트가 `t('dashboard.…')` 로 부른다.
 */
export default class UtilDashboardMessages {
    /**
     * 숫자 글자(천 단위 구분)
     * @param {number} n - 숫자.
     * @param {string} locale - 화면 언어.
     * @returns {string} 글자.
     */
    static num(n: number, locale: string): string {
        return n.toLocaleString(locale);
    }

    /**
     * 금액 글자 — `1,840,000원` · `₩1,840,000`
     * @param {Translate} t - 번역 함수.
     * @param {number} won - 금액(원).
     * @param {string} locale - 화면 언어.
     * @returns {string} 글자.
     */
    static won(t: Translate, won: number, locale: string): string {
        return t('dashboard.won', { n: won.toLocaleString(locale) });
    }

    /**
     * 비교 글자 — `어제보다 12 많아요` · `어제와 같아요`(`base` 는 비교 대상 — 어제 · 지난달 같은 날까지)
     * @param {Translate} t - 번역 함수.
     * @param {number} now - 지금 값.
     * @param {number} before - 비교 값.
     * @param {'yesterday' | 'lastMonth'} base - 비교 대상.
     * @param {string} locale - 화면 언어.
     * @param {(n: number) => string} [fmt] - 차이 숫자 글자(기본 천 단위 구분).
     * @returns {string} 글자.
     */
    static delta(t: Translate, now: number, before: number, base: 'yesterday' | 'lastMonth', locale: string, fmt?: (n: number) => string): string {
        const diff = now - before;
        const text = (fmt ?? ((n: number) => n.toLocaleString(locale)))(Math.abs(diff));
        if (diff === 0) return t(base === 'yesterday' ? 'dashboard.sameAsYesterday' : 'dashboard.sameAsLastMonth');
        if (base === 'yesterday') return t(diff > 0 ? 'dashboard.moreThanYesterday' : 'dashboard.lessThanYesterday', { n: text });
        return t(diff > 0 ? 'dashboard.moreThanLastMonth' : 'dashboard.lessThanLastMonth', { n: text });
    }

    /**
     * 마감까지 남은 시간 — `3시간 남음` · `2일 남음` · `곧 마감` · `마감됨`
     * @param {Translate} t - 번역 함수.
     * @param {string} iso - 마감 시각.
     * @param {number} [now] - 지금(ms).
     * @returns {string} 글자.
     */
    static remaining(t: Translate, iso: string, now = Date.now()): string {
        const ms = new Date(iso).getTime() - now;
        if (!Number.isFinite(ms) || ms <= 0) return t('dashboard.ended');
        const hours = Math.floor(ms / 3_600_000);
        if (hours < 1) return t('dashboard.endingSoon');
        if (hours < 48) return t('dashboard.hoursLeft', { n: hours });
        return t('dashboard.daysLeft', { n: Math.floor(hours / 24) });
    }

    /**
     * 남은 시간이 하루 안인가(마감 임박 표시)
     * @param {string} iso - 마감 시각.
     * @param {number} [now] - 지금(ms).
     * @returns {boolean} 24시간 안이면 true.
     */
    static urgent(iso: string, now = Date.now()): boolean {
        const ms = new Date(iso).getTime() - now;
        return ms > 0 && ms < 86_400_000;
    }

    /**
     * 클릭률 — 노출 0 이면 `—`
     * @param {{ impressions: number; clicks: number }} c - 노출·클릭.
     * @param {string} locale - 화면 언어.
     * @returns {string} 글자.
     */
    static ctr(c: { impressions: number; clicks: number }, locale: string): string {
        if (c.impressions <= 0) return '—';
        return `${((c.clicks / c.impressions) * 100).toLocaleString(locale, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`;
    }

    /**
     * 짧은 숫자 — `5.2만` · `52K`
     * @param {number} n - 숫자.
     * @param {string} locale - 화면 언어.
     * @returns {string} 글자.
     */
    static compact(n: number, locale: string): string {
        return new Intl.NumberFormat(locale, { notation: 'compact', maximumFractionDigits: 1 }).format(n);
    }

    /**
     * 구성 저장 실패(`400 VALIDATION`)의 칸 오류를 줄로 — `gadgets[i].<칸>` 은 보낸 본문의 i 번째 가젯 이름(설정 제목, 없으면 종류 이름)을 앞에 붙이고,
     * `version`·`columns`·`gadgets` 같은 나머지는 서버 문구 그대로. 같은 줄은 한 번만(서버 `DashboardLayoutService` 의 칸 경로).
     * @param {Translate} t - 번역 함수.
     * @param {Record<string, string>} fields - 서버 칸 오류(경로 → 문구).
     * @param {DashboardLayoutBody} body - 보낸 본문.
     * @returns {string[]} 줄.
     */
    static saveProblems(t: Translate, fields: Record<string, string>, body: DashboardLayoutBody): string[] {
        const out: string[] = [];
        for (const [path, reason] of Object.entries(fields)) {
            const m = /^gadgets\[(\d+)\]/.exec(path);
            const gadget = m ? body.gadgets?.[Number(m[1])] : undefined;
            const line = gadget ? t('dashboard.saveProblemOf', { title: gadget.config.title?.trim() || t(`dashboard.kind.${gadget.type}`), reason }) : reason;
            if (!out.includes(line)) out.push(line);
        }
        return out;
    }
}
