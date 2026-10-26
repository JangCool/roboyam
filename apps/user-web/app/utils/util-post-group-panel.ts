import type { GroupManageBlockedReason } from '~/composables/group/api/useGroupApi.types';
import type { PostGroupInfo } from '~/composables/board/api/usePostApi.types';
import UtilGroupVisibility from '~/utils/util-group-visibility';

/**
 * 소모임 글 상세의 공개 범위 패널 상태(5단계 — community `UtilPostGroupPanel`, `docs/api-groups.md` 11-6·11-7) — 서버가 준 `group`(`PostGroupInfo`)과
 * 「내가 작성자인가」로 패널이 어느 모습이고 어떤 단추가 켜지는지 정한다. **판정은 늘 서버다**(`can*` 칸을 그대로 따른다).
 *
 *  - `LEGACY`        공개 범위 기능이 꺼져 있고 비공개 소모임(`groupVisibility` 없음) — 옛 패널(작성자의 「전체 공개로 전환」·「비공개로 되돌리기」). 새 칸을 쓰지 않는다
 *  - `PRIVATE`       비공개 소모임의 비공개 글(요청 전) — 작성자 [공개 요청하기], 처리자 [직접 공개하기]
 *  - `REQUESTING`    공개 요청 중 — 작성자 [요청 취소], 처리자 [공개하기]·[거절]
 *  - `REJECTED`      공개 요청이 거절됨 — 사유와 재요청까지 남은 시간(또는 한도 도달)
 *  - `GRANTED`       비공개 소모임의 개별 공개 글(승인·직접 공개) — [비공개로 되돌리기]
 *  - `GROUP_PUBLIC`  공개 소모임의 글 — 작성자(일반 멤버)에게 단추가 없다. 처리자 [멤버 전용으로 돌리기]
 *  - `MEMBERS_ONLY`  공개 소모임에서 모임장·매니저가 멤버 전용으로 돌린 글 — 처리자 [다시 공개하기]
 */
export type PanelKind = 'LEGACY' | 'PRIVATE' | 'REQUESTING' | 'REJECTED' | 'GRANTED' | 'GROUP_PUBLIC' | 'MEMBERS_ONLY';

/** 거절 뒤 재요청 상태 — 바로 요청할 수 있다 · 시간이 남았다 · 거절 한도에 닿았다 · 거절 상태가 아니다 */
export type RetryKind = 'none' | 'ready' | 'wait' | 'limit';

export interface PanelState {
    kind: PanelKind;
    /** 작성자: [공개 요청하기] */
    canRequest: boolean;
    /** 작성자: [요청 취소] — 요청 중일 때 */
    canCancel: boolean;
    /** 처리자: [공개하기]·[거절] — 요청 중일 때 */
    canDecide: boolean;
    /** 처리자: [직접 공개하기] — `LEGACY` 에서는 작성자의 옛 [전체 공개로 전환] */
    canPublishDirect: boolean;
    /** [비공개로 되돌리기] — 개별 공개 글(작성자·모임장·권한 매니저) */
    canUnpublish: boolean;
    /** 처리자: [멤버 전용으로 돌리기] */
    canMakeMembersOnly: boolean;
    /** 처리자: [다시 공개하기] */
    canRelease: boolean;
    retry: RetryKind;
    /** `wait` 일 때만 남은 시간(올림) */
    retryHours: number | null;
}

/** 화면이 방금 한 일 — `afterAction` 이 글 상세의 `group` 에 덮어쓸 칸을 정한다 */
export type PanelAction = 'requested' | 'canceled' | 'published' | 'approved' | 'unpublished' | 'members-only' | 'released';

/** 글 상세 `group` 에 덮어쓸 칸 */
export type PostGroupPatch = Partial<Omit<PostGroupInfo, 'id' | 'name'>>;

export default class UtilPostGroupPanel {
    static stateOf(group: PostGroupInfo, isAuthor: boolean, now: number): PanelState {
        const base: PanelState = {
            kind: 'LEGACY',
            canRequest: false,
            canCancel: false,
            canDecide: false,
            canPublishDirect: false,
            canUnpublish: false,
            canMakeMembersOnly: false,
            canRelease: false,
            retry: 'none',
            retryHours: null,
        };
        const groupVisibility = UtilGroupVisibility.of(group.groupVisibility);

        // 기능이 꺼져 있고 비공개 소모임 — 옛 패널 그대로(작성자 전환·되돌리기)
        if (groupVisibility === null) return { ...base, canPublishDirect: group.canPublish, canUnpublish: group.canUnpublish };

        if (groupVisibility === 'PUBLIC') {
            if (group.membersOnly === true) return { ...base, kind: 'MEMBERS_ONLY', canRelease: group.canReleaseMembersOnly === true };
            return { ...base, kind: 'GROUP_PUBLIC', canMakeMembersOnly: group.canMakeMembersOnly === true };
        }

        // 비공개 소모임 — 글이 이미 모두에게 보이면 개별 공개 글
        if (group.visibility === 'PUBLIC') return { ...base, kind: 'GRANTED', canUnpublish: group.canUnpublish };

        const request = group.publishRequest ?? null;
        if (request?.status === 'PENDING') return { ...base, kind: 'REQUESTING', canCancel: isAuthor, canDecide: group.canDecidePublishRequest === true };

        const canRequest = group.canRequestPublish === true;
        if (request?.status === 'REJECTED') {
            const hours = UtilGroupVisibility.hoursUntil(request.canRetryAt, now);
            const retry: RetryKind = canRequest ? 'ready' : hours !== null && hours > 0 ? 'wait' : 'limit';
            return { ...base, kind: 'REJECTED', canRequest, canPublishDirect: group.canPublish, retry, retryHours: retry === 'wait' ? hours : null };
        }
        return { ...base, kind: 'PRIVATE', canRequest, canPublishDirect: group.canPublish };
    }

    /** 쓰기 단추가 있는 패널인가 — 없으면 정지 안내도 필요 없다 */
    static hasWriteAction(s: PanelState): boolean {
        return s.canRequest || s.canCancel || s.canDecide || s.canPublishDirect || s.canMakeMembersOnly || s.canRelease;
    }

    /** 처리자(승인·거절·직접 공개·돌리기)의 단추만 있는가 — 막힘 안내 문구가 갈린다 */
    static isProcessor(s: PanelState): boolean {
        return !s.canRequest && !s.canCancel;
    }

    /**
     * 지금 쓰기가 막혔나 — 이용 제한(정지·영구 정지) → 이메일 미인증 → 사업자 계정 차례(서버 `GroupAccess.writeBlocked` 와 같은 갈래, 이메일이 먼저인 서버 차례와는 문구만 다르다).
     * 판정은 서버다 — 화면은 단추를 끄고 까닭을 미리 말한다.
     */
    static blockedOf(user: { emailVerified: boolean; role?: string } | null | undefined, restriction: { status: string } | null | undefined): GroupManageBlockedReason | null {
        if (!user) return null;
        if (!user.emailVerified) return 'EMAIL_NOT_VERIFIED';
        if (restriction && (restriction.status === 'SUSPENDED' || restriction.status === 'BANNED')) return 'ACCOUNT_RESTRICTED';
        if (user.role === 'BUSINESS') return 'BUSINESS_ACCOUNT_RESTRICTED';
        return null;
    }

    /**
     * 비멤버가 보는 비공개 소모임의 **개별 공개 글**인가 — 글 맨 위에 「이 글은 {이름} 멤버가 모두에게 공개한 글이에요」가 선다.
     * 멤버는 `membersOnlyCommentCount` 가 null 이라 아니다. 공개 소모임의 글·멤버 전용으로 돌린 글·기능이 꺼진 서버도 아니다.
     */
    static isIndividualForVisitor(group: PostGroupInfo | null | undefined): boolean {
        if (!group || UtilGroupVisibility.of(group.groupVisibility) !== 'PRIVATE' || group.visibility !== 'PUBLIC') return false;
        return typeof group.membersOnlyCommentCount === 'number';
    }

    /** 공개 전 댓글 안내(「공개하기 전에 달린 댓글은 멤버에게만 보여요」)를 보일까 — 개별 공개 글을 멤버가 볼 때만(비멤버는 댓글 위 안내 줄이 숫자로 말한다) */
    static showsBeforePublicComments(group: PostGroupInfo): boolean {
        return group.membersOnlyCommentCount === null || group.membersOnlyCommentCount === undefined;
    }

    /** 처리 배너의 요청 한마디 — 앞뒤 공백을 뗀다. 없으면 null */
    static noteOf(group: PostGroupInfo): string | null {
        const note = group.publishRequest?.note?.trim();
        return note ? note : null;
    }

    /** 거절 사유 — 없으면 null(줄 자체를 그리지 않는다) */
    static rejectReasonOf(group: PostGroupInfo): string | null {
        const reason = group.publishRequest?.rejectReason?.trim();
        return reason ? reason : null;
    }

    /**
     * 방금 한 일을 글 상세의 `group` 에 반영할 값 — 응답이 값을 다 주지 않으므로 화면이 바로 아는 것만 덮고, 실제 권한은 다음에 글을 받을 때 서버가 다시 말한다
     * (글을 다시 받으면 조회수가 오르고 읽던 자리가 흔들린다).
     */
    static afterAction(action: PanelAction, group: PostGroupInfo, isAuthor: boolean, extra: { requestedAt?: string; note?: string } = {}): PostGroupPatch {
        const legacy = UtilGroupVisibility.of(group.groupVisibility) === null;
        switch (action) {
            case 'requested':
                return {
                    publishRequest: { status: 'PENDING', requestedAt: extra.requestedAt ?? '', note: extra.note ?? null, rejectCount: group.publishRequest?.rejectCount ?? 0 },
                    canRequestPublish: false,
                };
            case 'canceled':
                return { publishRequest: null, canRequestPublish: isAuthor };
            case 'published':
            case 'approved':
                // 직접 공개·승인 — 이제 개별 공개 글이다. 옛 길(작성자 전환)은 되돌리기 단추만 남는다
                return legacy
                    ? { visibility: 'PUBLIC', canPublish: false, canUnpublish: true }
                    : {
                          visibility: 'PUBLIC',
                          publicBy: action === 'approved' ? 'APPROVED' : 'DIRECT',
                          publishRequest: null,
                          canPublish: false,
                          canUnpublish: true,
                          canDecidePublishRequest: false,
                          canRequestPublish: false,
                      };
            case 'unpublished':
                return legacy
                    ? { visibility: 'PRIVATE', canPublish: isAuthor, canUnpublish: false }
                    : { visibility: 'PRIVATE', publicBy: null, publishRequest: null, canPublish: !isAuthor, canUnpublish: false, canRequestPublish: isAuthor };
            case 'members-only':
                return { visibility: 'PRIVATE', publicBy: null, membersOnly: true, canMakeMembersOnly: false, canReleaseMembersOnly: true };
            case 'released':
                return { visibility: 'PUBLIC', publicBy: 'GROUP', membersOnly: false, canMakeMembersOnly: true, canReleaseMembersOnly: false };
        }
    }
}
