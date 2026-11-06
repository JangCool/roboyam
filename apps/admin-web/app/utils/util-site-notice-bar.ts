import type { SiteNoticeBarRequest } from '~/composables/site/api/useSiteSettingsApi.types';

/** 공지 띠 입력칸 이름 — 서버 오류 `fields` 의 이름과 같다 */
export type SiteNoticeBarField = 'customText' | 'customLink';

/** 입력칸의 문제 — 비어 있음 · 너무 김 · 넣을 수 없는 글자 · 링크 모양이 아님 */
export type SiteNoticeBarProblem = 'REQUIRED' | 'LENGTH' | 'CHARS' | 'LINK';

/**
 * 공지 띠 설정(설정 > 일반 「공지 띠」)의 입력 규칙 — 서버가 거절할 것을 화면이 미리 알려 주는 용도이고 판정은 서버다(`SiteNoticeBarService`).
 * 직접 입력 문구: 출처가 `CUSTOM` 이면 필수, 1~100자(앞뒤 공백 뗀 코드포인트), `<` `>` 없음. 링크: 선택, `/…`(사이트 안) 또는 `http(s)://…`, 500자 이하.
 */
export default class UtilSiteNoticeBar {
    static readonly TEXT_MAX = 100;
    static readonly LINK_MAX = 500;
    static readonly COUNTS = [5, 10] as const;

    /**
     * 칸별 문제.
     * @param {SiteNoticeBarRequest} form - 입력 중인 값.
     * @returns {Partial<Record<SiteNoticeBarField, SiteNoticeBarProblem>>} 문제 있는 칸만.
     */
    static validate(form: SiteNoticeBarRequest): Partial<Record<SiteNoticeBarField, SiteNoticeBarProblem>> {
        const out: Partial<Record<SiteNoticeBarField, SiteNoticeBarProblem>> = {};
        const text = (form.customText ?? '').trim();
        if (form.source === 'CUSTOM' && !text) out.customText = 'REQUIRED';
        else if ([...text].length > UtilSiteNoticeBar.TEXT_MAX) out.customText = 'LENGTH';
        else if (/[<>]/.test(text)) out.customText = 'CHARS';

        const link = (form.customLink ?? '').trim();
        if (link.length > UtilSiteNoticeBar.LINK_MAX) out.customLink = 'LENGTH';
        else if (link && !UtilSiteNoticeBar.linkOk(link)) out.customLink = 'LINK';
        return out;
    }

    /**
     * 링크 모양 — 사이트 안 주소(`/` 로 시작, `//`·`/\` 아님 — 브라우저가 다른 사이트 주소로 읽는다) 또는 `http(s)://`, 어느 쪽이든 공백 없음(서버 `SiteNoticeBars` 와 같다).
     * @param {string} link - 앞뒤 공백을 뗀 링크.
     * @returns {boolean} 받을 수 있으면 true.
     */
    static linkOk(link: string): boolean {
        if (/\s/.test(link)) return false;
        if (link.startsWith('/')) return !/^\/[/\\]/.test(link);
        return /^https?:\/\/\S+$/i.test(link);
    }

    /**
     * 저장 본문 — 문구·링크는 앞뒤 공백을 떼고 비면 null.
     * @param {SiteNoticeBarRequest} form - 입력 중인 값.
     * @returns {SiteNoticeBarRequest} 보낼 값.
     */
    static request(form: SiteNoticeBarRequest): SiteNoticeBarRequest {
        return {
            enabled: form.enabled,
            source: form.source,
            boardCount: form.boardCount,
            customText: form.customText?.trim() || null,
            customLink: form.customLink?.trim() || null,
        };
    }

    /**
     * 저장된 값과 다른 칸 수.
     * @param {SiteNoticeBarRequest} saved - 저장된 값.
     * @param {SiteNoticeBarRequest} form - 입력 중인 값.
     * @returns {number} 바뀐 칸 수.
     */
    static changes(saved: SiteNoticeBarRequest, form: SiteNoticeBarRequest): number {
        const a = UtilSiteNoticeBar.request(saved);
        const b = UtilSiteNoticeBar.request(form);
        return (Object.keys(a) as (keyof SiteNoticeBarRequest)[]).filter((key) => a[key] !== b[key]).length;
    }
}
