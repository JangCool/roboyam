import type { PublicSitePopup, SitePopupDevice, SitePopupHideOption } from '~/composables/popup/api/useSitePopupApi.types';

/** 팝업 하나의 「보지 않기」 기록 — `until` 이 null 이면 다시 보지 않기(그 내용 표식이 바뀔 때까지) */
export interface SitePopupHideRecord {
    version: string;
    until: number | null;
}

/** 이 브라우저의 기록 — 팝업 ID → 기록 */
export type SitePopupHideRecords = Record<string, SitePopupHideRecord>;

/**
 * 팝업(`SitePopupLayer`)의 순수 규칙 — 어디·어느 기기에 띄울지, 「보지 않기」 기록 읽기·만들기·판정, 링크 종류.
 * 「보지 않기」 기록은 공지 띠처럼 **이 브라우저**(localStorage `roboyam:site-popups`)에만 남는다 — 손님도 닫을 수 있고 다른 기기에서는 다시 보인다(가정).
 *  · TODAY — 누른 날의 자정(이 기기 시각)까지 · WEEK — 누른 때부터 7일 · NEVER — 그 팝업의 내용 표식(`version`)이 바뀔 때까지
 *  · 어느 것이든 운영자가 팝업을 고쳐 표식이 바뀌면 다시 보인다.
 */
export default class UtilSitePopup {
    static readonly STORAGE_KEY = 'roboyam:site-popups';

    private static readonly DAY_MS = 86_400_000;

    /**
     * 이 화면·기기에 띄울 팝업 — 서버 차례를 지키고 보일 곳·기기·닫음을 가린다.
     * @param {readonly PublicSitePopup[]} items - 서버 목록(노출 켬·기간 안).
     * @param {{ home: boolean; device: SitePopupDevice; closed: readonly string[]; records: SitePopupHideRecords; now: number }} at - 지금 화면이 홈인지·기기·이번 방문에 닫은 ID·보지 않기 기록·지금.
     * @returns {PublicSitePopup[]} 띄울 팝업.
     */
    static visible(items: readonly PublicSitePopup[], at: { home: boolean; device: SitePopupDevice; closed: readonly string[]; records: SitePopupHideRecords; now: number }): PublicSitePopup[] {
        return items.filter((item) => (item.target === 'ALL' || at.home) && item.devices.includes(at.device) && !at.closed.includes(item.id) && !UtilSitePopup.hidden(at.records[item.id], item.version, at.now));
    }

    /**
     * 띄울 창별로 묶기 — 창 번호 차례, 창 안은 받은 차례 그대로.
     * @param {readonly PublicSitePopup[]} items - 띄울 팝업(서버 차례).
     * @returns {{ no: number; items: PublicSitePopup[] }[]} 창 목록.
     */
    static windows(items: readonly PublicSitePopup[]): { no: number; items: PublicSitePopup[] }[] {
        const map = new Map<number, PublicSitePopup[]>();
        for (const item of items) {
            const no = item.windowNo || 1;
            map.set(no, [...(map.get(no) ?? []), item]);
        }
        return [...map.entries()].sort(([a], [b]) => a - b).map(([no, rows]) => ({ no, items: rows }));
    }

    /**
     * 「보지 않기」 기록 만들기.
     * @param {string} version - 그 팝업의 내용 표식.
     * @param {Exclude<SitePopupHideOption, 'NONE'>} option - 고른 선택지.
     * @param {number} now - 지금(ms).
     * @returns {SitePopupHideRecord} 저장할 기록.
     */
    static record(version: string, option: Exclude<SitePopupHideOption, 'NONE'>, now: number): SitePopupHideRecord {
        if (option === 'NEVER') return { version, until: null };
        if (option === 'WEEK') return { version, until: now + 7 * UtilSitePopup.DAY_MS };
        const midnight = new Date(now);
        midnight.setHours(24, 0, 0, 0);
        return { version, until: midnight.getTime() };
    }

    /**
     * 저장소 값 읽기 — 모양이 틀린 기록은 버린다.
     * @param {string | null} raw - localStorage 값.
     * @returns {SitePopupHideRecords} 기록.
     */
    static parse(raw: string | null): SitePopupHideRecords {
        if (!raw) return {};
        try {
            const value = JSON.parse(raw) as Record<string, Partial<SitePopupHideRecord>>;
            if (!value || typeof value !== 'object' || Array.isArray(value)) return {};
            const out: SitePopupHideRecords = {};
            for (const [id, record] of Object.entries(value)) {
                if (typeof record?.version !== 'string') continue;
                if (record.until !== null && typeof record.until !== 'number') continue;
                out[id] = { version: record.version, until: record.until };
            }
            return out;
        } catch {
            return {};
        }
    }

    /**
     * 저장할 기록 — 지금 목록에 없는 팝업·기간이 끝난 기록은 지운다(저장소가 쌓이지 않게).
     * @param {SitePopupHideRecords} records - 지금 기록.
     * @param {readonly string[]} liveIds - 서버 목록의 팝업 ID.
     * @param {number} now - 지금(ms).
     * @returns {SitePopupHideRecords} 정리한 기록.
     */
    static prune(records: SitePopupHideRecords, liveIds: readonly string[], now: number): SitePopupHideRecords {
        return Object.fromEntries(Object.entries(records).filter(([id, record]) => liveIds.includes(id) && (record.until === null || now < record.until)));
    }

    /**
     * 지금 숨겨야 하는가 — 같은 내용을 닫았고 기간이 남았을 때만.
     * @param {SitePopupHideRecord | undefined} record - 그 팝업의 기록.
     * @param {string} version - 지금 내용 표식.
     * @param {number} now - 지금(ms).
     * @returns {boolean} 숨기면 true.
     */
    static hidden(record: SitePopupHideRecord | undefined, version: string, now: number): boolean {
        if (!record || record.version !== version) return false;
        return record.until === null || now < record.until;
    }

    /**
     * 링크 종류 — 사이트 안 주소(`/…`)는 같은 창, `http(s)://` 는 새 창, 그 밖은 링크 없음(공지 띠 `UtilSiteNotice.linkKind` 와 같은 규칙).
     * @param {string | null} link - 서버가 준 링크.
     * @returns {'internal' | 'external' | null} 종류.
     */
    static linkKind(link: string | null): 'internal' | 'external' | null {
        if (!link) return null;
        if (link.startsWith('/') && !link.startsWith('//')) return 'internal';
        return /^https?:\/\//i.test(link) ? 'external' : null;
    }
}
