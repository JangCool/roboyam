import type { ShippingAddress, ShippingAddressRequest } from '~/composables/address/api/useAddressBookApi.types';
import type { GroupBuyAddressInput } from '~/composables/groupbuy/api/useGroupBuyApi.types';

/** 화면 미리 검사에서 빠진 필수 칸 — 문구는 부르는 쪽이 i18n 키로 고른다 */
export type AddressRequiredField = 'recipient' | 'phone' | 'postcode' | 'address1';

/**
 * 배송지 목록(주소록) 도우미 — 설정 › 배송지 관리(`/settings/addresses`)와 공구 주문서(`/group-buys/{id}/order`)가 같이 쓴다.
 * 순수 함수만(서버 판정이 정본 — 화면 검사는 미리 알림). 서버 규칙 이름은 서버 단계에서 정한다(지금은 목업 `address-book-mock.ts` 의 `validate`).
 */
export default class UtilAddressBook {
    /** 둘 수 있는 배송지 수(서버가 `max` 로도 알려 준다 — 화면은 서버 값을 먼저 쓴다) */
    static readonly MAX = 10;
    /** 별칭 최대 글자 수 */
    static readonly LABEL_MAX = 20;
    /** 배송지 관리 화면 */
    static readonly SETTINGS_PATH = '/settings/addresses';

    /**
     * 비어 있는 배송지 입력.
     * @returns {GroupBuyAddressInput} 빈 칸.
     */
    static emptyInput(): GroupBuyAddressInput {
        return { recipient: '', phone: '', postcode: '', address1: '', address2: '', memo: '' };
    }

    /**
     * 저장된 배송지를 입력 칸 값으로 옮긴다(주문서에 넣거나 고칠 때).
     * @param {ShippingAddress} a - 저장된 배송지.
     * @returns {GroupBuyAddressInput} 칸 값(새 객체).
     */
    static toInput(a: ShippingAddress): GroupBuyAddressInput {
        return { recipient: a.recipient, phone: a.phone, postcode: a.postcode, address1: a.address1, address2: a.address2, memo: a.memo };
    }

    /**
     * 입력 칸 값 + 별칭 → 저장 요청(빈 별칭은 null).
     * @param {GroupBuyAddressInput} input - 칸 값.
     * @param {string} label - 별칭(빈 값 허용).
     * @param {boolean} makeDefault - 기본 배송지로 정할지.
     * @returns {ShippingAddressRequest} 요청 본문(평범한 객체).
     */
    static toRequest(input: GroupBuyAddressInput, label: string, makeDefault: boolean): ShippingAddressRequest {
        const trimmed = label.trim();
        return {
            label: trimmed ? trimmed : null,
            recipient: input.recipient,
            phone: input.phone,
            postcode: input.postcode,
            address1: input.address1,
            address2: input.address2,
            memo: input.memo,
            makeDefault,
        };
    }

    /**
     * 비어 있는 필수 칸(받는 분 · 전화 · 우편번호 · 주소) — 공구 주문서 `precheck` 와 같은 규칙.
     * @param {GroupBuyAddressInput} input - 칸 값.
     * @returns {AddressRequiredField[]} 빠진 칸 이름.
     */
    static missing(input: GroupBuyAddressInput): AddressRequiredField[] {
        const out: AddressRequiredField[] = [];
        if (!input.recipient.trim()) out.push('recipient');
        if (!input.phone.trim()) out.push('phone');
        if (!input.postcode.trim()) out.push('postcode');
        if (!input.address1.trim()) out.push('address1');
        return out;
    }

    /**
     * 두 칸 값이 같은가(떠나기 확인 · 바뀐 내용 판단).
     * @param {GroupBuyAddressInput} a - 하나.
     * @param {GroupBuyAddressInput} b - 다른 하나.
     * @returns {boolean} 모든 칸이 같으면 true.
     */
    static sameInput(a: GroupBuyAddressInput, b: GroupBuyAddressInput): boolean {
        return a.recipient === b.recipient && a.phone === b.phone && a.postcode === b.postcode && a.address1 === b.address1 && a.address2 === b.address2 && a.memo === b.memo;
    }

    /**
     * 카드 제목 — 별칭, 없으면 받는 분.
     * @param {ShippingAddress} a - 저장된 배송지.
     * @returns {string} 제목.
     */
    static title(a: ShippingAddress): string {
        return a.label?.trim() ? a.label : a.recipient;
    }

    /**
     * 한 줄 주소 `(우편번호) 주소 상세주소`.
     * @param {Pick<ShippingAddress, 'postcode' | 'address1' | 'address2'>} a - 주소 칸.
     * @returns {string} 한 줄.
     */
    static line(a: Pick<ShippingAddress, 'postcode' | 'address1' | 'address2'>): string {
        return [`(${a.postcode})`, a.address1, a.address2].filter((s) => s.trim() !== '' && s !== '()').join(' ');
    }

    /**
     * 주문서가 처음 고를 배송지 — 기본, 없으면 첫 줄.
     * @param {ShippingAddress[]} items - 서버 차례 그대로의 목록.
     * @returns {ShippingAddress | null} 고를 것. 목록이 비면 null.
     */
    static initial(items: readonly ShippingAddress[]): ShippingAddress | null {
        return items.find((a) => a.isDefault) ?? items[0] ?? null;
    }
}
