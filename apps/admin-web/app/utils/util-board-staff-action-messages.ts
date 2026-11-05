import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { AdminBoardStaffAction, BoardStaffActionActor, BoardStaffActionRestored, BoardStaffActionTargetAuthor } from '~/composables/sanction/api/useBoardStaffActionApi.types';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilBoardStaffAction from '~/utils/util-board-staff-action';

/**
 * 스텝 처리 감독 화면의 계산이 낀 문구 — 문구 본문은 `i18n/locales/{ko,en}/staffAction.ts`, 여기는 "어느 키를 고를지"만 안다.
 * 자리 이름(매니저·스텝)은 게시판 제재(`UtilBoardSanctionMessages.roleName`)와 같다. 되돌린 사유·서버 오류 문구는 그대로 쓴다.
 */
export default class UtilBoardStaffActionMessages {
    /** 종류 이름 — 「글 숨기기」「댓글 삭제」 */
    static kindName(t: Translate, kind: string): string {
        return UtilBoardStaffAction.KINDS.includes(kind as never) ? t(`staffAction.kind.${kind}`) : kind;
    }

    /** 사유 코드 이름 — 모르는 코드는 코드 그대로 */
    static reasonName(t: Translate, code: string): string {
        return UtilBoardStaffAction.REASONS.includes(code as never) ? t(`staffAction.reason.${code}`) : code;
    }

    /** 대상의 지금 상태 */
    static stateName(t: Translate, state: string): string {
        return UtilBoardStaffAction.STATES.includes(state as never) ? t(`staffAction.state.${state}`) : state;
    }

    static roleName(t: Translate, role: string): string {
        return UtilBoardSanctionMessages.roleName(t, role);
    }

    /** 처리자 자리 딱지 — 운영자 회원 계정이면 「운영자 · 운영팀 민지」 */
    static roleLabel(t: Translate, actor: BoardStaffActionActor): string {
        return UtilBoardSanctionMessages.roleLabel(t, actor);
    }

    /** 처리자 닉네임 — 탈퇴 등으로 없으면 「(알 수 없음)」 */
    static actorName(t: Translate, actor: BoardStaffActionActor | null): string {
        return actor?.nickname ?? t('staffAction.actorGone');
    }

    /** 처리자 「숲지기 · 매니저」, 운영자 회원 계정이면 「숲지기 (운영자 · 운영팀 민지)」 */
    static actor(t: Translate, actor: BoardStaffActionActor | null): string {
        if (!actor) return t('staffAction.actorGone');
        const name = UtilBoardStaffActionMessages.actorName(t, actor);
        if (actor.role === 'OPERATOR') return t('staffAction.actorOperatorValue', { name, role: UtilBoardStaffActionMessages.roleLabel(t, actor) });
        return t('staffAction.actorValue', { name, role: UtilBoardStaffActionMessages.roleName(t, actor.role) });
    }

    /** 대상 작성자 — 회원 닉네임, 비회원은 「이름(비회원)」 */
    static author(t: Translate, author: BoardStaffActionTargetAuthor): string {
        const name = author.name ?? t('staffAction.authorGone');
        return author.kind === 'GUEST' ? t('staffAction.authorGuest', { name }) : name;
    }

    /** 대상 한 줄 — 글이면 제목, 댓글이면 댓글 앞부분 */
    static targetTitle(t: Translate, action: Pick<AdminBoardStaffAction, 'kind' | 'target'>): string {
        if (action.kind === 'COMMENT_DELETE') return action.target.excerpt || t('staffAction.excerptNone');
        return action.target.title || t('staffAction.titleNone');
    }

    /** 되돌린 사람 「볼트너트(매니저)」「운영팀 민지(운영자)」 */
    static restoredBy(t: Translate, restored: BoardStaffActionRestored): string {
        return t('staffAction.restoredByValue', { name: restored.by.name ?? t('staffAction.actorGone'), who: t(`staffAction.restoredBy.${restored.by.kind}`) });
    }

    /** 되돌리기가 거절된 까닭 — 아는 코드는 화면 문구, 모르면 서버 문구 그대로 */
    static restoreProblem(t: Translate, code: string, serverMessage: string): string {
        const known = ['BOARD_STAFF_ACTION_RESTORED', 'POST_STATE_INVALID', 'POST_PURGED'];
        return known.includes(code) ? t(`staffAction.error.${code}`) : serverMessage;
    }
}
