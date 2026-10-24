import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { ReportAuthorRef, ReportBulkResult, ReportCaseItem, ReportEvent, ReportReasonCode, ReportTarget } from '~/composables/report/api/useReportApi.types';
import type { ReportActKind, ReportNoteProblem } from '~/utils/util-report';
import UtilReport from '~/utils/util-report';

/**
 * 신고 관리 화면의 계산이 낀 문구 — 대상 상태 줄·작성자·조치 글 문제·일괄 결과·처리 기록. 상수 문구는 화면이 `t('report.*')` 로 부른다.
 * 신고 사유 이름은 게시판 신고 정책 화면의 사유 이름(`board.reason.*`)을 그대로 쓴다(같은 코드 체계).
 */
export default class UtilReportMessages {
    /** 신고 사유 이름 — 게시판 신고 정책의 사유 표와 같은 문구. 모르는 코드는 코드 그대로 */
    static reasonName(t: Translate, code: ReportReasonCode | string | null | undefined): string {
        if (!code) return '';
        return (UtilReport.REASONS as readonly string[]).includes(code) ? t(`board.reason.${code}`) : code;
    }

    /** 조치 글 칸 문제 */
    static noteProblem(t: Translate, problem: ReportNoteProblem | null): string {
        if (problem === 'REQUIRED') return t('report.noteRequired');
        if (problem === 'TOO_LONG') return t('report.noteTooLong', { max: UtilReport.NOTE_MAX });
        return '';
    }

    /**
     * 대상 상태 알약 아래의 작은 줄 — 숨김 까닭, 임시조치 기한.
     * @returns {string[]} 보일 줄(없으면 빈 목록).
     */
    static targetLines(t: Translate, time: DisplayTime, target: ReportTarget): string[] {
        const lines: string[] = [];
        if (target.state === 'HIDDEN') {
            lines.push(target.hiddenReason ? t(`report.hiddenReason.${target.hiddenReason}`) : t('report.hiddenReasonUnknown'));
            if (target.hiddenUntil) lines.push(t('report.hiddenUntil', { at: time.listLabel(target.hiddenUntil) }));
        }
        return lines;
    }

    /** 작성자 이름 — 비회원·탈퇴 표시 */
    static author(t: Translate, author: Pick<ReportAuthorRef, 'kind' | 'name' | 'withdrawn'>): string {
        if (author.kind === 'GUEST') return t('report.guestAuthor', { name: author.name });
        if (author.withdrawn) return t('report.withdrawnAuthor', { name: author.name });
        return author.name;
    }

    /** 신고자 이름 — 탈퇴 표시 */
    static reporter(t: Translate, reporter: { name: string; withdrawn: boolean }): string {
        return reporter.withdrawn ? t('report.withdrawnAuthor', { name: reporter.name }) : reporter.name;
    }

    /** 사건 번호 */
    static caseNo(t: Translate, item: Pick<ReportCaseItem, 'caseNo'>): string {
        return t('report.caseNoValue', { no: String(item.caseNo) });
    }

    /** 처리 성공 토스트 */
    static done(t: Translate, kind: ReportActKind, falseReport: boolean): string {
        if (kind === 'DISMISS' && falseReport) return t('report.doneDismissFalse');
        return t(`report.done.${kind}`);
    }

    /** 일괄 무시 결과 한 줄(토스트·대화창) */
    static bulkSummary(t: Translate, result: ReportBulkResult): string {
        const done = result.done.length;
        const failed = result.failed.length;
        if (failed === 0) return t('report.bulkAllDone', { n: done });
        if (done === 0) return t('report.bulkAllFailed', { n: failed });
        return t('report.bulkPartial', { done, failed });
    }

    /** 일괄 처리에서 못 한 까닭 — 아는 코드는 문구로, 모르는 코드는 코드 그대로 */
    static bulkCode(t: Translate, code: string | null): string {
        if (!code) return '';
        const known = ['REPORT_CASE_CLOSED', 'REPORT_CASE_NOT_FOUND', 'REPORT_ACTION_INVALID', 'PERMISSION_DENIED'];
        return known.includes(code) ? t(`report.bulkCode.${code}`) : code;
    }

    /** 처리 기록의 한 일을 한 사람 */
    static actor(t: Translate, actor: ReportEvent['actor']): string {
        if (!actor || actor.kind === 'SYSTEM') return t('report.actorSystem');
        if (actor.kind === 'MEMBER') return actor.name ?? t('report.actorMember');
        return actor.name ?? t('report.actorGone');
    }

    /** 처리 기록 덧붙임 중 문구로 푸는 줄 — 조치 · 사유 코드 · 허위 표시 */
    static eventLines(t: Translate, ev: ReportEvent): string[] {
        const d = ev.detail ?? {};
        const lines: string[] = [];
        if (typeof d.action === 'string' && ['HIDE', 'DELETE', 'KEEP_HIDDEN'].includes(d.action)) lines.push(t(`report.actionName.${d.action}`));
        if (typeof d.reasonCode === 'string') lines.push(t('report.eventReason', { reason: UtilReportMessages.reasonName(t, d.reasonCode) }));
        if (typeof d.reason === 'string' && ev.type === 'REPORTED') lines.push(UtilReportMessages.reasonName(t, d.reason));
        if (d.falseReport === true) lines.push(t('report.falseReportMarked'));
        return lines;
    }

    /** 작성자 카드의 최근 90일 결과 */
    static closedCases(t: Translate, closed: { resolved: number; dismissed: number }): string {
        return t('report.authorClosedCases', { resolved: closed.resolved, dismissed: closed.dismissed });
    }
}
