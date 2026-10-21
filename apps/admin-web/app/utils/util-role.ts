import type { RoleView } from '~/composables/role/api/useRoleApi.types';
import type { RoleAxis, RoleTypeView } from '~/composables/role/api/useRoleTypeApi.types';
import type { Translate } from '#layers/i18n/utils/util-i18n';
import UtilPermissionCatalog, { type PermissionDef } from '~/utils/util-permission-catalog';

/**
 * 역할 화면의 탭 하나. 운영자 역할은 **영역(대분류)** 이 탭이고 그 안에 세부 유형들이 소제목으로 나뉜다. 회원 역할은 유형(등급·회원 유형·게시판 단위·사용자 정의)이 탭이다.
 */
export interface RoleTabDef {
    /** 탭 식별자 — 운영자는 영역 id(`content` …), 회원은 역할 유형 id(`memberGrade` …). 주소 `?tab=` 에 쓴다 */
    id: string;
    axis: RoleAxis;
    /** 운영자 탭이 맡은 영역 id — 회원 탭은 null */
    areaId: string | null;
    /** 이 탭에 든 역할 유형들(운영자: 영역의 세부 유형, 회원: 그 유형 하나) */
    types: RoleTypeView[];
}

/** 역할 화면의 종류 — 운영자 역할 · 회원 역할 · 등급 역할 (메뉴 역할 관리의 하위 항목 셋) */
export type RoleScreenKind = 'member' | 'operator' | 'grade';

/**
 * 역할 화면이 쓰는 계산. 역할 아이디 규칙은 서버 규칙과 같다(hamonica3 `CREATE_ROLE_SCHEMA`: `ROLE_` 로 시작하는 대문자·숫자·밑줄, 30자).
 * 용어: **역할 유형** = 역할이 속한 고정 분류. 운영자 역할은 영역 → 세부 유형 두 단계이고 세부 유형이 곧 역할 유형이며 그 역할이 고를 수 있는 권한 후보를 정한다. 회원 역할은 4종 유형이다.
 */
export default class UtilRole {
    static readonly ID_PREFIX = 'ROLE_';
    static readonly ID_MAX = 30;
    static readonly NAME_MAX = 50;
    static readonly DESC_MAX = 200;

    // ── 역할 유형 ─

    /** 회원 역할 유형의 아이디 — 등급(파생) · 회원 유형 · 게시판 단위 · 사용자 정의. 회원 쪽 화면(`MemberRoleTypeId`)과 같은 값이다 */
    static readonly TYPE_GRADE = 'memberGrade';
    static readonly TYPE_KIND = 'memberKind';
    static readonly TYPE_BOARD = 'memberBoard';
    static readonly TYPE_CUSTOM = 'memberCustom';
    /** 운영자 역할 유형의 아이디는 세부 유형 id 앞에 붙인다(`op_gbOrder`) */
    static readonly OPERATOR_TYPE_PREFIX = 'op_';
    /** 종합 관리 유형 — 유형 제한 없이 권한을 고르고 최고 관리자만 만들고 고친다 */
    static readonly TYPE_GENERAL = 'op_general';

    /**
     * 역할 유형의 표시 이름 — 운영자 쪽은 세부 유형 이름(`permission.type.<id>`), 회원 쪽은 `role.typeBuiltin.<id>`. 유형은 모두 고정이라 번역 키로 보인다.
     * @param {Translate} t - 번역 함수.
     * @param {Pick<RoleTypeView, 'roleTypeId'>} type - 역할 유형.
     * @returns {string} 소제목·태그에 보일 이름.
     */
    static typeLabel(t: Translate, type: Pick<RoleTypeView, 'roleTypeId'>): string {
        const catalogId = UtilRole.catalogTypeOf(type.roleTypeId);
        return catalogId ? t(`permission.type.${catalogId}`) : t(`role.typeBuiltin.${type.roleTypeId}`);
    }

    /**
     * 역할 유형의 경로 이름 — 운영자 역할은 `영역 › 세부 유형`, 회원 역할은 유형 이름 그대로.
     * @param {Translate} t - 번역 함수.
     * @param {Pick<RoleTypeView, 'roleTypeId' | 'area'>} type - 역할 유형.
     * @returns {string} 예: `공동구매 › 주문·배송`.
     */
    static typePathLabel(t: Translate, type: Pick<RoleTypeView, 'roleTypeId' | 'area'>): string {
        return type.area ? `${t(`permission.area.${type.area}`)} › ${UtilRole.typeLabel(t, type)}` : UtilRole.typeLabel(t, type);
    }

    /**
     * 세부 유형의 운영자 역할 유형 아이디.
     * @param {string} catalogTypeId - 권한 카탈로그의 세부 유형 id.
     * @returns {string} `op_<세부 유형 id>`.
     */
    static operatorTypeIdOf(catalogTypeId: string): string {
        return `${UtilRole.OPERATOR_TYPE_PREFIX}${catalogTypeId}`;
    }

    /**
     * 운영자 역할 유형이 가리키는 권한 카탈로그의 세부 유형 id — 회원 역할 유형이면 undefined.
     * @param {string} roleTypeId - 역할 유형 id.
     * @returns {string | undefined} 세부 유형 id.
     */
    static catalogTypeOf(roleTypeId: string): string | undefined {
        return roleTypeId.startsWith(UtilRole.OPERATOR_TYPE_PREFIX) ? roleTypeId.slice(UtilRole.OPERATOR_TYPE_PREFIX.length) : undefined;
    }

    /**
     * 운영자 역할 유형의 역할이 고를 수 있는 권한 후보 — 그 세부 유형의 권한만(종합 관리는 모든 권한). 모르는 유형이면 빈 목록.
     * @param {string} roleTypeId - 운영자 역할 유형 id.
     * @returns {PermissionDef[]} 권한 후보.
     */
    static operatorCandidatesOf(roleTypeId: string): PermissionDef[] {
        const catalogId = UtilRole.catalogTypeOf(roleTypeId);
        return catalogId ? UtilPermissionCatalog.candidatesOfType(catalogId) : [];
    }

    /**
     * 종합 관리 유형의 역할인가 — 최고 관리자만 만들고 고친다(권한 상승 방지).
     * @param {string} roleTypeId - 역할 유형 id.
     * @returns {boolean} 종합 관리 유형이면 true.
     */
    static isGeneralType(roleTypeId: string): boolean {
        return roleTypeId === UtilRole.TYPE_GENERAL;
    }

    // ── 화면 종류 ─

    /** 역할 화면의 종류 — 주소 `/settings/roles/<종류>` 가 정한다(메뉴 역할 관리의 하위 항목과 같다) */
    static readonly KINDS = ['member', 'operator', 'grade'] as const;
    /** 종류가 없거나 모르는 값일 때 가는 종류 */
    static readonly DEFAULT_KIND: RoleScreenKind = 'operator';

    /**
     * 주소의 종류 조각이 아는 종류인가.
     * @param {unknown} value - 라우트 파라미터 값.
     * @returns {RoleScreenKind | null} 아는 종류면 그 값, 아니면 null.
     */
    static kindOf(value: unknown): RoleScreenKind | null {
        return typeof value === 'string' && (UtilRole.KINDS as readonly string[]).includes(value) ? (value as RoleScreenKind) : null;
    }

    // ── 탭 ─

    /**
     * 한 화면 종류에 속한 탭만 — 운영자는 영역 탭들, 등급은 등급 탭 하나, 회원은 그 밖의 회원 탭들(회원 유형 · 게시판 단위 · 사용자 정의).
     * @param {RoleTabDef[]} tabs - `tabsOf` 의 전체 탭.
     * @param {RoleScreenKind} kind - 화면 종류.
     * @returns {RoleTabDef[]} 그 종류의 탭들(차례 유지).
     */
    static tabsOfKind(tabs: RoleTabDef[], kind: RoleScreenKind): RoleTabDef[] {
        if (kind === 'operator') return tabs.filter((tab) => tab.axis === 'OPERATOR');
        return tabs.filter((tab) => tab.axis === 'MEMBER' && (tab.id === UtilRole.TYPE_GRADE) === (kind === 'grade'));
    }

    /**
     * 역할 유형들을 화면의 탭으로 — 운영자 역할은 영역별(영역 차례, 유형이 없는 영역은 뺀다), 회원 역할은 유형마다 하나. 운영자 탭이 먼저 나온다.
     * @param {RoleTypeView[]} types - 서버가 준 역할 유형들(차례대로).
     * @returns {RoleTabDef[]} 탭들.
     */
    static tabsOf(types: RoleTypeView[]): RoleTabDef[] {
        // 탭 차례: 종합이 맨 앞(전체 탭 바로 오른쪽), 나머지는 영역 차례 — 카탈로그의 `AREAS` 차례(다른 화면이 쓴다)는 그대로 둔다
        const generalFirst = [...UtilPermissionCatalog.AREAS].sort((a, b) => Number(b.id === UtilPermissionCatalog.GENERAL_AREA) - Number(a.id === UtilPermissionCatalog.GENERAL_AREA));
        const operatorTabs = generalFirst.map((area): RoleTabDef => ({ id: area.id, axis: 'OPERATOR', areaId: area.id, types: types.filter((type) => type.axis === 'OPERATOR' && type.area === area.id) })).filter((tab) => tab.types.length > 0);
        const memberTabs = types.filter((type) => type.axis === 'MEMBER').map((type): RoleTabDef => ({ id: type.roleTypeId, axis: 'MEMBER', areaId: null, types: [type] }));
        return [...operatorTabs, ...memberTabs];
    }

    /** 「전체」 탭의 식별자 — 주소 `?tab=all`. 탭 줄 맨 앞에 서고, 탭이 없거나 모르는 값일 때의 기본이다 */
    static readonly TAB_ALL = 'all';

    /**
     * 「전체」 탭 — 그 종류의 모든 탭의 역할 유형을 한데 모은 탭. 탭이 둘 이상인 종류(운영자·회원)만 만든다.
     * @param {RoleTabDef[]} tabs - `tabsOfKind` 의 그 종류 탭들.
     * @returns {RoleTabDef | null} 전체 탭, 탭이 둘 미만이면 null(등급).
     */
    static allTabOf(tabs: RoleTabDef[]): RoleTabDef | null {
        const first = tabs[0];
        if (!first || tabs.length < 2) return null;
        return { id: UtilRole.TAB_ALL, axis: first.axis, areaId: null, types: tabs.flatMap((tab) => tab.types) };
    }

    /**
     * 탭의 표시 이름 — 전체 탭은 「전체」, 운영자 탭은 영역 이름, 회원 탭은 유형 이름.
     * @param {Translate} t - 번역 함수.
     * @param {RoleTabDef} tab - 탭.
     * @returns {string} 탭에 보일 글자.
     */
    static tabLabel(t: Translate, tab: Pick<RoleTabDef, 'id' | 'areaId' | 'types'>): string {
        if (tab.id === UtilRole.TAB_ALL) return t('common.all');
        return tab.areaId ? t(`permission.area.${tab.areaId}`) : tab.types[0] ? UtilRole.typeLabel(t, tab.types[0]) : '';
    }

    /**
     * 역할이 속한 탭.
     * @param {RoleTabDef[]} tabs - 탭들.
     * @param {string} roleTypeId - 역할의 유형 id.
     * @returns {RoleTabDef | undefined} 탭, 없으면 undefined.
     */
    static tabOfType(tabs: RoleTabDef[], roleTypeId: string): RoleTabDef | undefined {
        return tabs.find((tab) => tab.types.some((type) => type.roleTypeId === roleTypeId));
    }

    // ── 역할 아이디 ─

    /**
     * 사용자가 쓰는 아이디 뒷부분을 규칙에 맞게 다듬는다 — 소문자는 대문자로, 공백·하이픈은 밑줄로, 그 밖의 글자는 뺀다. `ROLE_` 를 붙여 넣어도 한 번만 남긴다.
     * @param {string} raw - 입력한 글자.
     * @returns {string} 접두어 없는 뒷부분.
     */
    static normalizeSuffix(raw: string): string {
        const upper = raw.toUpperCase().replace(/[\s-]+/g, '_').replace(/[^A-Z0-9_]/g, '');
        return upper.startsWith(UtilRole.ID_PREFIX) ? upper.slice(UtilRole.ID_PREFIX.length) : upper;
    }

    /**
     * 완전한 역할 아이디.
     * @param {string} suffix - 접두어 없는 뒷부분.
     * @returns {string} `ROLE_` + 뒷부분.
     */
    static fullId(suffix: string): string {
        return `${UtilRole.ID_PREFIX}${suffix}`;
    }

    /**
     * 뒷부분이 비었거나 전체가 너무 긴가.
     * @param {string} suffix - 접두어 없는 뒷부분.
     * @returns {'required' | 'tooLong' | null} 문제 종류, 괜찮으면 null.
     */
    static idProblem(suffix: string): 'required' | 'tooLong' | null {
        if (!suffix) return 'required';
        return UtilRole.fullId(suffix).length > UtilRole.ID_MAX ? 'tooLong' : null;
    }

    /**
     * 게시판 단위 역할(매니저·스텝)에 보여 주는 읽기 전용 기본 한도 — community 의 "지킴이" 기본값(하루 글 10 / 댓글 20 / 제재 5)과 게시판당 인원(매니저 3 · 스텝 10).
     * ⚠ 확정이 아니다(문서 미확정 1~4). 게시판 기능을 만들 때 정하고 편집한다 — 지금은 안내용 값이다.
     */
    static readonly BOARD_LIMITS = { postActionsPerDay: 10, commentDeletesPerDay: 20, sanctionsPerDay: 5, managersPerBoard: 3, staffPerBoard: 10 } as const;

    /**
     * 이 역할의 권한 중 지금 운영자가 못 가진 것 — 있으면 그 권한은 이 운영자가 건드릴 수 없다(권한 상승 방지).
     * @param {RoleView} role - 역할.
     * @param {(code: string) => boolean} can - 지금 운영자가 가졌는가.
     * @returns {string[]} 내 권한 밖의 권한 코드들.
     */
    static outsideOf(role: RoleView, can: (code: string) => boolean): string[] {
        return UtilPermissionCatalog.outside(role.permissionCodes, can);
    }
}
