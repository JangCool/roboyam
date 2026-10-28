/**
 * 커머스 공개 정보(`GET /api/commerce/info`)의 응답 모양 — 계약 commerce-groupbuy 결정 18·56. 2b-1 화면 목업이 제안하는 모양(서버는 2b-4).
 * 사업자 정보 칸은 평문이다. 판매 스위치가 꺼져 있거나 칸이 다 차지 않았으면 화면은 사업자 정보를 그리지 않는다.
 */
export interface CommerceInfo {
    /** 판매 스위치(공구 판매가 켜져 있나) */
    salesOpen: boolean;
    /** 사업자 정보 필수 칸이 모두 찼나(체크리스트) */
    ready: boolean;
    business: CommerceBusiness | null;
}

/** 통신판매업자 표시 칸(전자상거래법 제10조 ① — 사이트 전체 푸터·공구 화면 하단) */
export interface CommerceBusiness {
    /** 상호 */
    companyName: string;
    /** 대표자 */
    ceoName: string;
    /** 사업자등록번호 `123-45-67890` */
    businessNo: string;
    /** 통신판매업 신고번호 */
    mailOrderNo: string;
    /** 신고 기관(예: 서울특별시 강남구) */
    mailOrderAuthority: string | null;
    /** 주소(불만 처리 장소) */
    address: string;
    phone: string;
    email: string;
    /** 운영시간(예: 평일 10:00~17:00) */
    hours: string | null;
    /** 개인정보 관리책임자 */
    privacyOfficer: string | null;
    /** 호스팅 사업자 */
    hostingProvider: string | null;
    /** 배송·환불 정책 문구(≤ 3,000자, 평문) */
    policyText: string | null;
}
