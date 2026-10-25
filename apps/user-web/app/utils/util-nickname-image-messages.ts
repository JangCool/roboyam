import type { Translate } from '#layers/i18n/utils/util-i18n';
import UtilTime from '#layers/display-time/utils/util-time';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { NicknameImageIneligibleReason, NicknameImageLimits, NicknameImageRequirement } from '~/composables/profile/api/useNicknameImageApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilNicknameImage from '~/utils/util-nickname-image';
import type { NicknameImageOutcome, NicknameImageProblemCode } from '~/utils/util-nickname-image';

/** 제목 한 줄 + 설명 */
export interface NicknameImageCopy {
    title: string;
    text: string;
}

/** 신청을 막는 까닭 */
export type NicknameImageBlockedReason = 'EMAIL_NOT_VERIFIED' | 'ACCOUNT_RESTRICTED';

/**
 * 닉네임 이미지 화면의 **계산이 낀** 문구 — 안내·상태·확인 대화상자·서버 오류 풀이(community `UtilNicknameImageMessages`, 변경권 문구는 뺐다).
 * 상수 문구는 컴포넌트가 `t('nicknameImage.*')` 로 쓴다. 서버 `message` 는 입력칸 오류(`fields.file`)와 코드를 모르는 오류에만 쓴다.
 */
export default class UtilNicknameImageMessages {
    /**
     * 시간 수를 "3일"·"36시간"으로.
     * @param {Translate} t - 번역 함수.
     * @param {number} hours - 시간.
     * @returns {string} 글자.
     */
    static hoursLabel(t: Translate, hours: number): string {
        return hours % 24 === 0 ? t('nicknameImage.days', { n: hours / 24 }) : t('nicknameImage.hours', { n: hours });
    }

    /**
     * 남은 시간 — "2일 4시간" · "5시간 12분" · "12분" · "곧".
     * @param {Translate} t - 번역 함수.
     * @param {number} seconds - 초.
     * @returns {string} 글자.
     */
    static waitLabel(t: Translate, seconds: number): string {
        const s = Math.max(0, Math.ceil(seconds));
        if (s < 60) return t('nicknameImage.waitSoon');
        const days = Math.floor(s / 86_400);
        const hours = Math.floor((s % 86_400) / 3600);
        const minutes = Math.floor((s % 3600) / 60);
        if (days > 0) return hours > 0 ? t('nicknameImage.waitDaysHours', { d: days, h: hours }) : t('nicknameImage.days', { n: days });
        if (hours > 0) return minutes > 0 ? t('nicknameImage.waitHoursMinutes', { h: hours, m: minutes }) : t('nicknameImage.hours', { n: hours });
        return t('nicknameImage.minutes', { n: minutes });
    }

    /**
     * 올리기 전 검사 문구.
     * @param {Translate} t - 번역 함수.
     * @param {NicknameImageProblemCode} code - 문제.
     * @param {{ size?: number; maxBytes?: number; width?: number; height?: number; limits?: NicknameImageLimits | null }} detail - 끼울 값.
     * @returns {string} 한 줄.
     */
    static problem(t: Translate, code: NicknameImageProblemCode, detail: { size?: number; width?: number; height?: number; limits?: NicknameImageLimits | null } = {}): string {
        const max = detail.limits?.maxBytes ?? UtilNicknameImage.DEFAULT_MAX_BYTES;
        switch (code) {
            case 'FORMAT':
                return t('nicknameImage.problemFormat');
            case 'EMPTY':
                return t('nicknameImage.problemEmpty');
            case 'TOO_LARGE':
                return t('nicknameImage.problemTooLarge', { size: UtilNicknameImage.formatBytes(detail.size ?? 0), max: UtilNicknameImage.formatBytes(max) });
            case 'RATIO':
                return t('nicknameImage.problemRatio', {
                    size: `${detail.width ?? 0}×${detail.height ?? 0}`,
                    target: `${detail.limits?.width ?? UtilNicknameImage.DEFAULT_WIDTH}×${detail.limits?.height ?? UtilNicknameImage.DEFAULT_HEIGHT}`,
                });
            default:
                return t('nicknameImage.problemUnreadable');
        }
    }

    /**
     * 올리는 조건 줄들.
     * @param {Translate} t - 번역 함수.
     * @param {NicknameImageLimits} limits - 한도.
     * @returns {string[]} 줄들.
     */
    static rules(t: Translate, limits: NicknameImageLimits): string[] {
        const formats = limits.formats.length > 0 ? limits.formats.join('·') : 'PNG·JPG·GIF·WebP';
        return [
            t('nicknameImage.ruleFormat', { formats, max: UtilNicknameImage.formatBytes(limits.maxBytes) }),
            t('nicknameImage.ruleRatio', { ratio: limits.ratio, size: `${limits.width}×${limits.height}` }),
            t('nicknameImage.ruleGif', { n: limits.maxGifFrames }),
        ];
    }

    /**
     * 신청할 수 없는 까닭의 안내.
     * @param {Translate} t - 번역 함수.
     * @param {NicknameImageIneligibleReason | null} reason - 까닭.
     * @returns {NicknameImageCopy} 안내.
     */
    static ineligible(t: Translate, reason: NicknameImageIneligibleReason | null): NicknameImageCopy {
        if (reason === 'SUSPENDED') return { title: t('nicknameImage.suspendedTitle'), text: t('nicknameImage.suspendedText') };
        return { title: t('nicknameImage.requirementsTitle'), text: t('nicknameImage.requirementsText') };
    }

    /**
     * 신청 조건 한 줄 — 이름(`label`)과, 못 채웠을 때 지금 값과 필요한 값(`detail`, 채웠으면 빈 글자).
     * 등급: 「베테랑 이상」 + 「지금 단골 / 베테랑 이상」, 관리자가 고른 등급이 지워졌으면 「신청 조건을 확인 중이에요」.
     * @param {Translate} t - 번역 함수.
     * @param {NicknameImageRequirement} item - 조건.
     * @param {string} locale - 숫자 표기 언어.
     * @returns {{ label: string; detail: string }} 글자.
     */
    static requirement(t: Translate, item: NicknameImageRequirement, locale: string): { label: string; detail: string } {
        const n = (value: number) => value.toLocaleString(locale);
        switch (item.kind) {
            case 'EMAIL_VERIFIED':
                return { label: t('nicknameImage.reqEmail'), detail: item.met ? '' : t('nicknameImage.reqEmailMissing') };
            case 'GRADE':
                if (!item.required) return { label: t('nicknameImage.reqGradeUnknown'), detail: item.met ? '' : t('nicknameImage.reqChecking') };
                return {
                    label: t('nicknameImage.reqGrade', { grade: item.required.label }),
                    detail: item.met ? '' : t('nicknameImage.reqGradeNow', { current: item.current.label, required: item.required.label }),
                };
            case 'DAYS':
                return { label: t('nicknameImage.reqDays', { n: n(item.required) }), detail: item.met ? '' : t('nicknameImage.reqDaysNow', { current: n(item.current), required: n(item.required) }) };
            case 'ACTIVITY':
                return { label: t('nicknameImage.reqActivity', { n: n(item.required) }), detail: item.met ? '' : t('nicknameImage.reqActivityNow', { current: n(item.current), required: n(item.required) }) };
            default:
                return { label: t('nicknameImage.reqPoints', { n: n(item.required) }), detail: item.met ? '' : t('nicknameImage.reqPointsNow', { current: n(item.current), required: n(item.required) }) };
        }
    }

    /**
     * 막는 까닭별 안내.
     * @param {Translate} t - 번역 함수.
     * @param {NicknameImageBlockedReason} reason - 까닭.
     * @returns {NicknameImageCopy} 안내.
     */
    static blocked(t: Translate, reason: NicknameImageBlockedReason): NicknameImageCopy {
        return reason === 'EMAIL_NOT_VERIFIED'
            ? { title: t('nicknameImage.blockedUnverifiedTitle'), text: t('nicknameImage.blockedUnverifiedText') }
            : { title: t('nicknameImage.blockedRestrictedTitle'), text: t('nicknameImage.blockedRestrictedText') };
    }

    /**
     * 재신청까지 남은 시간 안내.
     * @param {Translate} t - 번역 함수.
     * @param {number} seconds - 남은 초.
     * @param {string | null | undefined} at - 가능 시각.
     * @param {DisplayTime} time - 표시 시각 함수.
     * @returns {string} 한 줄.
     */
    static reapplyWait(t: Translate, seconds: number, at: string | null | undefined, time: DisplayTime): string {
        if (seconds <= 0) return t('nicknameImage.reapplyNow');
        const head = t('nicknameImage.reapplyIn', { wait: UtilNicknameImageMessages.waitLabel(t, seconds) });
        return at ? t('nicknameImage.reapplyInAt', { head, at: time.full(at) }) : head;
    }

    /**
     * 실패 한 줄 — 코드를 아는 갈래는 화면 문구, 입력칸 오류·모르는 오류는 서버 문구.
     * @param {Translate} t - 번역 함수.
     * @param {NicknameImageOutcome} outcome - 갈래.
     * @param {{ message?: string; fileField?: string; retryAfterSeconds?: number; maxBytes?: number; reapplyHours?: number; language?: string }} detail - 끼울 값.
     * @returns {string} 한 줄.
     */
    static failure(
        t: Translate,
        outcome: NicknameImageOutcome,
        detail: { message?: string; fileField?: string; retryAfterSeconds?: number; maxBytes?: number; reapplyHours?: number; language?: string } = {},
    ): string {
        const message = (detail.message ?? '').trim();
        switch (outcome) {
            case 'REQUIREMENTS_NOT_MET':
                return t('nicknameImage.failRequirements');
            case 'REAPPLY_TOO_SOON':
                return t('nicknameImage.failReapply', { wait: UtilNicknameImageMessages.hoursLabel(t, UtilNicknameImage.reapplyHoursOf(detail.reapplyHours)) });
            case 'RESTRICTED':
                return UtilNicknameImageMessages.blocked(t, 'ACCOUNT_RESTRICTED').title;
            case 'UNVERIFIED':
                return UtilNicknameImageMessages.blocked(t, 'EMAIL_NOT_VERIFIED').title;
            case 'VALIDATION':
                return detail.fileField || message || t('nicknameImage.failValidation');
            case 'RATE_LIMITED': {
                const head = message || t('nicknameImage.failRateLimited');
                return detail.retryAfterSeconds ? t('nicknameImage.failRetryIn', { head, time: UtilTime.duration(detail.retryAfterSeconds, detail.language) }) : head;
            }
            case 'TOO_LARGE':
                return t('nicknameImage.failTooLarge', { max: UtilNicknameImage.formatBytes(detail.maxBytes ?? UtilNicknameImage.DEFAULT_MAX_BYTES) });
            case 'NOT_FOUND':
                return t('nicknameImage.failNotFound');
            case 'NETWORK':
                return UtilAuthMessages.fallback(t, 0, 'NETWORK');
            default:
                return message || t('nicknameImage.failOther');
        }
    }
}
