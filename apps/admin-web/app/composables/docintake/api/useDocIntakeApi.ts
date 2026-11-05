import {
    mockDocIntakeChangeHandling,
    mockDocIntakeCreate,
    mockDocIntakeDispose,
    mockDocIntakeFaxFile,
    mockDocIntakeList,
    mockDocIntakeMailDeclared,
    mockDocIntakeMarkFile,
    mockDocIntakeVoid,
} from '~/composables/docintake/api/mock/docintake-mock';
import type {
    DocFileChannel,
    DocIntakeBundle,
    DocIntakeCreateRequest,
    DocIntakeDisposeRequest,
    DocIntakeHandlingRequest,
    DocIntakeTarget,
} from '~/composables/docintake/api/useDocIntakeApi.types';

/**
 * 사업자 서류 접수 기록 API(관리자) — **목업 — 서버 연결 전, API 후보.** 상태는 없다. 설계 메모 2026-11-05(인쇄본·팩스 접수).
 * 대상 경로: 계약서 사본 = 신청 건 `/api/admin/biz-applications/{id}`, 사업자등록증 사본 = 사업자 `/api/admin/businesses/{id}`. 권한은 그 대상의 처리 권한(`ad.manage`·`hotdeal.manage` / `business.manage`).
 *  - `GET    …/{id}/doc-intakes` → `DocIntakeBundle`(종이 접수 줄 · 파일 줄 들어온 길 · 등록증은 우편·팩스 선택 여부)
 *  - `POST   …/{id}/doc-intakes` `DocIntakeCreateRequest` → 접수자 = 로그인 운영자. 계약서는 업로드 허용 상태(운영자 건 `SUBMITTED` · `AWAITING_DEPOSIT`)에서만 — 아니면 409
 *  - `POST   …/{id}/doc-intakes/{intakeId}/handling` `{ handling, storageLocation }` — 계속 보관 ↔ 스캔 후 폐기 예정(확정 뒤에도 됨 — 종이는 남아 있으니까)
 *  - `POST   …/{id}/doc-intakes/{intakeId}/dispose` `{ disposedOn, reason, scanFileId }` — 되돌릴 수 없다. 계약서는 그 건의 대체되지 않은 파일 사본이 있어야 하고 그중 하나를 스캔본으로(5년 보존은 파일로 채움)
 *  - `POST   …/{id}/doc-intakes/{intakeId}/void` `{ reason }`(5~200자) — 잘못 기록. 지우지 않고 줄 그어 남기며 개수에 세지 않는다
 *  - 전자 팩스(파일로 받은 팩스): 계약서는 기존 `POST …/contracts` 에 `channel=FAX` 를 더해 올린다(파일 줄 `channel`). 등록증은 관리자 등록증 올리기 API 가 새로 필요하다
 *    (`POST /api/admin/businesses/{id}/certificate` multipart `file` + `channel=FAX`) — 목업은 이름·크기만 메모리에 둔다.
 *  - 등록증 「우편·팩스로 보냄」은 사업자 상세(`certificateDelivery: UPLOAD | MAIL_FAX`)에 실린다 — 목업은 묶음의 `mailDeclared` + 목업 스위치.
 *  칸 오류 400 `fields.*` · 이미 바뀐 줄 409 `DOC_INTAKE_STATE_CHANGED` · 없는 줄 404 `DOC_INTAKE_NOT_FOUND`.
 *
 * ⚠ MOCK: 서버에 API 가 없어 `./mock/docintake-mock.ts` 를 부른다. 서버가 붙으면 `// MOCK:` 줄과 목업 import 를 지우고 `// REAL:` 줄을 살린다.
 */
const PATH = {
    owner: (target: DocIntakeTarget) => `${target.doc === 'CONTRACT' ? '/api/admin/biz-applications' : '/api/admin/businesses'}/${encodeURIComponent(target.ownerId)}`,
    list: (target: DocIntakeTarget) => `${PATH.owner(target)}/doc-intakes`,
    one: (target: DocIntakeTarget, id: string, action: 'handling' | 'dispose' | 'void') => `${PATH.list(target)}/${encodeURIComponent(id)}/${action}`,
};

export function useDocIntakeApi() {
    // REAL: const authApi = useAdminAuthApi();
    // REAL: const client = useApiClient({ refreshCsrf: () => authApi.me() });
    void PATH;
    // MOCK: 서버는 접수자·폐기자를 로그인 운영자로 채운다 — 목업은 화면이 아는 이름을 넘긴다
    const { user } = useAdminAuth();
    const operatorName = () => user.value?.nickname ?? '운영자';

    return {
        list: (target: DocIntakeTarget): Promise<DocIntakeBundle> =>
            // MOCK:
            mockDocIntakeList(target),
        // REAL: client.get<DocIntakeBundle>(PATH.list(target)),

        create: (target: DocIntakeTarget, body: DocIntakeCreateRequest): Promise<DocIntakeBundle> =>
            // MOCK:
            mockDocIntakeCreate(target, body, operatorName()),
        // REAL: client.post<DocIntakeBundle>(PATH.list(target), body),

        changeHandling: (target: DocIntakeTarget, id: string, body: DocIntakeHandlingRequest): Promise<DocIntakeBundle> =>
            // MOCK:
            mockDocIntakeChangeHandling(target, id, body),
        // REAL: client.post<DocIntakeBundle>(PATH.one(target, id, 'handling'), body),

        dispose: (target: DocIntakeTarget, id: string, body: DocIntakeDisposeRequest): Promise<DocIntakeBundle> =>
            // MOCK:
            mockDocIntakeDispose(target, id, body, operatorName()),
        // REAL: client.post<DocIntakeBundle>(PATH.one(target, id, 'dispose'), body),

        void: (target: DocIntakeTarget, id: string, reason: string): Promise<DocIntakeBundle> =>
            // MOCK:
            mockDocIntakeVoid(target, id, reason, operatorName()),
        // REAL: client.post<DocIntakeBundle>(PATH.one(target, id, 'void'), { reason }),

        /**
         * 파일 줄 들어온 길 표시(전자 팩스) — 서버가 붙으면 업로드 요청의 `channel=FAX` 로 대신하고 이 메서드는 없앤다.
         */
        markFileChannel: (target: DocIntakeTarget, fileId: string, channel: DocFileChannel): Promise<DocIntakeBundle> =>
            // MOCK:
            mockDocIntakeMarkFile(target, fileId, channel),
        // REAL: (서버 API 없음 — 업로드에 channel 을 실어 보낸다)

        /** 등록증 전자 팩스 파일 올리기 — REAL: `POST /api/admin/businesses/{id}/certificate`(multipart `file`, `channel=FAX`) */
        uploadFaxFile: (target: DocIntakeTarget, file: File): Promise<DocIntakeBundle> =>
            // MOCK:
            mockDocIntakeFaxFile(target, file, operatorName()),
        // REAL: client.upload<DocIntakeBundle>(`${PATH.owner(target)}/certificate`, ((f) => (f.append('file', file), f.append('channel', 'FAX'), f))(new FormData())),

        /** 목업 전용 — 신청자가 「우편·팩스로 보냄」을 고른 것으로 켜고 끈다(서버가 붙으면 사업자 상세 값이라 없앤다) */
        setMailDeclared: (target: DocIntakeTarget, on: boolean): Promise<DocIntakeBundle> =>
            // MOCK:
            mockDocIntakeMailDeclared(target, on),
        // REAL: (없음)
    };
}
