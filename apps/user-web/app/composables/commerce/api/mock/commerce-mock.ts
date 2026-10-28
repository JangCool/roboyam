// MOCK: 커머스 공개 정보 API(`GET /api/commerce/info`)의 메모리 목업 — `useCommerceInfoApi.ts` 가 부른다. 서버가 붙으면(2b-4) 이 파일을 지운다.
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름` ─
//   info-fail      읽기 실패(500) — 푸터·공구 하단의 사업자 정보가 빠진다(화면은 그대로 뜬다)
//   info-closed    판매 스위치 꺼짐(`salesOpen: false`) — 사업자 정보를 그리지 않는다
//   info-not-ready 사업자 정보 칸이 덜 참(`ready: false`) — 사업자 정보를 그리지 않는다
//
// ── 목업 가정 ─
//   · 값은 계약 결정 11 의 local 시드 「시험 사업자 정보」를 흉내 낸 것(실제 사업자가 아니다).

import { MOCK_ERRORS, mockClone, mockDelay, mockFlags } from '~/composables/common/mock/mock-util';
import type { CommerceInfo } from '~/composables/commerce/api/useCommerceInfoApi.types';

const INFO: CommerceInfo = {
    salesOpen: true,
    ready: true,
    business: {
        companyName: '로보얌 시험상점',
        ceoName: '홍길동',
        businessNo: '123-45-67890',
        mailOrderNo: '제2026-서울강남-00000호',
        mailOrderAuthority: '서울특별시 강남구',
        address: '서울특별시 강남구 테헤란로 000, 0층 (시험 주소)',
        phone: '02-000-0000',
        email: 'help@example.com',
        hours: '평일 10:00~17:00 (점심 12:30~13:30, 주말·공휴일 휴무)',
        privacyOfficer: '홍길동 (privacy@example.com)',
        hostingProvider: '시험 호스팅(주)',
        policyText: '공동구매 상품은 배송완료 후 7일 안에 단순 변심 반품을 받아요. 하자·오배송은 배송완료 후 3개월(안 날부터 30일) 안에 받아요. 반품 비용은 각 공구 상세에 금액으로 적혀 있어요.',
    },
};

export async function mockCommerceInfo(): Promise<CommerceInfo> {
    await mockDelay();
    const flags = mockFlags();
    if (flags.has('info-fail')) throw MOCK_ERRORS.internal();
    const out = mockClone(INFO);
    if (flags.has('info-closed')) out.salesOpen = false;
    if (flags.has('info-not-ready')) out.ready = false;
    return out;
}
