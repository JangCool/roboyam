import type { Ref } from 'vue';
import type { ScrollRowHandle } from '~/composables/menu/useScrollOverflow.types';

/** 얇은 스크롤 손잡이의 가장 작은 폭(px) — 칸이 아주 많아도 잡을 수 있게 */
const MIN_THUMB_PX = 24;
/** 화살표 단추 한 번에 넘기는 양 — 줄 폭의 80% */
const PAGE_RATIO = 0.8;

/**
 * 가로로 미는 줄(`overflow-x: auto`, 스크롤바는 숨김)이 **넘치는지와 어디까지 밀었는지**를 잰다 — 줄 아래 얇은 스크롤 표시·양끝 화살표 단추(`AppScrollRow`)가 쓴다.
 * 줄의 스크롤·크기 변화(`ResizeObserver` — 줄 자신과 직속 자식, 자식이 바뀌면 `MutationObserver` 로 다시 건다)마다 한 프레임에 한 번 다시 잰다.
 * 상태도 요청도 없다(화면 조각 하나의 계산). 오른쪽에서 왼쪽 글(RTL)은 다루지 않는다(지금 언어는 한국어·영어).
 *
 * @param scroller 옆으로 미는 줄.
 * @param options.edgeInset 키보드 초점 칸이 가장자리에서 이만큼(px) 안쪽에 보이게 민다 — 화살표 단추가 덮는 폭. 없으면 0.
 * @returns `overflowing` 넘치나 · `atStart`/`atEnd` 왼쪽·오른쪽 끝인가 · `thumbWidth`/`thumbLeft` 손잡이 폭·위치(px, 줄 폭 기준) · `dragging` 손잡이를 끄는 중 ·
 *   `page(dir)` 줄 폭의 80% 만큼 부드럽게 넘긴다 · `onThumbPointerDown`/`onTrackPointerDown` 손잡이 끌기·트랙 누르기 · `onFocusIn` 초점 칸을 보이게.
 */
export function useScrollOverflow(scroller: Ref<HTMLElement | null>, options: { edgeInset?: () => number } = {}) {
    const overflowing = ref(false);
    const atStart = ref(true);
    const atEnd = ref(true);
    const thumbWidth = ref(0);
    const thumbLeft = ref(0);
    const dragging = ref(false);

    function measure(): void {
        const box = scroller.value;
        const max = box ? box.scrollWidth - box.clientWidth : 0;
        // 반올림 오차(소수점 폭)로 1px 넘치는 것은 넘침으로 보지 않는다
        if (!box || max <= 1) {
            overflowing.value = false;
            atStart.value = true;
            atEnd.value = true;
            thumbWidth.value = 0;
            thumbLeft.value = 0;
            return;
        }
        const { scrollLeft, scrollWidth, clientWidth } = box;
        overflowing.value = true;
        atStart.value = scrollLeft <= 1;
        atEnd.value = scrollLeft >= max - 1;
        const width = Math.min(clientWidth, Math.max(MIN_THUMB_PX, Math.round((clientWidth * clientWidth) / scrollWidth)));
        thumbWidth.value = width;
        thumbLeft.value = Math.round((clientWidth - width) * Math.min(1, Math.max(0, scrollLeft / max)));
    }

    let frame = 0;
    function schedule(): void {
        if (frame) return;
        frame = requestAnimationFrame(() => {
            frame = 0;
            measure();
        });
    }

    watch(
        scroller,
        (box, _old, onCleanup) => {
            if (!box) {
                measure();
                return;
            }
            box.addEventListener('scroll', schedule, { passive: true });
            const resize = new ResizeObserver(schedule);
            const observeAll = () => {
                resize.disconnect();
                resize.observe(box);
                for (const child of Array.from(box.children)) resize.observe(child);
            };
            observeAll();
            // 메뉴가 바뀌어 칸 묶음이 새로 생기거나 빠지면 다시 건다(칸 수가 바뀌면 묶음 크기가 바뀌어 ResizeObserver 가 잡는다)
            const mutation = new MutationObserver(() => {
                observeAll();
                schedule();
            });
            mutation.observe(box, { childList: true });
            schedule();
            onCleanup(() => {
                box.removeEventListener('scroll', schedule);
                resize.disconnect();
                mutation.disconnect();
            });
        },
        { immediate: true, flush: 'post' },
    );

    onBeforeUnmount(() => {
        if (frame) cancelAnimationFrame(frame);
    });

    /** 움직임 줄이기를 켠 사용자에게는 바로 옮긴다 */
    function behavior(): ScrollBehavior {
        return window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
    }

    /** 줄 폭의 80% 만큼 넘긴다 — `-1` 왼쪽 · `1` 오른쪽 */
    function page(dir: -1 | 1): void {
        const box = scroller.value;
        if (box) box.scrollBy({ left: dir * box.clientWidth * PAGE_RATIO, behavior: behavior() });
    }

    /** 손잡이를 끌어 넘긴다 — 손잡이가 움직인 만큼 줄의 남은 길이 비율로 민다(포인터를 붙잡아 줄 밖으로 나가도 이어진다) */
    function onThumbPointerDown(event: PointerEvent): void {
        const box = scroller.value;
        if (!box || event.button !== 0) return;
        event.preventDefault();
        const handle = event.currentTarget as HTMLElement;
        handle.setPointerCapture(event.pointerId);
        const startX = event.clientX;
        const startLeft = box.scrollLeft;
        const max = box.scrollWidth - box.clientWidth;
        const travel = box.clientWidth - thumbWidth.value;
        dragging.value = true;
        const move = (e: PointerEvent) => {
            if (travel > 0) box.scrollLeft = startLeft + ((e.clientX - startX) * max) / travel;
        };
        const end = () => {
            dragging.value = false;
            handle.removeEventListener('pointermove', move);
            handle.removeEventListener('pointerup', end);
            handle.removeEventListener('pointercancel', end);
        };
        handle.addEventListener('pointermove', move);
        handle.addEventListener('pointerup', end);
        handle.addEventListener('pointercancel', end);
    }

    /** 트랙(손잡이 밖)을 누르면 그쪽으로 한 쪽 넘긴다 */
    function onTrackPointerDown(event: PointerEvent): void {
        const track = event.currentTarget as HTMLElement;
        if (event.button !== 0) return;
        const x = event.clientX - track.getBoundingClientRect().left;
        if (x < thumbLeft.value) page(-1);
        else if (x > thumbLeft.value + thumbWidth.value) page(1);
    }

    /**
     * 키보드로 칸에 초점이 오면 그 칸이 다 보이게 민다 — 브라우저가 이미 줄 안으로 끌어오지만, 화살표 단추가 가장자리를 덮으면(`edgeInset`) 그만큼 더 민다.
     * 페이지의 세로 스크롤은 건드리지 않는다(줄만 민다).
     */
    function onFocusIn(event: FocusEvent): void {
        const box = scroller.value;
        const target = event.target;
        if (!box || !(target instanceof HTMLElement) || target === box || !overflowing.value) return;
        const inset = options.edgeInset?.() ?? 0;
        const outer = box.getBoundingClientRect();
        const rect = target.getBoundingClientRect();
        if (rect.left < outer.left + inset) box.scrollLeft -= outer.left + inset - rect.left;
        else if (rect.right > outer.right - inset) box.scrollLeft += rect.right - (outer.right - inset);
    }

    return { overflowing, atStart, atEnd, thumbWidth, thumbLeft, dragging, page, onThumbPointerDown, onTrackPointerDown, onFocusIn };
}

/**
 * 부모가 `AppScrollRow` 의 스크롤 상자를 쓰기 위한 짝 — 템플릿에 `ref="<row 이름>"` 으로 달고, `scroller` 를 `useCenterActive(scroller, …)` 에 넘긴다.
 * @returns `row` 컴포넌트 ref · `scroller` 그 안의 스크롤 상자(없으면 null).
 */
export function useScrollRowRef() {
    const row = ref<ScrollRowHandle | null>(null);
    const scroller = computed<HTMLElement | null>(() => row.value?.scroller ?? null);
    return { row, scroller };
}
