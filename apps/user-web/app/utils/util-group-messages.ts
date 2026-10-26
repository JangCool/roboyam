import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type {
    GroupChatKind,
    GroupChatLink,
    GroupCreationMode,
    GroupCreationRules,
    GroupJoinReason,
    GroupJoinType,
    GroupManageBlockedReason,
    GroupMyState,
    GroupNewcomers,
    GroupRecruitState,
    GroupRow,
    GroupSort,
} from '~/composables/group/api/useGroupApi.types';
import type { PostGroupRef, PostGroupVisibility } from '~/composables/board/api/usePostApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilGroup, { type GroupCapacityRange, type GroupJoinAction, type GroupListState, type GroupProblem } from '~/utils/util-group';
import UtilBoardMessages from '~/utils/util-board-messages';

/**
 * 소모임 화면의 계산이 낀 문구 — 분기·자리표시자·조합이 있는 것만 둔다(상수 문구는 컴포넌트가 `t('group.키')` 로 부른다).
 * 문구 본문은 `i18n/locales/{ko,en}/group.ts`. 이름은 고정 「소모임」/"Club"(로보얌에는 community 의 용어 설정 `GROUPS` 이름 바꾸기가 소모임 문구에 걸려 있지 않다).
 * 서버 `message` 가 있는 오류는 서버 문구를 그대로 쓴다(`UtilBoardMessages.of`) — 여기서 푸는 것은 코드만 오는 상태(`join.reason` 등)다.
 */

/** 줄 오른쪽 표시 — 가입할 수 있다(테두리) · 정원 참·마감(꺼짐) · 이미 내 상태(글자만) */
export interface GroupRowAction {
    label: string;
    tone: 'go' | 'off' | 'note';
}

export interface GroupCopy {
    title: string;
    text: string;
}

export interface GroupFailure extends GroupCopy {
    kind: 'permission' | 'grade' | 'limit' | 'penalty' | 'rate' | 'unverified' | 'restricted' | 'generic';
}

/** 신규 가입자 줄 */
export interface GroupNewcomerLines {
    pending: { text: string; to: string | null } | null;
    recent: { title: string; empty: boolean; users: GroupNewcomers['items']; more: string | null } | null;
}

const SORT_KEY: Record<GroupSort, string> = {
    popular: 'group.sortPopular',
    new: 'group.sortNew',
    recruiting: 'group.sortRecruiting',
    mine: 'group.sortMine',
};

export default class UtilGroupMessages {
    /** 오류 한 줄(서버 문구 그대로, 아니면 네트워크 문구) */
    static of(t: Translate, e: unknown): string {
        return UtilBoardMessages.of(t, e);
    }

    /** 「{행동}. {사유}」 */
    static failed(t: Translate, actionKey: string, e: unknown): string {
        return t('group.failedWith', { action: t(actionKey), reason: UtilBoardMessages.of(t, e) });
    }

    static sortLabel(t: Translate, sort: GroupSort): string {
        return t(SORT_KEY[sort]);
    }

    static recruitLabel(t: Translate, state: GroupRecruitState): string {
        return t(state === 'OPEN' ? 'group.recruitOpen' : state === 'FULL' ? 'group.recruitFull' : 'group.recruitClosed');
    }

    static joinTypeLabel(t: Translate, joinType: GroupJoinType): string {
        return t(joinType === 'OPEN' ? 'group.joinOpen' : 'group.joinApproval');
    }

    static members(t: Translate, row: Pick<GroupRow, 'memberCount' | 'capacity'>): string {
        return t('group.members', { count: row.memberCount, capacity: row.capacity });
    }

    /** 목록 줄의 작은 표시 — 신청제·오프라인 */
    static badges(t: Translate, row: Pick<GroupRow, 'joinType' | 'offline'>): string[] {
        const out: string[] = [];
        if (row.joinType === 'APPROVAL') out.push(t('group.joinApproval'));
        if (row.offline) out.push(t('group.offline'));
        return out;
    }

    /** 목록 줄 오른쪽 — 내 상태가 먼저, 그다음 모집 상태. `long` 은 자리가 넉넉한 「지금 뜨는 소모임」 카드(「가입하기」) */
    static rowAction(t: Translate, row: Pick<GroupRow, 'recruitState' | 'joinType' | 'myState'>, long = false): GroupRowAction {
        if (row.myState === 'LEADER') return { label: t('group.roleLeader'), tone: 'note' };
        if (row.myState === 'MEMBER') return { label: t('group.rowMember'), tone: 'note' };
        if (row.myState === 'APPLIED') return { label: t('group.rowApplied'), tone: 'note' };
        if (row.recruitState === 'FULL') return { label: t('group.recruitFull'), tone: 'off' };
        if (row.recruitState === 'CLOSED') return { label: t('group.rowClosed'), tone: 'off' };
        if (row.joinType === 'APPROVAL' || row.myState === 'KICKED') return { label: t('group.rowApply'), tone: 'go' };
        return { label: t(long ? 'group.rowJoinLong' : 'group.rowJoin'), tone: 'go' };
    }

    /** 비었을 때의 안내 */
    static emptyCopy(t: Translate, state: GroupListState): GroupCopy {
        switch (state.q ? 'search' : state.sort === 'mine' ? 'mine' : state.category || state.sort !== 'popular' ? 'filtered' : 'none') {
            case 'search':
                return { title: t('group.emptySearchTitle', { q: state.q }), text: t('group.emptySearchText') };
            case 'mine':
                return { title: t('group.myEmptyTitle'), text: t('group.myEmptyText') };
            case 'filtered':
                return { title: t('group.emptyFilteredTitle'), text: t('group.emptyFilteredText') };
            default:
                return { title: t('group.emptyNoneTitle'), text: t('group.emptyNoneText') };
        }
    }

    /** 스크린리더가 읽는 목록 요약 */
    static summary(t: Translate, status: string, count: number, hasNext: boolean): string {
        if (status === 'loading') return t('group.listLoading');
        if (status === 'error') return t('group.listError');
        if (status !== 'ready') return '';
        if (count === 0) return t('group.listNone');
        return hasNext ? t('group.listCountMore', { count }) : t('group.listCount', { count });
    }

    /** 승인 대기 요약 — 「승인 대기 2 · 코딩 스터디 외 1」 */
    static pendingSummary(t: Translate, applications: readonly { name: string }[]): string {
        const first = applications[0];
        if (!first) return '';
        return applications.length > 1
            ? t('group.pendingSummaryMore', { count: applications.length, name: first.name, rest: applications.length - 1 })
            : t('group.pendingSummary', { count: applications.length, name: first.name });
    }

    static appliedNote(t: Translate, appliedAt: string | null | undefined, time: DisplayTime): string {
        const date = time.date(appliedAt);
        return date ? t('group.appliedOn', { date }) : t('group.appliedWaiting');
    }

    /* ── 상세 ── */

    static metaLine(t: Translate, group: { memberCount: number; capacity: number; openedAt: string | null }, time: DisplayTime): string {
        const members = UtilGroupMessages.members(t, group);
        const p = time.parts(group.openedAt);
        return p ? t('group.metaOpened', { members, y: p.y, mo: p.mo }) : members;
    }

    static joinedMonth(t: Translate, iso: string | null | undefined, time: DisplayTime): string {
        const p = time.parts(iso);
        return p ? t('group.joinedMonth', { y: p.y, mo: String(p.mo).padStart(2, '0') }) : '';
    }

    static joinLabel(t: Translate, label: GroupJoinAction['label']): string {
        switch (label) {
            case 'APPLY':
                return t('group.applyButton');
            case 'APPLIED':
                return t('group.appliedButton');
            case 'REAPPLY':
                return t('group.reapplyButton');
            case 'MEMBER':
                return t('group.joinedButton');
            case 'FULL':
                return t('group.recruitFull');
            case 'CLOSED':
                return t('group.recruitClosed');
            default:
                return t('group.joinButton');
        }
    }

    /** 남은 초 → 「6일 뒤」·「3시간 뒤」·「10분 뒤」·「잠시 뒤」 */
    static retryLabel(t: Translate, seconds: number | null): string {
        if (seconds == null || seconds <= 0) return t('group.retrySoon');
        const days = Math.floor(seconds / 86400);
        if (days >= 1) return t('group.retryDays', { n: days });
        const hours = Math.floor(seconds / 3600);
        if (hours >= 1) return t('group.retryHours', { n: hours });
        return t('group.retryMinutes', { n: Math.max(1, Math.ceil(seconds / 60)) });
    }

    /** 가입·신청을 못 하는 까닭의 안내 — 없으면 null */
    static joinNotice(t: Translate, reason: GroupJoinReason | null, retryAfterSeconds: number | null): GroupCopy | null {
        switch (reason) {
            case 'FULL':
                return { title: t('group.noticeFullTitle'), text: t('group.noticeFullText') };
            case 'CLOSED':
                return { title: t('group.noticeClosedTitle'), text: t('group.noticeClosedText') };
            case 'EMAIL_NOT_VERIFIED':
                return { title: t('group.noticeEmailTitle'), text: t('group.noticeEmailText') };
            case 'ACCOUNT_RESTRICTED':
                return { title: t('group.noticeRestrictedTitle'), text: t('group.noticeRestrictedText') };
            case 'BUSINESS_ACCOUNT_RESTRICTED':
                return { title: t('group.noticeBusinessTitle'), text: t('group.noticeBusinessText') };
            case 'ALREADY_APPLIED':
                return { title: t('group.noticeAppliedTitle'), text: t('group.noticeAppliedText') };
            case 'REAPPLY_LATER':
                return { title: t('group.noticeReapplyTitle'), text: t('group.noticeReapplyText', { when: UtilGroupMessages.retryLabel(t, retryAfterSeconds) }) };
            default:
                return null;
        }
    }

    static chatKindLabel(t: Translate, kind: GroupChatKind): string {
        return t(kind === 'KAKAO' ? 'group.chatKakao' : 'group.chatDiscord');
    }

    /** 외부 서비스로 나가기 전 확인 */
    static chatLeaveConfirm(t: Translate, kind: GroupChatKind): { title: string; message: string; confirmLabel: string } {
        return { title: t('group.chatLeaveTitle'), message: t(kind === 'KAKAO' ? 'group.chatLeaveKakao' : 'group.chatLeaveDiscord'), confirmLabel: t('group.chatLeaveConfirm') };
    }

    /** 신규 가입자 줄 — 서버가 준 칸만. 둘 다 없으면 null */
    static newcomerLines(t: Translate, group: { id: string; newcomers: GroupNewcomers | null; pendingApplicationCount: number | null }, applicationsPath: string, membersPath: string): GroupNewcomerLines | null {
        let pending: GroupNewcomerLines['pending'] = null;
        const count = group.pendingApplicationCount;
        if (typeof count === 'number' && Number.isFinite(count) && count >= 0) {
            const n = Math.floor(count);
            pending = n === 0 ? { text: t('group.newcomersPendingNone'), to: null } : { text: t('group.newcomersPending', { n }), to: applicationsPath };
        }
        let recent: GroupNewcomerLines['recent'] = null;
        const nc = group.newcomers;
        if (nc) {
            const users = nc.items.slice(0, 5);
            const total = Math.max(Math.floor(nc.count), users.length);
            recent =
                total === 0
                    ? { title: t('group.newcomersNone', { days: nc.windowDays }), empty: true, users: [], more: null }
                    : { title: t('group.newcomersRecent', { days: nc.windowDays, n: total }), empty: false, users, more: total > users.length ? membersPath : null };
        }
        return pending || recent ? { pending, recent } : null;
    }

    /* ── 개설 신청 ── */

    static creationNotice(t: Translate, mode: GroupCreationMode | null, immediate: boolean): string | null {
        if (immediate || mode === 'OPEN') return t('group.createNoticeOpen');
        if (mode === 'APPROVAL') return t('group.createNoticeApproval');
        return null;
    }

    static submitLabel(t: Translate, mode: GroupCreationMode | null, immediate: boolean): string {
        return !immediate && mode === 'APPROVAL' ? t('group.submitApproval') : t('group.submitOpen');
    }

    /** 회원 권한 없음·등급 미달·모임장 상한·해산 패널티 — 규칙 응답의 `me` 가 알려 준다(운영진·운영자는 면제) */
    static ruleBlock(t: Translate, rules: GroupCreationRules | null, gradeName: (key: string) => string, time: DisplayTime): GroupCopy | null {
        const me = rules?.me;
        if (!rules || !me || me.canCreate) return null;
        if (me.reason === 'NOT_PERMITTED') return { title: t('group.notPermittedTitle'), text: t('group.notPermittedText') };
        if (me.reason === 'GRADE_TOO_LOW') {
            return { title: t('group.gradeTooLowTitle', { grade: rules.minGradeLabel }), text: me.grade ? t('group.gradeNow', { grade: gradeName(me.grade) }) : '' };
        }
        if (me.reason === 'LIMIT_REACHED') return { title: t('group.limitTitle', { n: me.ledCount }), text: t('group.limitText') };
        if (me.reason === 'DISSOLVE_PENALTY') return { title: t('group.penaltyTitle'), text: UtilGroupMessages.penaltyText(t, me.penaltyUntil, time) };
        return null;
    }

    /** 해산 패널티 안내 — 끝나는 시각을 알면 「… 부터 다시 만들 수 있어요」, 모르면 까닭만 */
    static penaltyText(t: Translate, until: string | null | undefined, time: DisplayTime): string {
        const at = until ? time.full(until) : '';
        return at ? t('group.penaltyUntil', { time: at }) : t('group.penaltyText');
    }

    /** 칸 문제 → 문구 */
    static problem(t: Translate, field: string, problem: GroupProblem, ctx: { range: GroupCapacityRange; memberCount?: number; length?: number }): string {
        switch (field) {
            case 'name':
                return problem === 'REQUIRED' ? t('group.nameRequired') : t('group.nameLength', { min: UtilGroup.NAME_MIN, max: UtilGroup.NAME_MAX, n: ctx.length ?? 0 });
            case 'description':
                return problem === 'REQUIRED' ? t('group.descriptionRequired') : t('group.descriptionLength', { max: UtilGroup.DESCRIPTION_MAX, n: ctx.length ?? 0 });
            case 'categoryId':
                return t('group.categoryRequired');
            case 'boardSlug':
                return t('group.boardRequired');
            case 'capacity':
                if (problem === 'REQUIRED') return t('group.capacityRequired');
                if (problem === 'FORMAT') return t('group.capacityFormat');
                if (problem === 'BELOW_MEMBERS') return t('group.capacityBelowMembers', { n: ctx.memberCount ?? 0 });
                return t('group.capacityRange', { min: ctx.range.min, max: ctx.range.max });
            case 'agreeStock':
                return t('group.agreeStockRequired');
            case 'agreeOffline':
                return t('group.agreeOfflineRequired');
            case 'agreeRules':
                return t('group.agreeRequired');
            default:
                return t('group.fieldCheck');
        }
    }

    /** 개설 신청이 칸에 붙지 않는 까닭으로 거절됐을 때의 안내 */
    static createFailure(t: Translate, e: unknown, time: DisplayTime): GroupFailure {
        if (!(e instanceof ApiError)) return { kind: 'generic', title: t('group.failGenericTitle'), text: t('group.failKeep', { reason: UtilBoardMessages.of(t, e) }) };
        const wait = e.retryAfterSeconds && e.retryAfterSeconds > 0 ? ` ${t('group.failRetryAfter', { time: time.duration(e.retryAfterSeconds) })}` : '';
        switch (e.code) {
            case 'GROUP_CREATE_NOT_PERMITTED':
                return { kind: 'permission', title: t('group.notPermittedTitle'), text: e.message };
            case 'GROUP_GRADE_TOO_LOW':
                return { kind: 'grade', title: e.message, text: '' };
            case 'GROUP_LIMIT_REACHED':
                return { kind: 'limit', title: t('group.failLimitTitle'), text: e.message };
            case 'GROUP_DISSOLVE_PENALTY': {
                // 끝나는 시각은 오류 본문의 `until`(또는 `penaltyUntil`) — 없으면 `retryAfterSeconds` 로 남은 시간을, 그것도 없으면 서버 문구만
                const until = e.extraText('until') ?? e.extraText('penaltyUntil');
                if (until) return { kind: 'penalty', title: t('group.penaltyTitle'), text: UtilGroupMessages.penaltyText(t, until, time) };
                return { kind: 'penalty', title: t('group.penaltyTitle'), text: `${e.message}${wait}` };
            }
            case 'RATE_LIMITED':
                return { kind: 'rate', title: t('group.failRateTitle'), text: `${e.message}${wait}` };
            case 'EMAIL_NOT_VERIFIED':
                return { kind: 'unverified', title: t('group.unverifiedTitle'), text: e.message };
            case 'ACCOUNT_RESTRICTED':
            case 'BUSINESS_ACCOUNT_RESTRICTED':
                return { kind: 'restricted', title: t('group.failRestrictedTitle'), text: e.message };
            default:
                return { kind: 'generic', title: t('group.failGenericTitle'), text: t('group.failKeep', { reason: `${e.message}${e.status === 429 ? wait : ''}` }) };
        }
    }

    static pendingSummaryLine(t: Translate, categoryName: string, joinType: GroupJoinType, capacity: number): string {
        return t('group.pendingSummaryLine', { category: categoryName, joinType: UtilGroupMessages.joinTypeLabel(t, joinType), capacity });
    }

    /* ── 관리 ── */

    static blockedBand(t: Translate, reason: GroupManageBlockedReason): string {
        if (reason === 'EMAIL_NOT_VERIFIED') return t('group.blockedEmail');
        return t(reason === 'BUSINESS_ACCOUNT_RESTRICTED' ? 'group.blockedBusiness' : 'group.blockedRestricted');
    }

    static chatSummary(t: Translate, links: readonly GroupChatLink[]): string {
        const names: string[] = [];
        if (links.some((l) => l.kind === 'KAKAO')) names.push(t('group.chatKakao'));
        if (links.some((l) => l.kind === 'DISCORD')) names.push(t('group.chatDiscord'));
        return names.length ? t('group.chatConnectedNames', { names: names.join('·') }) : t('group.chatNone');
    }

    static dissolveDescription(t: Translate, audience: number): string {
        const base = t('group.dissolveDescription');
        return audience > 0 ? `${base} ${t('group.dissolveAudience', { n: audience })}` : base;
    }

    /** 해산 확인 시트의 패널티 한 줄 — 걸리지 않으면(꺼짐·1년 이상 유지·안내 없음) null */
    static dissolvePenalty(t: Translate, days: number): string | null {
        return days > 0 ? t('group.dissolvePenalty', { n: days }) : null;
    }

    static applicationMeta(t: Translate, appliedAt: string | null | undefined, message: string | null | undefined, time: DisplayTime): string {
        const date = time.date(appliedAt);
        const text = message?.trim() ? `“${message.trim()}”` : t('group.noMessage');
        return date ? `${date} · ${text}` : text;
    }

    /** 서버가 받아들이지 않은 칸 이름들 */
    static serverFieldsSummary(t: Translate, labels: readonly string[]): string {
        return t('group.serverFields', { fields: labels.join(', ') });
    }

    /* ── 소모임 글(2단계) ── */

    /** 글 탭 줄의 공개 라벨 — 「비공개」 · 「공개 전환됨」 */
    static postRowVisibility(t: Translate, visibility: PostGroupVisibility): string {
        return t(visibility === 'PUBLIC' ? 'group.postPublicRow' : 'group.postPrivateRow');
    }

    /** 글 상세의 공개 라벨 — 「멤버에게만 보여요」 · 「전체 공개」 */
    static postVisibility(t: Translate, visibility: PostGroupVisibility): string {
        return t(visibility === 'PUBLIC' ? 'group.postPublic' : 'group.postPrivate');
    }

    /** 글 상세 머리의 위치 — 「소모임 › 이름」, 해산한 소모임은 「해산한 소모임」 */
    static postLocation(t: Translate, group: PostGroupRef): string {
        return group.dissolved ? t('group.postDissolved') : t('group.postLocation', { name: group.name });
    }

    /** 스크린리더가 읽는 글 탭 요약 */
    static postsSummary(t: Translate, status: string, count: number, hasNext: boolean): string {
        if (status === 'loading') return t('group.postsLoading');
        if (status === 'error') return t('group.postsError');
        if (status !== 'ready') return '';
        return hasNext ? t('group.postsCountMore', { count }) : t('group.postsCount', { count });
    }

    /** 비공개로 되돌리기 확인 — 남(모임장·매니저)이 되돌리면 글쓴이에게 알림이 간다 */
    static unpublishConfirm(t: Translate, byAuthor: boolean): { title: string; message: string; confirmLabel: string } {
        return { title: t('group.unpublishTitle'), message: t(byAuthor ? 'group.unpublishMessageAuthor' : 'group.unpublishMessageOther'), confirmLabel: t('group.unpublishConfirm') };
    }

    /** 소모임 글쓰기 입구가 막힌 까닭 — 비회원 · 멤버 아님 · 이메일 인증 전 · 쓸 수 없는 상태(정지 등) */
    static writeGate(t: Translate, gate: 'login' | 'not-member' | 'unverified' | 'blocked', myState: GroupMyState | null): GroupCopy {
        switch (gate) {
            case 'login':
                return { title: t('group.writeLoginTitle'), text: t('group.writeLoginText') };
            case 'unverified':
                return { title: t('group.unverifiedTitle'), text: t('group.writeUnverifiedText') };
            case 'blocked':
                return { title: t('group.writeBlockedTitle'), text: t('group.postsSuspended') };
            default:
                return { title: t('group.writeNotMemberTitle'), text: t(myState === 'APPLIED' ? 'group.writeAppliedText' : 'group.writeNotMemberText') };
        }
    }

    static chatProblem(t: Translate, field: 'kakao' | 'discord', problem: 'FORMAT' | 'LABEL_LENGTH'): string {
        if (problem === 'LABEL_LENGTH') return t('group.chatLabelTooLong', { max: UtilGroup.LABEL_MAX });
        return t(field === 'kakao' ? 'group.chatFormatKakao' : 'group.chatFormatDiscord');
    }
}
