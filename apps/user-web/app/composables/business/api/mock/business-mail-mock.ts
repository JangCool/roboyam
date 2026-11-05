// MOCK: 사업자등록증 「우편·팩스로 보내기」 신청의 목업 — `useBusinessApi.ts` 가 부른다. 서버가 붙으면 이 파일을 지운다(2026-11-05 접수 기록 목업).
// 실제 신청 API 를 부르지 않고, 신청 내용을 이 탭의 sessionStorage 에 남겨 사업자 센터 홈(`/business`)이 「심사 중 — 등록증을 받으면 심사 시작」을 미리 보이게 한다.
// 탭을 닫거나 홈의 [목업 지우기] 를 누르면 사라진다. 보낼 곳(우편 주소·팩스 번호)은 목업 상수 — 나중에 사이트 설정에서 받는다.

import type { BusinessApplyByMailRequest, BusinessDocSendTo, BusinessProfile } from '~/composables/business/api/useBusinessApi.types';

const STORAGE_KEY = 'roboyam.business.mock-mail-application';

const SEND_TO: BusinessDocSendTo = {
    postcode: '00000',
    address: '(목업 주소) ○○시 ○○구 ○○로 00, 0층',
    recipient: '운영팀 사업자 심사 담당',
    fax: '02-0000-0000',
};

function delay(): Promise<void> {
    return new Promise((resolve) => setTimeout(resolve, 300 + Math.random() * 300));
}

/** 가린 등록번호(`123-45-*****`) · 연락처(`010-****-5678`) — 서버와 같은 모양으로 흉내 */
function maskBizNo(digits: string): string {
    return digits.length === 10 ? `${digits.slice(0, 3)}-${digits.slice(3, 5)}-*****` : '';
}
function maskPhone(digits: string): string {
    return digits.length >= 9 ? `${digits.slice(0, 3)}-****-${digits.slice(-4)}` : '';
}

export async function mockDocSendTo(): Promise<BusinessDocSendTo> {
    return { ...SEND_TO };
}

export async function mockApplyByMail(body: BusinessApplyByMailRequest): Promise<BusinessProfile> {
    await delay();
    const profile: BusinessProfile = {
        status: 'PENDING',
        companyName: body.companyName,
        bizNoMasked: maskBizNo(body.bizNo ?? '') || null,
        ownerName: body.ownerName,
        openedOn: body.openedOn,
        phoneMasked: maskPhone(body.phone ?? '') || null,
        email: body.email,
        address: { ...body.address },
        certificate: null,
        submittedAt: new Date().toISOString(),
        reviewedAt: null,
        rejectCode: null,
        rejectReason: null,
        suspendReason: null,
        reReview: false,
        services: [],
    };
    try {
        window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(profile));
    } catch {
        // 저장소를 못 쓰면(사생활 보호 모드 등) 미리보기만 못 한다
    }
    return profile;
}

/** 이 탭에서 「우편·팩스로 보내기」로 낸 목업 신청 — 없으면 null */
export function mockMailApplication(): BusinessProfile | null {
    try {
        const raw = window.sessionStorage.getItem(STORAGE_KEY);
        return raw ? (JSON.parse(raw) as BusinessProfile) : null;
    } catch {
        return null;
    }
}

export function mockClearMailApplication(): void {
    try {
        window.sessionStorage.removeItem(STORAGE_KEY);
    } catch {
        // 무시
    }
}
