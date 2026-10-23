import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { ApiError } from '~/composables/common/useApiClient';
import type { OperatorMfaView } from '~/composables/mfa/api/useMfaApi.types';
import UtilMfa from '~/utils/util-mfa';

/** 운영자 2차 인증 상태 딱지 */
export interface MfaStatusBadge {
    label: string;
    severity: 'success' | 'warn' | 'danger' | 'secondary' | 'info';
    icon: string;
}

/**
 * 2차 인증 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('mfa.…')` 로 바로 부른다(i18n.md 3).
 * 서버 오류 문구는 덮어쓰지 않고, 화면이 아는 사실(남은 횟수)만 뒤에 붙인다(forms.md 2-2).
 */
export default class UtilMfaMessages {
    /**
     * OTP·복구 코드 오류 한 줄 — 서버 문구 + (틀린 코드면) 남은 횟수. 형식 오류(400 `VALIDATION`)는 `fields.code`·`fields.recoveryCode` 의 칸 문구.
     * `remainingAttempts` 는 서버 오류 본문의 최상위 칸이라 `ApiError.extras` 로 온다.
     * @param {Translate} t - 번역 함수.
     * @param {ApiError} e - 서버 오류.
     * @returns {string} 칸 아래에 보일 글.
     */
    static codeError(t: Translate, e: ApiError): string {
        const remaining = e.extras?.remainingAttempts;
        if (e.code === 'MFA_CODE_INVALID' && typeof remaining === 'number') return `${e.message} ${t('mfa.attemptsLeft', { count: remaining })}`;
        return e.fields?.code ?? e.fields?.recoveryCode ?? e.message;
    }

    /**
     * 복구 코드로 들어간 뒤의 안내 — 3개 이하면 새로 만들라는 말을 붙인다.
     * @returns {{ text: string, low: boolean }} 토스트 글과 낮음 여부(낮으면 오래 보인다).
     */
    static recoveryLeft(t: Translate, left: number): { text: string; low: boolean } {
        const low = left <= UtilMfa.RECOVERY_LOW;
        return { text: low ? `${t('mfa.recoveryLeft', { count: left })} ${t('mfa.recoveryLowHint')}` : t('mfa.recoveryLeft', { count: left }), low };
    }

    /**
     * 기기 신뢰 기간 고르개의 이름 — 「N개월(N×30일)」.
     * @returns {string} 고르개 항목 글.
     */
    static trustMonths(t: Translate, months: number): string {
        return t('mfa.trustMonthsOption', { months, days: months * UtilMfa.DAYS_PER_MONTH });
    }

    /**
     * 운영자 2차 인증 상태 딱지 — 사이트 꺼짐 → 「사용 안 함」, 등록됨, 유예 중 D-N, 유예 지남, 미등록.
     * @param {Translate} t - 번역 함수.
     * @param {OperatorMfaView} view - 운영자 상태.
     * @param {number} [now] - 지금(밀리초).
     * @returns {MfaStatusBadge} 딱지 글·색·아이콘.
     */
    static operatorStatus(t: Translate, view: OperatorMfaView, now: number = Date.now()): MfaStatusBadge {
        if (!view.siteEnabled) return { label: t('mfa.statusOff'), severity: 'secondary', icon: 'pi pi-minus-circle' };
        if (view.enrolled) return { label: t('mfa.statusEnrolled'), severity: 'success', icon: 'pi pi-check-circle' };
        if (view.graceExpired) return { label: t('mfa.statusGraceExpired'), severity: 'danger', icon: 'pi pi-exclamation-circle' };
        if (view.graceEndsAt) return { label: t('mfa.statusInGrace', { days: UtilMfa.daysLeft(view.graceEndsAt, now) }), severity: 'warn', icon: 'pi pi-clock' };
        return { label: t('mfa.statusNotEnrolled'), severity: 'warn', icon: 'pi pi-exclamation-triangle' };
    }
}
