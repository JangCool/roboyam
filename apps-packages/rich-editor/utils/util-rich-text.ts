/**
 * 에디터 HTML 을 글자로 보는 순수 규칙 — 보이는 글자 수(본문 길이 검사), 내용이 있는가, 평문 ↔ HTML 바꾸기, 링크·이미지 수(미리 알림용).
 * **글자 수는 서버와 같은 기준**이다: 서버는 태그를 뗀 글자로 길이를 잰다 → 여기서도 태그를 떼고(문자 참조는 글자 하나로) 연속 공백을 하나로 접어 앞뒤를 자른 길이를 쓴다.
 * 판정은 서버가 한다 — 여기 값은 미리 알려 주는 용도다. 레이어의 순수 코어(`DOMParser` 만 쓴다, `vue`·Nuxt 자동 임포트 없음).
 */

/** 줄을 나누는 블록 요소 — 평문으로 바꿀 때 뒤에 빈 줄을 둔다 */
const BLOCKS = 'p, h1, h2, h3, h4, li, blockquote, pre, div[data-youtube-id], hr';
/** 글자 속 `http(s)://`·`www.` 주소(링크 아닌 글자에 남은 것) */
const URL_IN_TEXT = /(https?:\/\/[^\s<>"']+|\bwww\.[^\s<>"']+)/gi;

function parse(html: string): Document {
    return new DOMParser().parseFromString(html, 'text/html');
}

function escapeHtml(text: string): string {
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

export default class UtilRichText {
    /**
     * 태그를 뗀 글자 — 연속 공백(줄바꿈 포함)은 하나로 접고 앞뒤를 자른다.
     * @param {string} html - 에디터 HTML.
     * @returns {string} 보이는 글자.
     */
    static plainText(html: string): string {
        if (!html) return '';
        return (parse(html).body.textContent ?? '').replace(/\s+/g, ' ').trim();
    }

    /**
     * 보이는 글자 수(코드 단위 — 서버 `String.length` 와 같은 셈).
     * @param {string} html - 에디터 HTML.
     * @returns {number} 글자 수.
     */
    static visibleLength(html: string): number {
        return UtilRichText.plainText(html).length;
    }

    /**
     * 내용이 있는가 — 보이는 글자 또는 이미지·유튜브가 하나라도 있으면 true(빈 에디터의 `<p></p>` 는 false).
     * @param {string} html - 에디터 HTML.
     * @returns {boolean} 내용이 있으면 true.
     */
    static hasContent(html: string): boolean {
        if (!html) return false;
        if (UtilRichText.plainText(html)) return true;
        return parse(html).body.querySelector('img[data-image-id], div[data-youtube-id]') !== null;
    }

    /**
     * 평문 → 에디터 HTML — 빈 줄은 문단, 줄바꿈은 `<br>`. 글자는 모두 이스케이프한다(태그로 읽지 않는다).
     * 평문으로 쓴 글을 에디터 게시판에서 고칠 때(또는 글쓰기에서 평문 게시판 → 에디터 게시판으로 바꿀 때) 쓴다.
     * @param {string} text - 평문.
     * @returns {string} HTML(내용이 없으면 빈 글자).
     */
    static htmlFromText(text: string): string {
        const blocks = text
            .replace(/\r\n?/g, '\n')
            .split(/\n{2,}/)
            .filter((block) => block.trim() !== '');
        return blocks.map((block) => `<p>${block.split('\n').map(escapeHtml).join('<br>')}</p>`).join('');
    }

    /**
     * 에디터 HTML → 평문 — 블록 사이는 빈 줄, `<br>` 은 줄바꿈. 이미지·유튜브·서식은 빠진다.
     * 에디터로 쓴 글을 평문 게시판에서 고칠 때 쓴다.
     * @param {string} html - 에디터 HTML.
     * @returns {string} 평문.
     */
    static textFromHtml(html: string): string {
        if (!html) return '';
        const body = parse(html).body;
        for (const br of Array.from(body.querySelectorAll('br'))) br.replaceWith('\n');
        for (const block of Array.from(body.querySelectorAll(BLOCKS))) block.after('\n\n');
        return (body.textContent ?? '')
            .replace(/[ \t]+\n/g, '\n')
            .replace(/\n{3,}/g, '\n\n')
            .trim();
    }

    /**
     * 링크 수 — `a[href]` 하나가 하나, 링크가 아닌 글자에 남은 주소도 센다(미리 알림용 — 판정은 서버).
     * @param {string} html - 에디터 HTML.
     * @returns {number} 링크 수.
     */
    static linkCount(html: string): number {
        if (!html) return 0;
        const body = parse(html).body;
        const anchors = body.querySelectorAll('a[href]');
        for (const a of Array.from(anchors)) a.remove();
        return anchors.length + ((body.textContent ?? '').match(URL_IN_TEXT)?.length ?? 0);
    }

    /**
     * 본문 이미지 수(`img[data-image-id]`).
     * @param {string} html - 에디터 HTML.
     * @returns {number} 이미지 수.
     */
    static imageCount(html: string): number {
        if (!html) return 0;
        return parse(html).body.querySelectorAll('img[data-image-id]').length;
    }
}
