/** 띠를 닫아 둘 기간 — 하루 · 1주일 · 1달(지금 공지 묶음만 — 새 공지가 올라오면 다시 보인다). 「영원히」는 뺐다(사용자 결정 2026-11-12) */
export type SiteNoticeHidePeriod = 'DAY' | 'WEEK' | 'MONTH';

/** 이 브라우저에 남기는 닫음 기록 — `until` 이 null 인 것은 예전 「영원히」 기록(이제 숨기지 않는다 — `hidden`) */
export interface SiteNoticeDismissal {
    version: string;
    until: number | null;
}

/**
 * 공지 띠(`SiteNoticeBar`)의 순수 규칙 — 닫음 기록 읽기·만들기·판정, 링크 종류.
 * 닫음 기록은 이 브라우저(localStorage `irobit:site-notice`)에만 남는다(사용자 결정 2026-11-06 — 손님도 닫을 수 있고 다른 기기에서는 다시 보인다).
 * 기간은 누른 때부터 센다(하루 = 24시간 · 1주일 = 7일 · 1달 = 30일 — 가정).
 */
export default class UtilSiteNotice {
    static readonly STORAGE_KEY = 'irobit:site-notice';

    static readonly PERIODS: readonly SiteNoticeHidePeriod[] = ['DAY', 'WEEK', 'MONTH'];

    private static readonly DAY_MS = 86_400_000;

    /**
     * 닫음 기록 만들기.
     * @param {string} version - 지금 공지 묶음의 표식(서버 `version`).
     * @param {SiteNoticeHidePeriod} period - 고른 기간.
     * @param {number} now - 지금(ms).
     * @returns {SiteNoticeDismissal} 저장할 기록.
     */
    static dismissal(version: string, period: SiteNoticeHidePeriod, now: number): SiteNoticeDismissal {
        const days = period === 'DAY' ? 1 : period === 'WEEK' ? 7 : 30;
        return { version, until: now + days * UtilSiteNotice.DAY_MS };
    }

    /**
     * 저장소 값 읽기 — 모양이 틀리면 없는 것으로 본다.
     * @param {string | null} raw - localStorage 값.
     * @returns {SiteNoticeDismissal | null} 기록.
     */
    static parse(raw: string | null): SiteNoticeDismissal | null {
        if (!raw) return null;
        try {
            const value = JSON.parse(raw) as Partial<SiteNoticeDismissal>;
            if (typeof value.version !== 'string') return null;
            if (value.until !== null && typeof value.until !== 'number') return null;
            return { version: value.version, until: value.until };
        } catch {
            return null;
        }
    }

    /**
     * 지금 숨겨야 하는가 — 같은 공지 묶음을 닫았고 기간이 남았을 때만. 공지가 바뀌면(표식이 다르면) 다시 보인다. 예전 「영원히」 기록(`until` null)은 숨기지 않는다.
     * @param {SiteNoticeDismissal | null} record - 닫음 기록.
     * @param {string} version - 지금 공지 묶음의 표식.
     * @param {number} now - 지금(ms).
     * @returns {boolean} 숨기면 true.
     */
    static hidden(record: SiteNoticeDismissal | null, version: string, now: number): boolean {
        if (!record || record.version !== version) return false;
        return record.until !== null && now < record.until;
    }

    /**
     * 링크 종류 — 사이트 안 주소(`/…`)는 `NuxtLink`, `http(s)://` 는 새 창, 그 밖(빈 값·이상한 값)은 링크 없이 글자만.
     * @param {string | null} link - 서버가 준 링크.
     * @returns {'internal' | 'external' | null} 종류.
     */
    static linkKind(link: string | null): 'internal' | 'external' | null {
        if (!link) return null;
        if (link.startsWith('/') && !link.startsWith('//')) return 'internal';
        return /^https?:\/\//i.test(link) ? 'external' : null;
    }
}
