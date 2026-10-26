import type { LocationQuery } from 'vue-router';
import type { PostAuthor } from '~/composables/board/api/usePostApi.types';
import type {
    GroupCategoryBoard,
    GroupCategoryItem,
    GroupChatKind,
    GroupChatLink,
    GroupChatLinksRequest,
    GroupDetail,
    GroupJoinInfo,
    GroupJoinType,
    GroupLimits,
    GroupManageBlockedReason,
    GroupManagerInfo,
    GroupMyState,
    GroupNewcomers,
    GroupPermissions,
    GroupRecruitState,
    GroupResolvedPermissions,
    GroupRole,
    GroupSort,
    GroupUpdateRequest,
    GroupUser,
    GroupCategory,
    GroupJoinReason,
    GroupCreateRequest,
    GroupCreationRules,
    MyGroupRequest,
} from '~/composables/group/api/useGroupApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilGroupManager from '~/utils/util-group-manager';

/**
 * 소모임 화면이 같이 쓰는 순수 함수 — 목록 주소(쿼리)·상세 → 화면 값·가입 단추 상태·정원 범위·채팅 주소 검사·개설/정보 수정/채팅 링크 입력 검사.
 * 상태도 요청도 없다. 문구는 `util-group-messages.ts`(계산이 낀 것) 와 `i18n/locales/{ko,en}/group.ts`.
 * 입력 검사는 미리 알려 주는 용도이고 판정은 서버다(community `UtilGroupCreate`·`UtilGroupManage` 의 규칙을 옮겼다 — 서버 `community.groups.*` 한도와 같다고 가정).
 */

/** 목록 주소가 가리키는 것 — `?category=&sort=&q=` */
export interface GroupListState {
    /** 분류 id — 없으면 '' (전체) */
    category: string;
    sort: GroupSort;
    /** 검색어(공백을 접고 앞뒤를 자른 것). 없으면 '' */
    q: string;
}

/** 정원 범위 — 서버 운영 한도를 읽은 것 */
export interface GroupCapacityRange {
    min: number;
    max: number;
}

/** 상세 중 열려 있는 소모임 — 화면은 이 모양만 그린다(칸이 모두 채워져 있다) */
export interface ActiveGroup {
    id: string;
    name: string;
    description: string;
    category: GroupCategory | null;
    joinType: GroupJoinType;
    capacity: number;
    memberCount: number;
    recruiting: boolean;
    recruitState: GroupRecruitState;
    offline: boolean;
    openedAt: string | null;
    leader: GroupUser | null;
    stockNotice: boolean;
    myState: GroupMyState | null;
    myRole: GroupRole | null;
    join: GroupJoinInfo;
    chatLinks: GroupChatLink[];
    chatLocked: boolean;
    permissions: GroupResolvedPermissions;
    newcomers: GroupNewcomers | null;
    pendingApplicationCount: number | null;
    limits: GroupLimits | null;
    /** 멤버에게만 매니저 수·상한(3단계) — 그 밖·이상한 값이면 null */
    managerInfo: GroupManagerInfo | null;
}

export type GroupDetailKind = 'active' | 'inactive' | 'unknown';

/** 상세 탭 — 글(기본, 2단계) · 멤버 · 채팅(일정은 4단계에서 더한다) */
export type GroupTab = 'posts' | 'members' | 'chat';

/** 가입 영역의 모양 — 단추를 보일지·글자 종류·꺼짐·위 띠 */
export interface GroupJoinAction {
    visible: boolean;
    /** 단추 글자의 종류 — 문구는 `UtilGroupMessages.joinLabel` */
    label: 'JOIN' | 'APPLY' | 'APPLIED' | 'REAPPLY' | 'MEMBER' | 'FULL' | 'CLOSED';
    disabled: boolean;
    banner: 'REJECTED' | 'KICKED' | null;
}

/** 개설 폼 */
export interface GroupCreateForm {
    name: string;
    description: string;
    categoryId: string;
    boardSlug: string;
    joinType: GroupJoinType;
    capacity: string;
    offline: boolean;
    agreeRules: boolean;
    agreeStock: boolean;
    agreeOffline: boolean;
}

export type GroupCreateField = 'name' | 'description' | 'categoryId' | 'boardSlug' | 'joinType' | 'capacity' | 'offline' | 'agreeRules' | 'agreeStock' | 'agreeOffline';

/** 입력 문제의 종류 — 문구는 `UtilGroupMessages.problem` */
export type GroupProblem = 'REQUIRED' | 'LENGTH' | 'FORMAT' | 'RANGE' | 'BELOW_MEMBERS' | 'AGREE';

export type GroupCreateProblems = Partial<Record<GroupCreateField, GroupProblem>>;

/** 정보 수정 폼 */
export interface GroupEditForm {
    name: string;
    description: string;
    joinType: GroupJoinType;
    capacity: string;
    offline: boolean;
}

export type GroupEditField = 'name' | 'description' | 'joinType' | 'capacity' | 'offline';
export type GroupEditProblems = Partial<Record<GroupEditField, GroupProblem>>;

/** 채팅 링크 폼 */
export type GroupChatField = 'kakao' | 'discord';

export interface GroupChatForm {
    kakaoUrl: string;
    kakaoLabel: string;
    discordUrl: string;
    discordLabel: string;
}

export type GroupChatProblem = 'FORMAT' | 'LABEL_LENGTH';
export type GroupChatProblems = Partial<Record<GroupChatField, GroupChatProblem>>;

/** 분류 값의 모양 — 분류 id 는 숫자 문자열. 옛 주소의 게시판 slug 모양도 통과시켜 서버·화면이 「없는 분류」로 답하게 한다 */
const CATEGORY_VALUE = /^[A-Za-z0-9_-]{1,40}$/;
const SORTS: readonly GroupSort[] = ['popular', 'new', 'recruiting', 'mine'];
const TABS: readonly GroupTab[] = ['posts', 'members', 'chat'];

/** 카카오 오픈채팅 주소 — 서버 `GroupChatLinkRules` 와 같다고 가정(community `UtilGroupManage`) */
const KAKAO_URL = /^https:\/\/open\.kakao\.com(\/[^\s?#]*)$/;
const KAKAO_PATH = /^\/o\/[A-Za-z0-9_-]{2,60}$/;
const DISCORD_URL = /^https:\/\/(discord\.gg|discord\.com)(\/[^\s?#]*)$/;
const DISCORD_CODE = /^[A-Za-z0-9-]{2,40}$/;
const WHOLE_NUMBER = /^\d{1,9}$/;

/** 탈퇴한 회원을 가리는 쪽이 넘기는 이름 — 화면 언어의 「탈퇴한 회원」 */
type WithdrawnName = string;

function first(value: LocationQuery[string] | undefined): string {
    const v = Array.isArray(value) ? value[0] : value;
    return typeof v === 'string' ? v : '';
}

function wholeOr(raw: unknown, fallback: number): number {
    return typeof raw === 'number' && Number.isInteger(raw) && raw >= 1 ? raw : fallback;
}

export default class UtilGroup {
    /* ── 한도(서버 `community.groups.*` 와 같다고 가정 — 입력 중 글자 수를 보이는 용도, 판정은 서버) ── */
    static readonly NAME_MIN = 2;
    static readonly NAME_MAX = 20;
    static readonly DESCRIPTION_MAX = 500;
    static readonly MESSAGE_MAX = 100;
    static readonly REASON_MAX = 100;
    static readonly LABEL_MAX = 30;
    static readonly URL_MAX = 200;
    /** 정원 범위의 기본값 — 서버가 값을 주지 않을 때(옛 응답)만 쓴다 */
    static readonly CAPACITY_MIN = 5;
    static readonly CAPACITY_MAX = 200;
    static readonly DEFAULT_CAPACITY = 30;
    /** 검색어 최대 글자 수 — 서버 `q`(1~30자) */
    static readonly Q_MAX = 30;
    static readonly PAGE_SIZE = 20;
    static readonly MEMBER_PAGE_SIZE = 30;
    /** 글 탭 한 쪽 — 서버 `size` 1~50(기본 20) */
    static readonly POST_PAGE_SIZE = 20;
    static readonly SORTS = SORTS;
    static readonly TABS = TABS;
    static readonly DEFAULT_TAB: GroupTab = 'posts';
    static readonly DEFAULT_SORT: GroupSort = 'popular';
    static readonly LIST_PATH = '/groups';
    static readonly MY_PATH = '/groups/me';
    static readonly NEW_PATH = '/groups/new';

    /* ── 주소 ── */

    static detailPath(id: string): string {
        return `${UtilGroup.LIST_PATH}/${encodeURIComponent(id)}`;
    }

    /** 상세의 한 탭 — 기본 탭(글)은 쿼리를 붙이지 않는다 */
    static tabPath(id: string, tab: GroupTab): string {
        return tab === UtilGroup.DEFAULT_TAB ? UtilGroup.detailPath(id) : `${UtilGroup.detailPath(id)}?tab=${tab}`;
    }

    /** 소모임 글쓰기 */
    static writePath(id: string): string {
        return `${UtilGroup.detailPath(id)}/write`;
    }

    static managePath(id: string, sub = ''): string {
        return `${UtilGroup.detailPath(id)}/manage${sub ? `/${sub}` : ''}`;
    }

    static reeditPath(requestId: string): string {
        return `${UtilGroup.NEW_PATH}?from=${encodeURIComponent(requestId)}`;
    }

    /* ── 목록 주소(쿼리) ── */

    /** 검색어를 다듬는다 — 연속 공백을 한 칸으로 접고 앞뒤를 자른다 */
    static cleanQ(raw: string): string {
        return raw.replace(/\s+/g, ' ').trim();
    }

    /** 입력칸 글자 → 검색어(30자를 넘으면 앞 30자) */
    static fromInput(raw: string): string {
        return UtilGroup.cleanQ(raw).slice(0, UtilGroup.Q_MAX).trim();
    }

    /** 주소의 쿼리 → 검증된 값. 이상한 값은 기본값으로 돌린다(분류가 있는 분류인지는 `isKnownCategory` 가 가린다) */
    static parseQuery(query: LocationQuery): GroupListState {
        const category = first(query.category).trim();
        const sort = first(query.sort);
        const q = UtilGroup.cleanQ(first(query.q));
        return {
            category: CATEGORY_VALUE.test(category) ? category : '',
            sort: SORTS.includes(sort as GroupSort) ? (sort as GroupSort) : UtilGroup.DEFAULT_SORT,
            q: q.length <= UtilGroup.Q_MAX ? q : '',
        };
    }

    /** 값 → 주소의 쿼리. 기본값은 뺀다 */
    static buildQuery(value: Partial<GroupListState>): Record<string, string> {
        const out: Record<string, string> = {};
        if (value.category) out.category = value.category;
        if (value.sort && value.sort !== UtilGroup.DEFAULT_SORT) out.sort = value.sort;
        const q = value.q ? UtilGroup.cleanQ(value.q) : '';
        if (q) out.q = q;
        return out;
    }

    static queryKey(state: GroupListState): string {
        return `${state.category}|${state.sort}|${state.q}`;
    }

    /** 비었을 때의 종류 — 검색 · 내 소모임 · 걸러 놓음 · 아무것도 없음 */
    static emptyKind(state: GroupListState): 'search' | 'mine' | 'filtered' | 'none' {
        if (state.q) return 'search';
        if (state.sort === 'mine') return 'mine';
        return state.category || state.sort !== UtilGroup.DEFAULT_SORT ? 'filtered' : 'none';
    }

    /* ── 분류 ── */

    static categoryById(items: readonly GroupCategoryItem[] | null, id: string): GroupCategoryItem | null {
        if (!id) return null;
        return (items ?? []).find((c) => c.id === id) ?? null;
    }

    static isKnownCategory(items: readonly GroupCategoryItem[], id: string): boolean {
        return !id || items.some((c) => c.id === id);
    }

    /** 분류·게시판 선택을 분류 목록에 맞춘다 — 게시판은 그 분류의 켜진 게시판이어야 하고(없으면 첫 게시판), 분류가 없어졌으면 비운다 */
    static resolveSelection(items: readonly GroupCategoryItem[], categoryId: string, boardSlug: string): { categoryId: string; boardSlug: string } {
        const category = UtilGroup.categoryById(items, categoryId);
        if (!category) return { categoryId: '', boardSlug: '' };
        if (category.boards.some((b) => b.slug === boardSlug)) return { categoryId, boardSlug };
        return { categoryId, boardSlug: category.boards[0]?.slug ?? '' };
    }

    static boardOf(items: readonly GroupCategoryItem[], categoryId: string, boardSlug: string): GroupCategoryBoard | null {
        return UtilGroup.categoryById(items, categoryId)?.boards.find((b) => b.slug === boardSlug) ?? null;
    }

    /** 분류에 켜진 게시판이 둘 이상이면 게시판을 고르게 한다(하나면 자동) */
    static needsBoardChoice(category: GroupCategoryItem | null): boolean {
        return (category?.boards.length ?? 0) >= 2;
    }

    /* ── 숫자 ── */

    /** 지금 적용되는 정원 범위 — 규칙 응답(`capacityMin`·`capacityMax`) 또는 상세의 `limits`. 이상하면 기본 5~200 */
    static capacityRange(source: { capacityMin?: unknown; capacityMax?: unknown } | null | undefined): GroupCapacityRange {
        const min = wholeOr(source?.capacityMin, UtilGroup.CAPACITY_MIN);
        const max = wholeOr(source?.capacityMax, UtilGroup.CAPACITY_MAX);
        return min <= max ? { min, max } : { min: UtilGroup.CAPACITY_MIN, max: UtilGroup.CAPACITY_MAX };
    }

    /** 글자 수(서로게이트 쌍은 한 글자) */
    static length(text: string): number {
        return Array.from(text).length;
    }

    static trimmedLength(text: string): number {
        return UtilGroup.length(text.trim());
    }

    /** 이름 다듬기 — 앞뒤를 자르고 공백을 한 칸으로 */
    static normalizeName(raw: string): string {
        return raw.trim().replace(/\s+/g, ' ');
    }

    /** 숫자 입력칸 — 숫자만 3자리까지 */
    static digits(value: string): string {
        return value.replace(/\D/g, '').slice(0, 3);
    }

    static parseCapacity(text: string): number | null {
        const t = text.trim();
        return WHOLE_NUMBER.test(t) ? Number(t) : null;
    }

    /** 소모임 이름의 첫 글자 — 이름 앞 동그라미 */
    static initial(name: string): string {
        return Array.from(name.trim())[0] ?? '?';
    }

    /* ── 사람 ── */

    /** 소모임의 사람 → 글쓴이 표시(`PostAuthorLabel`) 모양. 탈퇴한 회원은 링크 없이 「탈퇴한 회원」 */
    static authorOf(user: GroupUser, withdrawnName: WithdrawnName): PostAuthor {
        const withdrawn = user.withdrawn || !user.id;
        return {
            kind: 'MEMBER',
            memberId: withdrawn ? null : user.id,
            name: withdrawn ? withdrawnName : user.nickname,
            grade: withdrawn ? null : user.grade,
            gradeLabel: withdrawn ? null : user.gradeLabel,
            role: user.role ?? 'USER',
            ipMasked: null,
            memo: withdrawn ? null : (user.memo ?? null),
            decoration: withdrawn ? null : (user.decoration ?? null),
            avatarUrl: withdrawn ? null : (user.avatarUrl ?? null),
        };
    }

    /* ── 상세 ── */

    /** 권한 칸을 모두 채운다 — 체크형 칸이 없는 응답(매니저 전 서버)은 `canManage`(= 모임장)로 채운다 */
    static permissionsOf(raw: GroupPermissions | null | undefined): GroupResolvedPermissions {
        const manage = raw?.canManage === true;
        const pick = (value: boolean | undefined) => (typeof value === 'boolean' ? value : manage);
        return {
            canManage: manage,
            canWritePost: raw?.canWritePost === true,
            canEditGroup: pick(raw?.canEditGroup),
            canAppointManager: raw?.canAppointManager === true,
            canHandleApplications: pick(raw?.canHandleApplications),
            canKickMembers: pick(raw?.canKickMembers),
            canSetRecruiting: pick(raw?.canSetRecruiting),
            canManageEvents: pick(raw?.canManageEvents),
            canUnpublishPosts: pick(raw?.canUnpublishPosts),
            manageBlockedReason: UtilGroup.manageBlockedOf(raw?.manageBlockedReason),
        };
    }

    /** 모임장·매니저의 쓰기를 막는 까닭으로 읽는다 — 모르는 값은 null(막지 않는다 — 판정은 서버) */
    static manageBlockedOf(value: unknown): GroupManageBlockedReason | null {
        return value === 'EMAIL_NOT_VERIFIED' || value === 'ACCOUNT_RESTRICTED' || value === 'BUSINESS_ACCOUNT_RESTRICTED' ? value : null;
    }

    static kindOf(detail: GroupDetail): GroupDetailKind {
        if (detail.status === 'INACTIVE') return 'inactive';
        return UtilGroup.active(detail) ? 'active' : 'unknown';
    }

    /** 열린 소모임의 상세 → 화면 값. 칸이 비어 있으면 null(화면은 「없는 소모임」으로 본다) */
    static active(detail: GroupDetail): ActiveGroup | null {
        if (detail.status !== 'ACTIVE') return null;
        const { joinType, capacity, memberCount, recruitState } = detail;
        if (!joinType || capacity == null || memberCount == null || !recruitState) return null;
        const myState = detail.myState ?? null;
        return {
            id: detail.id,
            name: detail.name,
            description: detail.description ?? '',
            category: detail.category ?? null,
            joinType,
            capacity,
            memberCount,
            recruiting: detail.recruiting ?? recruitState !== 'CLOSED',
            recruitState,
            offline: detail.offline ?? false,
            openedAt: detail.openedAt ?? null,
            leader: detail.leader ?? null,
            stockNotice: detail.stockNotice ?? false,
            myState,
            myRole: detail.myRole ?? (myState === 'LEADER' ? 'LEADER' : myState === 'MEMBER' ? 'MEMBER' : null),
            join: detail.join ?? { allowed: false, method: 'JOIN', reason: null, retryAfterSeconds: null },
            chatLinks: detail.chat?.links ?? [],
            chatLocked: detail.chatLocked ?? !UtilGroup.isMember(myState),
            permissions: UtilGroup.permissionsOf(detail.permissions),
            newcomers: detail.newcomers ?? null,
            pendingApplicationCount: typeof detail.pendingApplicationCount === 'number' ? detail.pendingApplicationCount : null,
            limits: detail.limits ?? null,
            managerInfo: UtilGroupManager.infoOf(detail.managerInfo),
        };
    }

    static isMember(state: GroupMyState | null | undefined): boolean {
        return state === 'MEMBER' || state === 'LEADER';
    }

    static parseTab(value: unknown): GroupTab {
        const raw = Array.isArray(value) ? value[0] : value;
        return TABS.find((t) => t === raw) ?? UtilGroup.DEFAULT_TAB;
    }

    static tabQuery(tab: GroupTab): Record<string, string> {
        return tab === UtilGroup.DEFAULT_TAB ? {} : { tab };
    }

    /** 가입 영역 — 멤버는 없다. 서버가 준 `join` 으로 단추 상태를 정한다(요청해 보기 전에). 비회원은 눌러서 로그인 안내 */
    static joinAction(myState: GroupMyState | null, join: GroupJoinInfo): GroupJoinAction {
        if (UtilGroup.isMember(myState)) return { visible: false, label: 'MEMBER', disabled: true, banner: null };
        if (myState === 'APPLIED') return { visible: true, label: 'APPLIED', disabled: true, banner: null };
        if (myState === 'REJECTED') return { visible: true, label: 'REAPPLY', disabled: !join.allowed, banner: 'REJECTED' };
        const base: GroupJoinAction['label'] = join.method === 'APPLY' ? 'APPLY' : 'JOIN';
        const banner = myState === 'KICKED' ? 'KICKED' : null;
        if (join.allowed || join.reason === 'LOGIN_REQUIRED') return { visible: true, label: base, disabled: false, banner };
        const label: GroupJoinAction['label'] =
            join.reason === 'ALREADY_MEMBER' ? 'MEMBER' : join.reason === 'ALREADY_APPLIED' ? 'APPLIED' : join.reason === 'FULL' ? 'FULL' : join.reason === 'CLOSED' ? 'CLOSED' : base;
        return { visible: true, label, disabled: true, banner };
    }

    /** 가입·신청 오류 중 화면이 낡았다는 뜻 — 상세를 다시 받는다 */
    static isStaleJoin(e: unknown): boolean {
        return (
            e instanceof ApiError &&
            ['GROUP_FULL', 'GROUP_CLOSED', 'GROUP_ALREADY_MEMBER', 'GROUP_ALREADY_APPLIED', 'GROUP_REAPPLY_LATER', 'GROUP_INACTIVE', 'GROUP_NOT_FOUND'].includes(e.code)
        );
    }

    static joinReasonIsRestricted(reason: GroupJoinReason | null): boolean {
        return reason === 'ACCOUNT_RESTRICTED' || reason === 'BUSINESS_ACCOUNT_RESTRICTED';
    }

    /* ── 채팅 링크 ── */

    /** 서버가 준 채팅 주소를 열기 전에 한 번 더 거른다 — https·허용 호스트만(사용자 정보·포트가 든 주소는 버린다) */
    static safeChatUrl(kind: GroupChatKind, url: string): string | null {
        let parsed: URL;
        try {
            parsed = new URL(url);
        } catch {
            return null;
        }
        if (parsed.protocol !== 'https:' || parsed.username || parsed.password || parsed.port) return null;
        const host = parsed.hostname.toLowerCase();
        if (kind === 'KAKAO') return host === 'open.kakao.com' ? parsed.href : null;
        if (host === 'discord.gg') return parsed.href;
        return host === 'discord.com' && parsed.pathname.startsWith('/invite/') ? parsed.href : null;
    }

    /** 열 수 있는 링크만 */
    static openableChats(links: readonly GroupChatLink[]): GroupChatLink[] {
        const out: GroupChatLink[] = [];
        for (const link of links) {
            const url = UtilGroup.safeChatUrl(link.kind, link.url);
            if (url) out.push({ kind: link.kind, url, label: (link.label ?? '').trim() || null });
        }
        return out;
    }

    static chatLinkOf(links: readonly GroupChatLink[], field: GroupChatField): GroupChatLink | null {
        const kind: GroupChatKind = field === 'kakao' ? 'KAKAO' : 'DISCORD';
        return links.find((link) => link.kind === kind) ?? null;
    }

    static chatFormOf(links: readonly GroupChatLink[]): GroupChatForm {
        const kakao = UtilGroup.chatLinkOf(links, 'kakao');
        const discord = UtilGroup.chatLinkOf(links, 'discord');
        return { kakaoUrl: kakao?.url ?? '', kakaoLabel: kakao?.label ?? '', discordUrl: discord?.url ?? '', discordLabel: discord?.label ?? '' };
    }

    static isKakaoUrl(raw: string): boolean {
        const text = raw.trim();
        if (text.length > UtilGroup.URL_MAX) return false;
        const match = KAKAO_URL.exec(text);
        return match !== null && KAKAO_PATH.test(match[1] ?? '');
    }

    static isDiscordUrl(raw: string): boolean {
        const text = raw.trim();
        if (text.length > UtilGroup.URL_MAX) return false;
        const match = DISCORD_URL.exec(text);
        if (!match) return false;
        const path = match[2] ?? '';
        if (match[1] === 'discord.gg') return DISCORD_CODE.test(path.slice(1));
        return path.startsWith('/invite/') && DISCORD_CODE.test(path.slice('/invite/'.length));
    }

    static chatProblems(form: GroupChatForm): GroupChatProblems {
        const problems: GroupChatProblems = {};
        if (form.kakaoUrl.trim()) {
            if (!UtilGroup.isKakaoUrl(form.kakaoUrl)) problems.kakao = 'FORMAT';
            else if (UtilGroup.trimmedLength(form.kakaoLabel) > UtilGroup.LABEL_MAX) problems.kakao = 'LABEL_LENGTH';
        }
        if (form.discordUrl.trim()) {
            if (!UtilGroup.isDiscordUrl(form.discordUrl)) problems.discord = 'FORMAT';
            else if (UtilGroup.trimmedLength(form.discordLabel) > UtilGroup.LABEL_MAX) problems.discord = 'LABEL_LENGTH';
        }
        return problems;
    }

    static isChatChanged(form: GroupChatForm, links: readonly GroupChatLink[]): boolean {
        const saved = UtilGroup.chatFormOf(links);
        return (
            form.kakaoUrl.trim() !== saved.kakaoUrl.trim() ||
            form.kakaoLabel.trim() !== saved.kakaoLabel.trim() ||
            form.discordUrl.trim() !== saved.discordUrl.trim() ||
            form.discordLabel.trim() !== saved.discordLabel.trim()
        );
    }

    /** 저장하면 지워지는 링크 — 저장된 것이 있는데 주소를 비웠다 */
    static chatClears(form: GroupChatForm, links: readonly GroupChatLink[]): GroupChatField[] {
        const out: GroupChatField[] = [];
        if (UtilGroup.chatLinkOf(links, 'kakao') && !form.kakaoUrl.trim()) out.push('kakao');
        if (UtilGroup.chatLinkOf(links, 'discord') && !form.discordUrl.trim()) out.push('discord');
        return out;
    }

    static toChatRequest(form: GroupChatForm): GroupChatLinksRequest {
        const link = (url: string, label: string) => {
            const u = url.trim();
            if (!u) return null;
            const l = label.trim();
            return l ? { url: u, label: l } : { url: u };
        };
        return { kakao: link(form.kakaoUrl, form.kakaoLabel), discord: link(form.discordUrl, form.discordLabel) };
    }

    /** 저장은 204 라 보낸 값으로 화면의 링크를 맞춘다 */
    static chatLinksOf(request: GroupChatLinksRequest): GroupChatLink[] {
        const links: GroupChatLink[] = [];
        if (request.kakao) links.push({ kind: 'KAKAO', url: request.kakao.url, label: request.kakao.label ?? null });
        if (request.discord) links.push({ kind: 'DISCORD', url: request.discord.url, label: request.discord.label ?? null });
        return links;
    }

    /* ── 개설 신청 ── */

    static emptyCreateForm(): GroupCreateForm {
        return {
            name: '',
            description: '',
            categoryId: '',
            boardSlug: '',
            joinType: 'OPEN',
            capacity: String(UtilGroup.DEFAULT_CAPACITY),
            offline: false,
            agreeRules: false,
            agreeStock: false,
            agreeOffline: false,
        };
    }

    /** 반려된 신청으로 폼을 채운다 — 동의 칸은 다시 확인해야 하니 비운다 */
    static createFormOf(request: MyGroupRequest): GroupCreateForm {
        return {
            ...UtilGroup.emptyCreateForm(),
            name: request.name,
            description: request.description,
            categoryId: request.category?.id ?? '',
            boardSlug: request.board?.slug ?? '',
            joinType: request.joinType,
            capacity: String(request.capacity),
            offline: request.offline,
        };
    }

    /** 칸 검사 — 칸마다 문제 하나(위에서 처음 걸린 것) */
    static createProblems(form: GroupCreateForm, isStock: boolean, range: GroupCapacityRange): GroupCreateProblems {
        const problems: GroupCreateProblems = {};
        const name = UtilGroup.trimmedLength(form.name);
        if (name === 0) problems.name = 'REQUIRED';
        else if (name < UtilGroup.NAME_MIN || name > UtilGroup.NAME_MAX) problems.name = 'LENGTH';
        const description = UtilGroup.trimmedLength(form.description);
        if (description === 0) problems.description = 'REQUIRED';
        else if (description > UtilGroup.DESCRIPTION_MAX) problems.description = 'LENGTH';
        if (!form.categoryId) problems.categoryId = 'REQUIRED';
        else if (!form.boardSlug) problems.boardSlug = 'REQUIRED';
        if (isStock && !form.agreeStock) problems.agreeStock = 'AGREE';
        if (form.capacity.trim() === '') problems.capacity = 'REQUIRED';
        else {
            const n = UtilGroup.parseCapacity(form.capacity);
            if (n === null) problems.capacity = 'FORMAT';
            else if (n < range.min || n > range.max) problems.capacity = 'RANGE';
        }
        if (form.offline && !form.agreeOffline) problems.agreeOffline = 'AGREE';
        if (!form.agreeRules) problems.agreeRules = 'AGREE';
        return problems;
    }

    /** 포커스를 옮길 첫 칸 — 화면 위에서 아래 차례 */
    static firstField<F extends string>(problems: Partial<Record<F, unknown>>, order: readonly F[]): F | null {
        return order.find((f) => Boolean(problems[f])) ?? null;
    }

    static readonly CREATE_ORDER: readonly GroupCreateField[] = ['name', 'description', 'categoryId', 'boardSlug', 'agreeStock', 'joinType', 'capacity', 'offline', 'agreeOffline', 'agreeRules'];

    static toCreateRequest(form: GroupCreateForm, isStock: boolean): GroupCreateRequest {
        return {
            name: UtilGroup.normalizeName(form.name),
            description: form.description.trim(),
            categoryId: form.categoryId,
            boardSlug: form.boardSlug,
            joinType: form.joinType,
            capacity: UtilGroup.parseCapacity(form.capacity) ?? 0,
            offline: form.offline,
            agreeRules: form.agreeRules,
            agreeStock: isStock && form.agreeStock,
            agreeOffline: form.offline && form.agreeOffline,
        };
    }

    /** 규칙 응답을 읽는다 — 모양이 틀리면 null(안내만 빠진다) */
    static normalizeRules(raw: unknown): GroupCreationRules | null {
        if (typeof raw !== 'object' || raw === null || Array.isArray(raw)) return null;
        const r = raw as Record<string, unknown>;
        const mode = r.creationMode === 'OPEN' || r.creationMode === 'APPROVAL' ? r.creationMode : null;
        if (!mode || typeof r.minGradeLabel !== 'string' || typeof r.maxPerOwner !== 'number') return null;
        let me: GroupCreationRules['me'] = null;
        if (typeof r.me === 'object' && r.me !== null && !Array.isArray(r.me)) {
            const m = r.me as Record<string, unknown>;
            const reason = m.reason === 'NOT_PERMITTED' || m.reason === 'GRADE_TOO_LOW' || m.reason === 'LIMIT_REACHED' ? m.reason : null;
            me = { canCreate: m.canCreate !== false && reason === null, reason, grade: typeof m.grade === 'string' ? m.grade : '', ledCount: typeof m.ledCount === 'number' ? m.ledCount : 0 };
        }
        return {
            creationMode: mode,
            minGrade: typeof r.minGrade === 'string' ? r.minGrade : '',
            minGradeLabel: r.minGradeLabel,
            maxPerOwner: r.maxPerOwner,
            ...(typeof r.capacityMin === 'number' ? { capacityMin: r.capacityMin } : {}),
            ...(typeof r.capacityMax === 'number' ? { capacityMax: r.capacityMax } : {}),
            me,
        };
    }

    /* ── 정보 수정 ── */

    static readonly EDIT_FIELDS: readonly GroupEditField[] = ['name', 'description', 'joinType', 'capacity', 'offline'];

    static editFormOf(group: Pick<ActiveGroup, 'name' | 'description' | 'joinType' | 'capacity' | 'offline'>): GroupEditForm {
        return { name: group.name, description: group.description, joinType: group.joinType, capacity: String(group.capacity), offline: group.offline };
    }

    static editProblems(form: GroupEditForm, memberCount: number, range: GroupCapacityRange): GroupEditProblems {
        const problems: GroupEditProblems = {};
        const name = UtilGroup.length(UtilGroup.normalizeName(form.name));
        if (name === 0) problems.name = 'REQUIRED';
        else if (name < UtilGroup.NAME_MIN || name > UtilGroup.NAME_MAX) problems.name = 'LENGTH';
        const description = UtilGroup.trimmedLength(form.description);
        if (description === 0) problems.description = 'REQUIRED';
        else if (description > UtilGroup.DESCRIPTION_MAX) problems.description = 'LENGTH';
        if (form.capacity.trim() === '') problems.capacity = 'REQUIRED';
        else {
            const capacity = UtilGroup.parseCapacity(form.capacity);
            if (capacity === null) problems.capacity = 'FORMAT';
            else if (capacity < range.min || capacity > range.max) problems.capacity = 'RANGE';
            else if (capacity < memberCount) problems.capacity = 'BELOW_MEMBERS';
        }
        return problems;
    }

    static isEditChanged(form: GroupEditForm, group: Pick<ActiveGroup, 'name' | 'description' | 'joinType' | 'capacity' | 'offline'>): boolean {
        return (
            UtilGroup.normalizeName(form.name) !== group.name ||
            form.description.trim() !== group.description.trim() ||
            form.joinType !== group.joinType ||
            UtilGroup.parseCapacity(form.capacity) !== group.capacity ||
            form.offline !== group.offline
        );
    }

    static toUpdateRequest(form: GroupEditForm): GroupUpdateRequest | null {
        const capacity = UtilGroup.parseCapacity(form.capacity);
        if (capacity === null) return null;
        return { name: UtilGroup.normalizeName(form.name), description: form.description.trim(), joinType: form.joinType, capacity, offline: form.offline };
    }

    /* ── 오류 ── */

    /** 서버 칸 오류 중 이 폼의 칸만 */
    static serverFields<F extends string>(e: unknown, fields: readonly F[]): Partial<Record<F, string>> {
        const out: Partial<Record<F, string>> = {};
        if (!(e instanceof ApiError) || !e.fields) return out;
        for (const field of fields) {
            const message = e.field(field);
            if (message) out[field] = message;
        }
        return out;
    }

    /** 모임장·매니저의 쓰기가 막힌 까닭(공통 403 — 이메일 미인증·정지·사업자 계정) — 아니면 null */
    static blockedReasonOf(e: unknown): GroupManageBlockedReason | null {
        if (!(e instanceof ApiError) || e.status !== 403) return null;
        return UtilGroup.manageBlockedOf(e.code);
    }

    static isCode(e: unknown, code: string): boolean {
        return e instanceof ApiError && e.code === code;
    }

    static isSessionLost(e: unknown): boolean {
        return e instanceof ApiError && e.status === 401;
    }

    static isAborted(e: unknown): boolean {
        return e instanceof ApiError && e.code === 'ABORTED';
    }

    /** 해산 확인 — 이름을 그대로 입력했는가 */
    static dissolveReady(typed: string, name: string): boolean {
        return name !== '' && UtilGroup.normalizeName(typed) === name;
    }
}
