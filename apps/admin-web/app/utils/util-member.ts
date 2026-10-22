import UtilTime from '#layers/display-time/utils/util-time';
import type {
    MemberActivity,
    MemberAuthMethod,
    MemberAutoPromotion,
    MemberBoardRole,
    MemberConsentAction,
    MemberConsentItem,
    MemberGradeChangeKind,
    MemberGradeRequirement,
    MemberNotificationKind,
    MemberOptionalConsentKey,
    MemberOrderStatus,
    MemberSanction,
    MemberSocialProvider,
    MemberGrade,
    MemberQuery,
    MemberRoleRef,
    MemberRoleTypeId,
    MemberSanctionKind,
    MemberSanctionStatus,
    MemberSearchField,
    MemberSortField,
    MemberStatus,
    MemberStatusChangeKind,
    MemberType,
} from '~/composables/member/api/useMemberApi.types';

/** 상세 검색 조건의 한 줄 — 「검색편집」이 어느 줄을 화면에 보일지 고른다 */
export type MemberDetailRow = 'lastLogin' | 'grade' | 'memberType' | 'emailVerified' | 'authMethod' | 'dormant' | 'statusChanged' | 'point' | 'boardRole' | 'sanction';

/** 표의 칸 키 */
export type MemberColumnKey = 'memberNo' | 'email' | 'nickname' | 'grade' | 'memberType' | 'status' | 'authMethod' | 'emailVerifiedAt' | 'joinedAt' | 'lastLoginAt' | 'point' | 'roles' | 'dormantAt' | 'statusChangedAt' | 'boardRole' | 'sanctionCount';

export interface MemberColumnDef {
    key: MemberColumnKey;
    /** 칸 이름의 i18n 키 */
    labelKey: string;
    /** 필수 칸 — 끌 수 없다 */
    required: boolean;
    /** 처음 보이는가 */
    defaultShown: boolean;
    /** 서버 정렬 키. 없으면 정렬하지 않는다 */
    sort?: MemberSortField;
}

/**
 * 조회 조건 화면 값 — 「검색」을 누를 때만 서버로 간다. 빈 값('' · [] · null)은 "거르지 않음"이다.
 * 날짜는 달력 글자(`YYYY-MM-DD`)로 들고 있다가 보낼 때 사이트 시간대 경계(순간)로 바꾼다(`UtilMember.toQuery`).
 * 라디오 값의 ''는 「전체」다.
 */
export interface MemberFilter {
    searchField: MemberSearchField;
    keyword: string;
    /** 비면 전체 */
    statuses: MemberStatus[];
    /** 비면 전체 */
    roleIds: string[];
    joinedFrom: string;
    joinedTo: string;
    lastLoginFrom: string;
    lastLoginTo: string;
    noLoginHistory: boolean;
    grades: MemberGrade[];
    memberType: MemberType | '';
    emailVerified: 'DONE' | 'NOT' | '';
    authMethods: MemberAuthMethod[];
    dormantFrom: string;
    dormantTo: string;
    statusChangedKind: MemberStatusChangeKind | '';
    statusChangedFrom: string;
    statusChangedTo: string;
    pointMin: number | null;
    pointMax: number | null;
    boardRole: MemberBoardRole | '';
    hasSanction: 'YES' | 'NO' | '';
}

export type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

/**
 * 회원 조회 화면이 쓰는 계산. 판정(마스킹·권한·검색 규칙)은 서버 몫이고 여기는 보여 주기 위한 값만 만든다.
 */
export default class UtilMember {
    static readonly SEARCH_FIELDS: readonly MemberSearchField[] = ['ALL', 'EMAIL', 'NICKNAME', 'MEMBER_NO'];
    static readonly STATUSES: readonly MemberStatus[] = ['ACTIVE', 'SUSPENDED', 'BANNED', 'WITHDRAWN', 'DORMANT'];
    static readonly TYPES: readonly MemberType[] = ['GENERAL', 'BUSINESS'];
    static readonly AUTH_METHODS: readonly MemberAuthMethod[] = ['EMAIL', 'KAKAO', 'NAVER', 'GOOGLE'];
    static readonly BOARD_ROLES: readonly MemberBoardRole[] = ['MANAGER', 'STAFF', 'NONE'];
    static readonly STATUS_CHANGE_KINDS: readonly MemberStatusChangeKind[] = ['SUSPENDED', 'WITHDRAWN', 'DORMANT'];

    /** 상세 검색 줄의 차례 */
    static readonly DETAIL_ROWS: readonly MemberDetailRow[] = ['lastLogin', 'grade', 'memberType', 'emailVerified', 'authMethod', 'dormant', 'statusChanged', 'point', 'boardRole', 'sanction'];

    /** 표의 칸 — 이 차례로 그린다 */
    static readonly COLUMNS: readonly MemberColumnDef[] = [
        { key: 'memberNo', labelKey: 'member.col.memberNo', required: true, defaultShown: true, sort: 'memberNo' },
        { key: 'email', labelKey: 'member.col.email', required: true, defaultShown: true, sort: 'email' },
        { key: 'nickname', labelKey: 'member.col.nickname', required: true, defaultShown: true, sort: 'nickname' },
        { key: 'grade', labelKey: 'member.col.grade', required: false, defaultShown: true, sort: 'grade' },
        { key: 'memberType', labelKey: 'member.col.memberType', required: false, defaultShown: true },
        { key: 'status', labelKey: 'member.col.status', required: true, defaultShown: true, sort: 'status' },
        { key: 'authMethod', labelKey: 'member.col.authMethod', required: false, defaultShown: true },
        { key: 'emailVerifiedAt', labelKey: 'member.col.emailVerifiedAt', required: false, defaultShown: false },
        { key: 'joinedAt', labelKey: 'member.col.joinedAt', required: false, defaultShown: true, sort: 'joinedAt' },
        { key: 'lastLoginAt', labelKey: 'member.col.lastLoginAt', required: false, defaultShown: true, sort: 'lastLoginAt' },
        { key: 'point', labelKey: 'member.col.point', required: false, defaultShown: true, sort: 'point' },
        { key: 'roles', labelKey: 'member.col.roles', required: false, defaultShown: true },
        { key: 'dormantAt', labelKey: 'member.col.dormantAt', required: false, defaultShown: false, sort: 'dormantAt' },
        { key: 'statusChangedAt', labelKey: 'member.col.statusChangedAt', required: false, defaultShown: false },
        { key: 'boardRole', labelKey: 'member.col.boardRole', required: false, defaultShown: false },
        { key: 'sanctionCount', labelKey: 'member.col.sanctionCount', required: false, defaultShown: false },
    ];

    /** 목록에서 역할 딱지를 몇 개까지 보이고 나머지를 "+N" 으로 줄이는가 */
    static readonly ROLE_TAGS_SHOWN = 2;

    static readonly DEFAULT_SORT: string = 'joinedAt,desc';

    static defaultColumns(): MemberColumnKey[] {
        return UtilMember.COLUMNS.filter((c) => c.defaultShown).map((c) => c.key);
    }

    /** 처음에는 상세 검색 줄을 전부 보인다 */
    static defaultDetailRows(): MemberDetailRow[] {
        return [...UtilMember.DETAIL_ROWS];
    }

    /**
     * 저장돼 있던 칸 선택을 지금 정의에 맞춘다 — 없어진 칸은 버리고 필수 칸은 늘 넣는다. 정의 차례로 돌려준다.
     * @param {unknown} stored - 저장소에서 읽은 값.
     * @returns {MemberColumnKey[]} 쓸 수 있는 칸 키들.
     */
    static sanitizeColumns(stored: unknown): MemberColumnKey[] {
        const set = new Set(Array.isArray(stored) ? stored : []);
        return UtilMember.COLUMNS.filter((c) => c.required || set.has(c.key)).map((c) => c.key);
    }

    /**
     * 저장돼 있던 상세 검색 줄 선택을 지금 정의에 맞춘다 — 없어진 줄은 버린다(모두 꺼도 된다).
     * @param {unknown} stored - 저장소에서 읽은 값.
     * @returns {MemberDetailRow[]} 쓸 수 있는 줄 키들.
     */
    static sanitizeDetailRows(stored: unknown): MemberDetailRow[] {
        const set = new Set(Array.isArray(stored) ? stored : []);
        return UtilMember.DETAIL_ROWS.filter((row) => set.has(row));
    }

    static emptyFilter(): MemberFilter {
        return {
            searchField: 'ALL',
            keyword: '',
            statuses: [],
            roleIds: [],
            joinedFrom: '',
            joinedTo: '',
            lastLoginFrom: '',
            lastLoginTo: '',
            noLoginHistory: false,
            grades: [],
            memberType: '',
            emailVerified: '',
            authMethods: [],
            dormantFrom: '',
            dormantTo: '',
            statusChangedKind: '',
            statusChangedFrom: '',
            statusChangedTo: '',
            pointMin: null,
            pointMax: null,
            boardRole: '',
            hasSanction: '',
        };
    }

    /**
     * 상세 검색 한 줄의 값을 비운다 — 줄을 화면에서 끄면 그 값도 같이 비워, 보이지 않는 조건이 검색에 남지 않게 한다.
     * @param {MemberFilter} filter - 조건(바로 고친다).
     * @param {MemberDetailRow} row - 비울 줄.
     */
    static clearRow(filter: MemberFilter, row: MemberDetailRow): void {
        const empty = UtilMember.emptyFilter() as unknown as Record<string, unknown>;
        const target = filter as unknown as Record<string, unknown>;
        const fields: Record<MemberDetailRow, (keyof MemberFilter)[]> = {
            lastLogin: ['lastLoginFrom', 'lastLoginTo', 'noLoginHistory'],
            grade: ['grades'],
            memberType: ['memberType'],
            emailVerified: ['emailVerified'],
            authMethod: ['authMethods'],
            dormant: ['dormantFrom', 'dormantTo'],
            statusChanged: ['statusChangedKind', 'statusChangedFrom', 'statusChangedTo'],
            point: ['pointMin', 'pointMax'],
            boardRole: ['boardRole'],
            sanction: ['hasSanction'],
        };
        for (const key of fields[row]) target[key] = empty[key];
    }

    /** 포인트 최소가 최대보다 큰가 — 서버는 `MEMBER_POINT_RANGE_REVERSED` 로 거절한다. 미리 알려 주는 용도 */
    static pointReversed(filter: MemberFilter): boolean {
        return filter.pointMin !== null && filter.pointMax !== null && filter.pointMin > filter.pointMax;
    }

    /** 그 날(`YYYY-MM-DD`)의 시작 순간 — 표시(사이트) 시간대의 00:00:00.000. 읽을 수 없으면 undefined */
    static dayStart(ymd: string, zoneId: string): string | undefined {
        const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd);
        if (!m) return undefined;
        return UtilTime.fromWall(Number(m[1]), Number(m[2]), Number(m[3]), 0, 0, zoneId) ?? undefined;
    }

    /** 그 날(`YYYY-MM-DD`)의 끝 순간 — 표시(사이트) 시간대의 23:59:59.999. 읽을 수 없으면 undefined */
    static dayEnd(ymd: string, zoneId: string): string | undefined {
        const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd);
        if (!m) return undefined;
        const start = UtilTime.fromWall(Number(m[1]), Number(m[2]), Number(m[3]), 23, 59, zoneId);
        return start ? new Date(Date.parse(start) + 59_999).toISOString() : undefined;
    }

    /**
     * 화면 조건 → 서버 조회 조건. 화면에서 꺼 둔 상세 줄의 값은 보내지 않는다. 쪽·정렬은 부르는 쪽이 얹는다.
     * @param {MemberFilter} filter - 화면 조건.
     * @param {readonly MemberDetailRow[]} rows - 화면에 보이는 상세 줄.
     * @param {string} zoneId - 기간 경계를 잡을 시간대(표시 시간대 = 사이트 시간대).
     * @returns {MemberQuery} 빈 값을 뺀 조건.
     */
    static toQuery(filter: MemberFilter, rows: readonly MemberDetailRow[], zoneId: string): MemberQuery {
        const shown = new Set(rows);
        const q: MemberQuery = {};
        const keyword = filter.keyword.trim();
        if (keyword) {
            q.keyword = keyword;
            q.searchField = filter.searchField;
        }
        if (filter.statuses.length) q.statuses = [...filter.statuses];
        if (filter.roleIds.length) q.roleIds = [...filter.roleIds];
        const range = (from: string, to: string, fromKey: keyof MemberQuery, toKey: keyof MemberQuery) => {
            const start = from ? UtilMember.dayStart(from, zoneId) : undefined;
            const end = to ? UtilMember.dayEnd(to, zoneId) : undefined;
            if (start) (q as Record<string, unknown>)[fromKey] = start;
            if (end) (q as Record<string, unknown>)[toKey] = end;
        };
        range(filter.joinedFrom, filter.joinedTo, 'joinedFrom', 'joinedTo');
        if (shown.has('lastLogin')) {
            if (filter.noLoginHistory) q.noLoginHistory = true;
            else range(filter.lastLoginFrom, filter.lastLoginTo, 'lastLoginFrom', 'lastLoginTo');
        }
        if (shown.has('grade') && filter.grades.length) q.grades = [...filter.grades];
        if (shown.has('memberType') && filter.memberType) q.memberType = filter.memberType;
        if (shown.has('emailVerified') && filter.emailVerified) q.emailVerified = filter.emailVerified === 'DONE';
        if (shown.has('authMethod') && filter.authMethods.length) q.authMethods = [...filter.authMethods];
        if (shown.has('dormant')) range(filter.dormantFrom, filter.dormantTo, 'dormantFrom', 'dormantTo');
        if (shown.has('statusChanged')) {
            if (filter.statusChangedKind) q.statusChangedKind = filter.statusChangedKind;
            range(filter.statusChangedFrom, filter.statusChangedTo, 'statusChangedFrom', 'statusChangedTo');
        }
        if (shown.has('point')) {
            if (filter.pointMin !== null) q.pointMin = filter.pointMin;
            if (filter.pointMax !== null) q.pointMax = filter.pointMax;
        }
        if (shown.has('boardRole') && filter.boardRole) q.boardRole = filter.boardRole;
        if (shown.has('sanction') && filter.hasSanction) q.hasSanction = filter.hasSanction === 'YES';
        return q;
    }

    static statusSeverity(status: MemberStatus): TagSeverity {
        return { ACTIVE: 'success', SUSPENDED: 'warn', BANNED: 'danger', WITHDRAWN: 'secondary', DORMANT: 'info' }[status] as TagSeverity;
    }

    /** 색만으로 구분하지 않도록 딱지에 함께 붙이는 아이콘 */
    static statusIcon(status: MemberStatus): string {
        return { ACTIVE: 'pi pi-check-circle', SUSPENDED: 'pi pi-clock', BANNED: 'pi pi-ban', WITHDRAWN: 'pi pi-sign-out', DORMANT: 'pi pi-moon' }[status];
    }

    static sanctionKindSeverity(kind: MemberSanctionKind): TagSeverity {
        return { WARNING: 'warn', SUSPENSION: 'danger', BAN: 'danger' }[kind] as TagSeverity;
    }

    static sanctionKindIcon(kind: MemberSanctionKind): string {
        return { WARNING: 'pi pi-exclamation-triangle', SUSPENSION: 'pi pi-clock', BAN: 'pi pi-ban' }[kind];
    }

    static sanctionStatusSeverity(status: MemberSanctionStatus): TagSeverity {
        return { ACTIVE: 'danger', ENDED: 'secondary', RELEASED: 'success' }[status] as TagSeverity;
    }

    static sanctionStatusIcon(status: MemberSanctionStatus): string {
        return { ACTIVE: 'pi pi-circle-fill', ENDED: 'pi pi-check', RELEASED: 'pi pi-unlock' }[status];
    }

    // ── 상세 보강(후보 계약, 확정 전) ─

    /** 소셜 연동 표시 차례 */
    static readonly SOCIAL_PROVIDERS: readonly MemberSocialProvider[] = ['KAKAO', 'NAVER', 'GOOGLE'];

    /** 알림 종류 표시 차례 — 끝의 둘(게시글 완료·답변)은 roboyam 에 대응 기능이 아직 없다(그누보드7 항목) */
    static readonly NOTIFICATION_KINDS: readonly MemberNotificationKind[] = ['COMMENT', 'REPLY', 'POPULAR_REWARD', 'DM', 'POST_DONE', 'POST_ANSWER'];

    /** 선택 동의 표시 차례 — 광고성 이메일은 마케팅의 하위다 */
    static readonly OPTIONAL_CONSENTS: readonly MemberOptionalConsentKey[] = ['MARKETING', 'EMAIL_AD', 'THIRD_PARTY', 'PROFILE_PUBLIC'];

    /** 대응 기능이 아직 없어 「준비 중」으로 보이는 알림 종류인가 */
    static notificationPending(kind: MemberNotificationKind): boolean {
        return kind === 'POST_DONE' || kind === 'POST_ANSWER';
    }

    /** 활동 요약 칸의 표시 차례 */
    static readonly ACTIVITY_KEYS: readonly (keyof MemberActivity)[] = ['postCount', 'commentCount', 'likesReceived', 'popularCount', 'recentPostCount', 'recentCommentCount', 'recentLoginDays'];

    /** 서버가 아직 집계하지 않아 늘 0 으로 오는 활동 칸 — 추천·인기글 기능과 날짜별 접속 기록이 생기면 여기서 뺀다 */
    private static readonly ACTIVITY_PENDING: readonly (keyof MemberActivity)[] = ['likesReceived', 'popularCount', 'recentLoginDays'];

    /** 서버가 아직 집계하지 않는 칸인가 — 0 을 그대로 보이면 "활동이 없다"로 오해한다 */
    static activityPending(key: keyof MemberActivity): boolean {
        return UtilMember.ACTIVITY_PENDING.includes(key);
    }

    static autoPromotionSeverity(state: MemberAutoPromotion): TagSeverity {
        return { ACTIVE: 'success', PAUSED_ADMIN: 'warn', PAUSED_SANCTION: 'danger' }[state] as TagSeverity;
    }

    static autoPromotionIcon(state: MemberAutoPromotion): string {
        return { ACTIVE: 'pi pi-play-circle', PAUSED_ADMIN: 'pi pi-user-edit', PAUSED_SANCTION: 'pi pi-ban' }[state];
    }

    static gradeChangeKindSeverity(kind: MemberGradeChangeKind): TagSeverity {
        return ({ AUTO: 'info', ADMIN: 'warn', RESUME: 'success' } as Record<string, TagSeverity>)[kind] ?? 'secondary';
    }

    static gradeChangeKindIcon(kind: MemberGradeChangeKind): string {
        return ({ AUTO: 'pi pi-arrow-up', ADMIN: 'pi pi-user-edit', RESUME: 'pi pi-play' } as Record<string, string>)[kind] ?? 'pi pi-circle';
    }

    /**
     * 활동 수 조건인데 서버의 자동 승급 엔진이 아직 활동 수를 0 으로 봐서, 달성도가 차도 승급되지 않을 수 있는 줄인가.
     * 엔진이 활동 수를 쓰게 되면(카운터 표 연결) 이 판정과 안내 문구를 함께 없앤다(`roles-contract.md`).
     */
    static activityPromotionNotApplied(requirement: MemberGradeRequirement): boolean {
        return requirement.kind === 'ACTIVITY' && requirement.required > 0;
    }

    /** 승급 조건 진행도(0~100) — 기준이 0 이면 이미 채운 것으로 본다 */
    static requirementPercent(requirement: MemberGradeRequirement): number {
        if (requirement.required <= 0) return 100;
        return Math.min(100, Math.floor((requirement.current / requirement.required) * 100));
    }

    static requirementMet(requirement: MemberGradeRequirement): boolean {
        return requirement.current >= requirement.required;
    }

    /** 진행 중인 제재들 */
    static activeSanctions(sanctions: readonly MemberSanction[]): MemberSanction[] {
        return sanctions.filter((s) => s.status === 'ACTIVE');
    }

    /** 증감 글자 — 늘면 `+120`, 줄면 `−500`(숫자는 화면 언어의 자리 구분) */
    static signedNumber(value: number, locale: string): string {
        const text = Math.abs(value).toLocaleString(locale);
        return value < 0 ? `−${text}` : `+${text}`;
    }

    /** 국가 이름 — 브라우저가 화면 언어로 풀어 준다. 모르는 코드는 코드 그대로 */
    static countryName(code: string, locale: string): string {
        try {
            return new Intl.DisplayNames([locale], { type: 'region' }).of(code) ?? code;
        } catch {
            return code;
        }
    }

    static consentActionSeverity(action: MemberConsentAction): TagSeverity {
        return action === 'AGREE' ? 'success' : 'warn';
    }

    static consentActionIcon(action: MemberConsentAction): string {
        return action === 'AGREE' ? 'pi pi-check' : 'pi pi-times';
    }

    /** 동의 항목이 상위 동의의 하위인가(광고성 이메일 ← 마케팅) */
    static consentIsChild(item: MemberConsentItem): boolean {
        return item === 'EMAIL_AD';
    }

    static orderStatusSeverity(status: MemberOrderStatus): TagSeverity {
        return { PENDING: 'secondary', PAID: 'info', PREPARING: 'info', SHIPPED: 'warn', DELIVERED: 'success', CANCELED: 'secondary', REFUNDED: 'danger' }[status] as TagSeverity;
    }

    static orderStatusIcon(status: MemberOrderStatus): string {
        return { PENDING: 'pi pi-hourglass', PAID: 'pi pi-wallet', PREPARING: 'pi pi-box', SHIPPED: 'pi pi-truck', DELIVERED: 'pi pi-check-circle', CANCELED: 'pi pi-times-circle', REFUNDED: 'pi pi-replay' }[status];
    }

    /** 역할 유형(상세의 역할 묶음) 차례 */
    static readonly ROLE_TYPES: readonly MemberRoleTypeId[] = ['memberGrade', 'memberKind', 'memberBoard', 'memberCustom'];

    static roleIcon(type: MemberRoleTypeId): string {
        return { memberGrade: 'pi pi-star', memberKind: 'pi pi-id-card', memberBoard: 'pi pi-shield', memberCustom: 'pi pi-tag' }[type];
    }

    static authIcon(method: MemberAuthMethod): string {
        return { EMAIL: 'pi pi-envelope', KAKAO: 'pi pi-comment', NAVER: 'pi pi-globe', GOOGLE: 'pi pi-google' }[method];
    }

    static typeIcon(type: MemberType): string {
        return type === 'BUSINESS' ? 'pi pi-briefcase' : 'pi pi-user';
    }

    /**
     * 목록의 역할 칸 — 앞의 몇 개만 딱지로, 나머지는 "+N" 으로.
     * @param {MemberRoleRef[]} roles - 받은 회원 역할들.
     * @returns {{ shown: MemberRoleRef[]; rest: MemberRoleRef[] }} 보일 것과 줄일 것.
     */
    static splitRoles(roles: MemberRoleRef[]): { shown: MemberRoleRef[]; rest: MemberRoleRef[] } {
        return { shown: roles.slice(0, UtilMember.ROLE_TAGS_SHOWN), rest: roles.slice(UtilMember.ROLE_TAGS_SHOWN) };
    }

    /**
     * 표의 정렬(`칸`, 1/-1) → 서버 정렬 글자(`칸,asc|desc`). 칸이 없으면 기본 정렬.
     * @param {string | undefined | null} field - 정렬 칸(`MemberSortField`).
     * @param {number | undefined | null} order - 1 오름 · -1 내림.
     * @returns {string} `joinedAt,desc` 꼴.
     */
    static sortParam(field: string | undefined | null, order: number | undefined | null): string {
        if (!field) return UtilMember.DEFAULT_SORT;
        return `${field},${order === 1 ? 'asc' : 'desc'}`;
    }
}
