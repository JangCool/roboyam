import type { PostSource } from '~/composables/board/api/usePostApi.types';

/** 글쓰기·고치기 「출처」 칸의 한 줄(입력 중 값 — 둘 다 빈 줄은 보내지 않는다) */
export interface PostSourceRow {
    name: string;
    url: string;
}

/**
 * 글 출처 규칙(shared V232 — 서버 `PostSources`) — 화면은 미리 알리고 판정은 서버다(`400 VALIDATION fields.sources`). 상태도 요청도 없다.
 *  - 최대 5개 · 이름 0~40자(앞뒤 공백 정리, 비면 글 보기가 주소의 도메인) · 주소 필수 http(s) 500자 · 같은 주소는 하나
 */
export default class UtilPostSource {
    /** 글당 최대 출처 수 */
    static readonly MAX = 5;
    /** 출처 이름 최대 글자 수 */
    static readonly NAME_MAX = 40;
    /** 주소 최대 글자 수 */
    static readonly URL_MAX = 500;

    /**
     * 주소 모양이 맞는가(http·https · 호스트 있음 · 공백 없음 · 500자 이하)
     * @param {string} url - 앞뒤 공백을 뗀 주소.
     * @returns {boolean} 맞으면 true.
     */
    static urlOk(url: string): boolean {
        if (!url || url.length > UtilPostSource.URL_MAX || /\s/.test(url)) return false;
        try {
            const u = new URL(url);
            return (u.protocol === 'http:' || u.protocol === 'https:') && u.hostname.length > 0;
        } catch {
            return false;
        }
    }

    /**
     * 한 줄의 문제 — 빈 줄·맞는 줄은 null
     * @param {PostSourceRow} row - 입력 줄.
     * @returns {'URL' | 'NAME' | null} 주소 틀림 · 이름 너무 김 · 없음.
     */
    static rowProblem(row: PostSourceRow): 'URL' | 'NAME' | null {
        const name = row.name.trim();
        const url = row.url.trim();
        if (!name && !url) return null;
        if ([...name].length > UtilPostSource.NAME_MAX) return 'NAME';
        if (!UtilPostSource.urlOk(url)) return 'URL';
        return null;
    }

    /**
     * 입력 줄 가운데 문제가 있는 줄이 있는가
     * @param {readonly PostSourceRow[]} rows - 입력 줄.
     * @returns {boolean} 하나라도 틀리면 true.
     */
    static hasProblem(rows: readonly PostSourceRow[]): boolean {
        return rows.some((row) => UtilPostSource.rowProblem(row) !== null);
    }

    /**
     * 보낼 출처 — 앞뒤 공백을 떼고 빈 줄은 빼고, 같은 주소는 처음 것 하나만
     * @param {readonly PostSourceRow[]} rows - 입력 줄.
     * @returns {PostSource[]} 요청 값(raw 배열).
     */
    static toRequest(rows: readonly PostSourceRow[]): PostSource[] {
        const seen = new Set<string>();
        const out: PostSource[] = [];
        for (const row of rows) {
            const name = row.name.trim();
            const url = row.url.trim();
            if (!name && !url) continue;
            if (seen.has(url)) continue;
            seen.add(url);
            out.push({ name: name || null, url });
        }
        return out;
    }

    /**
     * 글 보기 응답 → 입력 줄(고치기 화면의 처음 값)
     * @param {readonly PostSource[] | undefined} sources - 글의 출처.
     * @returns {PostSourceRow[]} 입력 줄.
     */
    static toRows(sources: readonly PostSource[] | undefined): PostSourceRow[] {
        return (sources ?? []).map((s) => ({ name: s.name ?? '', url: s.url }));
    }

    /**
     * 보이는 이름 — 이름이 없으면 주소의 도메인(앞의 www. 뗌), 주소도 읽을 수 없으면 주소 그대로
     * @param {PostSource} source - 출처.
     * @returns {string} 이름.
     */
    static label(source: PostSource): string {
        if (source.name && source.name.trim()) return source.name.trim();
        try {
            return new URL(source.url).hostname.replace(/^www\./, '');
        } catch {
            return source.url;
        }
    }

    /**
     * 두 목록이 같은가(보낼 값 기준, 차례 포함)
     * @param {readonly PostSource[]} a - 목록.
     * @param {readonly PostSource[]} b - 목록.
     * @returns {boolean} 같으면 true.
     */
    static same(a: readonly PostSource[], b: readonly PostSource[]): boolean {
        return a.length === b.length && a.every((s, i) => s.url === b[i]?.url && (s.name ?? null) === (b[i]?.name ?? null));
    }
}
