import { Node as TiptapNode } from '@tiptap/vue-3';
import type { DOMOutputSpec } from '@tiptap/pm/model';
import type { RichJjalResolve } from '#layers/rich-editor/utils/util-rich-jjal';
import UtilRichJjal from '#layers/rich-editor/utils/util-rich-jjal';

/**
 * 내 짤 인라인 노드 — 에디터 안에서는 그림으로 보이고, **저장(`getHTML`)할 때는 토큰 글자 `:jjal-ID:`** 로 나간다(계약 `docs/features/my-jjal-contract.md`).
 * 이모티콘 노드(`emoticonInline.ts`)와 같은 틀이다. 불러올 때는 `UtilRichJjal.toEditorHtml` 이 토큰을 `<img data-jjal-id>` 표식으로 바꿔 넣고 이 노드가 그 표식을 읽는다.
 * 쓰는 앱이 `RichEditor` 의 `jjals` 속성을 줄 때만 켜진다(admin-web 은 켜지 않는다). 그림은 `resolve` 로 찾고, 모르면 토큰 글자를 회색 글자로, 내려간 짤은 「내려간 짤」 자리로 보인다.
 *
 * 화면(NodeView)은 DOM API 로만 만든다(문자열을 HTML 로 넣지 않는다).
 */

export interface JjalInlineOptions {
    resolve: RichJjalResolve;
    /** 내려간 짤 자리 글(앱이 번역) */
    blockedLabel: string;
}

export const JjalInline = TiptapNode.create<JjalInlineOptions>({
    name: 'jjal',
    group: 'inline',
    inline: true,
    atom: true,
    selectable: true,
    draggable: false,

    addOptions() {
        return { resolve: () => null, blockedLabel: '' };
    },

    addAttributes() {
        return {
            id: {
                default: null,
                parseHTML: (el: HTMLElement) => el.getAttribute(UtilRichJjal.ID_ATTR),
                renderHTML: () => ({}),
            },
        };
    },

    parseHTML() {
        return [
            { tag: `img[${UtilRichJjal.ID_ATTR}]` },
            // 에디터 안 복사·붙여넣기 — 직렬화 표식 span 을 다시 노드로
            {
                tag: `span[${UtilRichJjal.MARKER_ATTR}]`,
                getAttrs: (el: HTMLElement) => {
                    const part = UtilRichJjal.split(el.textContent ?? '').find((p) => p.kind === 'jjal');
                    return part?.kind === 'jjal' ? { id: part.id } : false;
                },
            },
        ];
    },

    /** 저장 모양 = 토큰 글자(표식 span 으로 감싸 내보내고 `RichEditor` 가 저장 전에 `UtilRichJjal.fromEditorHtml` 로 벗긴다 — 이모티콘 노드와 같은 까닭) */
    renderHTML({ node }) {
        return ['span', { [UtilRichJjal.MARKER_ATTR]: '' }, UtilRichJjal.token(String(node.attrs.id ?? ''))] as DOMOutputSpec;
    },

    renderText({ node }) {
        return UtilRichJjal.token(String(node.attrs.id ?? ''));
    },

    addNodeView() {
        const { resolve, blockedLabel } = this.options;
        return ({ node }) => {
            const id = String(node.attrs.id ?? '');
            const dom = document.createElement('span');
            dom.className = 'rt-jjal';
            dom.contentEditable = 'false';
            const image = resolve(id);
            if (image) {
                dom.append(UtilRichJjal.element(document, id, image, blockedLabel));
            } else {
                dom.classList.add('rt-jjal-unknown');
                dom.textContent = UtilRichJjal.token(id);
            }
            return { dom };
        };
    },
});
