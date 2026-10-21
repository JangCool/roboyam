import type { Ref, WatchSource } from 'vue';
import UtilMenu from '~/utils/util-menu';

/**
 * 가로로 미는 줄(상단 탭·상단 메뉴)에서 **활성 항목이 화면 밖이면 가운데로 끌어온다**.
 * 활성 항목에 `data-active="true"` 를 붙여 두면 `sources` 가 바뀔 때마다 찾아서 줄(`scroller`)만 민다 —
 * `scrollIntoView` 는 쓰지 않는다(페이지의 세로 스크롤·뒤로 가기 복원을 건드리지 않는다).
 *
 * ⚠ `scroller` 는 `position: relative` 여야 한다 — 항목의 `offsetLeft` 가 그 줄 안의 위치가 되려면 줄이 기준 요소여야 한다.
 *
 * @param scroller 옆으로 미는 줄(`overflow-x: auto`).
 * @param sources 바뀌면 다시 가운데로 끌어올 값들(활성 항목·목록 길이 …).
 * @param options.onMount 처음 나타날 때도 한 번 한다 — 메뉴가 나중에 생기는 곳(메뉴 위치를 바꿔 상단 메뉴가 새로 뜰 때)에서 쓴다.
 */
export function useCenterActive(scroller: Ref<HTMLElement | null>, sources: WatchSource | WatchSource[], options: { onMount?: boolean } = {}): void {
    async function center(): Promise<void> {
        await nextTick();
        const box = scroller.value;
        const active = box?.querySelector<HTMLElement>('[data-active="true"]');
        if (box && active) box.scrollLeft = UtilMenu.centerScrollLeft(active.offsetLeft, active.offsetWidth, box.clientWidth);
    }

    watch(sources, center, { flush: 'post' });
    if (options.onMount) onMounted(() => void center());
}
