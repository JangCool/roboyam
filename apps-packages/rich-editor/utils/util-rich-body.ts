import type { Translate } from '#layers/i18n/utils/util-i18n';
import UtilRichMedia from '#layers/rich-editor/utils/util-rich-media';

/**
 * 글 보기 본문을 그린 뒤의 손질 — 화면이 서버가 정화한 HTML 을 `v-html` 로 넣은 **다음에** 부른다(community `util-post-body.ts` 에서 GIF 정지를 빼고 옮겼다).
 * ⚠ 손질은 DOM API(`createElement`·`setAttribute`)로만 한다. `innerHTML`·`insertAdjacentHTML` 로 서버·사용자 문자열이나 iframe 을 넣지 않는다.
 *
 * 하는 일 둘 —
 *  1. **유튜브** `div[data-youtube-id]`: 썸네일 + 재생 단추로 바꿔 그리고, **누를 때** 비로소 youtube-nocookie.com iframe 을 만든다
 *     (누르기 전에는 구글 플레이어를 불러오지 않는다). 영상 ID 가 11자 형식이 아니면 그리지 않는다.
 *  2. **이미지** `img[data-image-id]`: 게으르게 불러오고, 주소가 없거나 불러오지 못하면 같은 크기의 자리표시로 바꾼다.
 *
 * 이미 손질한 요소는 `data-rt-done` 으로 표시해 다시 불러도 두 번 바꾸지 않는다. 리스너는 손질한 요소에만 달려 요소와 함께 사라진다.
 */

const DONE = 'rtDone';

function element<K extends keyof HTMLElementTagNameMap>(tag: K, className?: string): HTMLElementTagNameMap[K] {
    const el = document.createElement(tag);
    if (className) el.className = className;
    return el;
}

function positive(value: string | null): number | null {
    const n = Number(value);
    return Number.isFinite(n) && n > 0 ? n : null;
}

function buildYoutube(t: Translate, id: string): HTMLElement {
    const box = element('div', 'rt-yt');
    box.dataset.youtubeId = id;
    box.dataset[DONE] = '1';

    const button = element('button', 'rt-yt-play');
    button.type = 'button';
    button.setAttribute('aria-label', t('richEditor.youtubePlay'));

    const thumb = element('img');
    thumb.alt = '';
    thumb.loading = 'lazy';
    thumb.decoding = 'async';
    thumb.referrerPolicy = 'no-referrer'; // 썸네일을 받으며 글 주소를 구글에 알리지 않는다
    thumb.src = UtilRichMedia.youtubeThumbnail(id);
    thumb.addEventListener('error', () => thumb.remove()); // 썸네일이 없어도 검은 자리와 재생 단추는 남는다

    const icon = element('span', 'rt-yt-icon');
    icon.setAttribute('aria-hidden', 'true');

    button.append(thumb, icon);
    button.addEventListener(
        'click',
        () => {
            const frame = element('iframe');
            frame.title = t('richEditor.youtubeFrame');
            frame.allow = 'autoplay; encrypted-media; picture-in-picture';
            frame.allowFullscreen = true;
            frame.referrerPolicy = 'strict-origin-when-cross-origin';
            frame.src = UtilRichMedia.youtubeEmbedUrl(id);
            box.replaceChildren(frame);
            frame.focus(); // 누른 단추가 사라지므로 키보드 초점을 플레이어로 옮긴다
        },
        { once: true },
    );
    box.append(button);
    return box;
}

function showImageFailure(t: Translate, target: HTMLElement, width: number | null, height: number | null): void {
    const fail = element('div', 'rt-img-fail');
    fail.setAttribute('role', 'img');
    fail.setAttribute('aria-label', t('richEditor.imageBroken'));
    fail.dataset[DONE] = '1';
    // 크기는 CSSOM 으로 준다(style 속성 문자열을 만들지 않는다)
    fail.style.aspectRatio = width && height ? `${width} / ${height}` : '16 / 9';
    if (width) fail.style.maxWidth = `${width}px`;
    const title = element('b');
    title.textContent = t('richEditor.imageBroken');
    const hint = element('span');
    hint.textContent = t('richEditor.imageBrokenHint');
    fail.append(title, hint);
    target.replaceWith(fail);
}

function enhanceImage(t: Translate, img: HTMLImageElement): void {
    img.dataset[DONE] = '1';
    img.loading = 'lazy';
    img.decoding = 'async';
    const width = positive(img.getAttribute('width'));
    const height = positive(img.getAttribute('height'));
    if (!UtilRichMedia.safeImageUrl(img.getAttribute('src'))) {
        showImageFailure(t, img, width, height);
        return;
    }
    img.addEventListener('error', () => showImageFailure(t, img, width, height), { once: true });
    // 이미 실패한 채로 그려졌다면(캐시된 깨진 이미지 등) 지금 바꾼다
    if (img.complete && img.naturalWidth === 0) showImageFailure(t, img, width, height);
}

export default class UtilRichBody {
    /**
     * 글 본문 안의 유튜브·이미지를 손질한다. 여러 번 불러도 안전하다.
     * @param {Translate} t - 번역 함수(단추 이름·자리표시 문구).
     * @param {HTMLElement} root - 본문이 들어 있는 요소(`v-html` 로 넣은 뒤).
     */
    static enhance(t: Translate, root: HTMLElement): void {
        for (const el of Array.from(root.querySelectorAll<HTMLElement>('div[data-youtube-id]'))) {
            if (el.dataset[DONE]) continue;
            const id = el.getAttribute('data-youtube-id');
            if (UtilRichMedia.isYoutubeId(id)) el.replaceWith(buildYoutube(t, id));
            else el.remove(); // 형식이 틀린 ID 는 그리지 않는다
        }
        for (const img of Array.from(root.querySelectorAll<HTMLImageElement>('img[data-image-id]'))) {
            if (img.dataset[DONE]) continue;
            enhanceImage(t, img);
        }
    }
}
