import type { EmoticonPackSort, EmoticonPackSource, EmoticonPriceFilter } from '~/composables/emoticon/api/useEmoticonApi.types';
import type { EmoticonContractAgreement, EmoticonSettlementStatus, EmoticonStudioPackRow, EmoticonStudioStatus } from '~/composables/emoticon/api/useEmoticonStudioApi.types';

/**
 * 로보티콘(코드 이름 emoticon) 규칙 — 화면 주소(`/robotikon/**`·`/me/robotikon`), 상점 거르기 값 읽기, 스튜디오 상태로 할 수 있는 일. 상태도 요청도 없다.
 * 토큰(`:emo-ID:`) 자르기·그리기는 레이어 `UtilRichEmoticon`(`#layers/rich-editor/utils/util-rich-emoticon`) 한 곳이다 — 에디터와 같은 규칙을 쓴다.
 * 계약 `docs/features/emoticon-contract.md`.
 */

/** 상점 출처 거르기 — 전체(`ALL`)는 `source` 를 보내지 않는다 */
export type EmoticonSourceFilter = 'ALL' | EmoticonPackSource;

/** 스튜디오 상태 딱지의 톤(`PointShopTag` 와 같은 값) */
export type EmoticonStatusTone = 'neutral' | 'primary' | 'success' | 'warn' | 'danger';

const STATUS_TONE: Readonly<Record<EmoticonStudioStatus, EmoticonStatusTone>> = Object.freeze({
    DRAFT: 'neutral',
    PENDING: 'warn',
    APPROVED: 'success',
    REJECTED: 'danger',
    SUSPENDED: 'danger',
});

/** 정산 은행 하나 — 이름은 고유 이름이라 번역 파일 대신 영문명을 함께 둔다(`useLocalizedName`) */
export interface EmoticonBank {
    code: string;
    name: string;
    nameEn: string;
}

/** 정산 은행 목록 — 서버 `emoticon_payout_accounts` 은행 CHECK · admin-web `UtilEmoticon.bank()` 와 같은 코드(목록을 서버에서 받지 않는다) */
const BANKS: readonly EmoticonBank[] = Object.freeze([
    { code: 'KB', name: '국민', nameEn: 'KB Kookmin' },
    { code: 'SHINHAN', name: '신한', nameEn: 'Shinhan' },
    { code: 'WOORI', name: '우리', nameEn: 'Woori' },
    { code: 'HANA', name: '하나', nameEn: 'Hana' },
    { code: 'NH', name: '농협', nameEn: 'NongHyup' },
    { code: 'IBK', name: '기업', nameEn: 'IBK' },
    { code: 'KAKAO', name: '카카오뱅크', nameEn: 'KakaoBank' },
    { code: 'TOSS', name: '토스뱅크', nameEn: 'Toss Bank' },
    { code: 'KBANK', name: '케이뱅크', nameEn: 'K bank' },
    { code: 'SC', name: 'SC제일', nameEn: 'SC First' },
    { code: 'POST', name: '우체국', nameEn: 'Korea Post' },
]);

const SETTLEMENT_TONE: Readonly<Record<EmoticonSettlementStatus, EmoticonStatusTone>> = Object.freeze({
    ESTIMATED: 'neutral',
    CONFIRMED: 'primary',
    PAID: 'success',
    HELD: 'danger',
});

export default class UtilEmoticon {
    /** 정산 은행 목록 */
    static banks(): readonly EmoticonBank[] {
        return BANKS;
    }

    /** 은행 코드 → 은행(모르면 null) */
    static bank(code: string | null | undefined): EmoticonBank | null {
        return BANKS.find((b) => b.code === code) ?? null;
    }

    /** 정산 상태 딱지 톤 */
    static settlementTone(status: EmoticonSettlementStatus): EmoticonStatusTone {
        return SETTLEMENT_TONE[status];
    }

    /**
     * 내 최근 상품화 계약 동의 — 동의 기록(추가만)에서 가장 늦은 것.
     * @param {EmoticonContractAgreement[]} agreements - 동의 기록.
     * @returns {EmoticonContractAgreement | null} 최근 동의(없으면 null).
     */
    static latestAgreement(agreements: readonly EmoticonContractAgreement[]): EmoticonContractAgreement | null {
        let latest: EmoticonContractAgreement | null = null;
        for (const a of agreements) if (!latest || a.agreedAt > latest.agreedAt) latest = a;
        return latest;
    }

    /** 계약 판 글자 — 게시본 버전 1 → 「v1」 */
    static versionLabel(version: number): string {
        return `v${version}`;
    }

    /** 유료 팩인가 */
    static paid(price: number): boolean {
        return price > 0;
    }

    /** 주소의 `?price=` — 모르는 값은 전체 */
    static parsePrice(value: unknown): EmoticonPriceFilter | 'ALL' {
        return value === 'FREE' || value === 'PAID' ? value : 'ALL';
    }

    /** 이모티콘 상점 */
    static readonly PATH = '/robotikon';
    /** 제작 가이드(비로그인도 본다) */
    static readonly GUIDE_PATH = '/robotikon/guide';
    /** 내 제작 팩 */
    static readonly STUDIO_PATH = '/robotikon/studio';
    /** 정산 정보(유료 팩 작가) */
    static readonly PAYOUT_PATH = '/robotikon/studio/payout';
    /** 새 팩 만들기 */
    static readonly STUDIO_NEW_PATH = '/robotikon/studio/new';
    /** 내 이모티콘(받은 팩) */
    static readonly MY_PATH = '/me/robotikon';

    /** 팩 상세 주소 */
    static packPath(id: string): string {
        return `${UtilEmoticon.PATH}/${encodeURIComponent(id)}`;
    }

    /** 스튜디오 팩 고치기 주소 */
    static studioPackPath(id: string): string {
        return `${UtilEmoticon.STUDIO_PATH}/${encodeURIComponent(id)}`;
    }

    /** 주소의 `?source=` — 모르는 값은 전체 */
    static parseSource(value: unknown): EmoticonSourceFilter {
        return value === 'OFFICIAL' || value === 'CREATOR' ? value : 'ALL';
    }

    /** 주소의 `?sort=` — 모르는 값은 인기 */
    static parseSort(value: unknown): EmoticonPackSort {
        return value === 'LATEST' ? 'LATEST' : 'POPULAR';
    }

    /** 상태 딱지 톤 */
    static statusTone(status: EmoticonStudioStatus): EmoticonStatusTone {
        return STATUS_TONE[status];
    }

    /**
     * 화면에서 고칠 수 있는가 — 심사 대기(새 팩) · 새 판 심사 중은 고칠 수 없다(심사가 끝나야).
     * @param {Pick<EmoticonStudioPackRow, 'status' | 'revision'>} pack - 내 팩.
     * @returns {boolean} 고칠 수 있으면 참.
     */
    static editable(pack: Pick<EmoticonStudioPackRow, 'status' | 'revision'>): boolean {
        return pack.status !== 'PENDING' && pack.revision?.status !== 'PENDING';
    }

    /**
     * 임시 저장이 되는가 — 판매 중인 팩은 새 판을 심사 요청으로만 낸다(임시 저장 없음, 목업 가정).
     * @param {EmoticonStudioStatus | null} status - 상태(새 팩은 null).
     * @returns {boolean} 임시 저장 단추를 보이면 참.
     */
    static draftable(status: EmoticonStudioStatus | null): boolean {
        return status === null || status === 'DRAFT' || status === 'REJECTED' || status === 'SUSPENDED';
    }

    /** 상점에 나와 있는가(판매 중) — 스튜디오에서 「상점에서 보기」 링크 */
    static listed(status: EmoticonStudioStatus): boolean {
        return status === 'APPROVED';
    }
}
