import type { AdminMenuNode } from '~/utils/util-admin-menu';

/**
 * 관리자 메뉴 노드의 이름(지금 언어) — 운영자가 메뉴 구성에서 덮어쓴 이름(`label`·`labelEn`)이 있으면 그것, 없으면 기본 이름(`labelKey` 번역).
 * 영문은 `labelEn` 이 있을 때만 이긴다(`useLocalizedName` 규칙) — 메뉴 편집 화면의 이름 규칙(`UtilMenuMessages.adminNodeName`)과 같다.
 */
export function useAdminMenuName() {
    const { t } = useI18n();
    const { localizedName } = useLocalizedName();

    function nameOf(node: AdminMenuNode): string {
        const base = node.label?.trim() || (node.labelKey ? t(node.labelKey) : '');
        return localizedName(base, node.labelEn?.trim() || null);
    }

    return { nameOf };
}
