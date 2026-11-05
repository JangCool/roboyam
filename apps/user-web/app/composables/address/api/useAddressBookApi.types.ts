/**
 * 배송지 목록(주소록) API 가 주고받는 모양 — **서버에 아직 없다**(목업). 회원이 배송지를 최대 10개 두고 하나를 기본으로 정한다.
 * 저장은 서버에서 암호화한다(나중 단계). 회원 본인 화면에는 전체 값을 그대로 보이고, 운영자는 회원 주소록을 볼 수 없다.
 * ⚠ 받는 분·전화·주소는 개인정보다 — 화면 메모리에만 두고 저장소·주소줄·로그·토스트에 남기지 않는다(forms.md 2-1).
 */

/** 저장된 배송지 한 줄 — `GET /api/me/addresses` 의 `items[]`, `POST`·`PUT` 의 응답 */
export interface ShippingAddress {
    id: string;
    /** 별칭(집·회사 등, 20자) — 없으면 null(화면은 받는 분을 제목으로 쓴다) */
    label: string | null;
    recipient: string;
    phone: string;
    postcode: string;
    address1: string;
    address2: string;
    /** 저장된 배송 메모(50자) — 주문서가 미리 채운다. 없으면 '' */
    memo: string;
    isDefault: boolean;
    /** 만든·고친 시각(ISO) — 목록 차례(기본 다음 최근 순)는 서버가 정해서 준다 */
    updatedAt: string;
}

/** `GET /api/me/addresses` 응답 — 기본이 맨 위, 나머지는 최근 만든/고친 순 */
export interface ShippingAddressList {
    items: ShippingAddress[];
    /** 둘 수 있는 개수(지금 10) */
    max: number;
}

/** `POST /api/me/addresses` · `PUT /api/me/addresses/{id}` 요청 — 칸 오류는 `400 VALIDATION fields` 의 이 이름(`label` …) */
export interface ShippingAddressRequest {
    /** 빈 값이면 null 로 보낸다 */
    label: string | null;
    recipient: string;
    phone: string;
    postcode: string;
    address1: string;
    address2: string;
    memo: string;
    /** 이 배송지를 기본으로(첫 배송지는 이 값과 상관없이 기본이 된다) */
    makeDefault: boolean;
}
