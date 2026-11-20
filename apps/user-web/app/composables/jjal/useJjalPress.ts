import type { InjectionKey } from 'vue';
import UtilRichJjal from '#layers/rich-editor/utils/util-rich-jjal';

/**
 * 글·댓글 속 짤 누르기 — 본문 요소(댓글 글자 `EmoticonText` · HTML 댓글 · 글 본문)에 `v-on="jjalPress"` 로 한 번 걸고, 안에 그린 `img.jjal[data-jjal-id]` 를 누르면 짤 메뉴를 연다(이벤트 위임).
 * 데스크톱은 누르기(클릭), 휴대폰(터치)은 길게 누르기(0.5초 — 움직이면 취소). 짧게 톡 누르기는 아무 일도 하지 않는다(스크롤과 겹치지 않게).
 * 내려간 짤(`.jjal-blocked`)은 그림이 아니라 메뉴가 뜨지 않는다(담기·크게 보기·신고 모두 할 것이 없다).
 *
 * 「담기」를 보일지: 그 글 맥락의 서버 `collectable`(`useJjalCatalog.collectableAt`) && 화면 판정(`JjalContext.collectable` — 19금 게시판이면 false).
 * 담기는 짤이 보이는 글·댓글을 함께 보낸다(서버 판정) — 그래서 글 맥락(`provideJjalContext`)이 없는 곳에서는 「담기」를 보이지 않는다.
 */

/** 짤이 그려지는 글 맥락 — 글 상세가 놓고(`provideJjalContext`), 댓글이 자기 ID 를 더한다(`provideJjalCommentContext`) */
export interface JjalContext {
    /** 짤이 보이는 글 ID(글을 아직 못 받았으면 null) */
    postId: () => string | null;
    /** 댓글 안이면 그 댓글 ID(글 본문이면 null) */
    commentId: () => string | null;
    /** 화면 판정 — 담기를 보여도 되면 참(19금 게시판이면 false, `UtilJjal.collectableIn`) */
    collectable: () => boolean;
}

const CONTEXT: InjectionKey<JjalContext> = Symbol('jjal-context');
const LONG_PRESS_MS = 500;
const MOVE_TOLERANCE_PX = 10;
/** 터치 뒤 따라오는 클릭을 무시하는 시간 */
const TOUCH_CLICK_GUARD_MS = 800;

/**
 * 이 화면 아래에서 그린 짤의 글 맥락을 놓는다 — 글 상세가 부른다. 같은 화면의 `useJjalPress` 에는 돌려받은 값을 넘긴다(자기가 놓은 값은 자기가 `inject` 하지 못한다).
 * @param context - 글 ID · 화면의 담기 판정(댓글 ID 는 비운다).
 * @returns 놓은 맥락.
 */
export function provideJjalContext(context: Omit<JjalContext, 'commentId'>): JjalContext {
    const value: JjalContext = { postId: context.postId, commentId: () => null, collectable: context.collectable };
    provide(CONTEXT, value);
    return value;
}

/**
 * 댓글 하나 아래의 맥락 — 위 글 맥락에 그 댓글 ID 를 더해 놓는다(`CommentItem`). 위 맥락이 없으면 댓글의 글 ID 로 채운다(화면 판정은 켬).
 * @param comment - 댓글의 글 ID·댓글 ID(읽는 순간에 푼다).
 * @returns 놓은 맥락(같은 컴포넌트의 `useJjalPress` 에 넘긴다).
 */
export function provideJjalCommentContext(comment: () => { postId: string; id: string }): JjalContext {
    const parent = inject(CONTEXT, null);
    const value: JjalContext = {
        postId: () => parent?.postId() ?? comment().postId,
        commentId: () => comment().id,
        collectable: () => (parent ? parent.collectable() : true),
    };
    provide(CONTEXT, value);
    return value;
}

/** 위에서 놓은 글 맥락(없으면 null) — 짤을 그리는 곳이 `ensure(ids, postId)` 에 쓴다 */
export function useJjalContext(): JjalContext | null {
    return inject(CONTEXT, null);
}

/**
 * @param own - 이 컴포넌트가 놓은 맥락(`provideJjalContext`·`provideJjalCommentContext` 의 반환값). 없으면 위에서 놓은 맥락을 쓴다.
 */
export function useJjalPress(own?: JjalContext | null) {
    const ui = useJjalUi();
    const catalog = useJjalCatalog();
    const context = own ?? inject(CONTEXT, null);
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
        const postId = context?.postId() ?? null;
        const commentId = context?.commentId() ?? null;
        const collectable = Boolean(postId) && catalog.collectableAt(id, postId) && (context ? context.collectable() : false);
        ui.openMenu({ id, collectable, postId, commentId }, el, event);
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
