import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { BoardStaffActionKind, BoardStaffQuota, BoardStaffTools } from '~/composables/board/api/useBoardStaffApi.types';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilBoardStaffAction from '~/utils/util-board-staff-action';

const KINDS: ReadonlySet<string> = new Set(['POST_HIDE', 'POST_DELETE', 'COMMENT_DELETE']);

/** 종류별 키 꼬리 — 모르는 종류는 `OTHER` */
function suffix(kind: string): string {
    return KINDS.has(kind) ? kind : 'OTHER';
}

/**
 * 게시판 스텝 처리 화면의 계산이 낀 문구 — 처리 대화창·성공 토스트·게시판 운영 줄·처리 내역(`/boards/{slug}/actions`). 문구 본문은 `i18n/locales/{ko,en}/boardStaffAction.ts`.
 * 상수 문구는 컴포넌트가 `t('boardStaffAction.…')` 를 바로 부른다. 사유 코드 이름은 게시판 제재와 같은 말(`boardSanction.reason_*`)을 쓴다.
 * **처리자는 작성자 쪽 어디에도 보이지 않는다**(알림은 운영자 조치와 같은 문구 — 서버 몫).
 */
export default class UtilBoardStaffActionMessages {
    /**
     * 종류 이름 — 「글 숨김」「글 삭제」「댓글 삭제」
     * @param {Translate} t - 번역 함수.
     * @param {string} kind - 처리 종류.
     * @returns {string} 이름.
     */
    static kindLabel(t: Translate, kind: string): string {
        return t(`boardStaffAction.kind_${suffix(kind)}`);
    }

    /**
     * 사유 코드 이름 — 게시판 제재와 같은 말
     * @param {Translate} t - 번역 함수.
     * @param {string} code - 사유 코드.
     * @returns {string} 이름.
     */
    static reasonLabel(t: Translate, code: string): string {
        return UtilBoardSanctionMessages.reasonLabel(t, code);
    }

    /**
     * 처리 대화창 제목 — 「글 숨기기」「글 삭제」「댓글 삭제」
     * @param {Translate} t - 번역 함수.
     * @param {BoardStaffActionKind} kind - 처리 종류.
     * @returns {string} 제목.
     */
    static title(t: Translate, kind: BoardStaffActionKind): string {
        return t(`boardStaffAction.title_${suffix(kind)}`);
    }

    /**
     * 처리 단추 — 「숨기기」「삭제하기」
     * @param {Translate} t - 번역 함수.
     * @param {BoardStaffActionKind} kind - 처리 종류.
     * @returns {string} 글자.
     */
    static submitLabel(t: Translate, kind: BoardStaffActionKind): string {
        return t(`boardStaffAction.submit_${suffix(kind)}`);
    }

    /**
     * 확인 단계의 물음 — 「이 글을 숨길까요?」
     * @param {Translate} t - 번역 함수.
     * @param {BoardStaffActionKind} kind - 처리 종류.
     * @returns {string} 글자.
     */
    static confirmQuestion(t: Translate, kind: BoardStaffActionKind): string {
        return t(`boardStaffAction.confirm_${suffix(kind)}`);
    }

    /**
     * 작성자 알림 안내 — 숨기기 「숨기면 작성자에게 사유와 함께 알림이 가요」 · 삭제 「삭제하면 작성자에게 알림이 가요」(되돌릴 수 있다는 말은 하지 않는다)
     * @param {Translate} t - 번역 함수.
     * @param {BoardStaffActionKind} kind - 처리 종류.
     * @returns {string} 글자.
     */
    static notice(t: Translate, kind: BoardStaffActionKind): string {
        return t(`boardStaffAction.notice_${suffix(kind)}`);
    }

    /**
     * 오늘 남은 횟수 — 「오늘 남은 글 처리 7건 / 10건」 · 「오늘 남은 댓글 삭제 18건 / 20건」 · 한도가 null(운영자 회원 계정)이면 「하루 한도 없음」
     * @param {Translate} t - 번역 함수.
     * @param {string} kind - 처리 종류(글 처리면 글 한도).
     * @param {BoardStaffQuota | null} quota - 한도(null = 한도 없음).
     * @returns {string} 글자.
     */
    static quotaLine(t: Translate, kind: string, quota: BoardStaffQuota | null): string {
        if (!quota) return t('boardStaffAction.quotaUnlimited');
        const key = UtilBoardStaffAction.isPostKind(kind) ? 'boardStaffAction.quotaPost' : 'boardStaffAction.quotaComment';
        return t(key, { n: Math.max(0, quota.remaining), limit: quota.limit });
    }

    /**
     * 성공 토스트 — 「글을 숨겼어요 · 오늘 남은 글 처리 6건」 · 한도가 null(운영자 회원 계정)이면 남은 횟수 없이 「글을 숨겼어요」
     * @param {Translate} t - 번역 함수.
     * @param {BoardStaffActionKind} kind - 처리 종류.
     * @param {BoardStaffQuota | null} quota - 남은 한도(null = 한도 없음).
     * @returns {string} 글자.
     */
    static doneToast(t: Translate, kind: BoardStaffActionKind, quota: BoardStaffQuota | null): string {
        if (!quota) return t(`boardStaffAction.donePlain_${suffix(kind)}`);
        return t(`boardStaffAction.done_${suffix(kind)}`, { n: Math.max(0, quota.remaining) });
    }

    /**
     * 게시판 머리 운영 줄의 남은 횟수 — 「오늘 남은 처리 글 8 · 댓글 20 · 제재 5」(할 수 있는 것만) · 운영자 회원 계정이면 「하루 한도 없음」
     * 한도가 null 인 칸(한도 없음)은 남은 횟수에서 뺀다.
     * @param {Translate} t - 번역 함수.
     * @param {BoardStaffTools} tools - 게시판의 `viewer.staffTools`.
     * @returns {string} 글자. 할 수 있는 처리가 없으면 빈 글자.
     */
    static remainingLine(t: Translate, tools: BoardStaffTools): string {
        if (tools.operator) return t('boardStaffAction.barUnlimited');
        const parts: string[] = [];
        const a = tools.abilities;
        if ((a.postHide || a.postDelete) && tools.post) parts.push(t('boardStaffAction.barPost', { n: Math.max(0, tools.post.remaining) }));
        if (a.commentDelete && tools.comment) parts.push(t('boardStaffAction.barComment', { n: Math.max(0, tools.comment.remaining) }));
        if (a.sanction && tools.sanction) parts.push(t('boardStaffAction.barSanction', { n: Math.max(0, tools.sanction.remaining) }));
        return parts.length ? t('boardStaffAction.barRemaining', { parts: parts.join(' · ') }) : '';
    }

    /**
     * 게시판 머리 운영 줄의 남은 횟수가 하나라도 0 인가(빨갛게) — 한도가 null(한도 없음)인 칸은 다 쓸 일이 없다
     * @param {BoardStaffTools} tools - 게시판의 `viewer.staffTools`.
     * @returns {boolean} 그러면 true.
     */
    static anyExhausted(tools: BoardStaffTools): boolean {
        const a = tools.abilities;
        const out = (quota: BoardStaffQuota | null): boolean => Boolean(quota && quota.remaining <= 0);
        return ((a.postHide || a.postDelete) && out(tools.post)) || (a.commentDelete && out(tools.comment)) || (a.sanction && out(tools.sanction));
    }

    /**
     * 처리 내역의 처리자 — 「처리 봄바람 · 스텝」, 운영자 회원 계정이면 「… · 운영자」(매니저에게만 온다)
     * @param {Translate} t - 번역 함수.
     * @param {{ nickname: string; role: string }} actor - 처리자.
     * @returns {string} 글자.
     */
    static actorLine(t: Translate, actor: { nickname: string; role: string }): string {
        return UtilBoardSanctionMessages.issuerLine(t, actor);
    }

    /**
     * 되돌림 줄 — 「2026.11.04(수) 15:00 되돌림」
     * @param {Translate} t - 번역 함수.
     * @param {string} at - 되돌린 시각.
     * @param {DisplayTime} time - 표시 시각.
     * @returns {string} 글자.
     */
    static restoredLine(t: Translate, at: string, time: DisplayTime): string {
        return t('boardStaffAction.restoredLine', { date: time.full(at) });
    }

    /**
     * 되돌리기 대화창 설명 — 「봄바람 님의 글 숨김을 되돌려요」
     * @param {Translate} t - 번역 함수.
     * @param {string} name - 대상 작성자.
     * @param {string} kind - 처리 종류.
     * @returns {string} 글자.
     */
    static restoreText(t: Translate, name: string, kind: string): string {
        return t('boardStaffAction.restoreText', { name, kind: UtilBoardStaffActionMessages.kindLabel(t, kind) });
    }

    /**
     * 되돌리기 사유 칸 아래 안내
     * @param {Translate} t - 번역 함수.
     * @param {'EMPTY' | 'TOO_LONG'} problem - 문제.
     * @param {number} length - 글자 수.
     * @returns {string} 안내.
     */
    static restoreReasonProblem(t: Translate, problem: 'EMPTY' | 'TOO_LONG', length: number): string {
        if (problem === 'TOO_LONG') return t('boardSanction.textTooLong', { n: length, max: UtilBoardStaffAction.RESTORE_REASON_MAX });
        return t('boardStaffAction.restoreReasonRequired');
    }
}
