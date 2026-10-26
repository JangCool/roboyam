import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { IdentityVerifyOutcome } from '~/composables/identity/useIdentity.types';

/** 인증 실패 안내 한 묶음 — 굵은 첫 줄과 이어지는 말 */
export interface IdentityFailureCopy {
    title: string;
    text: string;
}

/**
 * 본인인증 화면 문구 중 계산이 낀 것 — 실패 코드별 안내, 19세 생일 날짜. 본문은 `i18n/locales/{ko,en}/identity.ts`.
 * 실패는 `ApiError.code` 로 고른다(서버 `IdentityVerificationService` 의 오류 코드 — `useIdentityVerificationApi.types.ts` `IdentityErrorCode`). 그 밖의 코드는 서버 문구를 그대로 쓴다.
 */
export default class UtilIdentityMessages {
    /**
     * 인증 실패 안내.
     * @param {Translate} t - 번역 함수.
     * @param {IdentityVerifyOutcome} outcome - 실패 결과(`FAILED`).
     * @param {DisplayTime} time - 표시 형식(재인증 가능 날짜를 그린다).
     * @returns {IdentityFailureCopy} 안내.
     */
    static failure(t: Translate, outcome: Extract<IdentityVerifyOutcome, { kind: 'FAILED' }>, time: DisplayTime): IdentityFailureCopy {
        switch (outcome.code) {
            case 'IDENTITY_ALREADY_LINKED':
                return { title: t('identity.failLinkedTitle'), text: t('identity.failLinked') };
            case 'IDENTITY_REVERIFY_BLOCKED':
                return {
                    title: t('identity.failBlockedTitle'),
                    text: outcome.retryAfter ? t('identity.failBlocked', { date: time.serviceDate(outcome.retryAfter) }) : t('identity.failBlockedNoDate'),
                };
            case 'IDENTITY_PROVIDER_FAILED':
                return { title: t('identity.failProviderTitle'), text: t('identity.failProvider') };
            case 'IDENTITY_VERIFICATION_EXPIRED':
                return { title: t('identity.failExpiredTitle'), text: t('identity.failExpired') };
            default:
                return { title: t('identity.failTitle'), text: outcome.message };
        }
    }

    /**
     * 19세 생일 — 「2027년 3월 2일」.
     * @param {Translate} t - 번역 함수.
     * @param {string | null} ymd - 서비스 날짜 `YYYY-MM-DD`.
     * @returns {string} 날짜 글자. 값이 없거나 틀리면 ''.
     */
    static birthday(t: Translate, ymd: string | null): string {
        const match = ymd ? /^(\d{4})-(\d{2})-(\d{2})$/.exec(ymd) : null;
        if (!match) return '';
        return t('identity.birthdayDate', { year: Number(match[1]), month: Number(match[2]), day: Number(match[3]) });
    }

    /**
     * 만 19세 미만 안내의 둘째 줄 — 「만 19세 생일(2027년 3월 2일)이 지나면 다시 인증하지 않아도 이용할 수 있어요」.
     * @param {Translate} t - 번역 함수.
     * @param {string | null} adultFrom - 만 19세가 되는 날.
     * @returns {string} 안내. 날짜가 없으면 날짜 없는 문장.
     */
    static minorRelease(t: Translate, adultFrom: string | null): string {
        const date = UtilIdentityMessages.birthday(t, adultFrom);
        return date ? t('identity.minorRelease', { date }) : t('identity.minorReleaseNoDate');
    }
}
