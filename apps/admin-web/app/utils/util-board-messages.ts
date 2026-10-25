import type { Translate } from '#layers/i18n/utils/util-i18n';
import { ApiError } from '~/composables/common/useApiClient';
import type { BoardStaffIneligible } from '~/composables/board/api/useBoardApi.types';
import { BOARD_MIN_GUEST, BOARD_MIN_OFF, BOARD_MIN_STAFF } from '~/composables/board/api/useBoardApi.types';
import type { BoardFieldProblem, BoardFieldValue, BoardTextProblem } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';
import UtilAdminAuthMessages from '~/utils/util-admin-auth-messages';

/** 게시판 관리 화면의 계산이 낀 문구 — 상수 문구는 컴포넌트가 `t('board.…')` 로 부른다. 키 규칙은 `i18n.md`. */
export default class UtilBoardMessages {
    /**
     * 잡은 오류를 보일 한 줄로 — `ApiError` 면 서버 문구 그대로, 아니면(네트워크 등) 지금 언어의 대체 문구.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류.
     * @returns {string} 한 줄.
     */
    static of(t: Translate, e: unknown): string {
        return e instanceof ApiError ? e.message : UtilAdminAuthMessages.fallback(t, 0, 'NETWORK');
    }

    /** 칸 문제(숫자 칸 · 이름 칸) → 문구(범위를 넣는다) */
    static numProblem(t: Translate, field: string, problem: BoardFieldProblem): string {
        const spec = UtilBoard.spec(field);
        if (problem === 'LABEL_LENGTH') return t('board.problemLabelLength', { max: spec.max ?? 0 });
        if (problem === 'LABEL_CHARS') return t('board.problemLabelChars');
        if (problem === 'REQUIRED') return t('board.problemRequired');
        if (problem === 'NOT_INTEGER') return t('board.problemInteger');
        return t('board.problemRange', { min: (spec.min ?? 0).toLocaleString(), max: (spec.max ?? 0).toLocaleString() });
    }

    /** 이름·slug·설명·말머리 문제 → 문구 */
    static problem(t: Translate, problem: BoardTextProblem): string {
        switch (problem) {
            case 'NAME_REQUIRED':
                return t('board.problemNameRequired');
            case 'NAME_LENGTH':
                return t('board.problemNameLength', { max: UtilBoard.NAME_MAX });
            case 'NAME_DUPLICATE':
                return t('board.problemNameDuplicate');
            case 'NAME_EN_LENGTH':
                return t('board.problemNameEnLength', { max: UtilBoard.NAME_EN_MAX });
            case 'SLUG_REQUIRED':
                return t('board.problemSlugRequired');
            case 'SLUG_LENGTH':
                return t('board.problemSlugLength', { min: UtilBoard.SLUG_MIN, max: UtilBoard.SLUG_MAX });
            case 'SLUG_FORMAT':
                return t('board.problemSlugFormat');
            case 'SLUG_RESERVED':
                return t('board.problemSlugReserved', { words: UtilBoard.SLUG_RESERVED.join(', ') });
            case 'SLUG_DUPLICATE':
                return t('board.problemSlugDuplicate');
            case 'DESC_LENGTH':
                return t('board.problemDescLength', { max: UtilBoard.DESC_MAX });
            case 'TEXT_LENGTH':
                return t('board.problemTextLength', { max: UtilBoard.TEXT_MAX });
            case 'PREFIX_NAME_REQUIRED':
                return t('board.problemPrefixRequired');
            case 'PREFIX_NAME_LENGTH':
                return t('board.problemPrefixLength', { max: UtilBoard.PREFIX_NAME_MAX });
            case 'PREFIX_NAME_DUPLICATE':
                return t('board.problemPrefixDuplicate');
        }
    }

    /**
     * 최소 자격 값의 이름 — 「운영진만」 · 「끔」 · 「{등급} 이상」.
     * @param {(key: string) => string} gradeLabel - 등급 키 → 이름(목록에 없으면 키 글자).
     */
    static minLevel(t: Translate, value: string, gradeLabel: (key: string) => string): string {
        if (value === BOARD_MIN_GUEST) return t('board.minGuest');
        if (value === BOARD_MIN_STAFF) return t('board.minStaff');
        if (value === BOARD_MIN_OFF) return t('board.minOff');
        return t('board.minGrade', { grade: gradeLabel(value) });
    }

    /** 고르기 칸 값의 이름 */
    static choice(t: Translate, field: string, value: string | number): string {
        const spec = UtilBoard.spec(field);
        if (spec.unit === 'minutes' && typeof value === 'number') return value === 0 ? t('board.immediately') : t('board.unitMinutes', { n: value });
        return t(`board.choice.${field}.${value}`);
    }

    /**
     * 칸 값을 한 줄 글자로 — 「기본값: …」 표시용.
     * @returns {string} 예: 「20개」, 「켬」, 「일반 이상」.
     */
    static valueText(t: Translate, field: string, value: BoardFieldValue, gradeLabel: (key: string) => string): string {
        const spec = UtilBoard.spec(field);
        // 이름 칸의 null = 화면 기본 이름(「추천」·「비추천」)
        if (spec.kind === 'label') return UtilBoard.labelValue(value) ?? UtilBoardMessages.builtInLabel(t, field);
        if (value === null) return '';
        switch (spec.kind) {
            case 'multi':
                return UtilBoardMessages.multiText(t, field, value);
            case 'bool':
                return t(value ? 'common.on' : 'common.off');
            case 'writeMin':
            case 'commentMin':
            case 'grade':
                return UtilBoardMessages.minLevel(t, String(value), gradeLabel);
            case 'choice':
            case 'dateDisplay':
                return UtilBoardMessages.choice(t, field, value as string | number);
            case 'number':
                return UtilBoardMessages.withUnit(t, field, Number(value));
        }
    }

    /** 이름 칸의 화면 기본 이름 — 「추천(기본 이름)」 */
    static builtInLabel(t: Translate, field: string): string {
        return t('board.labelBuiltIn', { name: t(`board.labelDefault.${field}`) });
    }

    /** 여러 개 고르기 값 → 「글쓴이 · 운영자」, 비었으면 「받지 않음」 */
    static multiText(t: Translate, field: string, value: BoardFieldValue): string {
        const picked = UtilBoard.multiValue(field, value) ?? [];
        return picked.length ? picked.map((v) => t(`board.choice.${field}.${v}`)).join(' · ') : t(`board.multiNone.${field}`);
    }

    /** 숫자 + 단위 */
    static withUnit(t: Translate, field: string, n: number): string {
        const unit = UtilBoard.spec(field).unit;
        if (field === 'commentProtectMin' && n === 0) return t('board.protectOff');
        return unit ? t(`board.unit.${unit}`, { n: n.toLocaleString() }) : n.toLocaleString();
    }

    /** 칸 아래 범위 안내 — 「10~100」 */
    static range(t: Translate, field: string): string {
        const spec = UtilBoard.spec(field);
        return t('board.rangeHint', { min: (spec.min ?? 0).toLocaleString(), max: (spec.max ?? 0).toLocaleString() });
    }

    /** 기본 설정 맨 위 안내 */
    static defaultsBanner(t: Translate, following: number, total: number): string {
        return t('board.defaultsBanner', { following, differing: Math.max(0, total - following) });
    }

    /** 담당 지정 자격 미달 사유 */
    static ineligible(t: Translate, reason: BoardStaffIneligible): string {
        return t(`board.ineligible.${reason}`, { max: UtilBoard.MEMBER_BOARD_MAX });
    }
}
