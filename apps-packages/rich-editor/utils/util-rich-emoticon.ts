/**
 * 이모티콘 토큰 `:emo-{이모티콘ID}:` 의 순수 규칙 — 본문(댓글 글자·글 HTML)에는 이 글자만 저장하고, 그리는 화면이 카탈로그로 그림으로 바꾼다
 * (계약 `docs/features/emoticon-contract.md` 가정 7 · 본보기 community `docs/api-emoticon.md` 「핵심 설계」). 카탈로그에 없는 ID 는 글자 그대로 둔다.
 *
 * 쓰는 곳: 에디터의 이모티콘 노드(`editor/emoticonInline.ts` — 불러올 때 토큰 → 노드), 앱의 그리기(댓글 글자 조각 · 글 본문 HTML 의 글자 노드)·한 줄 요약.
 * 레이어의 순수 코어 — `vue`·Nuxt 자동 임포트를 쓰지 않는다(DOM API 만). ⚠ HTML 은 `innerHTML` 로 넣지 않고 글자 노드만 나눠 `img` 요소를 만든다.
 */

/** 토큰 하나 — ID 는 1~19자리 숫자(서버 `EmoticonGuard` 와 같은 꼴) */
const TOKEN_SOURCE = ':emo-(\\d{1,19}):';
/** 그리지 않는 자리 — 링크 글자·코드 안의 토큰은 글자 그대로 둔다 */
const SKIP_SELECTOR = 'a, code, pre, script, style, textarea';

/** 그림 하나 — 카탈로그가 준다 */
export interface RichEmoticonImage {
    url: string;
    /** 이름표(접근성 `alt`) */
    label: string;
}

/** 이모티콘 ID → 그림. 모르면 null(글자 그대로) */
export type RichEmoticonResolve = (id: string) => RichEmoticonImage | null;

/** 글자를 나눈 조각 — 글자 또는 이모티콘(원래 글자 `raw` 를 함께 든다) */
export type RichEmoticonSegment = { kind: 'text'; text: string } | { kind: 'emoticon'; id: string; raw: string };

function tokenPattern(): RegExp {
    return new RegExp(TOKEN_SOURCE, 'g');
}

export default class UtilRichEmoticon {
    /** 본문 하나에 넣을 수 있는 토큰 수(서버 검사 — 계약 가정 7). 화면은 미리 알린다 */
    static readonly MAX_PER_BODY = 20;
    /** `<img>` 에 붙이는 클래스 — 크기는 앱 CSS(`img.emoticon`, 100px · 모바일 80px) */
    static readonly CLASS = 'emoticon';

    /**
     * 토큰 글자를 만든다.
     * @param {string} id - 이모티콘 ID.
     * @returns {string} `:emo-ID:`.
     */
    static token(id: string): string {
        return `:emo-${id}:`;
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
     * 토큰 수 — 같은 토큰을 여러 번 써도, 카탈로그에 없는 ID 여도 센다(서버와 같다).
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
     * 글자를 글자·이모티콘 조각으로 나눈다 — 댓글 평문처럼 HTML 이 아닌 글자를 Vue 템플릿으로 그릴 때(v-html 을 쓰지 않는다).
     * @param {string} text - 평문.
     * @returns {RichEmoticonSegment[]} 조각들(빈 글자 조각은 없다).
     */
    static split(text: string): RichEmoticonSegment[] {
        const out: RichEmoticonSegment[] = [];
        if (!text) return out;
        let last = 0;
        for (const m of text.matchAll(tokenPattern())) {
            const at = m.index ?? 0;
            if (at > last) out.push({ kind: 'text', text: text.slice(last, at) });
            out.push({ kind: 'emoticon', id: m[1] as string, raw: m[0] });
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
     * 한 줄 요약 — 토큰을 「(이모티콘)」 같은 짧은 말로 바꾼다(목록 미리보기·알림).
     * @param {string} text - 글자.
     * @param {string} word - 바꿔 넣을 말(화면 언어 — 앱이 번역해 준다).
     * @returns {string} 바꾼 글자.
     */
    static summarize(text: string, word: string): string {
        if (!text) return text;
        return text.replace(tokenPattern(), word);
    }

    /**
     * 그림 요소 하나를 만든다(DOM API 만).
     * @param {Document} doc - 만들 문서.
     * @param {string} id - 이모티콘 ID.
     * @param {RichEmoticonImage} image - 카탈로그 그림.
     * @returns {HTMLImageElement} `<img class="emoticon">`.
     */
    static image(doc: Document, id: string, image: RichEmoticonImage): HTMLImageElement {
        const img = doc.createElement('img');
        img.className = UtilRichEmoticon.CLASS;
        img.src = image.url;
        img.alt = image.label;
        img.title = image.label;
        img.loading = 'lazy';
        img.decoding = 'async';
        img.width = 100;
        img.height = 100;
        img.dataset.emoticonId = id;
        return img;
    }

    /**
     * 요소 안 **글자 노드만** 훑어 아는 토큰을 그림으로 바꾼다 — 글 본문·HTML 댓글(서버가 정화한 HTML 을 `v-html` 로 넣은 뒤). 링크·코드 안은 그대로 둔다.
     * 여러 번 불러도 안전하다(이미 바꾼 자리는 글자가 아니다).
     * @param {HTMLElement} root - 본문이 든 요소.
     * @param {RichEmoticonResolve} resolve - ID → 그림(모르면 null).
     * @returns {number} 바꾼 수.
     */
    static replaceIn(root: HTMLElement, resolve: RichEmoticonResolve): number {
        return UtilRichEmoticon.walk(root, (id, raw) => {
            const image = resolve(id);
            return image ? UtilRichEmoticon.image(root.ownerDocument, id, image) : raw;
        });
    }

    /**
     * 에디터에 넣기 전 HTML — 토큰을 에디터의 이모티콘 노드 표식(`<img data-emoticon-id>`)으로 바꾼다(링크·코드 안 제외). 모르는 ID 도 노드가 된다(저장하면 같은 토큰 글자로 돌아간다).
     * @param {string} html - 저장된 HTML(토큰 글자).
     * @returns {string} 에디터용 HTML.
     */
    static toEditorHtml(html: string): string {
        if (!UtilRichEmoticon.has(html) || typeof DOMParser === 'undefined') return html;
        const doc = new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html');
        UtilRichEmoticon.walk(doc.body, (id) => {
            const img = doc.createElement('img');
            img.setAttribute('data-emoticon-id', id);
            return img;
        });
        return doc.body.innerHTML;
    }

    /** 글자 노드를 훑어 토큰 자리를 `make` 가 준 노드(또는 글자)로 바꾼다 */
    private static walk(root: HTMLElement, make: (id: string, raw: string) => Node | string): number {
        const doc = root.ownerDocument;
        const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        const targets: Text[] = [];
        for (let node = walker.nextNode(); node; node = walker.nextNode()) {
            const text = node as Text;
            if (!UtilRichEmoticon.has(text.data)) continue;
            if (text.parentElement?.closest(SKIP_SELECTOR)) continue;
            targets.push(text);
        }
        let replaced = 0;
        for (const text of targets) {
            const parts = UtilRichEmoticon.split(text.data);
            const nodes: (Node | string)[] = [];
            for (const part of parts) {
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
