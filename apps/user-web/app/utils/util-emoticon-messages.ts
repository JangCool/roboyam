import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { EmoticonRules } from '~/composables/emoticon/api/useEmoticonApi.types';
import type { EmoticonSettlementStatus, EmoticonStudioStatus } from '~/composables/emoticon/api/useEmoticonStudioApi.types';
import type { EmoticonFieldProblem, EmoticonImageInfo, EmoticonSpecProblem } from '~/utils/util-emoticon-spec';
import type { EmoticonBodyProblem } from '~/utils/util-emoticon-rule';
import { ApiError } from '~/composables/common/useApiClient';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilEmoticonSpec from '~/utils/util-emoticon-spec';
import UtilRichEmoticon from '#layers/rich-editor/utils/util-rich-emoticon';

/**
 * 이모티콘 화면의 계산이 낀 문구 — 규격 문제(값이 들어간다)·칸 문제·상태 이름·오류 한 줄·한 줄 요약. 상수 문구는 화면이 `t('emoticon.…')` 를 직접 부른다.
 */

const STATUS_KEY: Readonly<Record<EmoticonStudioStatus, string>> = Object.freeze({
    DRAFT: 'emoticon.statusDraft',
    PENDING: 'emoticon.statusPending',
    APPROVED: 'emoticon.statusApproved',
    REJECTED: 'emoticon.statusRejected',
    SUSPENDED: 'emoticon.statusSuspended',
});

const SETTLEMENT_KEY: Readonly<Record<EmoticonSettlementStatus, string>> = Object.freeze({
    ESTIMATED: 'emoticon.settlementEstimated',
    CONFIRMED: 'emoticon.settlementConfirmed',
    PAID: 'emoticon.settlementPaid',
    HELD: 'emoticon.settlementHeld',
});

export default class UtilEmoticonMessages {
    /**
     * 원 글자(`2,500원`).
     * @param {Translate} t - 번역 함수.
     * @param {number} amount - 원.
     * @returns {string} 글자.
     */
    static won(t: Translate, amount: number): string {
        return t('payment.won', { amount: amount.toLocaleString('ko-KR') });
    }

    /**
     * 부호 붙은 원 글자 — 음수면 `−2,500원`(빼기 기호), 아니면 `won` 과 같다. 정산 기준·작가 몫처럼 확정 뒤 환불로 음수가 될 수 있는 칸에 쓴다.
     * @param {Translate} t - 번역 함수.
     * @param {number} amount - 원(음수 가능).
     * @returns {string} 글자.
     */
    static signedWon(t: Translate, amount: number): string {
        return amount < 0 ? `−${UtilEmoticonMessages.won(t, -amount)}` : UtilEmoticonMessages.won(t, amount);
    }

    /**
     * 빼는 금액 글자(부가세·수수료·원천징수 칸) — 양수면 `−2,500원`, 음수(판매보다 환불이 많은 달 — 빼던 것을 되돌린다)면 `+2,500원`, 0 이면 `—`.
     * @param {Translate} t - 번역 함수.
     * @param {number} amount - 빼는 원(음수 가능).
     * @returns {string} 글자.
     */
    static deduction(t: Translate, amount: number): string {
        if (amount === 0) return '—';
        return amount > 0 ? `−${UtilEmoticonMessages.won(t, amount)}` : `+${UtilEmoticonMessages.won(t, -amount)}`;
    }

    /** 가격 글자 — 0 이면 「무료」, 아니면 원 */
    static price(t: Translate, price: number): string {
        return price > 0 ? UtilEmoticonMessages.won(t, price) : t('emoticon.free');
    }

    /** 정산 상태 이름 */
    static settlement(t: Translate, status: EmoticonSettlementStatus): string {
        return t(SETTLEMENT_KEY[status]);
    }

    /** 비율 글자(`30%`·`3.3%`) */
    static percent(rate: number): string {
        return `${Math.round(rate * 1000) / 10}%`;
    }

    /**
     * 본문 로보티콘 문제 한 줄 — 개수 초과 · 가지지 않은 팩의 로보티콘(이름표).
     * @param {Translate} t - 번역 함수.
     * @param {EmoticonBodyProblem | null} problem - `UtilEmoticonRule.check` 결과.
     * @param {(id: string) => string} labelOf - ID → 이름표.
     * @returns {string} 한 줄(문제 없으면 빈 글자).
     */
    static bodyProblem(t: Translate, problem: EmoticonBodyProblem | null, labelOf: (id: string) => string): string {
        if (!problem) return '';
        if (problem.code === 'TOO_MANY') return t('emoticon.tooManyInBody', { max: problem.max });
        return t('emoticon.notOwnedInBody', { name: labelOf(problem.emoticonId) });
    }

    /**
     * 글·댓글 저장 때 서버의 로보티콘 오류를 화면 문구로 — `409 EMOTICON_NOT_OWNED`(최상위 `emoticonId`)는 미리 검사와 같은 문구(이름표)로. 그 밖은 null(부르는 쪽 처리 그대로).
     * 개수 초과(`400 VALIDATION fields.content`)는 서버 칸 문구가 본문 칸 아래에 그대로 나온다(미리 검사 문구와 같은 뜻).
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류.
     * @param {(id: string) => string} labelOf - ID → 이름표(카탈로그에 없으면 ID).
     * @returns {string | null} 본문 칸 아래 한 줄, 로보티콘 오류가 아니면 null.
     */
    static bodyError(t: Translate, e: unknown, labelOf: (id: string) => string): string | null {
        if (!(e instanceof ApiError) || e.code !== 'EMOTICON_NOT_OWNED') return null;
        const id = e.extraText('emoticonId');
        return id ? t('emoticon.notOwnedInBody', { name: labelOf(id) }) : e.message;
    }

    /** 가격 단계 글자 — 「1,500 · 2,500 · 3,500원」 */
    static tiers(t: Translate, tiers: number[]): string {
        return tiers.map((price) => UtilEmoticonMessages.won(t, price)).join(' · ');
    }

    /** 상태 이름 */
    static status(t: Translate, status: EmoticonStudioStatus): string {
        return t(STATUS_KEY[status]);
    }

    /**
     * 그림 규격 문제 한 줄 — 지금 값(크기·용량·프레임)을 함께 보인다.
     * @param {Translate} t - 번역 함수.
     * @param {EmoticonSpecProblem} problem - 문제.
     * @param {EmoticonImageInfo | null} info - 읽은 그림 정보.
     * @param {number} size - 파일 바이트.
     * @param {EmoticonRules} rules - 규격.
     * @param {boolean} cover - 대표 이미지인가.
     * @returns {string} 한 줄.
     */
    static spec(t: Translate, problem: EmoticonSpecProblem, info: EmoticonImageInfo | null, size: number, rules: EmoticonRules, cover = false): string {
        switch (problem.code) {
            case 'TYPE':
                return t('emoticon.specType');
            case 'COVER_TYPE':
                return t('emoticon.specCoverType');
            case 'DIMENSION':
                return t('emoticon.specDimension', { size: rules.imageSize, width: info?.width ?? 0, height: info?.height ?? 0 });
            case 'TOO_LARGE':
                return t('emoticon.specTooLarge', { max: UtilEmoticonSpec.bytes(cover ? rules.coverMaxBytes : rules.maxBytes), now: UtilEmoticonSpec.bytes(size) });
            case 'TOO_MANY_FRAMES':
                return t('emoticon.specFrames', { max: rules.maxFrames, now: info?.frames ?? 0 });
            case 'NOT_LOOP':
                return t('emoticon.specLoop');
            case 'HEAVY':
                return t('emoticon.specHeavy', { recommended: UtilEmoticonSpec.bytes(rules.recommendedBytes), now: UtilEmoticonSpec.bytes(size) });
        }
    }

    /** 팩 이름 문제 */
    static name(t: Translate, problem: EmoticonFieldProblem | null, rules: EmoticonRules): string {
        if (!problem) return '';
        if (problem === 'REQUIRED') return t('emoticon.nameRequired');
        if (problem === 'PATTERN') return t('emoticon.namePattern');
        return t('emoticon.nameLength', { min: rules.nameMin, max: rules.nameMax });
    }

    /** 이름표 문제 */
    static label(t: Translate, problem: EmoticonFieldProblem | null, rules: EmoticonRules): string {
        if (!problem) return '';
        if (problem === 'REQUIRED') return t('emoticon.labelRequired');
        return t('emoticon.labelLength', { max: rules.labelMax });
    }

    /** 태그 문제 */
    static tags(t: Translate, problem: EmoticonFieldProblem | null, rules: EmoticonRules): string {
        if (!problem) return '';
        if (problem === 'TOO_MANY') return t('emoticon.tagsTooMany', { max: rules.maxTags });
        if (problem === 'DUPLICATE') return t('emoticon.tagsDuplicate');
        return t('emoticon.tagTooLong', { max: rules.tagMax });
    }

    /** 개수 문제 */
    static count(t: Translate, problem: EmoticonFieldProblem | null, rules: EmoticonRules, now: number): string {
        if (!problem) return '';
        return t('emoticon.countRange', { min: rules.minCount, max: rules.maxCount, now });
    }

    /**
     * 오류 한 줄 — 서버 `ApiError` 는 서버 문구 그대로(가공 금지), 그 밖(네트워크 등)은 공통 문구.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류.
     * @returns {string} 한 줄.
     */
    static error(t: Translate, e: unknown): string {
        if (!(e instanceof ApiError)) return UtilAuthMessages.fallback(t, 0, 'NETWORK');
        return e.message;
    }

    /**
     * 한 줄 요약 — 토큰을 「(이모티콘)」으로(목록 미리보기·알림).
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} text - 글자.
     * @returns {string} 바꾼 글자.
     */
    static summary(t: Translate, text: string | null | undefined): string {
        if (!text) return '';
        return UtilRichEmoticon.has(text) ? UtilRichEmoticon.summarize(text, t('emoticon.summaryWord')) : text;
    }
}
