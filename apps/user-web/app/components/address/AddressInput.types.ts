/**
 * 공용 주소 입력(`AddressInput`)의 값 — 칸 이름은 공구 배송지(`GroupBuyAddressInput` 의 `postcode`·`address1`·`address2`)와 같게 둔다(그 화면이 이 부품으로 바꿀 때 그대로 넘긴다).
 */
export interface AddressValue {
    /** 우편번호 5자리 */
    postcode: string;
    /** 기본 주소(도로명 주소를 먼저 쓴다) */
    address1: string;
    /** 상세 주소(동·호수) — 회원이 넣는다 */
    address2: string;
}

/** 칸 이름 → 서버 오류 문구(`ApiError.fields` 를 부르는 쪽이 이 이름으로 옮겨 넘긴다) */
export type AddressFieldErrors = Partial<Record<keyof AddressValue, string>>;
