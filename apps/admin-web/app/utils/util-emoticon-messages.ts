import type { Translate } from '#layers/i18n/utils/util-i18n';
import UtilEmoticon, { type EmoticonReasonProblem, type EmoticonSpecIssue } from '~/utils/util-emoticon';
import type { ApiError } from '~/composables/common/useApiClient';
import type { EmoticonPackStatus, EmoticonPayeeType, EmoticonSuspendKind, EmoticonSuspension } from '~/composables/emoticon/api/useEmoticonAdminApi.types';

/** 그림 올리기를 서버가 거절한 이유 코드(`400 VALIDATION fields.file`·`fields.cover` — 서버 `EmoticonImageInvalidException`) */
const UPLOAD_REASONS: ReadonlySet<string> = new Set(['TYPE', 'COVER_TYPE', 'DIMENSION', 'TOO_LARGE', 'TOO_MANY_FRAMES', 'NOT_LOOP']);

/** 설정 칸 이름 */
export type EmoticonSettingField = keyof typeof UtilEmoticon.SETTING_RANGES;

/**
 * 로보티콘 운영 화면의 **계산이 낀 문구**(규격 문제·사유 문제·설정 칸 범위) — 상수 문구는 컴포넌트가 `t('emoticon.…')` 로 부른다(`i18n.md` 3절).
 */
export default class UtilEmoticonMessages {
    /**
     * 규격 검사 결과 한 줄.
     * @param {Translate} t - 번역 함수.
     * @param {EmoticonSpecIssue} issue - 검사 결과.
     * @returns {string} 한 줄.
     */
    static specIssue(t: Translate, issue: EmoticonSpecIssue): string {
        return t(`emoticon.spec.${issue.code}`, issue.params);
    }

    /**
     * 운영 팩 그림 올리기 오류 한 줄 — `fields.file`(로보티콘)·`fields.cover`(대표)의 **이유 코드**로 화면 문구를 고른다(한도는 고정 규격 `UtilEmoticon.SPEC`).
     * 이유 코드가 아니면(파일 없음·서버 오류 등) 그 칸의 글자, 그것도 없으면 서버 `message`.
     * @param {Translate} t - 번역 함수.
     * @param {ApiError} error - 정규화한 오류.
     * @param {'file' | 'cover'} field - 칸.
     * @returns {string} 한 줄.
     */
    static uploadError(t: Translate, error: ApiError, field: 'file' | 'cover'): string {
        const reason = error.field(field);
        if (!reason) return error.message;
        if (!UPLOAD_REASONS.has(reason)) return reason;
        const S = UtilEmoticon.SPEC;
        return t(`emoticon.official.uploadReason.${reason}`, { size: S.size, frames: S.maxFrames, max: UtilEmoticon.fileSize(field === 'cover' ? S.coverMaxBytes : S.maxBytes) });
    }

    /**
     * 사유 칸 문제.
     * @param {Translate} t - 번역 함수.
     * @param {EmoticonReasonProblem} problem - 문제.
     * @returns {string} 칸 아래 한 줄.
     */
    static reasonProblem(t: Translate, problem: EmoticonReasonProblem): string {
        return t(`emoticon.reason.${problem}`, { min: UtilEmoticon.REASON.min, max: UtilEmoticon.REASON.max });
    }

    /**
     * 설정 칸 하나의 문제 — 비었음 · 정수 아님 · 범위 밖. 문제가 없으면 ''.
     * @param {Translate} t - 번역 함수.
     * @param {EmoticonSettingField} field - 칸.
     * @param {string} text - 입력 글자.
     * @param {string} locale - 숫자 표기 언어.
     * @returns {string} 칸 아래 한 줄.
     */
    static settingProblem(t: Translate, field: EmoticonSettingField, text: string, locale: string): string {
        if (!text.trim()) return t('emoticon.settings.required');
        const value = UtilEmoticon.parseInt(text);
        if (value === null) return t('emoticon.settings.notInteger');
        const range = UtilEmoticon.SETTING_RANGES[field];
        if (value < range.min || value > range.max) return t('emoticon.settings.outOfRange', { min: range.min.toLocaleString(locale), max: range.max.toLocaleString(locale) });
        return '';
    }

    /**
     * 팩 상태 딱지 글자 — 작가 탈퇴로 멈춘 판매 중단(`suspendKind: CREATOR_WITHDRAWN`)은 「작가 탈퇴로 판매 중단」, 그 밖은 상태 이름.
     * @param {Translate} t - 번역 함수.
     * @param {EmoticonPackStatus} status - 팩 상태.
     * @param {EmoticonSuspendKind | null} suspendKind - 판매 중단의 종류.
     * @returns {string} 글자.
     */
    static packStatus(t: Translate, status: EmoticonPackStatus, suspendKind: EmoticonSuspendKind | null): string {
        return status === 'SUSPENDED' && suspendKind === 'CREATOR_WITHDRAWN' ? t('emoticon.packStatusCreatorWithdrawn') : t(`emoticon.packStatus.${status}`);
    }

    /**
     * 판매 중단 안내 한 줄 — 작가 탈퇴로 멈춘 팩은 처리한 사람 「시스템」 + 화면 문구(서버 사유는 고정 문구라 쓰지 않는다),
     * 운영자 판매 중단은 처리한 운영자(지워졌으면 「지워진 운영자」) + 서버 사유.
     * @param {Translate} t - 번역 함수.
     * @param {EmoticonSuspension} suspension - 판매 중단 기록.
     * @param {EmoticonSuspendKind | null} suspendKind - 판매 중단의 종류.
     * @param {string} at - 표시용 시각 글자.
     * @returns {string} 한 줄.
     */
    static suspensionNote(t: Translate, suspension: EmoticonSuspension, suspendKind: EmoticonSuspendKind | null, at: string): string {
        if (suspendKind === 'CREATOR_WITHDRAWN') return t('emoticon.suspendedCreatorWithdrawnNote', { by: t('emoticon.systemActor'), at });
        return `${t('emoticon.suspendedNote', { by: suspension.by ?? t('emoticon.priceEventByUnknown'), at })} — ${suspension.reason}`;
    }

    /**
     * 작가 구분 이름 — 정산 정보가 없으면(null) 「정산 정보 없음」.
     * @param {Translate} t - 번역 함수.
     * @param {EmoticonPayeeType | null} type - 구분.
     * @returns {string} 이름.
     */
    static payeeType(t: Translate, type: EmoticonPayeeType | null): string {
        return type ? t(`emoticon.payeeType.${type}`) : t('emoticon.payeeTypeNone');
    }

    /**
     * 부호 붙은 원 글자 — 음수면 `−2,500원`(빼기 기호), 아니면 그대로. 확정 뒤 환불로 음수가 될 수 있는 정산 칸에 쓴다.
     * @param {Translate} t - 번역 함수.
     * @param {number} amount - 원(음수 가능).
     * @param {string} locale - 숫자 표기 언어.
     * @returns {string} 글자.
     */
    static signedWon(t: Translate, amount: number, locale: string): string {
        const text = t('emoticon.wonValue', { amount: Math.abs(amount).toLocaleString(locale) });
        return amount < 0 ? `−${text}` : text;
    }

    /**
     * 빼는 금액 글자(부가세·수수료·원천징수) — 양수면 `−2,500원`, 음수(판매보다 환불이 많은 달 — 빼던 것을 되돌린다)면 `+2,500원`, 0 이면 `empty`.
     * @param {Translate} t - 번역 함수.
     * @param {number} amount - 빼는 원(음수 가능).
     * @param {string} locale - 숫자 표기 언어.
     * @param {string} empty - 0 일 때 글자.
     * @returns {string} 글자.
     */
    static deduction(t: Translate, amount: number, locale: string, empty: string): string {
        if (amount === 0) return empty;
        const text = t('emoticon.wonValue', { amount: Math.abs(amount).toLocaleString(locale) });
        return amount > 0 ? `−${text}` : `+${text}`;
    }

    /**
     * 정산 계좌의 은행 이름 — 서버가 주는 은행 코드(가린 계좌·원문 보기 모두)를 이름으로. 모르는 코드는 그대로.
     * @param {string} code - 은행 코드.
     * @param {(ko: string, en: string) => string} localizedName - 언어에 맞는 이름 고르기(`useLocalizedName`).
     * @returns {string} 이름.
     */
    static bankName(code: string, localizedName: (ko: string, en: string) => string): string {
        const bank = UtilEmoticon.bank(code);
        return bank ? localizedName(bank.name, bank.nameEn) : code;
    }
}
