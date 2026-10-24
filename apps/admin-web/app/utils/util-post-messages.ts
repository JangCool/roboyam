import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { AdminPostItem, PostBulkResult, PostModerationActor, PostStateFilter } from '~/composables/post/api/usePostApi.types';
import type { PostMoveProblem, PostReasonProblem } from '~/utils/util-post';
import UtilPost from '~/utils/util-post';
import UtilReportMessages from '~/utils/util-report-messages';

/** 조치 기록 덧붙임의 한 줄 — `reportCaseId` 가 있으면 신고 관리(`/reports?report=`)로 잇는 링크 */
export interface PostEventDetailLine {
    key: string;
    text: string;
    reportCaseId?: string;
}

/** 덧붙임의 게시판·말머리 ID → 이름(모르면 null — 화면은 `#ID`) */
export interface PostEventDetailNames {
    board: (id: string) => string | null;
    prefix: (id: string) => string | null;
}

const HIDDEN_REASONS = ['REPORTED', 'ILLEGAL', 'RIGHTS', 'STAFF'];

function text(value: unknown): string | null {
    return typeof value === 'string' && value.trim() !== '' ? value : null;
}

function count(value: unknown): number | null {
    return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/**
 * 게시글 관리 화면의 계산이 낀 문구 — 숨김 줄·사유/이동 문제·일괄 결과·조치 기록의 처리자. 상수 문구는 화면이 `t('post.*')` 로 부른다.
 */
export default class UtilPostMessages {
    /** 사유 칸 문제 */
    static reasonProblem(t: Translate, problem: PostReasonProblem | null): string {
        if (problem === 'REQUIRED') return t('post.reasonRequired');
        if (problem === 'TOO_LONG') return t('post.reasonTooLong', { max: UtilPost.REASON_MAX });
        return '';
    }

    static moveProblem(t: Translate, problem: PostMoveProblem | null): string {
        if (problem === 'BOARD_REQUIRED') return t('post.moveBoardRequired');
        if (problem === 'SAME_LOCATION') return t('post.moveSame');
        return '';
    }

    /**
     * 상태 알약 아래의 작은 줄들 — 숨김 까닭·시각(기간 숨김이면 풀리는 시각), 지운 시각, 삭제 예정, 원문 파기.
     * @returns {string[]} 보일 줄(없으면 빈 목록).
     */
    static stateLines(t: Translate, time: DisplayTime, item: AdminPostItem): string[] {
        const lines: string[] = [];
        if (item.state === 'DELETED') {
            if (item.deletedAt) lines.push(t('post.deletedAt', { at: time.listLabel(item.deletedAt) }));
        } else if (item.state === 'HIDDEN') {
            const reason = item.hiddenReason ? t(`post.hiddenReason.${item.hiddenReason}`) : t('post.hiddenReasonUnknown');
            lines.push(item.hiddenAt ? t('post.hiddenLine', { reason, at: time.listLabel(item.hiddenAt) }) : reason);
            if (item.hiddenUntil) lines.push(t('post.hiddenUntil', { at: time.listLabel(item.hiddenUntil) }));
        }
        if (item.deleteScheduledAt && item.state !== 'DELETED') lines.push(t('post.deleteScheduled', { at: time.listLabel(item.deleteScheduledAt) }));
        if (item.purged) lines.push(t('post.purged'));
        return lines;
    }

    /**
     * 조치 기록의 덧붙임(서버 `AdminPostEventView.detail`)을 문구 줄로 — 키 원문은 보이지 않는다. 아는 키만 풀고 **모르는 키는 숨긴다**.
     * 이동(`fromBoardId`·`toBoardId` — 게시판 이름, `fromPrefixId`·`toPrefixId` — 말머리 이름), 포인트 회수·복구, 작성자 알림, 사유 코드(신고 사유 이름), 자동 숨김 확정,
     * 신고 무시로 복구, 일괄, 고정 해제, 숨김 까닭·기한, 투표(질문·참여 수 — `pollId` 는 숨김), 신고 사건(`reportCaseId` → 링크).
     */
    static eventDetailLines(t: Translate, time: DisplayTime, detail: Record<string, unknown> | null, names: PostEventDetailNames): PostEventDetailLine[] {
        if (!detail) return [];
        const d = detail;
        const lines: PostEventDetailLine[] = [];
        const boardName = (id: string | null) => (id ? (names.board(id) ?? `#${id}`) : t('post.eventDetail.noPrefix'));
        const prefixName = (id: string | null) => (id ? (names.prefix(id) ?? `#${id}`) : t('post.eventDetail.noPrefix'));

        const fromBoard = text(d.fromBoardId);
        const toBoard = text(d.toBoardId);
        if ((fromBoard || toBoard) && fromBoard !== toBoard) lines.push({ key: 'board', text: t('post.eventDetail.board', { from: boardName(fromBoard), to: boardName(toBoard) }) });
        if ('fromPrefixId' in d || 'toPrefixId' in d) {
            const fromPrefix = text(d.fromPrefixId);
            const toPrefix = text(d.toPrefixId);
            if (fromPrefix !== toPrefix) lines.push({ key: 'prefix', text: t('post.eventDetail.prefix', { from: prefixName(fromPrefix), to: prefixName(toPrefix) }) });
        }
        const revoked = count(d.revokedPoints);
        if (revoked) lines.push({ key: 'revokedPoints', text: t('post.eventDetail.revokedPoints', { n: revoked }) });
        const restored = count(d.restoredPoints);
        if (restored) lines.push({ key: 'restoredPoints', text: t('post.eventDetail.restoredPoints', { n: restored }) });
        if (typeof d.notifyAuthor === 'boolean') lines.push({ key: 'notifyAuthor', text: d.notifyAuthor ? t('post.eventDetail.notifyAuthor') : t('post.eventDetail.notNotifyAuthor') });
        const reasonCode = text(d.reasonCode);
        if (reasonCode) lines.push({ key: 'reasonCode', text: t('report.eventReason', { reason: UtilReportMessages.reasonName(t, reasonCode) }) });
        if (d.keepHidden === true) lines.push({ key: 'keepHidden', text: t('post.eventDetail.keepHidden') });
        if (d.restoredByDismiss === true) lines.push({ key: 'restoredByDismiss', text: t('post.eventDetail.restoredByDismiss') });
        if (d.bulk === true) lines.push({ key: 'bulk', text: t('post.eventDetail.bulk') });
        if (d.unpinned === true) lines.push({ key: 'unpinned', text: t('post.eventDetail.unpinned') });
        const hiddenReason = text(d.hiddenReason);
        if (hiddenReason && HIDDEN_REASONS.includes(hiddenReason)) lines.push({ key: 'hiddenReason', text: t('post.eventDetail.hiddenReason', { reason: t(`post.hiddenReason.${hiddenReason}`) }) });
        const hiddenUntil = text(d.hiddenUntil);
        if (hiddenUntil) lines.push({ key: 'hiddenUntil', text: t('post.eventDetail.hiddenUntil', { at: time.listLabel(hiddenUntil) }) });
        const question = text(d.question);
        if (question) lines.push({ key: 'poll', text: t('post.eventDetail.poll', { question }) });
        const voters = count(d.voterCount);
        if (voters !== null) lines.push({ key: 'voterCount', text: t('post.eventDetail.pollVoters', { n: voters }) });
        const reportCaseId = text(d.reportCaseId);
        if (reportCaseId) lines.push({ key: 'reportCaseId', text: t('post.eventDetail.reportCase'), reportCaseId });
        return lines;
    }

    /** 작성자 이름 — 비회원·탈퇴 표시 */
    static author(t: Translate, item: Pick<AdminPostItem, 'author'>): string {
        const a = item.author;
        if (a.kind === 'GUEST') return t('post.guestAuthor', { name: a.name });
        if (a.withdrawn) return t('post.withdrawnAuthor', { name: a.name });
        return a.name;
    }

    /** 조치 성공 토스트 */
    static done(t: Translate, kind: string, notified: boolean): string {
        if (kind === 'HIDE' && notified) return t('post.doneHideNotified');
        return t(`post.done.${kind}`);
    }

    /** 일괄 처리 결과 한 줄(토스트·대화창) */
    static bulkSummary(t: Translate, result: PostBulkResult): string {
        const done = result.done.length;
        const failed = result.failed.length;
        if (failed === 0) return t('post.bulkAllDone', { n: done });
        if (done === 0) return t('post.bulkAllFailed', { n: failed });
        return t('post.bulkPartial', { done, failed });
    }

    /** 일괄 처리에서 못 한 까닭 — 아는 코드는 문구로, 모르는 코드는 코드 그대로 */
    static bulkCode(t: Translate, code: string | null): string {
        if (!code) return '';
        const known = ['POST_STATE_INVALID', 'POST_NOT_FOUND', 'PERMISSION_DENIED', 'POST_PURGED'];
        return known.includes(code) ? t(`post.bulkCode.${code}`) : code;
    }

    /** 조치 기록의 처리자 */
    static actor(t: Translate, actor: PostModerationActor): string {
        if (actor.kind === 'SYSTEM') return t('post.actorSystem');
        return actor.name ?? t('post.actorGone');
    }
}
