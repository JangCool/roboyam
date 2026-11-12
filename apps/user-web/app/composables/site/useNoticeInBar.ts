import type { MenuItemNode } from '~/composables/menu/api/useMenuApi.types';
import UtilMenu from '~/utils/util-menu';

/**
 * 공지 게시판 칸을 상단 메뉴(첫 줄·GNB)에서 뺄지 — 공지 띠가 켜져 공지가 있을 때만 뺀다(사용자 결정 2026-11-12: 공지는 띠의 「공지」 글자·확성기로 들어간다).
 * 띠가 꺼졌거나(설정) 받지 못했으면 메뉴에 그대로 둔다 — 공지로 들어갈 길이 없어지지 않게. 이 브라우저에서 띠를 닫은 것은 보지 않는다(메뉴가 들쭉날쭉하지 않게).
 */
export function useNoticeInBar() {
    const { notice, ensureLoaded } = useSiteNotice();
    void ensureLoaded();
    const barOn = computed(() => (notice.value?.items.length ?? 0) > 0);
    /** 상단 메뉴에서 뺄 칸인가 — 공지 게시판 칸이고 띠가 켜져 있을 때 */
    function hidden(node: Pick<MenuItemNode, 'type' | 'board'>): boolean {
        return barOn.value && UtilMenu.isNoticeBoard(node);
    }
    return { barOn, hidden };
}
