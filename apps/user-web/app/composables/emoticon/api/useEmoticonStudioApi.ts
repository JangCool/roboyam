import type {
    EmoticonCreatorContract,
    EmoticonPayoutAccount,
    EmoticonPayoutAccountRequest,
    EmoticonSettlementList,
    EmoticonStudioImage,
    EmoticonStudioOverview,
    EmoticonStudioPack,
    EmoticonStudioSaveRequest,
    EmoticonStudioSubmitRequest,
} from '~/composables/emoticon/api/useEmoticonStudioApi.types';

/**
 * 로보티콘 스튜디오(내 제작 팩·판매·정산) API — 서버 주소 문자열(`/api/me/emoticon-studio…`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인·이메일 인증·정상 회원(계약 가정 6).
 *  - `GET /api/me/emoticon-studio` → `EmoticonStudioOverview`(내 팩 · 받은 수 · 보상 합계 · 최근 보상 — 후보 `GET …/rewards` 를 여기에 합쳤다)
 *  - `GET /api/me/emoticon-studio/packs/{id}` → `EmoticonStudioPack`(편집 모양) · `404 EMOTICON_PACK_NOT_FOUND`(남의 팩 포함)
 *  - `POST /api/me/emoticon-studio/images`(multipart `file` · `kind=EMOTICON|COVER`) → `EmoticonStudioImage` · `400 VALIDATION fields.file`(이유 코드)
 *  - `POST /api/me/emoticon-studio/packs` · `PUT …/packs/{id}` `EmoticonStudioSaveRequest` → `EmoticonStudioPack`(임시 저장) · `409 EMOTICON_PACK_LOCKED`(심사 중·판매 중)
 *  - `POST …/packs/submit`(새 팩) · `POST …/packs/{id}/submit` `EmoticonStudioSubmitRequest` → `EmoticonStudioPack`(PENDING, 판매 중이면 새 판 심사)
 *  - `GET /api/me/emoticon-studio/settlements` → `EmoticonSettlementList`(월별 정산 — 예정·확정·지급 완료·보류)
 *  - `GET /api/me/emoticon-studio/contract` → `EmoticonCreatorContract | null`(로보티콘 상품화 계약 — 판·제목·요약·평문 전문·내 동의). 문서는 약관 종류 `ROBOTIKON_CONTRACT`
 *    (운영자가 약관 관리에서 판을 올린다), 공개 화면은 `/robotikon/contract`(공개 약관 API `GET /api/terms/robotikon_contract`)
 *  - `GET·PUT /api/me/emoticon-studio/payout-account` → `EmoticonPayoutAccount`(정산 정보 — 가린 값만 내려온다)
 *
 * 실제 API(S1 — 서버 `EmoticonStudioController`): 요약 · 팩 하나 · 그림 올리기(201) · 새 팩 임시 저장(201)·임시 저장 · 새 팩 심사 요청(201)·심사 요청. 쓰기는 정상 회원·이메일 인증(`403 ACCOUNT_RESTRICTED`·`EMAIL_NOT_VERIFIED`).
 *  - 그림 올리기 하루 300장(`429 EMOTICON_UPLOAD_LIMIT`). 판매 중단 팩도 임시 저장·심사 요청이 된다(새 판 심사 — 승인되면 다시 판매, 반려면 판매 중단 그대로).
 * 실제 API(S2 — 계약 「S2 구현 결과」):
 *  - `contract` — 게시된 계약(시행 중)이 없으면 `data` 가 없다 → null. 판은 숫자, 제목·요약은 서버가 운영자 본문에서 뽑는다.
 *    동의는 따로 부르는 API 없이 심사 요청 본문의 `agreeContractVersion`(지금 판이어야 한다 — 이미 지금 판에 동의했으면 보내지 않아도 통과)
 *  - `payout-account` — 가린 값만. `identityNameMasked` 는 미인증·인증 이름 없음이면 null.
 *    저장 오류 차례: 작가 자격 403(`ACCOUNT_RESTRICTED`·`EMAIL_NOT_VERIFIED`) → `403 IDENTITY_REQUIRED` → `409 EMOTICON_IDENTITY_NAME_MISSING`
 *    → `400 VALIDATION fields.type|businessNumber|bankCode|accountNumber|holderName`(모아서) → `503 DATA_KEY_MISSING`
 *  - 심사 요청 오류 차례: 자격 403 → 팩 404 → 잠금 409 → 칸 400(유료 판매가 꺼져 있는데 새 유료 희망이면 `fields.saleWish`)
 *    → 유료 조건(새 유료 희망 · 이미 유료로 나온 팩의 새 판): `409 EMOTICON_PAYOUT_REQUIRED` → `409 EMOTICON_CONTRACT_REQUIRED` → `409 EMOTICON_PENDING_LIMIT`
 *  - `payout-account` 저장의 `409 EMOTICON_IDENTITY_NAME_MISSING` 에는 최상위 `reverify`(S3 — 데이터 키가 있으면 true)가 붙는다. 화면은 이 표시를 보지 않고 코드만으로 재인증 대화상자를 연다.
 * 실제 값(S3): 요약의 `sales`(이번 달 KST — 결제된 판매 수·금액·환불·부가세·정산 기준·수수료·예정 작가 몫(원천징수 전)) · 팩 `salesTotal`(환불 뺀 판매액).
 *
 * 실제 API(S4 — 계약 「S4 구현 결과」): `settlements` — 처음 달 ~ 이번 달, 최근 달 먼저(이번 달 예정 줄이 맨 앞). 예정 금액은 조회 때 서버가 계산한다.
 *  - `carriedIn` 은 앞 달에서 넘어온 **원천징수 전** 금액(음수면 앞 달 음수를 뺀다). 넘긴 달(음수 이월 포함)은 `carriedOver: true`·`payoutAmount 0`·`payDate null`, 넘긴 금액 `carriedOut`
 *  - 요약 `sales` = 이번 달 정산 줄의 예정 값 — `expectedPayout`(원천징수 전 작가 몫)은 음수일 수 있다(확정된 달 판매의 환불 — 다음 달 정산에서 뺀다)
 */
const PATH = {
    studio: '/api/me/emoticon-studio',
    images: '/api/me/emoticon-studio/images',
    packs: '/api/me/emoticon-studio/packs',
    pack: (id: string) => `${PATH.packs}/${encodeURIComponent(id)}`,
    settlements: '/api/me/emoticon-studio/settlements',
    payout: '/api/me/emoticon-studio/payout-account',
    contract: '/api/me/emoticon-studio/contract',
    submit: (id: string | null) => (id ? `${PATH.pack(id)}/submit` : `${PATH.packs}/submit`),
};

/** 그림 하나 올리기 본문(multipart `file` · `kind`) */
function imageForm(file: File, kind: 'EMOTICON' | 'COVER'): FormData {
    const form = new FormData();
    form.append('file', file);
    form.append('kind', kind);
    return form;
}

export function useEmoticonStudioApi() {
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });

    return {
        /** 내 팩·보상 요약 */
        overview: (): Promise<EmoticonStudioOverview> =>
            client.get<EmoticonStudioOverview>(PATH.studio),

        /** 내 팩 하나(편집) */
        pack: (id: string): Promise<EmoticonStudioPack> =>
            client.get<EmoticonStudioPack>(PATH.pack(id)),

        /** 그림 하나 올리기 — 이모티콘 또는 대표 이미지 */
        upload: (file: File, kind: 'EMOTICON' | 'COVER'): Promise<EmoticonStudioImage> =>
            client.upload<EmoticonStudioImage>(PATH.images, imageForm(file, kind)),

        /** 임시 저장 — 새 팩은 `id` 없이 */
        save: (id: string | null, body: EmoticonStudioSaveRequest): Promise<EmoticonStudioPack> =>
            id ? client.put<EmoticonStudioPack>(PATH.pack(id), body) : client.post<EmoticonStudioPack>(PATH.packs, body),

        /** 심사 요청 */
        submit: (id: string | null, body: EmoticonStudioSubmitRequest): Promise<EmoticonStudioPack> =>
            client.post<EmoticonStudioPack>(PATH.submit(id), body),

        /** 월별 정산 */
        settlements: (): Promise<EmoticonSettlementList> =>
            client.get<EmoticonSettlementList>(PATH.settlements),

        /** 로보티콘 상품화 계약(지금 판 + 내 동의) — 게시본이 없으면 null(서버는 `data` 칸을 빼고 준다 → `undefined` 를 null 로). 동의는 심사 요청 본문의 `agreeContractVersion` */
        contract: (): Promise<EmoticonCreatorContract | null> =>
            client.get<EmoticonCreatorContract | null | undefined>(PATH.contract).then((c) => c ?? null),

        /** 정산 정보(가린 값만) */
        payoutAccount: (): Promise<EmoticonPayoutAccount> =>
            client.get<EmoticonPayoutAccount>(PATH.payout),

        /** 정산 정보 저장 — 계좌 원문은 이 요청에만 싣고 화면에 남기지 않는다 */
        savePayoutAccount: (body: EmoticonPayoutAccountRequest): Promise<EmoticonPayoutAccount> =>
            client.put<EmoticonPayoutAccount>(PATH.payout, body),
    };
}
