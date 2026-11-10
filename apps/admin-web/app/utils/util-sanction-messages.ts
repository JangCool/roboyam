import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { SanctionActorRef, SanctionBlockedReason, SanctionKind, SanctionLine, SanctionPreview, SanctionUserRef } from '~/composables/sanction/api/useSanctionApi.types';
import type { SanctionChoice, SanctionSearchKind, SanctionTextProblem } from '~/utils/util-sanction';
import UtilSanction from '~/utils/util-sanction';

/**
 * 제재·이의제기 화면의 계산이 낀 문구 — 문구 본문은 `i18n/locales/{ko,en}/sanction.ts`·`appeal.ts`, 여기는 "어느 키를 고를지"만 안다.
 * 서버가 준 사유·답변·오류 문구는 그대로 쓴다(가공하지 않는다).
 */
export default class UtilSanctionMessages {
    /** 단계 이름 — 「경고」「경고 2회째」「정지 7일」「영구 정지」 */
    static kindName(t: Translate, kind: SanctionKind | string, warningNo: number | null = null): string {
        if (kind === 'WARNING' && (warningNo === 1 || warningNo === 2)) return t('sanction.kindWarningN', { n: warningNo });
        return UtilSanction.LADDER.includes(kind as SanctionKind) ? t(`sanction.kind.${kind}`) : String(kind);
    }

    /** 상태 이름 — 경고의 ACTIVE 는 「유효」, 정지는 「진행 중」 */
    static statusName(t: Translate, status: string, kind: SanctionKind | string): string {
        if (status === 'ACTIVE') return kind === 'WARNING' ? t('sanction.status.VALID') : t('sanction.status.ACTIVE');
        return ['ENDED', 'LIFTED', 'VOIDED'].includes(status) ? t(`sanction.status.${status}`) : status;
    }

    static appealStatusName(t: Translate, status: string): string {
        return ['PENDING', 'ACCEPTED', 'REJECTED'].includes(status) ? t(`appeal.status.${status}`) : status;
    }

    /**
     * 제재 대상 이름 — 봉인된 익명 글 작성자(anonymous-board-contract 정책 9)는 「익명 글 작성자(봉인)」, 그 밖은 닉네임(없으면 빈 글자).
     * 제재·게시판 제재·이의제기의 목록·서랍이 닉네임 자리에 쓴다
     */
    static userName(t: Translate, user: Pick<SanctionUserRef, 'nickname' | 'sealed'>): string {
        return user.sealed ? t('anonymousAuthor.sanctionTarget') : (user.nickname ?? '');
    }

    /** 제재 번호 「#12」 */
    static no(t: Translate, line: { no: number }): string {
        return t('sanction.noValue', { no: line.no });
    }

    /** 기간 — 기간 정지 「10.01 14:00 ~ 10.08 14:00」, 경고·영구 정지 「10.01 14:00 부터」 */
    static period(t: Translate, time: DisplayTime, line: Pick<SanctionLine, 'startsAt' | 'endsAt'>): string {
        return line.endsAt ? t('sanction.periodRange', { from: time.full(line.startsAt), to: time.full(line.endsAt) }) : t('sanction.periodFrom', { from: time.full(line.startsAt) });
    }

    /** 표의 짧은 기간 */
    static shortPeriod(t: Translate, time: DisplayTime, line: Pick<SanctionLine, 'startsAt' | 'endsAt' | 'kind'>): string {
        if (line.kind === 'WARNING') return '—';
        if (!line.endsAt) return t('sanction.periodFromShort', { from: time.listLabel(line.startsAt) });
        return t('sanction.periodRange', { from: time.listLabel(line.startsAt), to: time.listLabel(line.endsAt) });
    }

    static actor(t: Translate, actor: SanctionActorRef | null): string {
        return actor?.name ?? t('sanction.actorGone');
    }

    /** 경고 「1 / 2회」 */
    static warningCount(t: Translate, count: number): string {
        return t('sanction.warningCountValue', { n: count, max: UtilSanction.WARNING_LIMIT });
    }

    /** 다음 단계 한 줄 — 막혔으면 그 까닭 */
    static nextStep(t: Translate, preview: SanctionPreview): string {
        if (preview.blockedReason === 'UNAVAILABLE') return t('sanction.nextUnavailable');
        if (preview.blockedReason === 'ALREADY_BANNED' || !preview.next) return t('sanction.nextNone');
        if (preview.blockedReason === 'SUSPENDED') return t('sanction.nextBanOnly');
        return UtilSanctionMessages.kindName(t, preview.next.kind, preview.next.kind === 'WARNING' ? preview.next.rank : null);
    }

    static blocked(t: Translate, reason: SanctionBlockedReason): string {
        return t(`sanction.blocked.${reason}`);
    }

    /** 선택지를 못 누르는 까닭 */
    static choiceNote(t: Translate, choice: SanctionChoice): string {
        if (choice.disabledBy) return t(`sanction.choiceDisabled.${choice.disabledBy}`);
        return t(`sanction.choiceState.${choice.option.state}`);
    }

    static searchProblem(t: Translate, kind: SanctionSearchKind): string {
        if (kind === 'TOO_SHORT') return t('sanction.searchTooShort', { min: UtilSanction.SEARCH_MIN });
        if (kind === 'NUMBER_INVALID') return t('sanction.searchNumberInvalid');
        return '';
    }

    static textProblem(t: Translate, problem: SanctionTextProblem | null, label: string): string {
        if (problem === 'REQUIRED') return t('sanction.textRequired', { label });
        if (problem === 'TOO_LONG') return t('sanction.textTooLong', { label, max: UtilSanction.TEXT_MAX });
        return '';
    }

    /** 대기 시간 — 「45분」「5시간」「2일 3시간」 */
    static wait(t: Translate, ms: number): string {
        const minutes = Math.floor(ms / 60000);
        if (minutes < 60) return t('appeal.waitMinutes', { n: Math.max(1, minutes) });
        const hours = Math.floor(minutes / 60);
        if (hours < 24) return t('appeal.waitHours', { n: hours });
        return t('appeal.waitDays', { d: Math.floor(hours / 24), h: hours % 24 });
    }

    /** 부여 성공 토스트 */
    static issued(t: Translate, line: Pick<SanctionLine, 'kind' | 'warningNo' | 'user'>): string {
        return t('sanction.issued', { name: UtilSanctionMessages.userName(t, line.user), kind: UtilSanctionMessages.kindName(t, line.kind, line.warningNo) });
    }
}
