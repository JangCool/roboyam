import { Node as TiptapNode } from '@tiptap/vue-3';
import type { RichEmbedProvider } from '#layers/rich-editor/utils/util-rich-media';
import UtilRichMedia from '#layers/rich-editor/utils/util-rich-media';

/**
 * 유튜브 밖의 영상 노드(치지직 클립·SOOP 다시보기·틱톡·인스타그램) — 글에 실리는 표식은 `<div data-embed="플랫폼:ID"></div>` 하나다
 * (보기는 `UtilRichBody.enhance` 가 누르면 그 플랫폼의 퍼가기 iframe 으로 그린다). 표식 값의 형식이 틀리면 읽지 않는다.
 * 썸네일은 플랫폼이 주소로 주지 않아 에디터 안에서는 플랫폼 이름 + 재생 아이콘 카드만 보인다(재생은 되지 않는다). 모양은 유튜브 카드(`rt-yt`)를 같이 쓴다.
 */

export interface EmbedBlockLabels {
    /** 플랫폼 이름(카드 글자) */
    names: Record<RichEmbedProvider, string>;
    /** 지우기 단추 이름(플랫폼마다) */
    remove: Record<RichEmbedProvider, string>;
}

export interface EmbedBlockOptions {
    labels: EmbedBlockLabels;
}

export const EmbedBlock = TiptapNode.create<EmbedBlockOptions>({
    name: 'embed',
    group: 'block',
    atom: true,
    draggable: true,
    selectable: true,

    addOptions() {
        const names = { chzzk: 'CHZZK', soop: 'SOOP', tiktok: 'TikTok', instagram: 'Instagram' };
        return { labels: { names, remove: names } };
    },

    addAttributes() {
        return {
            marker: {
                default: null,
                parseHTML: (el: HTMLElement) => {
                    const embed = UtilRichMedia.parseEmbedMarker(el.getAttribute('data-embed'));
                    return embed ? UtilRichMedia.embedMarker(embed) : null;
                },
                renderHTML: (attrs: Record<string, unknown>) => (attrs.marker ? { 'data-embed': String(attrs.marker) } : {}),
            },
        };
    },

    parseHTML() {
        return [
            {
                tag: 'div[data-embed]',
                // 형식이 틀린 표식은 읽지 않는다(서버도 저장 때 다시 검사한다)
                getAttrs: (el: HTMLElement | string) => (typeof el !== 'string' && UtilRichMedia.parseEmbedMarker(el.getAttribute('data-embed')) ? null : false),
            },
        ];
    },

    renderHTML({ HTMLAttributes }) {
        return ['div', HTMLAttributes];
    },

    addNodeView() {
        const labels = this.options.labels;
        return ({ node, getPos, editor }) => {
            let current = node;

            const dom = document.createElement('div');
            dom.className = 'rt-figure rt-figure--yt';
            dom.contentEditable = 'false';

            const box = document.createElement('div');
            box.className = 'rt-yt';

            const icon = document.createElement('span');
            icon.className = 'rt-yt-icon';
            icon.setAttribute('aria-hidden', 'true');

            const label = document.createElement('span');
            label.className = 'rt-yt-label';

            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'rt-remove';
            remove.textContent = '✕';
            remove.addEventListener('mousedown', (event) => event.preventDefault());
            remove.addEventListener('click', () => {
                const pos = getPos();
                if (typeof pos !== 'number') return;
                editor.chain().focus().deleteRange({ from: pos, to: pos + current.nodeSize }).run();
            });

            box.append(icon, label, remove);
            dom.append(box);

            const paint = (n: typeof node) => {
                const embed = UtilRichMedia.parseEmbedMarker(n.attrs.marker as string | null);
                if (!embed) return;
                label.textContent = labels.names[embed.provider];
                remove.setAttribute('aria-label', labels.remove[embed.provider]);
                remove.title = labels.remove[embed.provider];
                box.classList.toggle('rt-yt--tall', UtilRichMedia.isTallEmbed(embed.provider));
                dom.dataset.embed = UtilRichMedia.embedMarker(embed);
            };
            paint(node);

            return {
                dom,
                update(updated) {
                    if (updated.type !== current.type) return false;
                    current = updated;
                    paint(updated);
                    return true;
                },
                stopEvent: (event) => event.target instanceof Element && event.target.closest('button') !== null,
            };
        };
    },
});
