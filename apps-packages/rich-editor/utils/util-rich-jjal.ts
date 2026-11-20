/**
 * 내 짤 토큰 `:jjal-{공개ID}:` 의 순수 규칙 — 본문(댓글 글자·글 HTML)에는 이 글자만 저장하고, 그리는 화면이 짤 정보로 그림으로 바꾼다
 * (계약 `docs/features/my-jjal-contract.md` 「글·댓글에 붙이기」 — 로보티콘 토큰 `:emo-ID:`(`util-rich-emoticon.ts`)과 같은 방식). 모르는 ID 는 글자 그대로 둔다.
 *
 * 쓰는 곳: 에디터의 짤 노드(`editor/jjalInline.ts`), 앱의 그리기(댓글 글자 조각 · 글 본문 HTML 의 글자 노드).
 * 레이어의 순수 코어 — `vue`·Nuxt 자동 임포트를 쓰지 않는다(DOM API 만). ⚠ HTML 은 `innerHTML` 로 넣지 않고 글자 노드만 나눠 요소를 만든다.
 * 「내려간 짤」 같은 화면 문구는 쓰는 앱이 번역해 넘긴다(레이어는 문구를 모른다).
 */

/** 토큰 하나 — 공개 ID 는 영문·숫자 1~32자(가정 — 서버가 정하면 맞춘다) */
const TOKEN_SOURCE = ':jjal-([A-Za-z0-9]{1,32}):';
/** 그리지 않는 자리 — 링크 글자·코드 안의 토큰은 글자 그대로 둔다 */
const SKIP_SELECTOR = 'a, code, pre, script, style, textarea';

/** 짤 그림 하나 — 쓰는 앱이 준다 */
export interface RichJjalImage {
    url: string;
    width: number;
    height: number;
    /** 신고로 내려간 짤 — 그림 대신 「내려간 짤」 자리 */
    blocked: boolean;
}

/** 짤 ID → 그림. 모르면 null(글자 그대로) */
export type RichJjalResolve = (id: string) => RichJjalImage | null;

/** 글자를 나눈 조각 — 글자 또는 짤(원래 글자 `raw` 를 함께 든다) */
export type RichJjalSegment = { kind: 'text'; text: string } | { kind: 'jjal'; id: string; raw: string };

function tokenPattern(): RegExp {
    return new RegExp(TOKEN_SOURCE, 'g');
}

export default class UtilRichJjal {
    /** `<img>` 에 붙이는 클래스 — 크기는 앱 CSS(`img.jjal`) */
    static readonly CLASS = 'jjal';
    /** 내려간 짤 자리(`<span>`)의 클래스 — 모양은 앱 CSS(`.jjal-blocked`) */
    static readonly BLOCKED_CLASS = 'jjal-blocked';
    /** 그린 요소의 짤 ID 속성 — 앱이 누르기(짤 메뉴)를 이 속성으로 찾는다 */
    static readonly ID_ATTR = 'data-jjal-id';
    /** 에디터 직렬화 때 토큰을 감싸는 표식 속성 — 저장 전에 `fromEditorHtml` 이 벗긴다 */
    static readonly MARKER_ATTR = 'data-jjal-token';

    /**
     * 토큰 글자를 만든다.
     * @param {string} id - 짤 공개 ID.
     * @returns {string} `:jjal-ID:`.
     */
    static token(id: string): string {
        return `:jjal-${id}:`;
    }

    /**
     * 글자에 토큰이 하나라도 있는가.
     * @param {string | null | undefined} text - 글자 또는 HTML.
     * @returns {boolean} 있으면 참.
     */
    static has(text: string | null | undefined): boolean {
        return Boolean(text) && new RegExp(TOKEN_SOURCE).test(text as string);
    }

    /**
     * 토큰 수 — 같은 짤을 여러 번 써도 센다(댓글 1개 · 글 5개 한도를 미리 알릴 때).
     * @param {string | null | undefined} text - 글자 또는 HTML.
     * @returns {number} 토큰 수.
     */
    static count(text: string | null | undefined): number {
        if (!text) return 0;
        return text.match(tokenPattern())?.length ?? 0;
    }

    /**
     * 본문 순서대로 토큰의 ID 들.
     * @param {string | null | undefined} text - 글자 또는 HTML.
     * @returns {string[]} ID 목록(겹치면 겹친 대로).
     */
    static ids(text: string | null | undefined): string[] {
        if (!text) return [];
        return Array.from(text.matchAll(tokenPattern()), (m) => m[1] as string);
    }

    /**
     * 글자를 글자·짤 조각으로 나눈다 — 댓글 평문처럼 HTML 이 아닌 글자를 Vue 템플릿으로 그릴 때(v-html 을 쓰지 않는다).
     * @param {string} text - 평문.
     * @returns {RichJjalSegment[]} 조각들(빈 글자 조각은 없다).
     */
    static split(text: string): RichJjalSegment[] {
        const out: RichJjalSegment[] = [];
        if (!text) return out;
        let last = 0;
        for (const m of text.matchAll(tokenPattern())) {
            const at = m.index ?? 0;
            if (at > last) out.push({ kind: 'text', text: text.slice(last, at) });
            out.push({ kind: 'jjal', id: m[1] as string, raw: m[0] });
            last = at + m[0].length;
        }
        if (last < text.length) out.push({ kind: 'text', text: text.slice(last) });
        return out;
    }

    /**
     * 본문 순서로 `index` 번째 토큰을 지운다(평문 입력칸 미리보기 칩의 ✕).
     * @param {string} text - 평문.
     * @param {number} index - 0부터 센 차례.
     * @returns {string} 지운 글자.
     */
    static removeAt(text: string, index: number): string {
        let seen = -1;
        return text.replace(tokenPattern(), (raw) => (++seen === index ? '' : raw));
    }

    /**
     * 한 줄 요약 — 토큰을 「(짤)」 같은 짧은 말로 바꾼다.
     * @param {string} text - 글자.
     * @param {string} word - 바꿔 넣을 말(화면 언어 — 앱이 번역해 준다).
     * @returns {string} 바꾼 글자.
     */
    static summarize(text: string, word: string): string {
        if (!text) return text;
        return text.replace(tokenPattern(), word);
    }

    /**
     * 짤 요소 하나를 만든다(DOM API 만) — 그림이면 `<img class="jjal">`, 내려간 짤이면 `<span class="jjal-blocked">내려간 짤</span>`.
     * @param {Document} doc - 만들 문서.
     * @param {string} id - 짤 ID.
     * @param {RichJjalImage} image - 짤 그림.
     * @param {string} blockedLabel - 내려간 짤 자리 글(앱이 번역).
     * @returns {HTMLElement} 요소.
     */
    static element(doc: Document, id: string, image: RichJjalImage, blockedLabel: string): HTMLElement {
        if (image.blocked) {
            const span = doc.createElement('span');
            span.className = UtilRichJjal.BLOCKED_CLASS;
            span.setAttribute(UtilRichJjal.ID_ATTR, id);
            span.textContent = blockedLabel;
            return span;
        }
        const img = doc.createElement('img');
        img.className = UtilRichJjal.CLASS;
        img.src = image.url;
        img.alt = '';
        img.loading = 'lazy';
        img.decoding = 'async';
        if (image.width > 0) img.width = image.width;
        if (image.height > 0) img.height = image.height;
        img.setAttribute(UtilRichJjal.ID_ATTR, id);
        return img;
    }

    /**
     * 요소 안 **글자 노드만** 훑어 아는 토큰을 짤로 바꾼다 — 글 본문·HTML 댓글(서버가 정화한 HTML 을 `v-html` 로 넣은 뒤). 링크·코드 안은 그대로 둔다.
     * 여러 번 불러도 안전하다(이미 바꾼 자리는 글자가 아니다).
     * @param {HTMLElement} root - 본문이 든 요소.
     * @param {RichJjalResolve} resolve - ID → 그림(모르면 null).
     * @param {string} blockedLabel - 내려간 짤 자리 글.
     * @returns {number} 바꾼 수.
     */
    static replaceIn(root: HTMLElement, resolve: RichJjalResolve, blockedLabel: string): number {
        return UtilRichJjal.walk(root, (id, raw) => {
            const image = resolve(id);
            return image ? UtilRichJjal.element(root.ownerDocument, id, image, blockedLabel) : raw;
        });
    }

    /**
     * 에디터에 넣기 전 HTML — 토큰을 에디터의 짤 노드 표식(`<img data-jjal-id>`)으로 바꾼다(링크·코드 안 제외). 모르는 ID 도 노드가 된다(저장하면 같은 토큰 글자로 돌아간다).
     * @param {string} html - 저장된 HTML(토큰 글자).
     * @returns {string} 에디터용 HTML.
     */
    static toEditorHtml(html: string): string {
        if (!UtilRichJjal.has(html) || typeof DOMParser === 'undefined') return html;
        const doc = new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html');
        UtilRichJjal.walk(doc.body, (id) => {
            const img = doc.createElement('img');
            img.setAttribute(UtilRichJjal.ID_ATTR, id);
            return img;
        });
        return doc.body.innerHTML;
    }

    /**
     * 에디터에서 나온 HTML — 짤 노드의 직렬화 표식(`<span data-jjal-token>토큰</span>`)을 벗겨 토큰 글자만 남긴다(이모티콘 `fromEditorHtml` 과 같은 까닭).
     * @param {string} html - 에디터 `getHTML()` 결과.
     * @returns {string} 저장할 HTML(토큰 글자).
     */
    static fromEditorHtml(html: string): string {
        return html.replace(new RegExp(`<span ${UtilRichJjal.MARKER_ATTR}="">(${TOKEN_SOURCE})</span>`, 'g'), '$1');
    }

    /** 글자 노드를 훑어 토큰 자리를 `make` 가 준 노드(또는 글자)로 바꾼다 */
    private static walk(root: HTMLElement, make: (id: string, raw: string) => Node | string): number {
        const doc = root.ownerDocument;
        const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        const targets: Text[] = [];
        for (let node = walker.nextNode(); node; node = walker.nextNode()) {
            const text = node as Text;
            if (!UtilRichJjal.has(text.data)) continue;
            if (text.parentElement?.closest(SKIP_SELECTOR)) continue;
            targets.push(text);
        }
        let replaced = 0;
        for (const text of targets) {
            const nodes: (Node | string)[] = [];
            for (const part of UtilRichJjal.split(text.data)) {
                if (part.kind === 'text') {
                    nodes.push(part.text);
                    continue;
                }
                const made = make(part.id, part.raw);
                if (typeof made !== 'string') replaced++;
                nodes.push(made);
            }
            text.replaceWith(...nodes);
        }
        return replaced;
    }
}
