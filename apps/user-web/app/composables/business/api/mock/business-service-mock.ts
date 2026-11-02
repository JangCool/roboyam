// MOCK: 내 사업자 신원(`GET /api/business/me`)에 서비스별 상태(`services[]`)를 덧씌우는 목업 — `useBusinessApi.ts` 의 `profile()` 이 부른다(2026-11-02 서비스별 정지, 화면 확정 전).
// 서버가 `services` 칸을 주면 `business/api/mock/` 와 함께 지운다(`// MOCK:` 줄 → `// REAL:` 줄).
//
// ── 개발 전용 스위치 — 주소의 `?mock=이름`(쉼표로 여럿) ─
//   svc-suspend-hotdeal   신원은 승인인데 핫딜 서비스만 정지
//   svc-suspend-ad        신원은 승인인데 광고 서비스만 정지
//   (둘 다 주면 둘 다 정지)  스위치가 없으면 서버 응답 그대로다.
//
// ── 목업 규칙(가정) ─
//   · 신원이 승인(`APPROVED`)일 때만 덧씌운다(신원 정지가 우선). 정지하지 않은 서비스는 `APPROVED`(이용 중)로 둔다.
//   · 사유 글은 운영자가 쓴 글이라 번역 키로 옮기지 않는다(`i18n.md` 5절).

import { mockFlags } from '~/composables/common/mock/mock-util';
import type { BusinessProfile, BusinessProfileService, BusinessServiceKind } from '~/composables/business/api/useBusinessApi.types';

const REASON: Record<BusinessServiceKind, string> = {
    HOTDEAL: '할인 전 가격을 부풀려 올린 핫딜이 반복돼 핫딜 서비스만 멈췄어요. 고친 가격 근거를 보내 주시면 다시 확인할게요.',
    AD: '광고 소재가 게시 기준(과장 문구)에 맞지 않아 광고 서비스만 멈췄어요.',
};

export function mockBusinessServices(profile: BusinessProfile): BusinessProfile {
    const flags = mockFlags();
    const suspended = (['HOTDEAL', 'AD'] as const).filter((s) => flags.has(s === 'HOTDEAL' ? 'svc-suspend-hotdeal' : 'svc-suspend-ad'));
    if (!suspended.length || profile.status !== 'APPROVED') return profile;
    const at = new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString();
    const services: BusinessProfileService[] = (['HOTDEAL', 'AD'] as const).map((service) =>
        suspended.includes(service)
            ? { service, status: 'SUSPENDED', suspendCode: 'POLICY_VIOLATION', suspendReason: REASON[service], suspendedAt: at }
            : { service, status: 'APPROVED', suspendCode: null, suspendReason: null, suspendedAt: null },
    );
    return { ...profile, services };
}
