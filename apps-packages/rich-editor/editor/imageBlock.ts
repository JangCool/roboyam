import { mergeAttributes, Node as TiptapNode } from '@tiptap/vue-3';
import { NodeSelection } from '@tiptap/pm/state';
import UtilRichMedia from '#layers/rich-editor/utils/util-rich-media';

/**
 * 본문 이미지 노드 — 글에 실리는 표식은 `<img data-image-id="이미지ID">` 이다(서버가 `data-image-id` 만 보고 주소·크기를 다시 채운다).
 * community `frontend/app/editor/imageBlock.ts` 에서 옮겼다(GIF 정지 이미지 `data-animated`·`data-poster` 는 아이로빗 서버에 없어 뺐다).
 * 에디터 미리보기를 위해 `src`·`width`·`height` 도 함께 직렬화한다 — 서버가 준 본문을 그대로 불러와 다시 보내도 같은 결과가 되도록(수정 왕복) **모두 읽고 모두 쓴다**.
 * `data-image-id` 가 없는 `<img>`(다른 곳에서 붙여넣은 외부 이미지)는 읽지 않는다 — 서버도 받지 않는다.
 *
 * **이미지 도구**(계약 docs/features/editor-image-tools-contract.md) — 이미지를 고르면 위에 도구 막대가 뜬다: 정렬(왼쪽·가운데·오른쪽), 크기(25·50·75%·원본),
 * 캡션(서식 없는 글자), 대체 텍스트(화면 읽기용), 지우기. 정렬·크기·캡션 중 하나라도 있으면 `figure` 로 감싸 저장한다 —
 * `<figure data-align="center" data-width="50"><img data-image-id="…" alt="…"><figcaption>글자</figcaption></figure>`.
 * 셋 다 없으면(예전 글의 이미지를 건드리지 않았을 때) 지금처럼 `<img>` 하나다 — 글 보기에서 왼쪽·원본 크기로 보인다. 새로 넣는 이미지는 가운데 정렬로 들어간다(부르는 쪽이 정한다).
 *
 * 화면(NodeView)은 DOM API 로만 만든다(문자열을 HTML 로 넣지 않는다). 지우기는 선택 후 Delete, ✕ 단추 또는 도구 막대의 지우기. 단추 이름은 `labels` 로 받는다(화면 언어).
 */

export type ImageAlign = 'left' | 'center' | 'right';
export type ImageSize = 25 | 50 | 75;

export interface ImageBlockLabels {
    image: string;
    remove: string;
    broken: string;
    tools: string;
    alignLeft: string;
    alignCenter: string;
    alignRight: string;
    size25: string;
    size50: string;
    size75: string;
    sizeOriginal: string;
    sizeOriginalShort: string;
    caption: string;
    captionPlaceholder: string;
    alt: string;
    altPlaceholder: string;
}

export interface ImageBlockOptions {
    labels: ImageBlockLabels;
}

/** 캡션·대체 텍스트 최대 글자 수 — 서버 `RichText.CAPTION_MAX` 와 같다 */
export const IMAGE_TEXT_MAX = 200;

const ALIGNS: readonly ImageAlign[] = ['left', 'center', 'right'];
const SIZES: readonly ImageSize[] = [25, 50, 75];

function positiveNumber(el: HTMLElement | null, name: string): number | null {
    const value = Number(el?.getAttribute(name));
    return Number.isFinite(value) && value > 0 ? value : null;
}

/** 규칙이 맞춘 요소에서 이미지 — `img` 규칙이면 자기 자신, `figure` 규칙이면 안의 표식 이미지 */
function imgOf(el: HTMLElement): HTMLElement | null {
    return el.tagName === 'IMG' ? el : el.querySelector<HTMLElement>('img[data-image-id]');
}

/** 규칙이 맞춘 요소의 figure(없으면 null — 예전 글의 `<img>` 하나) */
function figureOf(el: HTMLElement): HTMLElement | null {
    return el.tagName === 'FIGURE' ? el : null;
}

/** 공백을 하나로 줄이고 최대 글자 수까지 — 서버도 같은 규칙으로 다시 자른다 */
function cleanText(value: string | null | undefined): string {
    return Array.from((value ?? '').replace(/\s+/g, ' ').trim())
        .slice(0, IMAGE_TEXT_MAX)
        .join('');
}

export const ImageBlock = TiptapNode.create<ImageBlockOptions>({
    name: 'image',
    group: 'block',
    atom: true,
    draggable: true,
    selectable: true,

    addOptions() {
        return {
            labels: {
                image: 'image',
                remove: 'remove',
                broken: 'broken',
                tools: 'image tools',
                alignLeft: 'align left',
                alignCenter: 'align center',
                alignRight: 'align right',
                size25: '25%',
                size50: '50%',
                size75: '75%',
                sizeOriginal: 'original size',
                sizeOriginalShort: 'original',
                caption: 'caption',
                captionPlaceholder: 'caption',
                alt: 'alt text',
                altPlaceholder: 'alt text',
            },
        };
    },

    addAttributes() {
        return {
            imageId: {
                default: null,
                parseHTML: (el: HTMLElement) => imgOf(el)?.getAttribute('data-image-id') ?? null,
                renderHTML: (attrs: Record<string, unknown>) => (attrs.imageId ? { 'data-image-id': String(attrs.imageId) } : {}),
            },
            src: {
                default: null,
                parseHTML: (el: HTMLElement) => UtilRichMedia.safeImageUrl(imgOf(el)?.getAttribute('src') ?? null),
                renderHTML: (attrs: Record<string, unknown>) => (attrs.src ? { src: String(attrs.src) } : {}),
            },
            width: {
                default: null,
                parseHTML: (el: HTMLElement) => positiveNumber(imgOf(el), 'width'),
                renderHTML: (attrs: Record<string, unknown>) => (attrs.width ? { width: String(attrs.width) } : {}),
            },
            height: {
                default: null,
                parseHTML: (el: HTMLElement) => positiveNumber(imgOf(el), 'height'),
                renderHTML: (attrs: Record<string, unknown>) => (attrs.height ? { height: String(attrs.height) } : {}),
            },
            /** 정렬 — null 은 왼쪽(예전 글) */
            align: {
                default: null,
                rendered: false,
                parseHTML: (el: HTMLElement) => {
                    const value = figureOf(el)?.getAttribute('data-align');
                    return ALIGNS.includes(value as ImageAlign) ? value : null;
                },
            },
            /** 크기(본문 폭의 %) — null 은 원본(본문 폭까지) */
            size: {
                default: null,
                rendered: false,
                parseHTML: (el: HTMLElement) => {
                    const value = Number(figureOf(el)?.getAttribute('data-width'));
                    return SIZES.includes(value as ImageSize) ? value : null;
                },
            },
            /** 캡션 — null 은 캡션 칸을 열지 않았다, '' 는 열었지만 비었다(저장하지 않는다) */
            caption: {
                default: null,
                rendered: false,
                parseHTML: (el: HTMLElement) => cleanText(figureOf(el)?.querySelector('figcaption')?.textContent) || null,
            },
            /** 대체 텍스트 — 화면 읽기용, 보이지 않는다 */
            alt: {
                default: null,
                rendered: false,
                parseHTML: (el: HTMLElement) => cleanText(imgOf(el)?.getAttribute('alt')) || null,
            },
        };
    },

    parseHTML() {
        return [
            // 이미지 묶음 — 표식 이미지가 들어 있을 때만(잎 노드라 안의 img·figcaption 은 따로 읽히지 않는다)
            { tag: 'figure', priority: 60, getAttrs: (el) => ((el as HTMLElement).querySelector('img[data-image-id]') ? null : false) },
            { tag: 'img[data-image-id]' },
        ];
    },

    renderHTML({ node, HTMLAttributes }) {
        const { align, size, caption, alt } = node.attrs as { align: ImageAlign | null; size: ImageSize | null; caption: string | null; alt: string | null };
        const img = ['img', mergeAttributes(HTMLAttributes, { alt: cleanText(alt) })] as const;
        const text = cleanText(caption);
        if (!align && !size && !text) return img;
        const figure: Record<string, string> = {};
        if (align) figure['data-align'] = align;
        if (size) figure['data-width'] = String(size);
        return text ? ['figure', figure, img, ['figcaption', {}, text]] : ['figure', figure, img];
    },

    addNodeView() {
        const labels = this.options.labels;
        return ({ node, getPos, editor }) => {
            let current = node;
            let altOpen = false;

            const el = <K extends keyof HTMLElementTagNameMap>(tag: K, className?: string): HTMLElementTagNameMap[K] => {
                const made = document.createElement(tag);
                if (className) made.className = className;
                return made;
            };

            /** 이 노드의 속성을 바꾼다 — 위치가 바뀌지 않는 속성 단계라 선택이 그대로 남는다(`reselect` 면 이 이미지를 다시 고른다) */
            const setAttrs = (attrs: Record<string, unknown>, reselect = true) => {
                const pos = getPos();
                if (typeof pos !== 'number') return;
                const tr = editor.state.tr;
                for (const [key, value] of Object.entries(attrs)) tr.setNodeAttribute(pos, key, value);
                if (reselect) tr.setSelection(NodeSelection.create(tr.doc, pos));
                editor.view.dispatch(tr);
            };

            const removeNode = () => {
                const pos = getPos();
                if (typeof pos !== 'number') return;
                editor.chain().focus().deleteRange({ from: pos, to: pos + current.nodeSize }).run();
            };

            /** 고른 이미지로 돌아간다(캡션·대체 텍스트 칸에서 Enter·Esc) */
            const backToImage = () => {
                const pos = getPos();
                if (typeof pos !== 'number') return;
                editor.chain().focus().setNodeSelection(pos).run();
            };

            const dom = el('figure', 'rt-figure');
            dom.contentEditable = 'false';

            /* ── 도구 막대(고른 동안만 보인다) ── */
            const tools = el('div', 'rt-img-tools');
            tools.setAttribute('role', 'toolbar');
            tools.setAttribute('aria-label', labels.tools);

            const button = (label: string, content: { icon?: string; text?: string }, onClick: () => void, extra?: string) => {
                const b = el('button', extra ? `rt-img-tool ${extra}` : 'rt-img-tool');
                b.type = 'button';
                b.title = label;
                b.setAttribute('aria-label', label);
                if (content.icon) {
                    const i = el('i', content.icon);
                    i.setAttribute('aria-hidden', 'true');
                    b.append(i);
                }
                if (content.text) {
                    const s = el('span');
                    s.textContent = content.text;
                    s.setAttribute('aria-hidden', 'true');
                    b.append(s);
                }
                // 눌러도 에디터가 초점·선택을 잃지 않게
                b.addEventListener('mousedown', (event) => event.preventDefault());
                b.addEventListener('click', onClick);
                tools.append(b);
                return b;
            };
            const separator = () => {
                const s = el('span', 'rt-img-tools-sep');
                s.setAttribute('aria-hidden', 'true');
                tools.append(s);
            };

            const alignButtons: Record<ImageAlign, HTMLButtonElement> = {
                left: button(labels.alignLeft, { icon: 'pi pi-align-left' }, () => setAttrs({ align: 'left' })),
                center: button(labels.alignCenter, { icon: 'pi pi-align-center' }, () => setAttrs({ align: 'center' })),
                right: button(labels.alignRight, { icon: 'pi pi-align-right' }, () => setAttrs({ align: 'right' })),
            };
            separator();
            const sizeButtons: Array<[ImageSize | null, HTMLButtonElement]> = [
                [25, button(labels.size25, { text: '25%' }, () => setAttrs({ size: 25 }))],
                [50, button(labels.size50, { text: '50%' }, () => setAttrs({ size: 50 }))],
                [75, button(labels.size75, { text: '75%' }, () => setAttrs({ size: 75 }))],
                [null, button(labels.sizeOriginal, { text: labels.sizeOriginalShort }, () => setAttrs({ size: null }))],
            ];
            separator();
            const captionButton = button(labels.caption, { icon: 'pi pi-comment', text: labels.caption }, () => {
                if (current.attrs.caption === null) {
                    setAttrs({ caption: '' });
                    requestAnimationFrame(() => captionInput.focus());
                } else {
                    setAttrs({ caption: null });
                }
            });
            const altButton = button(labels.alt, { icon: 'pi pi-eye', text: labels.alt }, () => {
                altOpen = !altOpen;
                paint(current);
                if (altOpen) requestAnimationFrame(() => altInput.focus());
            });
            separator();
            button(labels.remove, { icon: 'pi pi-trash' }, removeNode, 'rt-img-tool--danger');

            /* ── 이미지 ── */
            const box = el('div', 'rt-figure-box');

            const img = el('img');
            img.decoding = 'async';
            img.draggable = false;

            const broken = el('span', 'rt-figure-broken');
            broken.textContent = labels.broken;
            broken.hidden = true;

            const remove = el('button', 'rt-remove');
            remove.type = 'button';
            remove.setAttribute('aria-label', labels.remove);
            remove.title = labels.remove;
            remove.textContent = '✕';
            remove.addEventListener('mousedown', (event) => event.preventDefault());
            remove.addEventListener('click', removeNode);

            img.addEventListener('error', () => {
                box.classList.add('rt-figure-box--broken');
                broken.hidden = false;
            });
            img.addEventListener('load', () => {
                box.classList.remove('rt-figure-box--broken');
                broken.hidden = true;
            });
            box.append(img, broken, remove);

            /* ── 캡션 칸(캡션을 켰을 때) ── */
            const captionInput = el('input', 'rt-figure-caption');
            captionInput.type = 'text';
            captionInput.maxLength = IMAGE_TEXT_MAX;
            captionInput.placeholder = labels.captionPlaceholder;
            captionInput.setAttribute('aria-label', labels.caption);
            captionInput.addEventListener('input', () => setAttrs({ caption: captionInput.value }, false));

            /* ── 대체 텍스트 칸(도구 막대에서 열었을 때, 고른 동안만) ── */
            const altRow = el('label', 'rt-figure-alt');
            const altLabel = el('span');
            altLabel.textContent = labels.alt;
            const altInput = el('input');
            altInput.type = 'text';
            altInput.maxLength = IMAGE_TEXT_MAX;
            altInput.placeholder = labels.altPlaceholder;
            altInput.addEventListener('input', () => setAttrs({ alt: altInput.value || null }, false));
            altRow.append(altLabel, altInput);

            for (const input of [captionInput, altInput]) {
                input.addEventListener('keydown', (event) => {
                    if (event.key === 'Enter' || event.key === 'Escape') {
                        event.preventDefault();
                        backToImage();
                    }
                });
            }

            dom.append(tools, box, captionInput, altRow);

            function paint(n: typeof node) {
                const { src, width, height, align, size, caption, alt } = n.attrs as {
                    src: string | null;
                    width: number | null;
                    height: number | null;
                    align: ImageAlign | null;
                    size: ImageSize | null;
                    caption: string | null;
                    alt: string | null;
                };
                const shown = src ?? '';
                if (img.getAttribute('src') !== shown) img.src = shown;
                img.alt = alt || labels.image;
                if (width && height) {
                    img.width = width;
                    img.height = height;
                } else {
                    img.removeAttribute('width');
                    img.removeAttribute('height');
                }
                dom.dataset.imageId = String(n.attrs.imageId ?? '');
                if (align) dom.dataset.align = align;
                else delete dom.dataset.align;
                if (size) dom.dataset.width = String(size);
                else delete dom.dataset.width;

                for (const key of ALIGNS) alignButtons[key].setAttribute('aria-pressed', String((align ?? 'left') === key));
                for (const [value, b] of sizeButtons) b.setAttribute('aria-pressed', String(size === value));
                captionButton.setAttribute('aria-pressed', String(caption !== null));
                altButton.setAttribute('aria-pressed', String(altOpen));

                captionInput.hidden = caption === null;
                if (caption !== null && captionInput.value !== caption) captionInput.value = caption;
                altRow.hidden = !altOpen;
                if (altInput.value !== (alt ?? '')) altInput.value = alt ?? '';
            }
            paint(node);

            return {
                dom,
                update(updated) {
                    if (updated.type !== current.type) return false;
                    current = updated;
                    paint(updated);
                    return true;
                },
                selectNode() {
                    dom.classList.add('ProseMirror-selectednode');
                },
                deselectNode() {
                    dom.classList.remove('ProseMirror-selectednode');
                    // 대체 텍스트 칸에 글자를 치는 동안에는 닫지 않는다
                    if (altOpen && !altRow.contains(document.activeElement)) {
                        altOpen = false;
                        paint(current);
                    }
                },
                // 단추·글자 칸의 누르기·입력은 에디터가 가로채지 않는다
                stopEvent: (event) => event.target instanceof Element && event.target.closest('button, input, label') !== null,
                ignoreMutation: () => true,
            };
        };
    },
});
