import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { NicknameImageGradeOption, NicknameImageSettingsRequest } from '~/composables/member/api/useNicknameImageApi.types';
import type { NicknameImageNumberField, NicknameImageSettingsProblem } from '~/utils/util-nickname-image';
import UtilNicknameImage from '~/utils/util-nickname-image';

/**
 * 닉네임 이미지 신청 조건 설정(`/settings/nickname-image`)의 **계산이 낀 문구** — 본문은 `i18n/locales/{ko,en}/nicknameImage.ts`, 여기는 어느 키를 고를지와 자리표시자만 안다.
 * 번역 함수 `t` 를 첫 인자로 받는다(`.claude/guides/frontend/i18n.md`). 서버 오류 문구는 서버 `message` 를 그대로 쓴다.
 */
export default class UtilNicknameImageMessages {
    /** 숫자 칸의 입력 범위 글자 — `0 ~ 3,650` */
    static range(t: Translate, field: NicknameImageNumberField, locale: string): string {
        const [min, max] = UtilNicknameImage.LIMITS[field];
        return t('nicknameImage.settingsRange', { min: min.toLocaleString(locale), max: max.toLocaleString(locale) });
    }

    /** 칸 문제를 입력칸 아래 한 줄로 */
    static problem(t: Translate, problem: NicknameImageSettingsProblem, field: NicknameImageNumberField | 'gradeKey', locale: string): string {
        if (problem === 'GRADE_REQUIRED') return t('nicknameImage.settingsGradeRequired');
        if (problem === 'GRADE_MISSING') return t('nicknameImage.settingsGradeMissingProblem');
        if (problem === 'REQUIRED') return t('nicknameImage.settingsNumberRequired');
        if (problem === 'NOT_INTEGER') return t('nicknameImage.settingsNumberNotInteger');
        if (field === 'gradeKey') return '';
        const [min, max] = UtilNicknameImage.LIMITS[field];
        return t('nicknameImage.settingsNumberRange', { min: min.toLocaleString(locale), max: max.toLocaleString(locale) });
    }

    /** 등급 고르개의 항목 이름 — `베테랑 이상` */
    static gradeAtLeast(t: Translate, label: string): string {
        return t('nicknameImage.settingsGradeAtLeast', { grade: label });
    }

    /**
     * 조건을 한 줄로 — `베테랑 이상 · 이메일 인증` / `단골 이상 · 가입 30일 · 글·댓글 50개 · 포인트 1,000 · 이메일 인증 없이`.
     * 등급 기준은 등급과 이메일 인증만, 사용자 정의는 켜진 조건만(0 은 빼고) 차례대로 잇는다. 아무 조건도 없으면 「조건 없음」.
     * @param {Translate} t - 번역 함수.
     * @param {NicknameImageSettingsRequest} value - 조건 값.
     * @param {readonly NicknameImageGradeOption[]} grades - 등급 목록(이름 찾기용).
     * @param {string} locale - 숫자 표기 언어.
     * @returns {string} 한 줄.
     */
    static summary(t: Translate, value: NicknameImageSettingsRequest, grades: readonly NicknameImageGradeOption[], locale: string): string {
        const parts: string[] = [];
        if (value.gradeKey) {
            const grade = grades.find((g) => g.key === value.gradeKey);
            parts.push(grade ? UtilNicknameImageMessages.gradeAtLeast(t, grade.label) : t('nicknameImage.summaryGradeMissing'));
        }
        if (value.mode === 'GRADE') {
            parts.push(t('nicknameImage.summaryEmail'));
            return parts.join(' · ');
        }
        if (value.minDays > 0) parts.push(t('nicknameImage.summaryDays', { days: value.minDays.toLocaleString(locale) }));
        if (value.minActivity > 0) parts.push(t('nicknameImage.summaryActivity', { count: value.minActivity.toLocaleString(locale) }));
        if (value.minPoints > 0) parts.push(t('nicknameImage.summaryPoints', { points: value.minPoints.toLocaleString(locale) }));
        if (!parts.length && !value.requireEmailVerified) return t('nicknameImage.summaryOpen');
        parts.push(value.requireEmailVerified ? t('nicknameImage.summaryEmail') : t('nicknameImage.summaryNoEmail'));
        return parts.join(' · ');
    }
}
