import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { BoardSanctionIssuer, BoardSanctionKind, BoardSanctionLiftActor, BoardSanctionLine, BoardSanctionTriggerRef } from '~/composables/sanction/api/useBoardSanctionApi.types';
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

/**
 * 게시판 제재 화면의 계산이 낀 문구 — 문구 본문은 `i18n/locales/{ko,en}/boardSanction.ts`, 여기는 "어느 키를 고를지"만 안다.
 * 상태 이름(경고의 ACTIVE = 「유효」)·번호·운영자 이름은 사이트 제재(`UtilSanctionMessages`)와 같다. 덧붙인 글·사유·서버 오류 문구는 그대로 쓴다.
 */
export default class UtilBoardSanctionMessages {
    /** 단계 이름 — 「경고」「쓰기 제한 3일」 */
    static kindName(t: Translate, kind: BoardSanctionKind | string): string {
        return UtilBoardSanction.KINDS.includes(kind as BoardSanctionKind) ? t(`boardSanction.kind.${kind}`) : String(kind);
    }

    static reasonName(t: Translate, code: string): string {
        return UtilBoardSanction.REASONS.includes(code as never) ? t(`boardSanction.reason.${code}`) : code;
    }

    static statusName(t: Translate, status: string, kind: string): string {
        return UtilSanctionMessages.statusName(t, status, kind === 'WARNING' ? 'WARNING' : 'SUSPEND_3');
    }

    static roleName(t: Translate, role: string): string {
        return role === 'MANAGER' || role === 'STAFF' ? t(`boardSanction.role.${role}`) : role;
    }

    /** 처리자 「숲지기 · 매니저」 — 관리자 화면에만 보인다(정책 7) */
    static issuer(t: Translate, issuer: BoardSanctionIssuer | null): string {
        if (!issuer) return t('boardSanction.issuerGone');
        return t('boardSanction.issuerValue', { name: issuer.nickname ?? t('boardSanction.issuerGone'), role: UtilBoardSanctionMessages.roleName(t, issuer.role) });
    }

    /** 해제한 사람 「볼트너트(매니저)」「운영팀 민지(운영자)」 */
    static liftedBy(t: Translate, actor: BoardSanctionLiftActor | null): string {
        if (!actor) return t('boardSanction.issuerGone');
        return t('boardSanction.liftedByValue', { name: actor.name ?? t('boardSanction.issuerGone'), who: t(`boardSanction.liftedBy.${actor.type}`) });
    }

    /** 사유 한 줄 — 「스팸·광고 — 오픈 채팅 링크」 */
    static reasonLine(t: Translate, line: Pick<BoardSanctionLine, 'reasonCode' | 'reasonText'>): string {
        const name = UtilBoardSanctionMessages.reasonName(t, line.reasonCode);
        return line.reasonText ? t('boardSanction.reasonLine', { name, text: line.reasonText }) : name;
    }

    /** 기간 — 쓰기 제한 「11.01 14:00 ~ 11.04 14:00」, 경고 「11.01 14:00」 */
    static period(t: Translate, time: DisplayTime, line: Pick<BoardSanctionLine, 'startsAt' | 'endsAt'>): string {
        return line.endsAt ? t('sanction.periodRange', { from: time.full(line.startsAt), to: time.full(line.endsAt) }) : time.full(line.startsAt);
    }

    /** 무효 한 줄 — 「11.03 10:00 · 이의제기 인용」 */
    static voidedLine(t: Translate, time: DisplayTime, line: Pick<BoardSanctionLine, 'voidedAt' | 'voidSource'>): string {
        const source = line.voidSource ? t(`boardSanction.voidSource.${line.voidSource}`) : '';
        return source ? t('boardSanction.voidedLine', { at: time.full(line.voidedAt), source }) : time.full(line.voidedAt);
    }

    /** 계기 글·댓글 이름 */
    static trigger(t: Translate, trigger: BoardSanctionTriggerRef): string {
        const title = trigger.title || t('boardSanction.triggerUntitled');
        return trigger.type === 'COMMENT' ? t('boardSanction.triggerComment', { title }) : t('boardSanction.triggerPost', { title });
    }

    static reviewResultName(t: Translate, result: string): string {
        return UtilBoardSanction.REVIEW_RESULTS.includes(result as never) ? t(`boardSanction.review.result.${result}`) : result;
    }

    static reviewStatusName(t: Translate, status: string): string {
        return UtilBoardSanction.REVIEW_STATUSES.includes(status as never) ? t(`boardSanction.review.status.${status}`) : status;
    }
}
