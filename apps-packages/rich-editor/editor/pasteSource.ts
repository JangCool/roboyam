import { Slice } from '@tiptap/pm/model';
import type { Fragment, Node as PmNode } from '@tiptap/pm/model';
import UtilRichPasteSource, { type RichPasteSource } from '#layers/rich-editor/utils/util-rich-paste-source';

/** 붙여 넣은 내용의 한 줄 — 글자 블록 하나, 또는 그 안의 줄바꿈(hardBreak) 사이 */
interface PasteLine {
    text: string;
    /** 줄 안 링크의 주소(글자에 주소가 없을 때 쓴다) */
    hrefs: string[];
    /** 줄 첫 글자 앞 · 마지막 글자 뒤 위치(붙여 넣은 내용 기준) */
    start: number;
    end: number;
    /** 이 줄을 담은 글자 블록 안쪽의 깊이 — 여기서 자르면 끝이 열린 깊이 */
    depth: number;
}

/**
 * 붙여 넣은 내용(`Slice`)의 끝 줄에서 기사 사이트가 덧붙인 출처를 찾는다(뉴스 글쓰기 출처 칸 자동 등록 — 사용자 2026-11-26).
 *  - 빈 줄을 뺀 마지막 3줄에서, 아래부터 주소가 있는 첫 줄이 출처 주소. 글자에 주소가 없으면 출처 낱말이 있는 줄의 링크 주소.
 *  - 이름은 그 줄 → 바로 위 줄 → 아래 줄의 「출처: 이름」·「ⓒ 이름」. 없으면 빈 글자.
 *  - 다른 내용 없이 출처 줄만 있으면(링크 하나 붙여 넣기 등) 찾지 않는다.
 *  - 주소 줄(+ 바로 위의 출처 낱말 줄)부터 끝까지가 모두 출처 줄(출처 낱말이 있거나 주소만 있음)이면 본문에서 뺀다.
 *    문장 속 주소처럼 출처 줄이 아니면 본문은 그대로 두고 출처만 알린다. 그 뒤에 그림·영상 같은 글자 아닌 것이 있어도 그대로 둔다.
 *  - 글자에서 주소를 못 찾으면 `fallbackUrl`(클립보드 HTML 의 `<!--SourceURL:-->`)을 이름 없이 쓴다.
 * @param {Slice} slice - 붙여 넣을 내용.
 * @param {string | null} fallbackUrl - 클립보드가 알려 준 원문 주소.
 * @returns {{ slice: Slice; source: RichPasteSource } | null} 출처 줄을 뺀 내용과 출처, 못 찾으면 null.
 */
export function takePasteSource(slice: Slice, fallbackUrl: string | null): { slice: Slice; source: RichPasteSource } | null {
    const lines = linesOf(slice.content);
    const filled = lines.filter((line) => line.text.trim() !== '' || line.hrefs.length > 0);
    const tail = filled.slice(-UtilRichPasteSource.TAIL_LINES);

    let at = -1;
    let url: string | null = null;
    for (let i = tail.length - 1; i >= 0 && url === null; i--) {
        const line = tail[i] as PasteLine;
        url = UtilRichPasteSource.urlIn(line.text);
        if (url === null && UtilRichPasteSource.hasSourceWord(line.text)) url = firstUrl(line.hrefs);
        if (url !== null) at = i;
    }

    if (url === null) {
        const fallback = fallbackUrl ? UtilRichPasteSource.cleanUrl(fallbackUrl) : null;
        return fallback && filled.length > 0 ? { slice, source: { name: '', url: fallback } } : null;
    }

    const urlLine = tail[at] as PasteLine;
    const above = at > 0 && UtilRichPasteSource.hasSourceWord((tail[at - 1] as PasteLine).text) ? (tail[at - 1] as PasteLine) : null;
    const below = tail.slice(at + 1);
    const name = [urlLine, ...(above ? [above] : []), ...below].map((line) => UtilRichPasteSource.nameIn(line.text)).find((n) => n !== '') ?? '';

    // 출처 줄 말고 다른 내용이 있어야 한다(링크 하나만 붙여 넣은 것은 본문이다)
    const belowSource = below.filter((line) => UtilRichPasteSource.hasSourceWord(line.text)).length;
    const sourceLines = 1 + (above ? 1 : 0) + belowSource;
    const hasOther = filled.length > sourceLines || hasNonText(slice.content);
    if (!hasOther) return null;

    const source = { name, url };
    const first = above ?? urlLine;
    // 주소 줄은 출처 낱말이 있거나 주소만 있고, 그 아래 줄은 모두 출처 낱말 줄이어야 뺀다(바로 위 줄은 출처 낱말 줄일 때만 `above`)
    const removable = (UtilRichPasteSource.hasSourceWord(urlLine.text) || UtilRichPasteSource.onlyUrl(urlLine.text)) && belowSource === below.length;
    const keptIndex = filled.indexOf(first) - 1;
    const kept = keptIndex >= 0 ? (filled[keptIndex] as PasteLine) : null;
    if (!removable || !kept || hasNonTextBetween(slice.content, kept.end, slice.content.size)) return { slice, source };

    return { slice: new Slice(slice.content.cut(0, kept.end), slice.openStart, kept.depth), source };
}

/** 링크 주소 가운데 첫 http(s) */
function firstUrl(hrefs: string[]): string | null {
    for (const href of hrefs) {
        const url = UtilRichPasteSource.cleanUrl(href);
        if (url) return url;
    }
    return null;
}

/** 붙여 넣은 내용을 줄로 — 글자 블록마다, 그 안의 줄바꿈마다 나눈다(차례대로) */
function linesOf(content: Fragment): PasteLine[] {
    const lines: PasteLine[] = [];
    const walk = (fragment: Fragment, base: number, depth: number): void => {
        fragment.forEach((node, offset) => {
            const pos = base + offset;
            if (node.isTextblock) linesInBlock(node, pos + 1, depth + 1, lines);
            else if (!node.isLeaf) walk(node.content, pos + 1, depth + 1);
        });
    };
    walk(content, 0, 0);
    return lines;
}

function linesInBlock(block: PmNode, contentStart: number, depth: number, out: PasteLine[]): void {
    let line: PasteLine | null = null;
    const flush = (): void => {
        if (line) out.push(line);
        line = null;
    };
    block.forEach((child, offset) => {
        const pos = contentStart + offset;
        if (child.type.name === 'hardBreak') return flush();
        const current: PasteLine = line ?? { text: '', hrefs: [], start: pos, end: pos, depth };
        line = current;
        if (child.isText) {
            current.text += child.text ?? '';
            for (const mark of child.marks) {
                const href = mark.type.name === 'link' ? mark.attrs.href : null;
                if (typeof href === 'string' && href) current.hrefs.push(href);
            }
        }
        current.end = pos + child.nodeSize;
    });
    flush();
}

/** 내용에 글자가 아닌 것(그림·영상·이모티콘)이 있는가 */
function hasNonText(content: Fragment): boolean {
    return hasNonTextBetween(content, 0, content.size);
}

function hasNonTextBetween(content: Fragment, from: number, to: number): boolean {
    let found = false;
    content.nodesBetween(from, to, (node) => {
        if (found) return false;
        if (node.isLeaf && !node.isText && node.type.name !== 'hardBreak') found = true;
        return !found;
    });
    return found;
}
