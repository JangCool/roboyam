// MOCK: 서버를 붙이기 전까지 `TRIAL_ADMIN_MOCK` 분기로 목업을 돌려준다 — 서버가 붙으면 이 import 와 각 메서드의 `// MOCK:` 줄을 지운다
import TrialAdminMock, { TRIAL_ADMIN_MOCK } from '~/composables/trial/api/mock/trial-admin-mock';
import type {
    AdminTrialApplicantPage,
    AdminTrialDamageReport,
    AdminTrialDamageRequest,
    AdminTrialDecideRequest,
    AdminTrialDetail,
    AdminTrialRequest,
    AdminTrialReturnedRequest,
    AdminTrialRow,
    AdminTrialSelectRequest,
    AdminTrialShipmentRequest,
    AdminTrialStockRequest,
    TrialPhotoKind,
    TrialSettings,
    TrialStatus,
    TrialUploaded,
} from '~/composables/trial/api/useTrialApi.types';

/**
 * 체험단 관리 API(관리자) — 서버 주소 문자열(`/api/admin/trials…`·`/api/admin/purchase-proofs…`)이 있는 **유일한 곳**이다. 상태는 없다.
 * 계약 `docs/features/trial-group-contract.md` 「구현 결정」 10 — 서버 admin `AdminTrialController`. 권한(`trial.manage`·`trial.settings`·`trial.review`)은 서버가 판정한다.
 *
 *  - `GET  /api/admin/trials?status=` → `AdminTrialRow[]` · `POST /api/admin/trials` → 201 상세(준비로 시작) · `GET/PUT /api/admin/trials/{id}`(본문 `action` 이면 상태만 바꾼다)
 *  - `POST /api/admin/trials/images`(multipart — 대표 그림) · `POST /api/admin/trials/{id}/photos?kind=STOCK|PRE_SHIP|RETURN|DAMAGE`(multipart) → 201 `TrialUploaded`
 *  - `GET  /api/admin/trials/{id}/applications` → `AdminTrialApplicantPage` · `POST /api/admin/trials/{id}/select` `{ applicationIds }` → 상세(인원 넘으면 400)
 *  - `POST /api/admin/trials/applications/{id}/recommend` `{ recommended }` → 204(운영자도 후보 추천)
 *  - 입고·발송 `POST …/{id}/stock` → 상세 · `PUT …/participants/{id}/shipment` → 204 · `POST …/participants/{id}/returned` → 204 · `POST …/{id}/return-shipment` → 상세
 *  - 파손 `POST …/participants/{id}/damage-reports` → 201 신고 · `POST /api/admin/trials/damage-reports/{id}/decide` `{ verdict, note }` → 판정한 신고
 *  - 구매 인증 `GET /api/admin/purchase-proofs/{id}/photo`(JPEG · 열람 기록) · `POST …/{id}/approve`(본문 없음) · `POST …/{id}/reject` `{ reason }` → 204
 *  - `GET/PUT /api/admin/trials/settings`
 *
 * ⚠ MOCK — `TRIAL_ADMIN_MOCK` 이면 `mock/trial-admin-mock.ts` 가 답한다(실제 호출 줄은 그 아래에 그대로 있다).
 */
const ROOT = '/api/admin/trials';
const PATH = {
    root: ROOT,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    images: `${ROOT}/images`,
    applications: (id: string) => `${ROOT}/${encodeURIComponent(id)}/applications`,
    select: (id: string) => `${ROOT}/${encodeURIComponent(id)}/select`,
    recommend: (applicationId: string) => `${ROOT}/applications/${encodeURIComponent(applicationId)}/recommend`,
    photos: (id: string) => `${ROOT}/${encodeURIComponent(id)}/photos`,
    stock: (id: string) => `${ROOT}/${encodeURIComponent(id)}/stock`,
    shipment: (participantId: string) => `${ROOT}/participants/${encodeURIComponent(participantId)}/shipment`,
    returned: (participantId: string) => `${ROOT}/participants/${encodeURIComponent(participantId)}/returned`,
    returnShipment: (id: string) => `${ROOT}/${encodeURIComponent(id)}/return-shipment`,
    damage: (participantId: string) => `${ROOT}/participants/${encodeURIComponent(participantId)}/damage-reports`,
    decide: (reportId: string) => `${ROOT}/damage-reports/${encodeURIComponent(reportId)}/decide`,
    proof: (id: string, action: 'photo' | 'approve' | 'reject') => `/api/admin/purchase-proofs/${encodeURIComponent(id)}/${action}`,
    settings: `${ROOT}/settings`,
};

export function useTrialApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    /** 사진 한 장 올리기(multipart) */
    function upload(url: string, file: File, kind?: string): Promise<TrialUploaded> {
        const form = new FormData();
        form.append('file', file);
        return client.post<TrialUploaded>(url, form, kind ? { query: { kind } } : undefined);
    }

    return {
        /** 모집 목록(상태 빈 글자 = 전체) */
        list: (status: TrialStatus | ''): Promise<AdminTrialRow[]> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.list(status); // MOCK:
            return client.get<AdminTrialRow[]>(PATH.root, { query: { status: status || undefined } });
        },

        /** 모집 상세(입고·발송·파손 포함) */
        detail: (id: string): Promise<AdminTrialDetail> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.detail(id); // MOCK:
            return client.get<AdminTrialDetail>(PATH.one(id));
        },

        /** 만들기(준비로 시작) */
        create: (body: AdminTrialRequest): Promise<AdminTrialDetail> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.save(null, body); // MOCK:
            return client.post<AdminTrialDetail>(PATH.root, body);
        },

        /** 고치기 — `action` 이 있으면 상태만 바꾼다(모집 열기·신청 마감·끝내기·취소) */
        update: (id: string, body: AdminTrialRequest): Promise<AdminTrialDetail> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.save(id, body); // MOCK:
            return client.put<AdminTrialDetail>(PATH.one(id), body);
        },

        /** 대표 그림 올리기 → `key` 를 `imageKey` 로 */
        uploadCover: (file: File): Promise<TrialUploaded> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.uploadPhoto('COVER', file); // MOCK:
            return upload(PATH.images, file);
        },

        /** 신청자(뽑기 표) */
        applications: (id: string): Promise<AdminTrialApplicantPage> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.applications(); // MOCK:
            return client.get<AdminTrialApplicantPage>(PATH.applications(id));
        },

        /** 후보 추천 켜기·끄기(운영자도) */
        recommend: (applicationId: string, recommended: boolean): Promise<void> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.recommend(applicationId, recommended); // MOCK:
            return client.post<void>(PATH.recommend(applicationId), { recommended });
        },

        /** 뽑기 확정 → 바뀐 상세 */
        select: (id: string, body: AdminTrialSelectRequest): Promise<AdminTrialDetail> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.select(id, body.applicationIds); // MOCK:
            return client.post<AdminTrialDetail>(PATH.select(id), body);
        },

        /** 사진 올리기(입고 · 발송 전 · 반납 · 파손) */
        uploadPhoto: (id: string, kind: TrialPhotoKind, file: File): Promise<TrialUploaded> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.uploadPhoto(kind, file); // MOCK:
            return upload(PATH.photos(id), file, kind);
        },

        /** 입고 완료(사진 필수) → 바뀐 상세 */
        stock: (id: string, body: AdminTrialStockRequest): Promise<AdminTrialDetail> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.stock(id, body.quantity, body.note, body.photoIds); // MOCK:
            return client.post<AdminTrialDetail>(PATH.stock(id), body);
        },

        /** 회원별 송장 */
        shipment: (participantId: string, body: AdminTrialShipmentRequest): Promise<void> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.shipment(participantId, body.carrier, body.trackingNo); // MOCK:
            return client.put<void>(PATH.shipment(participantId), body);
        },

        /** 반납 받음 + 사진 */
        returned: (participantId: string, body: AdminTrialReturnedRequest): Promise<void> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.returned(participantId, body.photoIds); // MOCK:
            return client.post<void>(PATH.returned(participantId), body);
        },

        /** 광고주 반송 송장 → 바뀐 상세 */
        returnShipment: (id: string, body: AdminTrialShipmentRequest): Promise<AdminTrialDetail> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.returnShipment(id, body.carrier, body.trackingNo); // MOCK:
            return client.post<AdminTrialDetail>(PATH.returnShipment(id), body);
        },

        /** 파손 신고 */
        damage: (participantId: string, body: AdminTrialDamageRequest): Promise<AdminTrialDamageReport> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.damage(participantId, body.description, body.photoIds); // MOCK:
            return client.post<AdminTrialDamageReport>(PATH.damage(participantId), body);
        },

        /** 파손 판정 */
        decide: (reportId: string, body: AdminTrialDecideRequest): Promise<AdminTrialDamageReport> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.decide(reportId, body); // MOCK:
            return client.post<AdminTrialDamageReport>(PATH.decide(reportId), body);
        },

        /** 구매 인증 사진 주소(비공개 — `<img>` 가 로그인 쿠키로 받는다, 열람 기록) */
        proofPhotoUrl: (id: string): string => {
            if (TRIAL_ADMIN_MOCK) return `https://picsum.photos/seed/irobit-receipt-${encodeURIComponent(id)}/600/800`; // MOCK:
            return PATH.proof(id, 'photo');
        },

        /** 구매 인증 승인 */
        approveProof: (id: string): Promise<void> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.reviewProof(id, true, ''); // MOCK:
            return client.post<void>(PATH.proof(id, 'approve'));
        },

        /** 구매 인증 거절(사유 필수) */
        rejectProof: (id: string, reason: string): Promise<void> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.reviewProof(id, false, reason); // MOCK:
            return client.post<void>(PATH.proof(id, 'reject'), { reason });
        },

        /** 체험단 설정 */
        settings: (): Promise<TrialSettings> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.settings(); // MOCK:
            return client.get<TrialSettings>(PATH.settings);
        },

        /** 체험단 설정 저장 */
        saveSettings: (body: TrialSettings): Promise<TrialSettings> => {
            if (TRIAL_ADMIN_MOCK) return TrialAdminMock.saveSettings(body); // MOCK:
            return client.put<TrialSettings>(PATH.settings, body);
        },
    };
}
