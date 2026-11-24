import type { RichEmoticonImage, RichEmoticonResolve } from '#layers/rich-editor/utils/util-rich-emoticon';
import UtilRichEmoticon from '#layers/rich-editor/utils/util-rich-emoticon';
import type { RichJjalImage, RichJjalResolve } from '#layers/rich-editor/utils/util-rich-jjal';
import UtilRichJjal from '#layers/rich-editor/utils/util-rich-jjal';
import type { AdminBodyEmoticonView, AdminBodyJjalView } from '~/composables/post/api/usePostApi.types';

/**
 * 본문 토큰(로보티콘 `:emo-ID:` · 내 짤 `:jjal-ID:`) 그리기 재료 — 글 서랍(`PostDetailDrawer`)·신고 서랍(`ReportDetailDrawer`)이 같이 쓰는 본문 부품(`PostBodyView`)이 쓴다.
 * 토큰 규칙·DOM 바꾸기는 레이어 `UtilRichEmoticon`·`UtilRichJjal`(user-web 과 같은 규칙) 한 곳이고, 여기는 상세가 준 그림 목록(서버 `AdminBodyTokenImages` 의
 * `emoticons`·`jjals`)을 찾기 함수로 바꾸고 평문을 조각으로 나눈다. 순수 함수 — 상태·요청이 없다.
 */

/** 평문 본문 조각 — 글자 · 로보티콘(그림 또는 null) · 짤(그림 또는 null — 내려간 짤은 `image.blocked`) */
export type BodyTokenSegment =
    | { kind: 'text'; text: string }
    | { kind: 'emoticon'; id: string; raw: string; image: RichEmoticonImage | null }
    | { kind: 'jjal'; id: string; raw: string; image: RichJjalImage | null };

export default class UtilBodyToken {
    /**
     * 상세의 로보티콘 그림 목록 → 찾기 함수. 목록이 없거나 그 ID 가 없으면 null(부품이 「[로보티콘]」 으로).
     * @param {readonly AdminBodyEmoticonView[] | null | undefined} list - 상세가 준 그림들.
     * @returns {RichEmoticonResolve} ID → 그림.
     */
    static emoticonResolver(list: readonly AdminBodyEmoticonView[] | null | undefined): RichEmoticonResolve {
        const map = new Map((list ?? []).map((e) => [e.id, { url: e.url, label: e.label }] as const));
        return (id) => map.get(id) ?? null;
    }

    /**
     * 상세의 짤 목록 → 찾기 함수. 주소가 없으면(내려간 짤) `blocked` 로 — 「내려간 짤」 자리. 목록에 없으면 null(부품이 「[짤]」 로).
     * @param {readonly AdminBodyJjalView[] | null | undefined} list - 상세가 준 짤들.
     * @returns {RichJjalResolve} ID → 그림.
     */
    static jjalResolver(list: readonly AdminBodyJjalView[] | null | undefined): RichJjalResolve {
        const map = new Map(
            (list ?? []).map((j) => [j.id, { url: j.url ?? '', width: j.width, height: j.height, blocked: j.blocked || !j.url }] as const),
        );
        return (id) => map.get(id) ?? null;
    }

    /**
     * 본문에 토큰(로보티콘·짤)이 하나라도 있는가.
     * @param {string | null | undefined} text - 글자 또는 HTML.
     * @returns {boolean} 있으면 참.
     */
    static has(text: string | null | undefined): boolean {
        return UtilRichEmoticon.has(text) || UtilRichJjal.has(text);
    }

    /**
     * 평문을 글자·로보티콘·짤 조각으로 나눈다(로보티콘으로 먼저 나누고 글자 조각을 짤로 다시 나눈다 — 두 토큰은 꼴이 달라 겹치지 않는다).
     * @param {string} text - 평문.
     * @param {RichEmoticonResolve} emoticon - 로보티콘 찾기.
     * @param {RichJjalResolve} jjal - 짤 찾기.
     * @returns {BodyTokenSegment[]} 조각들.
     */
    static segments(text: string, emoticon: RichEmoticonResolve, jjal: RichJjalResolve): BodyTokenSegment[] {
        const out: BodyTokenSegment[] = [];
        for (const part of UtilRichEmoticon.split(text)) {
            if (part.kind === 'emoticon') {
                out.push({ ...part, image: emoticon(part.id) });
                continue;
            }
            for (const sub of UtilRichJjal.split(part.text)) out.push(sub.kind === 'jjal' ? { ...sub, image: jjal(sub.id) } : sub);
        }
        return out;
    }
}
