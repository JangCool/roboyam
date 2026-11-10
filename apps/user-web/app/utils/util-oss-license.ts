import type { OssCollectedFile, OssManualCategory, OssManualEntry, OssUsedIn } from '~/composables/osslicense/api/useOssLicenseApi.types';

/** 거르기 칩 — 전체 · 공개 화면 · 관리자 화면 · 서버 · 글꼴·아이콘 */
export type OssFilter = 'all' | 'user' | 'admin' | 'server' | 'fontIcon';

/** 화면이 그리는 한 줄 — 자동 수집 항목과 직접 추가 항목을 같은 모양으로 */
export interface OssLicenseRow {
    key: string;
    name: string;
    version: string | null;
    license: string;
    usedIn: OssUsedIn[];
    fontIcon: boolean;
    /** 직접 추가 항목인가 */
    manual: boolean;
    copyright: string | null;
    notice: string | null;
    text: string | null;
    /** `package` 패키지 파일 · `template` 표준 문구로 대신 · `none` 전문 없음 · `manual` 운영자가 넣은 글 */
    textSource: 'package' | 'template' | 'none' | 'manual';
    repository: string | null;
    licenseUrl: string | null;
}

/** 직접 추가 항목의 묶음 → 쓰는 곳 */
const MANUAL_USED_IN: Record<OssManualCategory, OssUsedIn[]> = {
    USER: ['user'],
    ADMIN: ['admin'],
    SERVER: ['server'],
    FONT_ICON: [],
    OTHER: [],
};

/** 라이선스 종류 한 줄에 보일 최대 개수(나머지는 「그 밖 N」) */
const DISTRIBUTION_MAX = 8;

/** 접힌 전문의 줄 수 — 이보다 길면 「전문 펼치기」 */
export const OSS_TEXT_FOLD_LINES = 24;

export default class UtilOssLicense {
    static readonly FILTERS: readonly OssFilter[] = ['all', 'user', 'admin', 'server', 'fontIcon'];

    /**
     * 자동 수집 목록 + 직접 추가 항목 → 이름순 한 목록.
     * @param {OssCollectedFile | null} file - 정적 파일(못 받았으면 null).
     * @param {OssManualEntry[]} manual - 숨기지 않은 직접 추가 항목.
     * @returns {OssLicenseRow[]} 이름순(대소문자 무시, 같으면 버전순).
     */
    static rows(file: OssCollectedFile | null, manual: OssManualEntry[]): OssLicenseRow[] {
        const texts = file?.texts ?? {};
        const out: OssLicenseRow[] = (file?.items ?? []).map((item) => ({
            key: item.id,
            name: item.name,
            version: item.version || null,
            license: item.license,
            usedIn: item.usedIn,
            fontIcon: item.category === 'fontIcon',
            manual: false,
            copyright: item.copyright,
            notice: item.noticeId ? (texts[item.noticeId] ?? null) : null,
            text: item.textId ? (texts[item.textId] ?? null) : null,
            textSource: item.textId ? item.textSource : 'none',
            repository: item.repository,
            licenseUrl: item.licenseUrl,
        }));
        for (const entry of manual) {
            out.push({
                key: `manual:${entry.id}`,
                name: entry.name,
                version: entry.version,
                license: entry.license,
                usedIn: MANUAL_USED_IN[entry.category] ?? [],
                fontIcon: entry.category === 'FONT_ICON',
                manual: true,
                copyright: entry.copyright,
                notice: null,
                text: entry.licenseText,
                textSource: entry.licenseText ? 'manual' : 'none',
                repository: entry.sourceUrl,
                licenseUrl: null,
            });
        }
        return out.sort((a, b) => a.name.localeCompare(b.name, 'en', { sensitivity: 'base' }) || (a.version ?? '').localeCompare(b.version ?? '', 'en', { numeric: true }));
    }

    /** 칩에 맞는 줄인가 */
    static matches(row: OssLicenseRow, filter: OssFilter): boolean {
        if (filter === 'all') return true;
        if (filter === 'fontIcon') return row.fontIcon;
        return row.usedIn.includes(filter);
    }

    /** 칩별 개수 */
    static counts(rows: OssLicenseRow[]): Record<OssFilter, number> {
        const counts: Record<OssFilter, number> = { all: 0, user: 0, admin: 0, server: 0, fontIcon: 0 };
        for (const row of rows) {
            for (const filter of UtilOssLicense.FILTERS) if (UtilOssLicense.matches(row, filter)) counts[filter] += 1;
        }
        return counts;
    }

    /**
     * 칩 + 이름 찾기로 거른 줄.
     * @param {string} query - 찾을 글(앞뒤 공백 무시, 대소문자 무시 — 이름·라이선스에서 찾는다).
     */
    static filter(rows: OssLicenseRow[], filter: OssFilter, query: string): OssLicenseRow[] {
        const q = query.trim().toLowerCase();
        return rows.filter((row) => UtilOssLicense.matches(row, filter) && (!q || row.name.toLowerCase().includes(q) || row.license.toLowerCase().includes(q)));
    }

    /**
     * 라이선스 종류별 개수 — 많은 차례, 같으면 이름순. 앞 {@link DISTRIBUTION_MAX} 개와 나머지 합.
     * @returns {{ items: { license: string; count: number }[]; rest: number }} 보일 종류와 그 밖 개수.
     */
    static distribution(rows: OssLicenseRow[]): { items: { license: string; count: number }[]; rest: number } {
        const counts = new Map<string, number>();
        for (const row of rows) counts.set(row.license, (counts.get(row.license) ?? 0) + 1);
        const sorted = [...counts.entries()].map(([license, count]) => ({ license, count })).sort((a, b) => b.count - a.count || a.license.localeCompare(b.license));
        const items = sorted.slice(0, DISTRIBUTION_MAX);
        const rest = sorted.slice(DISTRIBUTION_MAX).reduce((sum, item) => sum + item.count, 0);
        return { items, rest };
    }

    /** 전문이 접어 둘 만큼 긴가 */
    static isLong(text: string | null): boolean {
        if (!text) return false;
        let lines = 1;
        for (let i = 0; i < text.length; i++) if (text.charCodeAt(i) === 10 && ++lines > OSS_TEXT_FOLD_LINES) return true;
        return text.length > OSS_TEXT_FOLD_LINES * 90;
    }

    /** 바깥 링크로 써도 되는 주소인가(http·https 만) */
    static safeUrl(url: string | null): string | null {
        return url && /^https?:\/\//i.test(url) ? url : null;
    }
}
