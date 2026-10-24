import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { AdminPostItem, PostBulkResult, PostModerationActor, PostStateFilter } from '~/composables/post/api/usePostApi.types';
import type { PostMoveProblem, PostReasonProblem } from '~/utils/util-post';
import UtilPost from '~/utils/util-post';

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
