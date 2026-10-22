import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { BoardDateDisplay, BoardGuestRules, BoardNewMemberRules } from '~/composables/board/api/useBoardApi.types';
import type { PostProtection } from '~/composables/board/api/usePostApi.types';
import UtilPost, { type LengthProblem } from '~/utils/util-post';

/**
 * 글·댓글 화면의 계산이 낀 문구 — 날짜 표시 방식·입력칸 문제·보호 안내. 문구 본문은 `i18n/locales/{ko,en}/post.ts`.
 * 상수 문구는 컴포넌트가 `t('post.키')` 로 부른다.
 */
export default class UtilPostMessages {
    /**
     * 목록 한 줄의 날짜 — 관리자 기본 설정 `dateDisplay` 를 따른다.
     *  · 표준형(STANDARD): 하루 안은 「N분 전·N시간 전」, 올해는 연 없는 날짜(`time.shortDate`), 그 전 해는 연 포함 날짜(`time.date` — 표시 값의 날짜 형식을 따른다. 계약의 `YY-MM-DD` 와 자릿수가 다를 수 있다)
     *  · 유동형(RELATIVE): 모두 상대 시간 — N분·N시간·N일·N달·N년 전
     * @param {Translate} t - 번역 함수.
     * @param {DisplayTime} time - 표시 값에 묶인 시각 함수(`useDisplayTime().value`).
     * @param {string} iso - 작성 시각.
     * @param {BoardDateDisplay} mode - 날짜 표시 방식.
     * @param {number} now - 지금(밀리초).
     * @returns {string} 날짜 글자.
     */
    static listDate(t: Translate, time: DisplayTime, iso: string, mode: BoardDateDisplay, now: number): string {
        const age = UtilPost.age(iso, now);
        if (!age) return '';
        if (age.unit === 'minute' || age.unit === 'hour') return time.ago(iso, now);
        if (mode === 'RELATIVE') {
            if (age.unit === 'day') return t('post.daysAgo', { n: age.value });
            if (age.unit === 'month') return t('post.monthsAgo', { n: age.value });
            return t('post.yearsAgo', { n: age.value });
        }
        return UtilPost.sameYear(iso, now) ? time.shortDate(iso) : time.date(iso);
    }

    /**
     * 제목 칸 문제 문구
     * @param {Translate} t - 번역 함수.
     * @param {LengthProblem | null} problem - 문제.
     * @returns {string} 문구. 문제가 없으면 빈 글자.
     */
    static title(t: Translate, problem: LengthProblem | null): string {
        if (problem === 'REQUIRED') return t('post.titleRequired');
        if (problem === 'LENGTH') return t('post.titleLength', { max: UtilPost.TITLE_MAX });
        return '';
    }

    /**
     * 본문 칸 문제 문구
     * @param {Translate} t - 번역 함수.
     * @param {LengthProblem | null} problem - 문제.
     * @returns {string} 문구. 문제가 없으면 빈 글자.
     */
    static content(t: Translate, problem: LengthProblem | null): string {
        if (problem === 'REQUIRED') return t('post.contentRequired');
        if (problem === 'LENGTH') return t('post.contentLength', { min: UtilPost.CONTENT_MIN, max: UtilPost.CONTENT_MAX.toLocaleString() });
        return '';
    }

    /**
     * 비회원 이름 칸 문제 문구
     * @param {Translate} t - 번역 함수.
     * @param {LengthProblem | null} problem - 문제.
     * @param {BoardGuestRules} rules - 비회원 규칙.
     * @returns {string} 문구. 문제가 없으면 빈 글자.
     */
    static guestName(t: Translate, problem: LengthProblem | null, rules: BoardGuestRules): string {
        if (problem === 'REQUIRED') return t('post.guestNameRequired');
        if (problem === 'LENGTH') return t('post.guestNameLength', { min: rules.nameMin, max: rules.nameMax });
        return '';
    }

    /**
     * 비회원 비밀번호 칸 문제 문구
     * @param {Translate} t - 번역 함수.
     * @param {LengthProblem | null} problem - 문제.
     * @param {BoardGuestRules} rules - 비회원 규칙.
     * @returns {string} 문구. 문제가 없으면 빈 글자.
     */
    static guestPassword(t: Translate, problem: LengthProblem | null, rules: BoardGuestRules): string {
        if (problem === 'REQUIRED') return t('post.guestPasswordRequired');
        if (problem === 'LENGTH') return t('post.guestPasswordLength', { min: rules.passwordMin, max: rules.passwordMax });
        return '';
    }

    /**
     * 링크·이미지 규칙 미리 알림
     * @param {Translate} t - 번역 함수.
     * @param {ReturnType<typeof UtilPost.linkProblem>} problem - 넘은 규칙.
     * @param {BoardGuestRules} guestRules - 비회원 규칙.
     * @param {BoardNewMemberRules} newMember - 새 회원 규칙.
     * @returns {string} 문구. 문제가 없으면 빈 글자.
     */
    static links(t: Translate, problem: ReturnType<typeof UtilPost.linkProblem>, guestRules: BoardGuestRules, newMember: BoardNewMemberRules): string {
        switch (problem) {
            case 'GUEST_LINK':
                return guestRules.linkLimit === 0 ? t('post.guestNoLink') : t('post.guestLinkLimit', { n: guestRules.linkLimit });
            case 'GUEST_IMAGE':
                return t('post.guestNoImage');
            case 'NO_IMAGE':
                return t('post.commentNoImage');
            case 'NEW_LINK':
                return t('post.newMemberLinkLimit', { n: newMember.linkLimit });
            case 'NEW_IMAGE':
                return t('post.newMemberImageLimit', { n: newMember.imageLimit });
            default:
                return '';
        }
    }

    /**
     * 댓글 많은 글 보호 안내 — 제목 한 줄과 막힌 동작 목록
     * @param {Translate} t - 번역 함수.
     * @param {PostProtection} protection - 서버가 준 보호 상태.
     * @returns {{ title: string; items: string[] } | null} 보호 중이 아니면 null.
     */
    static protection(t: Translate, protection: PostProtection): { title: string; items: string[] } | null {
        if (!protection.active) return null;
        const items: string[] = [];
        if (protection.editBlocked) items.push(t('post.protectEdit'));
        if (protection.deleteMode === 'BLOCK') items.push(t('post.protectDeleteBlock'));
        if (protection.deleteMode === 'DELAY') items.push(t('post.protectDeleteDelay', { minutes: protection.delayMinutes }));
        return { title: t('post.protectTitle', { n: protection.threshold }), items };
    }
}
