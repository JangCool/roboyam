import type { Translate } from '#layers/i18n/utils/util-i18n';
import { ApiError } from '~/composables/common/useApiClient';
import type { MenuAudience, MenuGradeOption } from '~/composables/menu/api/useMenuApi.types';
import type { AdminMenuEditNode, AdminMenuMoveProblem, AdminMenuProblemCode } from '~/utils/util-admin-menu-config';
import UtilAdminMenu from '~/utils/util-admin-menu';
import UtilAdminMenuConfig from '~/utils/util-admin-menu-config';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';
import type { MenuContext, MenuEditItem, MenuEditNode, MenuTreeProblem } from '~/utils/util-menu';
import UtilMenu from '~/utils/util-menu';

/** 이름 + 선택 영문명 → 지금 언어의 이름(`useLocalizedName().localizedName`) */
type LocalizedName = (name: string, nameEn?: string | null) => string;

/** 메뉴 관리 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('menuConfig.…')` 로 부른다. 키 규칙은 `i18n.md`. */
export default class UtilMenuMessages {
    /**
     * 잡은 오류를 보일 한 줄로 — `ApiError` 면 서버 문구 그대로, 아니면(네트워크 등) 지금 언어의 대체 문구.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류.
     * @returns {string} 한 줄.
     */
    static of(t: Translate, e: unknown): string {
        return e instanceof ApiError ? e.message : UtilAdminAuthMessages.fallback(t, 0, 'NETWORK');
    }

    /** 칸 문제 → 문구(한도를 넣는다) */
    static problem(t: Translate, code: AdminMenuProblemCode): string {
        if (code.startsWith('PATH_')) return t(`menuConfig.problem.${code}`, { max: UtilMenu.LIMITS.pathMax });
        if (code.startsWith('HREF_')) return t(`menuConfig.problem.${code}`, { max: UtilMenu.LIMITS.hrefMax });
        return t(`menuConfig.problem.${code}`, { max: UtilMenu.LIMITS.labelMax, maxEn: UtilMenu.LIMITS.nameEnMax });
    }

    /** 트리 전체 문제 → 문구 */
    static treeProblem(t: Translate, code: MenuTreeProblem): string {
        const L = UtilMenu.LIMITS;
        return t(`menuConfig.treeProblem.${code}`, { nodes: L.nodes, tabs: L.mobileTabs, folder: L.folderDepth, item: L.itemDepth });
    }

    /** 옮기기가 막힌 이유 → 문구 */
    static moveProblem(t: Translate, code: 'NOWHERE' | 'FOLDER_DEPTH' | 'ITEM_DEPTH' | AdminMenuMoveProblem): string {
        const L = UtilMenu.LIMITS;
        return t(`menuConfig.moveProblem.${code}`, { folder: L.folderDepth, item: L.itemDepth });
    }

    /** 위·아래 카운터 — 「노드 12/80 · 모바일 탭 3/10」 */
    static counter(t: Translate, nodes: number, tabs: number | null): string {
        const base = t('menuConfig.counterNodes', { n: nodes, max: UtilMenu.LIMITS.nodes });
        return tabs === null ? base : `${base} · ${t('menuConfig.counterTabs', { n: tabs, max: UtilMenu.LIMITS.mobileTabs })}`;
    }

    /**
     * 보이는 대상 글자 — 「모두」 · 「우수 이상」.
     * @param {MenuGradeOption[]} grades - 등급 목록(이름).
     */
    static audience(t: Translate, audience: MenuAudience, minGrade: string | null, grades: MenuGradeOption[]): string {
        if (audience !== 'GRADE_MIN') return t(`menuConfig.audience.${audience}`);
        const grade = grades.find((g) => g.gradeKey === minGrade);
        return t('menuConfig.audienceGrade', { grade: grade?.label ?? minGrade ?? '?' });
    }

    /**
     * 항목이 따라가는 대상 이름(지금 언어). 따라갈 이름이 없으면 빈 글자.
     * @param {LocalizedName} localizedName - 지금 언어의 이름.
     */
    static targetName(t: Translate, item: MenuEditItem, ctx: MenuContext, localizedName: LocalizedName): string {
        const name = UtilMenu.targetName(item, ctx);
        if (!name) return '';
        return 'labelKey' in name ? t(name.labelKey) : localizedName(name.name, name.nameEn);
    }

    /**
     * 노드의 보일 이름 — 운영자가 적은 이름(+영문명), 비었으면 대상 이름. `following` 이면 대상 이름을 따라가는 중(흐리게 그린다).
     * @param {LocalizedName} localizedName - 지금 언어의 이름.
     * @returns {{ text: string; following: boolean }} 이름.
     */
    static nodeName(t: Translate, node: MenuEditNode, ctx: MenuContext, localizedName: LocalizedName): { text: string; following: boolean } {
        const nameEn = node.nameEn.trim() || null;
        if (node.label.trim()) return { text: localizedName(node.label.trim(), nameEn), following: false };
        const target = node.kind === 'ITEM' ? UtilMenuMessages.targetName(t, node, ctx, localizedName) : '';
        return { text: localizedName(target || t('menuConfig.untitled'), nameEn), following: true };
    }

    /**
     * 관리자 메뉴 노드의 권한 표시 — 권한 코드는 그대로, 최고 관리자 전용 화면의 예약 코드(`UtilAdminMenu.SUPER_ONLY`)는 「최고 관리자만」.
     * @param {Translate} t - 번역 함수.
     * @param {string} code - `UtilAdminMenuConfig.permissionOf` 가 준 코드.
     * @returns {string} 보일 글자.
     */
    static adminPermissionLabel(t: Translate, code: string): string {
        return code === UtilAdminMenu.SUPER_ONLY ? t('menuConfig.superOnly') : code;
    }

    /**
     * 관리자 메뉴 노드의 보일 이름 — 덮어쓴 이름(+영문명), 비었으면 기본 이름(묶음·부모의 키 또는 화면의 이름).
     * @param {LocalizedName} localizedName - 지금 언어의 이름.
     * @returns {{ text: string; following: boolean }} 이름.
     */
    static adminNodeName(t: Translate, node: AdminMenuEditNode, localizedName: LocalizedName): { text: string; following: boolean } {
        const labelEn = node.labelEn.trim() || null;
        if (node.label.trim()) return { text: localizedName(node.label.trim(), labelEn), following: false };
        const key = UtilAdminMenuConfig.defaultLabelKey(node);
        return { text: localizedName(key ? t(key) : t('menuConfig.untitled'), labelEn), following: true };
    }
}
