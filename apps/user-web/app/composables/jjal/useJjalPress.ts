import type { InjectionKey } from 'vue';
import UtilRichJjal from '#layers/rich-editor/utils/util-rich-jjal';

/**
 * 글·댓글 속 짤 누르기 — 본문 요소(댓글 글자 `EmoticonText` · HTML 댓글 · 글 본문)에 `v-on="jjalPress"` 로 한 번 걸고, 안에 그린 `img.jjal[data-jjal-id]` 를 누르면 짤 메뉴를 연다(이벤트 위임).
 * 데스크톱은 누르기(클릭), 휴대폰(터치)은 길게 누르기(0.5초 — 움직이면 취소). 짧게 톡 누르기는 아무 일도 하지 않는다(스크롤과 겹치지 않게).
 * 내려간 짤(`.jjal-blocked`)은 그림이 아니라 메뉴가 뜨지 않는다(담기·크게 보기·신고 모두 할 것이 없다).
 *
 * 「담기」를 보일지: 서버 `collectable` && 그리는 화면의 문맥(`provideJjalContext` — 글 상세가 19금 게시판이면 false). 문맥이 없으면 서버 값만 본다.
 */
const CONTEXT: InjectionKey<() => boolean> = Symbol('jjal-context');
const LONG_PRESS_MS = 500;
const MOVE_TOLERANCE_PX = 10;
/** 터치 뒤 따라오는 클릭을 무시하는 시간 */
const TOUCH_CLICK_GUARD_MS = 800;

/**
 * 이 화면 아래에서 그린 짤의 「담기」 가능 여부를 정한다 — 글 상세가 게시판을 받은 뒤 부른다(`UtilJjal.collectableIn(board)`).
 * @param collectable - 담기를 보여도 되면 참(읽는 순간에 푼다).
 */
export function provideJjalContext(collectable: () => boolean): void {
    provide(CONTEXT, collectable);
}

export function useJjalPress() {
    const ui = useJjalUi();
    const catalog = useJjalCatalog();
    const context = inject(CONTEXT, null);
    let timer: ReturnType<typeof setTimeout> | null = null;
    let startX = 0;
    let startY = 0;
    let touchAt = 0;

    function targetOf(event: Event): HTMLElement | null {
        const found = (event.target as Element | null)?.closest?.(`img.${UtilRichJjal.CLASS}[${UtilRichJjal.ID_ATTR}]`);
        return found instanceof HTMLElement ? found : null;
    }

    function open(el: HTMLElement, event: Event): void {
        const id = el.getAttribute(UtilRichJjal.ID_ATTR);
        if (!id) return;
        const view = catalog.resolve(id);
        if (!view || view.blocked) return;
        ui.openMenu({ id, collectable: view.collectable && (context ? context() : true) }, el, event);
    }

    function clear(): void {
        if (timer) clearTimeout(timer);
        timer = null;
    }

    return {
        click(event: MouseEvent): void {
            const el = targetOf(event);
            if (!el) return;
            event.preventDefault();
            if (Date.now() - touchAt < TOUCH_CLICK_GUARD_MS) return;
            open(el, event);
        },
        pointerdown(event: PointerEvent): void {
            if (event.pointerType !== 'touch') return;
            const el = targetOf(event);
            if (!el) return;
            touchAt = Date.now();
            startX = event.clientX;
            startY = event.clientY;
            clear();
            timer = setTimeout(() => {
                timer = null;
                touchAt = Date.now();
                open(el, event);
            }, LONG_PRESS_MS);
        },
        pointermove(event: PointerEvent): void {
            if (timer && Math.hypot(event.clientX - startX, event.clientY - startY) > MOVE_TOLERANCE_PX) clear();
        },
        pointerup(): void {
            clear();
        },
        pointercancel(): void {
            clear();
        },
        /** 휴대폰 기본 길게 누르기 메뉴(그림 저장 등)를 막는다 — 짤 위에서만 */
        contextmenu(event: MouseEvent): void {
            if (targetOf(event) && Date.now() - touchAt < TOUCH_CLICK_GUARD_MS * 2) event.preventDefault();
        },
    };
}
