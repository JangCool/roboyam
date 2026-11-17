import type {
    BizTrialDamageRequest,
    BizTrialPhotoKind,
    BizTrialReturnedRequest,
    BizTrialRow,
    BizTrialShipmentRequest,
    BizTrialUploaded,
    BizTrialView,
} from '~/composables/business/api/useBusinessTrialApi.types';

/**
 * 사업자 센터 「체험단」 API(광고주 — trial-group-contract 「구현 결정」 8, 서버 `BusinessTrialController`) — 서버 주소 문자열(`/api/business/trials…`)이 있는 **유일한 곳**이다. 상태는 없다.
 *
 *  - `GET  /api/business/trials` → `BizTrialRow[]`(그 광고주의 모집 — 광고주 = 사업자 신원으로 이어진 광고주)
 *  - `GET  /api/business/trials/{id}` → `{ trial, applicants }`(뽑힌 줄 주소는 동의 범위 안만)
 *  - `POST /api/business/trials/applications/{applicationId}/recommend` `{ recommended }` → 204(후보 추천 — 최종 선정은 운영자)
 *  - `POST /api/business/trials/{id}/photos?kind=PRE_SHIP|RETURN|DAMAGE`(multipart `file`) → 201 `{ id, kind, key, url }`(먼저 올리고 처리 때 붙인다)
 *  - `PUT  /api/business/trials/participants/{id}/shipment` `{ carrier, trackingNo, photoIds }` → 204(발송 전 사진 없으면 400)
 *  - `POST /api/business/trials/participants/{id}/returned` `{ photoIds }` → 204 · `POST …/participants/{id}/damage-reports` `{ description, photoIds }` → 201
 */
const ROOT = '/api/business/trials';
const PATH = {
    list: ROOT,
    one: (id: string) => `${ROOT}/${encodeURIComponent(id)}`,
    recommend: (applicationId: string) => `${ROOT}/applications/${encodeURIComponent(applicationId)}/recommend`,
    photos: (id: string) => `${ROOT}/${encodeURIComponent(id)}/photos`,
    shipment: (participantId: string) => `${ROOT}/participants/${encodeURIComponent(participantId)}/shipment`,
    returned: (participantId: string) => `${ROOT}/participants/${encodeURIComponent(participantId)}/returned`,
    damage: (participantId: string) => `${ROOT}/participants/${encodeURIComponent(participantId)}/damage-reports`,
};

export function useBusinessTrialApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 모집 */
        list: (signal?: AbortSignal): Promise<BizTrialRow[]> => {
            return client.get<BizTrialRow[]>(PATH.list, { signal });
        },

        /** 모집 하나 — 신청자 · 뽑힌 줄 · 파손 신고 */
        detail: (id: string, signal?: AbortSignal): Promise<BizTrialView> => {
            return client.get<BizTrialView>(PATH.one(id), { signal });
        },

        /** 후보 추천 켜기·끄기 */
        recommend: (applicationId: string, recommended: boolean): Promise<void> => {
            return client.post<void>(PATH.recommend(applicationId), { recommended });
        },

        /** 사진 올리기(발송 전 · 반납 · 파손) */
        uploadPhoto: (id: string, kind: BizTrialPhotoKind, file: File): Promise<BizTrialUploaded> => {
            const form = new FormData();
            form.append('file', file);
            return client.upload<BizTrialUploaded>(PATH.photos(id), form, { query: { kind } });
        },

        /** 송장 넣기(발송 전 사진 필수) */
        shipment: (participantId: string, body: BizTrialShipmentRequest): Promise<void> => {
            return client.put<void>(PATH.shipment(participantId), body);
        },

        /** 반납 받음(대여형) + 반납 사진 */
        returned: (participantId: string, body: BizTrialReturnedRequest): Promise<void> => {
            return client.post<void>(PATH.returned(participantId), body);
        },

        /** 파손 신고(사진 + 설명) */
        damage: async (participantId: string, body: BizTrialDamageRequest): Promise<void> => {
            await client.post<unknown>(PATH.damage(participantId), body);
        },
    };
}
