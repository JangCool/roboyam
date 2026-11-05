import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { BoardSanctionKind, BoardSanctionPreview, BoardStaffQuota, BoardWriteRestriction } from '~/composables/board/api/useBoardStaffApi.types';
import type { MyBoardSanction } from '~/composables/sanction/api/useSanctionApi.types';
import type { BoardSanctionTextProblem } from '~/utils/util-board-sanction';
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilSanction from '~/utils/util-sanction';
import type { SanctionHistoryRow, SanctionSummaryRow } from '~/utils/util-sanction-messages';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

/** 내 게시판 제재 한 줄(`/sanction` 「게시판 제재」 구획) — 사이트 제재 이력 줄에 게시판 이름·사유 코드·확인 여부를 더했다 */
export interface MyBoardSanctionRow extends SanctionHistoryRow {
    boardName: string;
    boardSlug: string;
    /** 사유 코드 이름(「스팸·광고」) */
    reasonLabel: string;
}

/**
 * 게시판 제재 화면의 계산이 낀 문구 — 매니저·스텝의 제재 대화창·제재 내역, 쓰기 제한 안내, 내 게시판 제재(`/sanction`). 문구 본문은 `i18n/locales/{ko,en}/boardSanction.ts`.
 * 상수 문구는 컴포넌트가 `t('boardSanction.…')` 를 바로 부른다. 날짜는 표시 값(`useDisplayTime`)으로 그린다. **처리자 신원은 받은 회원 쪽 문구 어디에도 넣지 않는다**(계약 결정 7).
 */
export default class UtilBoardSanctionMessages {
    /**
     * 단계 이름 — 「경고」「쓰기 제한 3일」
     * @param {Translate} t - 번역 함수.
     * @param {string} kind - 단계.
     * @returns {string} 이름.
     */
    static kindLabel(t: Translate, kind: string): string {
        if (kind === 'WARNING') return t('boardSanction.kindWarning');
        const days = UtilBoardSanction.daysOf(kind);
        return days ? t('boardSanction.kindRestrict', { days }) : t('boardSanction.kindOther');
    }

    /**
     * 사유 코드 이름 — 모르는 코드는 「기타」
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} code - 사유 코드.
     * @returns {string} 이름. 코드가 없으면 빈 글자.
     */
    static reasonLabel(t: Translate, code: string | null | undefined): string {
        if (!code) return '';
        return UtilBoardSanction.isReasonCode(code) ? t(`boardSanction.reason_${code}`) : t('boardSanction.reason_OTHER');
    }

    /**
     * 사유 한 줄 — 「스팸·광고 · 덧붙임」(덧붙임이 없으면 코드 이름만)
     * @param {Translate} t - 번역 함수.
     * @param {string} code - 사유 코드.
     * @param {string | null} text - 덧붙임.
     * @returns {string} 글자.
     */
    static reasonLine(t: Translate, code: string, text: string | null): string {
        const label = UtilBoardSanctionMessages.reasonLabel(t, code);
        return text?.trim() ? t('boardSanction.reasonWithText', { reason: label, text: text.trim() }) : label;
    }

    /**
     * 상태 이름 — 경고는 「유효」, 쓰기 제한은 「진행 중」, 그 밖 「끝남」「해제됨」「무효」
     * @param {Translate} t - 번역 함수.
     * @param {string} kind - 단계.
     * @param {string} status - 상태.
     * @returns {string} 이름.
     */
    static statusLabel(t: Translate, kind: string, status: string): string {
        if (status === 'ACTIVE') return kind === 'WARNING' ? t('boardSanction.statusValid') : t('boardSanction.statusActive');
        if (status === 'ENDED') return t('boardSanction.statusEnded');
        if (status === 'LIFTED') return t('boardSanction.statusLifted');
        if (status === 'VOIDED') return t('boardSanction.statusVoided');
        return '';
    }

    /**
     * 끝 시각 글자 — 「2026.11.07(토) 15:00」
     * @param {string | null | undefined} at - 시각.
     * @param {DisplayTime} time - 표시 시각.
     * @returns {string} 글자.
     */
    static untilText(at: string | null | undefined, time: DisplayTime): string {
        return UtilSanctionMessages.releaseText(at, time);
    }

    /**
     * 대화창의 그 게시판 제재 현황 — 「경고 1 · 쓰기 제한 0」(+ 진행 중이면 「쓰기 제한 중 · …까지」는 따로)
     * @param {Translate} t - 번역 함수.
     * @param {BoardSanctionPreview} preview - 현황.
     * @returns {string} 글자.
     */
    static countsLine(t: Translate, preview: BoardSanctionPreview): string {
        return t('boardSanction.countsLine', { warnings: preview.counts.warnings, restrictions: preview.counts.restrictions });
    }

    /**
     * 권장 설명 한 줄 — 「권장: 쓰기 제한 1일 — 이 게시판에서 받은 제재 1건을 기준으로 했어요. 다른 단계도 고를 수 있어요.」
     * @param {Translate} t - 번역 함수.
     * @param {BoardSanctionKind} kind - 권장 단계.
     * @param {BoardSanctionPreview} preview - 현황.
     * @returns {string} 글자.
     */
    static recommendLine(t: Translate, kind: BoardSanctionKind, preview: BoardSanctionPreview): string {
        const total = preview.counts.warnings + preview.counts.restrictions;
        const label = UtilBoardSanctionMessages.kindLabel(t, kind);
        return total === 0 ? t('boardSanction.recommendFirst', { kind: label }) : t('boardSanction.recommendLine', { kind: label, n: total });
    }

    /**
     * 오늘 남은 횟수 — 「오늘 남은 제재 7건 / 10건」 · 한도가 null(운영자 회원 계정)이면 「하루 한도 없음」
     * @param {Translate} t - 번역 함수.
     * @param {BoardStaffQuota | null} quota - 한도(null = 한도 없음).
     * @returns {string} 글자.
     */
    static quotaLine(t: Translate, quota: BoardStaffQuota | null): string {
        if (!quota) return t('boardSanction.quotaUnlimited');
        return t('boardSanction.quotaLine', { n: Math.max(0, quota.remaining), limit: quota.limit });
    }

    /**
     * 처리자 줄 — 「처리 봄바람 · 스텝」 · 운영자 회원 계정(`OPERATOR`)이면 「처리 봄바람 · 운영자」(매니저에게만 온다). 처리 내역·제재 내역이 같이 쓴다
     * @param {Translate} t - 번역 함수.
     * @param {{ nickname: string; role: string }} who - 처리자.
     * @returns {string} 글자.
     */
    static issuerLine(t: Translate, who: { nickname: string; role: string }): string {
        const role = who.role === 'MANAGER' ? t('boardSanction.roleManager') : who.role === 'OPERATOR' ? t('boardSanction.roleOperator') : t('boardSanction.roleStaff');
        return t('boardSanction.issuerLine', { name: who.nickname, role });
    }

    /**
     * 덧붙임 칸 아래 안내
     * @param {Translate} t - 번역 함수.
     * @param {BoardSanctionTextProblem} problem - 문제.
     * @param {number} length - 글자 수.
     * @returns {string} 안내.
     */
    static textProblem(t: Translate, problem: BoardSanctionTextProblem, length: number): string {
        if (problem === 'TOO_LONG') return t('boardSanction.textTooLong', { n: length, max: UtilBoardSanction.TEXT_MAX });
        return t('boardSanction.textOtherRequired', { min: UtilBoardSanction.OTHER_TEXT_MIN });
    }

    /**
     * 쓰기 제한 안내의 첫 줄 — 「이 게시판에서 11월 7일(토) 15:00까지 글·댓글 쓰기가 제한돼요」
     * @param {Translate} t - 번역 함수.
     * @param {BoardWriteRestriction} restriction - 제한.
     * @param {DisplayTime} time - 표시 시각.
     * @returns {string} 글자.
     */
    static restrictionTitle(t: Translate, restriction: Pick<BoardWriteRestriction, 'until'>, time: DisplayTime): string {
        const until = UtilBoardSanctionMessages.untilText(restriction.until, time);
        return until ? t('boardSanction.restrictedUntil', { date: until }) : t('boardSanction.restrictedPlain');
    }

    /**
     * 성공 토스트 — 「봄바람 님에게 쓰기 제한 3일을 줬어요 · 오늘 남은 제재 6건」 · 한도가 null(운영자 회원 계정)이면 남은 횟수 없이
     * @param {Translate} t - 번역 함수.
     * @param {string} nickname - 대상.
     * @param {string} kind - 준 단계.
     * @param {BoardStaffQuota | null} quota - 남은 한도(null = 한도 없음).
     * @returns {string} 글자.
     */
    static issuedToast(t: Translate, nickname: string, kind: string, quota: BoardStaffQuota | null): string {
        if (!quota) return t('boardSanction.issuedToastPlain', { name: nickname, kind: UtilBoardSanctionMessages.kindLabel(t, kind) });
        return t('boardSanction.issuedToast', { name: nickname, kind: UtilBoardSanctionMessages.kindLabel(t, kind), n: Math.max(0, quota.remaining) });
    }

    /**
     * 내 게시판 제재 한 줄(`/sanction`) — 처리자 없음
     * @param {Translate} t - 번역 함수.
     * @param {MyBoardSanction} s - 게시판 제재.
     * @param {DisplayTime} time - 표시 시각.
     * @param {string} boardName - 화면 언어의 게시판 이름(`localizedName`).
     * @returns {MyBoardSanctionRow} 그릴 값.
     */
    static myRow(t: Translate, s: MyBoardSanction, time: DisplayTime, boardName: string): MyBoardSanctionRow {
        const appeal = s.appeal;
        return {
            id: s.id,
            title: UtilBoardSanctionMessages.kindLabel(t, s.kind),
            kindTone: UtilBoardSanction.kindTone(s.kind),
            statusLabel: UtilBoardSanctionMessages.statusLabel(t, s.kind, s.status),
            statusTone: UtilBoardSanction.statusTone(s.kind, s.status),
            voided: s.status === 'VOIDED',
            reason: s.reasonText?.trim() ?? '',
            reasonLabel: UtilBoardSanctionMessages.reasonLabel(t, s.reasonCode),
            dateText: time.date(s.createdAt),
            periodText: s.endsAt ? UtilSanctionMessages.period(s.startsAt, s.endsAt, time) : null,
            needsConfirm: UtilSanction.boardNeedsConfirm(s),
            appeal: appeal
                ? { no: appeal.no, statusLabel: UtilSanctionMessages.appealStatusLabel(t, appeal.status), tone: UtilSanction.appealTone(appeal.status), answer: appeal.answer?.trim() ? appeal.answer : null }
                : null,
            canAppeal: UtilSanction.canAppeal(s),
            appealDeadlineText: UtilSanction.canAppeal(s) ? UtilSanctionMessages.releaseText(s.appealDeadline, time) || null : null,
            appealExpired: UtilSanction.missedAppeal(s),
            boardName,
            boardSlug: s.board.slug,
        };
    }

    /**
     * 게시판 제재 이의제기 대상 요약 — 게시판 · 제재 · 기간(또는 처리일) · 사유 · 마감
     * @param {Translate} t - 번역 함수.
     * @param {MyBoardSanction} s - 게시판 제재.
     * @param {DisplayTime} time - 표시 시각.
     * @param {string} boardName - 화면 언어의 게시판 이름.
     * @returns {SanctionSummaryRow[]} 줄들.
     */
    static appealTargetRows(t: Translate, s: MyBoardSanction, time: DisplayTime, boardName: string): SanctionSummaryRow[] {
        const rows: SanctionSummaryRow[] = [
            { label: t('boardSanction.rowBoard'), value: boardName },
            { label: t('sanction.rowSanction'), value: UtilBoardSanctionMessages.kindLabel(t, s.kind) },
        ];
        if (s.endsAt) rows.push({ label: t('sanction.rowTerm'), value: UtilSanctionMessages.period(s.startsAt, s.endsAt, time) });
        else rows.push({ label: t('sanction.rowDate'), value: time.date(s.createdAt) });
        rows.push({ label: t('sanction.rowReason'), value: UtilBoardSanctionMessages.reasonLine(t, s.reasonCode, s.reasonText) });
        rows.push({ label: t('sanction.rowAppealDeadline'), value: UtilSanctionMessages.releaseText(s.appealDeadline, time) });
        return rows.filter((row) => row.value !== '');
    }
}
