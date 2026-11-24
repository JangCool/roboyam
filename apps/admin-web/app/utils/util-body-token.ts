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

/**
 * 평문 본문 조각 — 글자 · 로보티콘(그림 또는 null) · 짤(그림 또는 null — 내려간 짤은 `image.blocked`).
 * 짤의 `adminOnly` 는 내려간 짤을 운영자 전용 원본(`adminUrl`)으로 그린 것 — 그림 위에 「내려간 짤 · 운영자만 보임」 딱지를 단다.
 */
export type BodyTokenSegment =
    | { kind: 'text'; text: string }
    | { kind: 'emoticon'; id: string; raw: string; image: RichEmoticonImage | null }
    | { kind: 'jjal'; id: string; raw: string; image: RichJjalImage | null; adminOnly: boolean };

/** 운영자 전용 원본으로 그린 짤을 감싸는 `<span>` 의 클래스 — 모양은 앱 CSS(`main.css` `.jjal-admin-only`). 평문 조각(`PostBodyView` 템플릿)도 같은 클래스를 쓴다 */
const ADMIN_ONLY_CLASS = 'jjal-admin-only';
/** 그 위 딱지(`<span>`)의 클래스 */
const ADMIN_ONLY_BADGE_CLASS = 'jjal-admin-only__badge';

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
     * 짤 하나를 어느 주소로 그리나 — `url`(공개 짤) / 없고 내려간 짤에 `adminUrl` 이 있으면 그 주소(운영자 전용 원본) / 둘 다 없으면 null(「내려간 짤」 자리).
     * @param {AdminBodyJjalView} j - 상세가 준 짤.
     * @returns {{ url: string; adminOnly: boolean } | null} 그릴 주소와 운영자 전용 여부.
     */
    private static jjalSource(j: AdminBodyJjalView): { url: string; adminOnly: boolean } | null {
        if (j.url) return { url: j.url, adminOnly: false };
        if (j.blocked && j.adminUrl) return { url: j.adminUrl, adminOnly: true };
        return null;
    }

    /**
     * 상세의 짤 목록 → 찾기 함수. 공개 주소가 있으면 그 그림, 내려간 짤에 운영자 전용 원본(`adminUrl`)이 있으면 그 그림(딱지는 `jjalAdminOnlyIds` 로 단다),
     * 둘 다 없으면 `blocked` 로 — 「내려간 짤」 자리. 목록에 없으면 null(부품이 「[짤]」 로).
     * @param {readonly AdminBodyJjalView[] | null | undefined} list - 상세가 준 짤들.
     * @returns {RichJjalResolve} ID → 그림.
     */
    static jjalResolver(list: readonly AdminBodyJjalView[] | null | undefined): RichJjalResolve {
        const map = new Map(
            (list ?? []).map((j) => {
                const src = UtilBodyToken.jjalSource(j);
                return [j.id, { url: src?.url ?? '', width: j.width, height: j.height, blocked: !src }] as const;
            }),
        );
        return (id) => map.get(id) ?? null;
    }

    /**
     * 운영자 전용 원본으로 그리는 짤(내려갔지만 `adminUrl` 이 있는 것)의 ID 들.
     * @param {readonly AdminBodyJjalView[] | null | undefined} list - 상세가 준 짤들.
     * @returns {ReadonlySet<string>} ID 모음.
     */
    static jjalAdminOnlyIds(list: readonly AdminBodyJjalView[] | null | undefined): ReadonlySet<string> {
        return new Set((list ?? []).filter((j) => UtilBodyToken.jjalSource(j)?.adminOnly).map((j) => j.id));
    }

    /**
     * HTML 본문에서 운영자 전용 원본으로 그린 짤(`UtilRichJjal.replaceIn` 이 만든 `<img class="jjal">`)을 딱지 달린 `<span class="jjal-admin-only">` 로 감싼다 —
     * `replaceIn` 바로 뒤에 부른다(DOM API 만, 글은 `textContent`). 이미 감싼 그림은 건너뛰어 여러 번 불러도 안전하다. 레이어(user-web 과 같은 규칙)는 건드리지 않는다.
     * @param {HTMLElement} root - 본문이 든 요소.
     * @param {ReadonlySet<string>} ids - 운영자 전용 짤 ID(`jjalAdminOnlyIds`).
     * @param {string} label - 딱지·`alt`·`title` 글(앱이 번역).
     * @returns {number} 감싼 수.
     */
    static markAdminOnly(root: HTMLElement, ids: ReadonlySet<string>, label: string): number {
        if (ids.size === 0) return 0;
        const doc = root.ownerDocument;
        let marked = 0;
        for (const img of Array.from(root.querySelectorAll<HTMLImageElement>(`img.${UtilRichJjal.CLASS}[${UtilRichJjal.ID_ATTR}]`))) {
            const id = img.getAttribute(UtilRichJjal.ID_ATTR);
            if (!id || !ids.has(id) || img.parentElement?.classList.contains(ADMIN_ONLY_CLASS)) continue;
            const wrap = doc.createElement('span');
            wrap.className = ADMIN_ONLY_CLASS;
            wrap.title = label;
            const badge = doc.createElement('span');
            badge.className = ADMIN_ONLY_BADGE_CLASS;
            badge.setAttribute('aria-hidden', 'true');
            badge.textContent = label;
            img.alt = label;
            img.replaceWith(wrap);
            wrap.append(img, badge);
            marked++;
        }
        return marked;
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
     * @param {ReadonlySet<string>} [adminOnly] - 운영자 전용 원본으로 그리는 짤 ID(`jjalAdminOnlyIds`).
     * @returns {BodyTokenSegment[]} 조각들.
     */
    static segments(text: string, emoticon: RichEmoticonResolve, jjal: RichJjalResolve, adminOnly?: ReadonlySet<string>): BodyTokenSegment[] {
        const out: BodyTokenSegment[] = [];
        for (const part of UtilRichEmoticon.split(text)) {
            if (part.kind === 'emoticon') {
                out.push({ ...part, image: emoticon(part.id) });
                continue;
            }
            for (const sub of UtilRichJjal.split(part.text)) out.push(sub.kind === 'jjal' ? { ...sub, image: jjal(sub.id), adminOnly: Boolean(adminOnly?.has(sub.id)) } : sub);
        }
        return out;
    }
}
