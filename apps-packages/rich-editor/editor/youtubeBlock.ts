import { mergeAttributes, Node as TiptapNode } from '@tiptap/vue-3';
import UtilRichMedia from '#layers/rich-editor/utils/util-rich-media';

/**
 * 유튜브 노드 — 글에 실리는 표식은 `<div data-youtube-id="영상ID"></div>` 하나다(글 보기는 `UtilRichBody.enhance` 가 youtube-nocookie iframe 으로 그린다).
 * community `frontend/app/editor/youtubeBlock.ts` 에서 옮겼다. 영상 ID 는 영문·숫자·`-`·`_` 11자일 때만 읽는다.
 * 에디터 안에서는 썸네일 + 재생 아이콘만 보인다(재생은 되지 않는다). 단추·글자 이름은 `labels` 로 받는다(화면 언어).
 */

export interface YoutubeBlockLabels {
    video: string;
    remove: string;
}

export interface YoutubeBlockOptions {
    labels: YoutubeBlockLabels;
}

export const YoutubeBlock = TiptapNode.create<YoutubeBlockOptions>({
    name: 'youtube',
    group: 'block',
    atom: true,
    draggable: true,
    selectable: true,

    addOptions() {
        return { labels: { video: 'YouTube', remove: 'remove' } };
    },

    addAttributes() {
        return {
            videoId: {
                default: null,
                parseHTML: (el: HTMLElement) => {
                    const id = el.getAttribute('data-youtube-id');
                    return UtilRichMedia.isYoutubeId(id) ? id : null;
                },
                renderHTML: (attrs: Record<string, unknown>) => (attrs.videoId ? { 'data-youtube-id': String(attrs.videoId) } : {}),
            },
        };
    },

    parseHTML() {
        return [
            {
                tag: 'div[data-youtube-id]',
                // 형식이 틀린 ID 는 읽지 않는다(서버도 저장 때 다시 검사한다)
                getAttrs: (el: HTMLElement | string) => (typeof el !== 'string' && UtilRichMedia.isYoutubeId(el.getAttribute('data-youtube-id')) ? null : false),
            },
        ];
    },

    renderHTML({ HTMLAttributes }) {
        return ['div', mergeAttributes(HTMLAttributes)];
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

            const thumb = document.createElement('img');
            thumb.alt = '';
            thumb.decoding = 'async';
            thumb.draggable = false;
            thumb.referrerPolicy = 'no-referrer';
            thumb.addEventListener('error', () => thumb.remove()); // 썸네일이 없어도 검은 자리와 재생 아이콘은 남는다

            const icon = document.createElement('span');
            icon.className = 'rt-yt-icon';
            icon.setAttribute('aria-hidden', 'true');

            const label = document.createElement('span');
            label.className = 'rt-yt-label';
            label.textContent = labels.video;

            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'rt-remove';
            remove.setAttribute('aria-label', labels.remove);
            remove.title = labels.remove;
            remove.textContent = '✕';
            remove.addEventListener('mousedown', (event) => event.preventDefault());
            remove.addEventListener('click', () => {
                const pos = getPos();
                if (typeof pos !== 'number') return;
                editor.chain().focus().deleteRange({ from: pos, to: pos + current.nodeSize }).run();
            });

            box.append(thumb, icon, label, remove);
            dom.append(box);

            const paint = (n: typeof node) => {
                const id = n.attrs.videoId as string | null;
                if (UtilRichMedia.isYoutubeId(id)) {
                    thumb.src = UtilRichMedia.youtubeThumbnail(id);
                    if (!thumb.isConnected) box.prepend(thumb);
                    dom.dataset.youtubeId = id;
                }
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
