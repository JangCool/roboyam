import type { GroupManagerInfo, GroupManagerPermission, GroupResolvedPermissions } from '~/composables/group/api/useGroupApi.types';
import { ApiError } from '~/composables/common/useApiClient';

/**
 * 소모임 매니저(3단계)의 순수 로직 — 맡길 일 5개의 순서, 체크 시트가 켜지는 조건, 인원 설정 범위, 권한 칸 → 맡은 일, 서버 오류 가르기.
 * 계약은 community `docs/api-groups.md` 5-1(서버 `GroupManagerPermission`·`GroupAccess` — 5개 키·고정 순서). **판정은 늘 서버다** — 여기 있는 것은 보내기 전에 미리 아는 용도다.
 * 문구는 `util-group-manager-messages.ts` 와 `i18n/locales/{ko,en}/groupManager.ts`. 상태도 요청도 없다(community `UtilGroupManager` 를 옮겼다).
 */

/** 서버 응답 배열이 늘 따르는 고정 순서 — 요약·시트가 모두 이 순서다 */
const ORDER: readonly GroupManagerPermission[] = ['APPLICATIONS', 'KICK', 'RECRUITING', 'EVENTS', 'UNPUBLISH'];

/** 권한 칸 → 맡길 일 키 */
const FLAG_OF: Readonly<Record<GroupManagerPermission, keyof GroupResolvedPermissions>> = {
    APPLICATIONS: 'canHandleApplications',
    KICK: 'canKickMembers',
    RECRUITING: 'canSetRecruiting',
    EVENTS: 'canManageEvents',
    UNPUBLISH: 'canUnpublishPosts',
};

function whole(value: unknown): number | null {
    return typeof value === 'number' && Number.isInteger(value) && value >= 0 ? value : null;
}

export default class UtilGroupManager {
    static readonly ORDER = ORDER;
    /** 인원 상한의 아래 한계 — 매니저를 한 명도 둘 수 없는 상한은 없다 */
    static readonly LIMIT_MIN = 1;

    /* ── 맡길 일 ── */

    /** 모르는 키를 버리고 중복을 없애 고정 순서로 맞춘다 — 서버가 먼저 새 키를 내보내도 깨지지 않는다 */
    static normalize(raw: unknown): GroupManagerPermission[] {
        if (!Array.isArray(raw)) return [];
        return ORDER.filter((key) => raw.includes(key));
    }

    /** 두 맡은 일이 같은가(순서·중복 무관) — 같으면 서버도 기록·알림 없이 `204` 로 넘어간다 */
    static sameSet(a: readonly GroupManagerPermission[], b: readonly GroupManagerPermission[]): boolean {
        const x = UtilGroupManager.normalize(a);
        const y = UtilGroupManager.normalize(b);
        return x.length === y.length && x.every((key, i) => key === y[i]);
    }

    /** 체크 한 줄을 켜거나 끈 새 선택 — 늘 고정 순서 */
    static toggle(selected: readonly GroupManagerPermission[], key: GroupManagerPermission, on: boolean): GroupManagerPermission[] {
        const rest = selected.filter((k) => k !== key);
        return UtilGroupManager.normalize(on ? [...rest, key] : rest);
    }

    /** [임명하기]가 켜지는가 — 하나 이상 골라야 하고 인원이 차 있지 않아야 한다 */
    static canAppoint(selected: readonly GroupManagerPermission[], full: boolean): boolean {
        return selected.length > 0 && !full;
    }

    /** 맡은 일 바꾸기의 [저장]이 켜지는가 — 지금 값과 다르고 1개 이상일 때만 */
    static canSaveChange(selected: readonly GroupManagerPermission[], original: readonly GroupManagerPermission[]): boolean {
        return selected.length > 0 && !UtilGroupManager.sameSet(selected, original);
    }

    /** 마지막 체크를 풀었다 — 「해임해 주세요」 안내를 보인다(체크가 모두 해제된 매니저는 두지 않는다) */
    static clearedAll(selected: readonly GroupManagerPermission[], original: readonly GroupManagerPermission[]): boolean {
        return selected.length === 0 && original.length > 0;
    }

    /* ── 인원 ── */

    /** 상세의 `managerInfo` 를 읽는다 — 칸이 이상하면 null(매니저 줄·거르기를 그리지 않는다) */
    static infoOf(raw: GroupManagerInfo | null | undefined): GroupManagerInfo | null {
        if (!raw) return null;
        const count = whole(raw.count);
        const limit = whole(raw.limit);
        const limitMax = whole(raw.limitMax);
        if (count === null || limit === null || limitMax === null) return null;
        return { count, limit, limitMax };
    }

    /** 인원이 상한에 닿았는가 — 닿으면 새로 임명할 수 없다(맡은 일 바꾸기는 상한을 다시 보지 않는다). 모르면 false(서버가 거절한다) */
    static isFull(info: GroupManagerInfo | null | undefined): boolean {
        return info ? info.count >= info.limit : false;
    }

    /** 인원 설정이 고를 수 있는 범위 — 아래는 지금 매니저 수(최소 1), 위는 설정의 최대 인원 */
    static limitRange(info: GroupManagerInfo): { min: number; max: number } {
        const max = Math.max(UtilGroupManager.LIMIT_MIN, info.limitMax);
        return { min: Math.min(max, Math.max(UtilGroupManager.LIMIT_MIN, info.count)), max };
    }

    /** 인원 숫자를 한 칸 바꾼 값 — 범위 밖으로 나가지 않는다 */
    static stepLimit(value: number, step: 1 | -1, info: GroupManagerInfo): number {
        const { min, max } = UtilGroupManager.limitRange(info);
        return Math.min(max, Math.max(min, value + step));
    }

    /** 인원 숫자가 닿은 쪽 — 하한(지금 매니저 수) · 상한(최대 인원) · 아님 */
    static limitBound(value: number, info: GroupManagerInfo): 'FLOOR' | 'CEILING' | null {
        const { min, max } = UtilGroupManager.limitRange(info);
        if (value <= min && min === info.count && info.count >= UtilGroupManager.LIMIT_MIN) return 'FLOOR';
        if (value >= max) return 'CEILING';
        return null;
    }

    /* ── 권한 읽기 ── */

    /** 내가 맡은 일 — 권한 칸(`can*`)에서 읽어 고정 순서로. 모임장은 다섯 모두, 매니저는 체크된 것 */
    static grantedOf(permissions: GroupResolvedPermissions): GroupManagerPermission[] {
        return ORDER.filter((key) => permissions[FLAG_OF[key]] === true);
    }

    static can(permissions: GroupResolvedPermissions, key: GroupManagerPermission): boolean {
        return permissions[FLAG_OF[key]] === true;
    }

    /* ── 서버 오류 ── */

    /** 체크가 없는 일을 했다(`403 GROUP_MANAGER_PERMISSION_DENIED`) — 열어 둔 채 회수된 경우 포함 */
    static isPermissionDenied(e: unknown): boolean {
        return e instanceof ApiError && e.status === 403 && e.code === 'GROUP_MANAGER_PERMISSION_DENIED';
    }

    /** 모임장도 매니저도 아니거나 모임장 고정 일이다(`403 GROUP_LEADER_ONLY`) — 열어 둔 채 해임당한 경우도 여기다 */
    static isLeaderOnly(e: unknown): boolean {
        return e instanceof ApiError && e.status === 403 && e.code === 'GROUP_LEADER_ONLY';
    }

    /** 매니저끼리·모임장은 강퇴할 수 없다(`403 GROUP_MANAGER_PROTECTED`) */
    static isProtected(e: unknown): boolean {
        return e instanceof ApiError && e.status === 403 && e.code === 'GROUP_MANAGER_PROTECTED';
    }

    /** 임명 인원이 찼다(`409 GROUP_MANAGER_LIMIT_REACHED`) */
    static isLimitReached(e: unknown): boolean {
        return e instanceof ApiError && e.status === 409 && e.code === 'GROUP_MANAGER_LIMIT_REACHED';
    }

    /** 매니저 기능이 꺼져 있다(`403 GROUP_MANAGERS_DISABLED`) — 임명·맡은 일 바꾸기만 막힌다 */
    static isDisabled(e: unknown): boolean {
        return e instanceof ApiError && e.status === 403 && e.code === 'GROUP_MANAGERS_DISABLED';
    }
}
