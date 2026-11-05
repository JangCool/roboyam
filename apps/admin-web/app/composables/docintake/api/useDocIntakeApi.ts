import type {
    DocFileChannel,
    DocIntakeBundle,
    DocIntakeCreateRequest,
    DocIntakeDisposeRequest,
    DocIntakeHandlingRequest,
    DocIntakeTarget,
} from '~/composables/docintake/api/useDocIntakeApi.types';

/**
 * 사업자 서류 접수 기록 API(관리자) — 서버 `DocIntakeAdminController`(docs/features/biz-doc-intake-contract.md). 상태는 없다.
 * 대상 경로: 계약서 사본 = 신청 건 `/api/admin/biz-applications/{id}`, 사업자등록증 사본 = 사업자 `/api/admin/businesses/{id}`. 권한: 계약서 조회 `ad.read`|`hotdeal.read` · 쓰기
 * `ad.manage`|`hotdeal.manage`(서버가 건의 도메인으로 다시 본다) / 등록증 조회 `business.read` · 쓰기 `business.manage`. 응답은 모두 그 대상의 묶음 전체(`DocIntakeBundle`).
 *  - `GET    …/{id}/doc-intakes` → 종이 접수 줄 · 파일 줄 들어온 길 · 등록증 전자 팩스 파일 · 등록증은 우편·팩스 선택 여부
 *  - `POST   …/{id}/doc-intakes` `DocIntakeCreateRequest` → 접수자 = 로그인 운영자. 계약서는 업로드 허용 상태(운영자 건 `SUBMITTED` · `AWAITING_DEPOSIT`), 등록증은 탈퇴가 아닐 때만 — 아니면 409
 *  - `POST   …/{id}/doc-intakes/{intakeId}/handling` `{ handling, storageLocation }` — 계속 보관 ↔ 스캔 후 폐기 예정(확정 뒤에도 됨 — 종이는 남아 있으니까)
 *  - `POST   …/{id}/doc-intakes/{intakeId}/dispose` `{ disposedOn, reason, scanFileId }` — 되돌릴 수 없다. 계약서는 그 건의 대체되지 않은 파일 사본 중 하나가 스캔본(필수), 등록증은 선택
 *  - `POST   …/{id}/doc-intakes/{intakeId}/void` `{ reason }`(5~200자) — 잘못 기록. 지우지 않고 줄 그어 남기며 개수에 세지 않는다. 폐기한 줄은 안 된다
 *  - 등록증 올리기 `POST /api/admin/businesses/{id}/certificate`(multipart `file` + `channel` `UPLOAD`|`FAX`) → 등록증 묶음. 그 사업자의 지금 등록증이 되므로 화면이 사업자 상세를 다시 읽는다.
 *    (같은 주소의 `GET` 열람은 사업자 심사 쪽 `useBusinessApi.certificate` — 서버 주인이 다르다.) 탈퇴·파기된 신원 409 `BUSINESS_STATE_CHANGED`.
 *  - 계약서 전자 팩스는 기존 계약서 올리기(`useBizApplicationApi.uploadContracts`)에 `channel=FAX` 를 싣는다(파일 줄 `channel`).
 *  칸 오류 400 `fields.*` · 이미 바뀐 줄·허용 안 되는 상태 409 `DOC_INTAKE_STATE_CHANGED` · 없는 줄 404 `DOC_INTAKE_NOT_FOUND`.
 */
const PATH = {
    owner: (target: DocIntakeTarget) => `${target.doc === 'CONTRACT' ? '/api/admin/biz-applications' : '/api/admin/businesses'}/${encodeURIComponent(target.ownerId)}`,
    list: (target: DocIntakeTarget) => `${PATH.owner(target)}/doc-intakes`,
    one: (target: DocIntakeTarget, id: string, action: 'handling' | 'dispose' | 'void') => `${PATH.list(target)}/${encodeURIComponent(id)}/${action}`,
    certificate: (target: DocIntakeTarget) => `${PATH.owner(target)}/certificate`,
};

export function useDocIntakeApi() {
    const authApi = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => authApi.me() });

    return {
        list: (target: DocIntakeTarget): Promise<DocIntakeBundle> => client.get<DocIntakeBundle>(PATH.list(target)),

        create: (target: DocIntakeTarget, body: DocIntakeCreateRequest): Promise<DocIntakeBundle> => client.post<DocIntakeBundle>(PATH.list(target), body),

        changeHandling: (target: DocIntakeTarget, id: string, body: DocIntakeHandlingRequest): Promise<DocIntakeBundle> =>
            client.post<DocIntakeBundle>(PATH.one(target, id, 'handling'), body),

        dispose: (target: DocIntakeTarget, id: string, body: DocIntakeDisposeRequest): Promise<DocIntakeBundle> => client.post<DocIntakeBundle>(PATH.one(target, id, 'dispose'), body),

        void: (target: DocIntakeTarget, id: string, reason: string): Promise<DocIntakeBundle> => client.post<DocIntakeBundle>(PATH.one(target, id, 'void'), { reason }),

        /** 운영자의 등록증 사본 올리기(등록증 대상만) — 전자 팩스면 `channel = 'FAX'` */
        uploadCertificate: (target: DocIntakeTarget, file: File, channel: DocFileChannel): Promise<DocIntakeBundle> => {
            const form = new FormData();
            form.append('file', file);
            form.append('channel', channel);
            return client.post<DocIntakeBundle>(PATH.certificate(target), form);
        },
    };
}
