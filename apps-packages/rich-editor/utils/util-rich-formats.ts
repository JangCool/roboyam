/**
 * 고급 에디터(`RichEditor` level `ADVANCED`)의 글자색·배경색 **고정 팔레트**와 붙여넣기 style 정리 — community `layers/rich-editor/app/editor/richFormats.ts` 의 색 부분을 옮겼다.
 * 서버 정화가 같은 팔레트로 거른다(community `UtilPostHtml.TEXT_COLORS`·`BACKGROUND_COLORS` 와 같은 값) — 한쪽을 바꾸면 다른 쪽도 바꾼다.
 * 글꼴·글자 크기·줄 간격·정렬은 로보얌 에디터 수준에 없어 가져오지 않았다(그 확장도 켜지 않는다).
 * 레이어의 순수 코어 — `vue`·Nuxt 자동 임포트를 쓰지 않는다. 이름은 화면이 `richEditor.color.<key>` 로 번역한다.
 */

export interface RichColorOption {
    /** 문구 키의 끝 마디 — `richEditor.color.<key>` */
    key: string;
    /** 글에 저장되는 CSS 값(소문자 `#rrggbb`) */
    value: string;
}

/** 글자색 팔레트 — 자유 입력 없이 여기서만 고른다 */
const TEXT_COLORS: readonly RichColorOption[] = [
    { key: 'red', value: '#dc2626' },
    { key: 'orange', value: '#ea580c' },
    { key: 'yellow', value: '#ca8a04' },
    { key: 'green', value: '#16a34a' },
    { key: 'blue', value: '#2563eb' },
    { key: 'purple', value: '#9333ea' },
    { key: 'gray', value: '#64748b' },
];

/** 배경색 팔레트 */
const BACKGROUND_COLORS: readonly RichColorOption[] = [
    { key: 'yellow', value: '#fef08a' },
    { key: 'lime', value: '#bbf7d0' },
    { key: 'sky', value: '#bfdbfe' },
    { key: 'pink', value: '#fecaca' },
    { key: 'purple', value: '#e9d5ff' },
    { key: 'gray', value: '#e2e8f0' },
];

const ALLOWED_COLORS: Record<string, Set<string>> = {
    color: new Set(TEXT_COLORS.map((o) => o.value)),
    'background-color': new Set(BACKGROUND_COLORS.map((o) => o.value)),
};

export default class UtilRichFormats {
    static readonly TEXT_COLORS: readonly RichColorOption[] = TEXT_COLORS;
    static readonly BACKGROUND_COLORS: readonly RichColorOption[] = BACKGROUND_COLORS;

    /**
     * 색 값을 소문자 `#rrggbb` 로 맞춘다 — 브라우저는 `style` 의 색을 `rgb(r, g, b)` 로 되돌려 주므로 둘 다 받는다.
     * @param {string | null | undefined} value - 색 글자.
     * @returns {string | undefined} `#rrggbb` 또는(모르는 모양이면) undefined.
     */
    static colorToHex(value: string | null | undefined): string | undefined {
        const v = (value ?? '').trim().toLowerCase();
        if (/^#[0-9a-f]{6}$/.test(v)) return v;
        const m = /^rgb\(\s*(\d{1,3})\s*,\s*(\d{1,3})\s*,\s*(\d{1,3})\s*\)$/.exec(v);
        if (!m) return undefined;
        const parts = [m[1], m[2], m[3]].map(Number);
        if (parts.some((n) => n > 255)) return undefined;
        return '#' + parts.map((n) => n.toString(16).padStart(2, '0')).join('');
    }

    /**
     * 에디터가 내는 HTML 의 색을 `#rrggbb` 로 되돌린다 — TipTap 의 글자 스타일 마크는 브라우저를 거쳐 `color: rgb(220, 38, 38)` 꼴로 직렬화한다(실측).
     * 서버 팔레트 검사와 저장 값이 같은 모양(`#rrggbb`)이 되게 `style` 속성 안의 `rgb(...)` 만 바꾼다.
     * @param {string} html - 에디터 HTML.
     * @returns {string} 색을 `#rrggbb` 로 바꾼 HTML.
     */
    static normalizeColors(html: string): string {
        if (!html.includes('rgb(')) return html;
        return html.replace(/style="([^"]*)"/g, (whole, style: string) =>
            `style="${style.replace(/rgb\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*\)/g, (rgb) => UtilRichFormats.colorToHex(rgb) ?? rgb)}"`,
        );
    }

    /**
     * 붙여넣은 HTML 의 `style` 에서 팔레트 밖의 글자색·배경색과 다른 style 을 지운다 — 다른 곳(워드·웹 페이지)에서 복사한 글이
     * 팔레트 밖의 색을 달고 들어와 에디터에서는 보이다가 저장하면 사라지는 일이 없게 한다.
     * @param {string} html - 붙여넣는 HTML.
     * @returns {string} 허용 밖의 style 을 지운 HTML.
     */
    static cleanPastedStyles(html: string): string {
        if (!html.includes('style')) return html;
        const doc = new DOMParser().parseFromString(html, 'text/html');
        for (const el of Array.from(doc.body.querySelectorAll<HTMLElement>('[style]'))) {
            const kept: [string, string][] = [];
            for (const property of Object.keys(ALLOWED_COLORS)) {
                const hex = UtilRichFormats.colorToHex(el.style.getPropertyValue(property));
                if (hex && ALLOWED_COLORS[property]!.has(hex)) kept.push([property, hex]);
            }
            el.removeAttribute('style');
            for (const [property, value] of kept) el.style.setProperty(property, value);
            if (!el.getAttribute('style')?.trim()) el.removeAttribute('style');
        }
        return doc.body.innerHTML;
    }
}
