import { mergeAttributes, Node as TiptapNode } from '@tiptap/vue-3';
import UtilRichMedia from '#layers/rich-editor/utils/util-rich-media';

/**
 * 본문 이미지 노드 — 글에 실리는 표식은 `<img data-image-id="이미지ID">` 하나다(서버가 `data-image-id` 만 보고 주소·크기를 다시 채운다).
 * community `frontend/app/editor/imageBlock.ts` 에서 옮겼다(GIF 정지 이미지 `data-animated`·`data-poster` 는 로보얌 서버에 없어 뺐다).
 * 에디터 미리보기를 위해 `src`·`width`·`height` 도 함께 직렬화한다 — 서버가 준 본문을 그대로 불러와 다시 보내도 같은 결과가 되도록(수정 왕복) **모두 읽고 모두 쓴다**.
 * `data-image-id` 가 없는 `<img>`(다른 곳에서 붙여넣은 외부 이미지)는 읽지 않는다 — 서버도 받지 않는다.
 *
 * 화면(NodeView)은 DOM API 로만 만든다(문자열을 HTML 로 넣지 않는다). 지우기는 선택 후 Delete 또는 ✕ 단추. 단추 이름은 `labels` 로 받는다(화면 언어).
 */

export interface ImageBlockLabels {
    image: string;
    remove: string;
    broken: string;
}

export interface ImageBlockOptions {
    labels: ImageBlockLabels;
}

function positiveNumber(el: HTMLElement, name: string): number | null {
    const value = Number(el.getAttribute(name));
    return Number.isFinite(value) && value > 0 ? value : null;
}

export const ImageBlock = TiptapNode.create<ImageBlockOptions>({
    name: 'image',
    group: 'block',
    atom: true,
    draggable: true,
    selectable: true,

    addOptions() {
        return { labels: { image: 'image', remove: 'remove', broken: 'broken' } };
    },

    addAttributes() {
        return {
            imageId: {
                default: null,
                parseHTML: (el: HTMLElement) => el.getAttribute('data-image-id'),
                renderHTML: (attrs: Record<string, unknown>) => (attrs.imageId ? { 'data-image-id': String(attrs.imageId) } : {}),
            },
            src: {
                default: null,
                parseHTML: (el: HTMLElement) => UtilRichMedia.safeImageUrl(el.getAttribute('src')),
                renderHTML: (attrs: Record<string, unknown>) => (attrs.src ? { src: String(attrs.src) } : {}),
            },
            width: {
                default: null,
                parseHTML: (el: HTMLElement) => positiveNumber(el, 'width'),
                renderHTML: (attrs: Record<string, unknown>) => (attrs.width ? { width: String(attrs.width) } : {}),
            },
            height: {
                default: null,
                parseHTML: (el: HTMLElement) => positiveNumber(el, 'height'),
                renderHTML: (attrs: Record<string, unknown>) => (attrs.height ? { height: String(attrs.height) } : {}),
            },
        };
    },

    parseHTML() {
        return [{ tag: 'img[data-image-id]' }];
    },

    renderHTML({ HTMLAttributes }) {
        return ['img', mergeAttributes(HTMLAttributes, { alt: '' })];
    },

    addNodeView() {
        const labels = this.options.labels;
        return ({ node, getPos, editor }) => {
            let current = node;

            const dom = document.createElement('figure');
            dom.className = 'rt-figure';
            dom.contentEditable = 'false';

            const box = document.createElement('div');
            box.className = 'rt-figure-box';

            const img = document.createElement('img');
            img.alt = labels.image;
            img.decoding = 'async';
            img.draggable = false;

            const broken = document.createElement('span');
            broken.className = 'rt-figure-broken';
            broken.textContent = labels.broken;
            broken.hidden = true;

            const remove = document.createElement('button');
            remove.type = 'button';
            remove.className = 'rt-remove';
            remove.setAttribute('aria-label', labels.remove);
            remove.title = labels.remove;
            remove.textContent = '✕';
            // 눌러도 에디터가 초점을 잃지 않게
            remove.addEventListener('mousedown', (event) => event.preventDefault());
            remove.addEventListener('click', () => {
                const pos = getPos();
                if (typeof pos !== 'number') return;
                editor.chain().focus().deleteRange({ from: pos, to: pos + current.nodeSize }).run();
            });

            img.addEventListener('error', () => {
                box.classList.add('rt-figure-box--broken');
                broken.hidden = false;
            });
            img.addEventListener('load', () => {
                box.classList.remove('rt-figure-box--broken');
                broken.hidden = true;
            });

            box.append(img, broken, remove);
            dom.append(box);

            const paint = (n: typeof node) => {
                const { src, width, height } = n.attrs as { src: string | null; width: number | null; height: number | null };
                const shown = src ?? '';
                if (img.getAttribute('src') !== shown) img.src = shown;
                if (width && height) {
                    img.width = width;
                    img.height = height;
                } else {
                    img.removeAttribute('width');
                    img.removeAttribute('height');
                }
                dom.dataset.imageId = String(n.attrs.imageId ?? '');
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
                // ✕ 단추의 누르기는 에디터가 가로채지 않는다
                stopEvent: (event) => event.target instanceof Element && event.target.closest('button') !== null,
            };
        };
    },
});
