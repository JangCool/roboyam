import type { MenuItemRow } from '~/utils/util-menu';

/**
 * 메뉴 설명 — 지금 있는 곳이 최상위 항목(인기글·최신글·공지 …, 홈 제외)이면 그 항목의 한 줄 설명(사용자 결정 2026-10-28).
 * 로보얌 메뉴 둘째 줄(데스크톱 `DaumMenuBar` 의 `inlineGroups`, 모바일 `AppMobileMenuRows`)이 주제 대신 이것을 보인다. 홈이나 그룹 안에서는 null(주제·그룹 칸).
 *
 * 설명 고르는 차례: 운영자가 메뉴 구성에 적은 `description` → 게시판 항목이면 게시판 소개 → 앱 화면 기본 문구(인기글·최신글) → 게시판 이름으로 만든 기본 문구.
 * 컴포저블은 setup 에서 부른다.
 *
 * @returns `description` `{ key, title, text }` 또는 null.
 */
export function useMenuDescription() {
    const { t } = useI18n();
    const { bar } = useMenuItems();
    const { bySlug } = useBoards();
    const { localizedName } = useLocalizedName();

    /** 지금 있는 곳인 최상위 항목(그룹 밖) */
    const activeTop = computed<MenuItemRow | null>(() => {
        for (const section of bar.value) {
            if (section.title !== null) continue;
            for (const entry of section.entries) {
                if (entry.kind === 'item' && entry.row.active) return entry.row;
            }
        }
        return null;
    });

    const description = computed<{ key: string; title: string; text: string } | null>(() => {
        const row = activeTop.value;
        if (!row || row.node.key === 'HOME') return null;
        const node = row.node;
        const title = localizedName(node.label, node.nameEn);
        const boardText = node.board ? bySlug(node.board.slug)?.description?.trim() : null;
        const builtin = node.key === 'POPULAR' ? t('menu.descPopular') : node.key === 'LATEST' ? t('menu.descLatest') : null;
        const text = node.description || boardText || builtin || t('menu.descBoard', { name: title });
        return { key: row.key, title, text };
    });

    return { description };
}
