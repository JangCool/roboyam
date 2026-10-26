import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { GroupManageBlockedReason, GroupVisibility, GroupVisibilityPreview } from '~/composables/group/api/useGroupApi.types';
import type { PostGroupPublicBy } from '~/composables/board/api/usePostApi.types';
import type { PanelKind } from '~/utils/util-post-group-panel';
import type { PublishFailureKind } from '~/utils/util-group-visibility';
import UtilBoardMessages from '~/utils/util-board-messages';
import { ApiError } from '~/composables/common/useApiClient';

/**
 * 소모임 공개 범위·공개 요청(5단계)의 계산이 낀 문구 — 분기·자리표시자·조합이 있는 것만 둔다(상수 문구는 컴포넌트가 `t('groupVisibility.키')` 로 부른다).
 * 문구 본문은 `i18n/locales/{ko,en}/groupVisibility.ts`. **서버 `message` 가 있는 오류는 서버 문구를 그대로 쓴다**(`UtilBoardMessages.of`) — 여기서 갈래별 기본 말은 서버 문구가 빌 때만이다.
 */

/** 제목 + 설명 */
export interface VisibilityCopy {
    title: string;
    text: string;
}

/** 체크가 있는(또는 없는) 확인 시트의 문구 */
export interface VisibilitySheetCopy extends VisibilityCopy {
    ackLabel: string | null;
    confirmLabel: string;
}

/** 확인 대화상자 문구 */
export interface VisibilityConfirmCopy {
    title: string;
    message: string;
    confirmLabel: string;
}

/** 라디오 한 줄 */
export interface VisibilityOption {
    value: GroupVisibility;
    label: string;
    hint: string;
}

/** 패널 안내 줄 */
export interface PanelNotice {
    icon: string;
    title: string | null;
    text: string;
}

export default class UtilGroupVisibilityMessages {
    /* ── 이름·칩 ── */

    /** 문장 안의 이름 — 「공개 소모임」·「비공개 소모임」 */
    static nameOf(t: Translate, visibility: GroupVisibility): string {
        return t(visibility === 'PUBLIC' ? 'groupVisibility.namePublic' : 'groupVisibility.namePrivate');
    }

    /** 칩 글자 — 색만으로 가르지 않도록 늘 글자와 아이콘이 함께 있다 */
    static chipLabel(t: Translate, visibility: GroupVisibility): string {
        return t(visibility === 'PUBLIC' ? 'groupVisibility.chipPublic' : 'groupVisibility.chipPrivate');
    }

    /** 칩 아이콘 — 공개는 지구, 비공개는 자물쇠 */
    static chipIcon(visibility: GroupVisibility): string {
        return visibility === 'PUBLIC' ? 'pi pi-globe' : 'pi pi-lock';
    }

    /** 상세 머리 칩을 누르면 열리는 한 줄 설명 */
    static chipSheet(t: Translate, visibility: GroupVisibility): VisibilityCopy {
        return {
            title: UtilGroupVisibilityMessages.nameOf(t, visibility),
            text: t(visibility === 'PUBLIC' ? 'groupVisibility.sheetPublicText' : 'groupVisibility.sheetPrivateText'),
        };
    }

    /* ── 만들기 ── */

    /** 라디오 둘 — 비공개가 먼저(기본) */
    static createOptions(t: Translate): VisibilityOption[] {
        return [
            { value: 'PRIVATE', label: UtilGroupVisibilityMessages.nameOf(t, 'PRIVATE'), hint: t('groupVisibility.createPrivateHint') },
            { value: 'PUBLIC', label: UtilGroupVisibilityMessages.nameOf(t, 'PUBLIC'), hint: t('groupVisibility.createPublicHint') },
        ];
    }

    /** 공개를 골랐을 때의 안내 띠 — 승인제(일반 회원)이면 운영팀이 공개 범위도 함께 확인한다 */
    static createPublicNotice(t: Translate, approval: boolean): string {
        const parts = [t('groupVisibility.createPublicNotice')];
        if (approval) parts.push(t('groupVisibility.createPublicNoticeApproval'));
        parts.push(t('groupVisibility.createPublicNoticeTail'));
        return parts.join(' ');
    }

    /** 신청 완료 화면·내 개설 신청 줄의 한 줄 — 「공개 범위: 공개 소모임」 */
    static requestedLine(t: Translate, visibility: GroupVisibility): string {
        return t('groupVisibility.requestedLine', { name: UtilGroupVisibilityMessages.nameOf(t, visibility) });
    }

    /* ── 변경 시트 ── */

    /** 반대 값으로 바꾸는 단추 */
    static changeButton(t: Translate, to: GroupVisibility): string {
        return t(to === 'PUBLIC' ? 'groupVisibility.changeToPublic' : 'groupVisibility.changeToPrivate');
    }

    /** 비공개→공개 확인(체크 필수) */
    static toPublicSheet(t: Translate, p: GroupVisibilityPreview): VisibilitySheetCopy {
        const lines = [
            p.individuallyPublicCount > 0
                ? t('groupVisibility.toPublicPostsIndividual', { posts: p.postCount, comments: p.commentCount, n: p.individuallyPublicCount })
                : t('groupVisibility.toPublicPosts', { posts: p.postCount, comments: p.commentCount }),
            t('groupVisibility.toPublicTrust'),
            t('groupVisibility.toPublicSearch'),
            t('groupVisibility.notifyAll'),
        ];
        if (p.membersOnlyCount > 0) lines.push(t('groupVisibility.toPublicMembersOnly', { n: p.membersOnlyCount }));
        if (p.pendingRequestCount > 0) lines.push(t('groupVisibility.toPublicPending', { n: p.pendingRequestCount }));
        return { title: t('groupVisibility.toPublicTitle'), text: lines.join(' '), ackLabel: t('groupVisibility.changeAck'), confirmLabel: t('groupVisibility.changeToPublicConfirm') };
    }

    /** 공개→비공개 확인(체크 없음) */
    static toPrivateSheet(t: Translate, p: GroupVisibilityPreview): VisibilitySheetCopy {
        const lines = [
            p.individuallyPublicCount > 0 ? t('groupVisibility.toPrivateAllIndividual', { n: p.individuallyPublicCount }) : t('groupVisibility.toPrivateAll'),
            t('groupVisibility.toPrivateLinks'),
            t('groupVisibility.notifyAll'),
        ];
        if (p.membersOnlyCount > 0) lines.push(t('groupVisibility.toPrivateMembersOnly', { n: p.membersOnlyCount }));
        return { title: t('groupVisibility.toPrivateTitle'), text: lines.join(' '), ackLabel: null, confirmLabel: t('groupVisibility.changeToPrivateConfirm') };
    }

    /** 바꾼 뒤 토스트 */
    static changed(t: Translate, visibility: GroupVisibility): string {
        return t(visibility === 'PUBLIC' ? 'groupVisibility.changedToPublic' : 'groupVisibility.changedToPrivate');
    }

    /* ── 확인 시트(공개하기·직접 공개·다시 공개) ── */

    /** 승인(요청 줄·글 상세의 처리 배너) */
    static publishSheet(t: Translate): VisibilitySheetCopy {
        return { title: t('groupVisibility.publishTitle'), text: t('groupVisibility.publishText'), ackLabel: t('groupVisibility.ackPrivacy'), confirmLabel: t('groupVisibility.publish') };
    }

    /** 직접 공개 — 승인 시트에 작성자 알림 안내가 붙는다(처리자 본인의 글이면 없다) */
    static directSheet(t: Translate, authorName: string | null): VisibilitySheetCopy {
        const base = UtilGroupVisibilityMessages.publishSheet(t);
        return authorName ? { ...base, text: `${base.text} ${t('groupVisibility.directNotify', { name: authorName })}` } : base;
    }

    /** 다시 공개(멤버 전용으로 돌린 글) */
    static releaseSheet(t: Translate): VisibilitySheetCopy {
        return { title: t('groupVisibility.releaseTitle'), text: t('groupVisibility.releaseText'), ackLabel: t('groupVisibility.ackPrivacy'), confirmLabel: t('groupVisibility.release') };
    }

    /** 옛 패널(기능이 꺼진 서버)의 작성자 「전체 공개로 전환」 */
    static legacySheet(t: Translate): VisibilitySheetCopy {
        return { title: t('group.publishTitle'), text: t('group.publishText'), ackLabel: t('group.publishAck'), confirmLabel: t('group.publishConfirm') };
    }

    /** 멤버 전용으로 돌리기 확인(체크 없음 — 숨기는 일이라 쉽게 한다) */
    static membersOnlyConfirm(t: Translate, ownPost: boolean): VisibilityConfirmCopy {
        return {
            title: t('groupVisibility.membersOnlyTitle'),
            message: t(ownPost ? 'groupVisibility.membersOnlyMessageOwn' : 'groupVisibility.membersOnlyMessageOther'),
            confirmLabel: t('groupVisibility.makeMembersOnly'),
        };
    }

    /** 공개 요청 취소 확인 */
    static cancelConfirm(t: Translate): VisibilityConfirmCopy {
        return { title: t('groupVisibility.cancelTitle'), message: t('groupVisibility.cancelMessage'), confirmLabel: t('groupVisibility.requestCancel') };
    }

    /* ── 글 상세 패널 ── */

    /** 패널 맨 위 안내 — 모습마다 */
    static panelNotice(t: Translate, kind: PanelKind, publicBy: PostGroupPublicBy | null | undefined, legacyVisibility: 'PUBLIC' | 'PRIVATE'): PanelNotice {
        switch (kind) {
            case 'PRIVATE':
            case 'REQUESTING':
            case 'REJECTED':
                return { icon: 'pi pi-lock', title: null, text: t('groupVisibility.privateNotice') };
            case 'GRANTED': {
                const by = publicBy === 'DIRECT' ? t('groupVisibility.grantedByDirect') : publicBy === 'APPROVED' ? t('groupVisibility.grantedByApproved') : '';
                return { icon: 'pi pi-globe', title: t('groupVisibility.grantedTitle'), text: by ? `· ${by}` : '' };
            }
            case 'GROUP_PUBLIC':
                return { icon: 'pi pi-globe', title: null, text: t('groupVisibility.publicGroupNotice') };
            case 'MEMBERS_ONLY':
                return { icon: 'pi pi-lock', title: null, text: t('groupVisibility.membersOnlyNotice') };
            default:
                // 옛 패널 — 글의 공개 범위 안내 그대로
                return legacyVisibility === 'PUBLIC'
                    ? { icon: 'pi pi-globe', title: t('group.postNoticePublicTitle'), text: t('group.postNoticePublicText') }
                    : { icon: 'pi pi-lock', title: t('group.postNoticePrivateTitle'), text: t('group.postNoticePrivateText') };
        }
    }

    /** 쓰기 자격이 없을 때 단추 대신 보이는 안내 — 처리자(승인·거절·돌리기)인지 작성자(요청)인지로 갈린다 */
    static blockedText(t: Translate, reason: GroupManageBlockedReason, processor: boolean): string {
        if (reason === 'BUSINESS_ACCOUNT_RESTRICTED') return t('groupVisibility.blockedBusiness');
        if (reason === 'EMAIL_NOT_VERIFIED') return t(processor ? 'groupVisibility.blockedEmailProcess' : 'groupVisibility.blockedEmailRequest');
        return t(processor ? 'groupVisibility.blockedRestrictedProcess' : 'groupVisibility.blockedRestrictedRequest');
    }

    /* ── 실패 ── */

    /**
     * 실패 문구 — 서버 문구가 먼저(`ApiError.message`), 없으면 갈래별 기본 말. 네트워크 오류는 공통 문구.
     * 열어 둔 채 맡은 일이 빠졌으면(`permission-denied`) 서버 문구보다 화면 문구(「모임장이 이 일을 맡기지 않았어요」)가 뜻이 분명하다.
     */
    static failure(t: Translate, kind: PublishFailureKind, e: unknown): string {
        if (kind === 'permission-denied') return t('groupManager.revoked');
        if (kind === 'request-gone') return t('groupVisibility.requestCanceled');
        if (kind === 'decided') return e instanceof ApiError && e.message ? e.message : t('groupVisibility.alreadyDecided');
        return UtilBoardMessages.of(t, e);
    }

    /* ── 요청 목록 ── */

    /** 화면 제목 — 「공개 요청 3」 */
    static requestsTitle(t: Translate, count: number): string {
        return count > 0 ? t('groupVisibility.requestsTitleCount', { n: count }) : t('groupVisibility.rowRequests');
    }
}
