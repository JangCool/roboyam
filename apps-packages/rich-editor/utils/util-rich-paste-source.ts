/** 붙여 넣은 글 끝에서 찾은 출처 — 이름은 못 찾으면 빈 글자 */
export interface RichPasteSource {
    name: string;
    url: string;
}

/** 출처 줄로 보는 낱말 — 「출처」·저작권 표시(ⓒ·©·Copyright·(c))·「무단 전재」·「재배포 금지」 */
const SOURCE_WORD = /출처|ⓒ|©|copyright|\(c\)|무단\s*전재|재배포\s*금지/i;
/** 주소 — http(s) 로 시작하는 공백 없는 ASCII(바로 붙은 한글은 주소가 아니다) */
const URL_IN_TEXT = /https?:\/\/[!-~]+/gi;
/** 이름으로 보지 않는 앞·뒤 글자(괄호·구분 기호·문장 부호) */
const EDGE_MARKS = /^[\s[\]【】「」『』<>()（）:：|·,\-–—]+|[\s[\]【】「」『』<>()（）:：|·,.\-–—]+$/g;
/** 이름이 끝나는 자리 — 구분 기호(앞뒤 공백), 닫는 괄호·쉼표·&, 뒤따르는 저작권 낱말 */
const NAME_END = /\s[|\-–—·/]\s|[|,&\]】」』)）]|무단|all rights|copyright|ⓒ|©|\(c\)/i;
/** 이름 최대 글자 수 — 넘으면 이름으로 보지 않는다(출처 칸 규칙과 같다) */
const NAME_MAX = 40;

/**
 * 붙여 넣은 글의 끝에 기사 사이트가 덧붙인 「출처」 줄 읽기(뉴스 글쓰기 출처 칸 자동 등록).
 * 줄 나누기·지우기는 `editor/pasteSource.ts` 가 하고, 여기는 한 줄의 글자만 본다. 상태는 없다.
 */
export default class UtilRichPasteSource {
    /** 끝에서 몇 줄(빈 줄 빼고)까지 출처를 찾는가 */
    static readonly TAIL_LINES = 3;

    /**
     * 출처 줄 낱말이 있는가
     * @param {string} text - 한 줄 글자.
     * @returns {boolean} 있으면 true.
     */
    static hasSourceWord(text: string): boolean {
        return SOURCE_WORD.test(text);
    }

    /**
     * 줄 안의 마지막 http(s) 주소(끝의 문장 부호·닫는 괄호는 뗀다)
     * @param {string} text - 한 줄 글자.
     * @returns {string | null} 주소, 없거나 주소 모양이 아니면 null.
     */
    static urlIn(text: string): string | null {
        const found = text.match(URL_IN_TEXT);
        if (!found) return null;
        return UtilRichPasteSource.cleanUrl(found[found.length - 1] ?? '');
    }

    /**
     * 주소 다듬기 — 끝의 문장 부호·닫는 괄호를 떼고 http(s) 주소인지 본다
     * @param {string} raw - 주소 후보(글자 속 주소나 링크 href).
     * @returns {string | null} 주소, 맞지 않으면 null.
     */
    static cleanUrl(raw: string): string | null {
        const url = raw.trim().replace(/[.,;:!?'"`)\]}>]+$/, '');
        try {
            const u = new URL(url);
            return (u.protocol === 'http:' || u.protocol === 'https:') && u.hostname.length > 0 ? url : null;
        } catch {
            return null;
        }
    }

    /**
     * 줄에서 주소를 빼고 남는 것이 없는가(문장 부호·괄호만 남으면 주소만 있는 줄)
     * @param {string} text - 한 줄 글자.
     * @returns {boolean} 주소만 있으면 true.
     */
    static onlyUrl(text: string): boolean {
        return text.replace(URL_IN_TEXT, '').replace(EDGE_MARKS, '').trim() === '';
    }

    /**
     * 출처 줄에서 매체 이름 — 「출처: 전자신문」·「[출처: 중앙일보] 제목」·「ⓒ 2026 매일경제 & mk.co.kr」 모양
     * @param {string} text - 한 줄 글자.
     * @returns {string} 이름, 못 찾거나 40자를 넘으면 빈 글자.
     */
    static nameIn(text: string): string {
        const rest = text.replace(URL_IN_TEXT, ' ');
        const match = rest.match(/출처\s*[:：]?\s*(.+)/) ?? rest.match(/(?:copyright\s*)?(?:ⓒ|©|\(c\))\s*(.+)/i) ?? rest.match(/copyright\s+(.+)/i);
        if (!match?.[1]) return '';
        let name = match[1].replace(EDGE_MARKS, '');
        const end = name.search(NAME_END);
        if (end >= 0) name = name.slice(0, end);
        name = name
            .replace(/^\d{4}(?:\s*[-–~]\s*\d{4})?\s*/, '')
            .replace(EDGE_MARKS, '')
            .replace(/\s+/g, ' ')
            .trim();
        return name && [...name].length <= NAME_MAX ? name : '';
    }
}
