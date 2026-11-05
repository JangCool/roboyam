import { Node as TiptapNode } from '@tiptap/vue-3';
import type { DOMOutputSpec } from '@tiptap/pm/model';
import type { RichEmoticonResolve } from '#layers/rich-editor/utils/util-rich-emoticon';
import UtilRichEmoticon from '#layers/rich-editor/utils/util-rich-emoticon';

/**
 * 이모티콘 인라인 노드 — 에디터 안에서는 그림으로 보이고, **저장(`getHTML`)할 때는 토큰 글자 `:emo-ID:`** 로 나간다(본문에는 토큰만 저장 — 이모티콘 계약 가정 7).
 * 불러올 때는 `UtilRichEmoticon.toEditorHtml` 이 토큰을 `<img data-emoticon-id>` 표식으로 바꿔 넣고 이 노드가 그 표식을 읽는다.
 * 쓰는 앱이 `RichEditor` 의 `emoticons` 속성을 줄 때만 켜진다(admin-web 은 켜지 않는다). 그림은 `resolve` 로 찾고, 모르면 토큰 글자를 회색 글자로 보인다.
 *
 * 화면(NodeView)은 DOM API 로만 만든다(문자열을 HTML 로 넣지 않는다).
 */

export interface EmoticonInlineOptions {
    resolve: RichEmoticonResolve;
}

export const EmoticonInline = TiptapNode.create<EmoticonInlineOptions>({
    name: 'emoticon',
    group: 'inline',
    inline: true,
    atom: true,
    selectable: true,
    draggable: false,

    addOptions() {
        return { resolve: () => null };
    },

    addAttributes() {
        return {
            id: {
                default: null,
                parseHTML: (el: HTMLElement) => el.getAttribute('data-emoticon-id'),
                renderHTML: () => ({}),
            },
        };
    },

    parseHTML() {
        return [
            { tag: 'img[data-emoticon-id]' },
            // 에디터 안 복사·붙여넣기 — 직렬화 표식 span 을 다시 노드로
            {
                tag: `span[${UtilRichEmoticon.MARKER_ATTR}]`,
                getAttrs: (el: HTMLElement) => {
                    const part = UtilRichEmoticon.split(el.textContent ?? '').find((p) => p.kind === 'emoticon');
                    return part?.kind === 'emoticon' ? { id: part.id } : false;
                },
            },
        ];
    },

    /**
     * 저장 모양 = 토큰 글자. ProseMirror `renderSpec` 은 글자만 내보낼 수 없어(문자열을 주면 첫 글자를 태그 이름으로 써 `<:>emo-ID:</:>` 가 된다)
     * 표식 span 으로 감싸 내보내고, `RichEditor` 가 저장 전에 `UtilRichEmoticon.fromEditorHtml` 로 벗긴다(저장 HTML 에 태그가 남으면 서버 정화·글자 수가 달라진다).
     */
    renderHTML({ node }) {
        return ['span', { [UtilRichEmoticon.MARKER_ATTR]: '' }, UtilRichEmoticon.token(String(node.attrs.id ?? ''))] as DOMOutputSpec;
    },

    renderText({ node }) {
        return UtilRichEmoticon.token(String(node.attrs.id ?? ''));
    },

    addNodeView() {
        const resolve = this.options.resolve;
        return ({ node }) => {
            const id = String(node.attrs.id ?? '');
            const dom = document.createElement('span');
            dom.className = 'rt-emoticon';
            dom.contentEditable = 'false';
            const image = resolve(id);
            if (image) {
                dom.append(UtilRichEmoticon.image(document, id, image));
            } else {
                dom.classList.add('rt-emoticon-unknown');
                dom.textContent = UtilRichEmoticon.token(id);
            }
            return { dom };
        };
    },
});
