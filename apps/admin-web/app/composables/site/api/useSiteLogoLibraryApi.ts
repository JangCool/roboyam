import { ApiError } from '~/composables/common/useApiClient';
import type {
    SiteLogoBundleInput,
    SiteLogoBundleRequest,
    SiteLogoCellRequest,
    SiteLogoHolidayStyle,
    SiteLogoLibraryRawResponse,
    SiteLogoLibraryResponse,
    SiteLogoRules,
} from '~/composables/site/api/useSiteLogoLibraryApi.types';
import UtilSiteLogo from '~/utils/util-site-logo';

/**
 * 로고 보관함 + 보이기 규칙 API — 서버 주소 문자열(`/api/admin/site/logo-library…`)이 있는 유일한 곳이다. 상태는 없다.
 * 서버 `SiteLogoLibraryController`·`SiteLogoLibraryService`(`docs/features/site-logo-library-contract.md` 「서버 구현 2026-11-18」). 응답은 모두 `SiteLogoLibraryResponse`(묶음 목록 + 규칙 전체):
 *   - `GET  /api/admin/site/logo-library`  (`site.read`)
 *   - `POST /api/admin/site/logo-library/bundles` `{ name, nameInLogo, style: "CHARACTER"|"WORDMARK"(칸 오류 `fields.style`), cells: { koLight: {fileId}|{svgSource}|"KEEP"|null, … } }`  (`site.manage`)
 *       그림 파일은 먼저 `POST /api/admin/files`(purpose=SITE_LOGO — `useFileApi`)로 올려 받은 `fileId` 를 칸에 넣는다. 만들기에서 `"KEEP"` 은 빈 칸이다.
 *   - `PUT  /api/admin/site/logo-library/bundles/{id}` 같은 본문(`"KEEP"` = 지금 그대로, null = 비움) · 404 `SITE_LOGO_BUNDLE_NOT_FOUND` · 기본 제공 묶음이면 409 `SITE_LOGO_BUNDLE_NOT_EDITABLE`  (`site.manage`)
 *   - `PUT  /api/admin/site/logo-library/rules` `SiteLogoRules`(기본 로고·기념일 줄 차례대로·성별·기념일 로고 유형 `holidayStyle`)  (`site.manage`)
 *       `holidayStyle`(`AUTO`|`CHARACTER`|`WORDMARK`)은 응답의 `rules` 안에 오고 요청 본문 최상위로 보낸다. 응답에 없으면 `AUTO` 로 본다(`normalize`). 칸 오류는 `fields.holidayStyle`.
 *       기념일 줄은 통째로 갈아 끼운다 — 요청의 줄 `id` 는 쓰이지 않고 응답에 새 id 가 온다(화면은 응답으로 다시 채운다). 줄 수 한도 50.
 *   - 묶음 삭제 API 는 없다(올린 로고는 보관 — 사용자 결정). 묶음 id 는 숫자 글자다(기본 제공 `"1"`·`"2"` — 화면은 id 가 아니라 `builtIn` 으로 가린다).
 *   - 400 VALIDATION `fields.name` · `cells`(한 칸 이상) · `cells.koLight`…(파일·SVG 검사) · `defaultBundleId` · `anniversaries[i].name|mode|startDate|endDate|holidayName|bundleId` · `gender.male|female` · 403 PERMISSION_DENIED
 */
const PATH = {
    library: '/api/admin/site/logo-library',
    bundles: '/api/admin/site/logo-library/bundles',
    bundle: (id: string) => `/api/admin/site/logo-library/bundles/${encodeURIComponent(id)}`,
    rules: '/api/admin/site/logo-library/rules',
};

/** 서버 응답 → 화면 모양. `rules.holidayStyle` 이 없거나 모르는 값이면 `AUTO` */
function normalize(res: SiteLogoLibraryRawResponse): SiteLogoLibraryResponse {
    const value = res.rules.holidayStyle;
    const holidayStyle: SiteLogoHolidayStyle = value === 'CHARACTER' || value === 'WORDMARK' ? value : 'AUTO';
    // 묶음 유형이 없으면(옛 서버의 올린 묶음) 캐릭터 로고로 본다
    const bundles = res.bundles.map((bundle) => ({ ...bundle, style: bundle.style === 'WORDMARK' ? ('WORDMARK' as const) : ('CHARACTER' as const) }));
    return { bundles, rules: { ...res.rules, holidayStyle } };
}

export function useSiteLogoLibraryApi() {
    const auth = useAdminAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    // 그림 바이트 올리기는 파일 API(`/api/admin/files` 의 유일한 자리)에 맡긴다
    const files = useFileApi();

    /**
     * 화면 입력 → 요청 본문. 파일 칸은 먼저 올려 `fileId` 로 바꾼다.
     * 올리기가 거절되면 어느 칸인지 알 수 있게 `fields['cells.<칸>']` 에 서버 문구(`fields.file`)를 실어 다시 던진다(묶음 저장의 칸 오류 경로와 같게).
     */
    async function toRequest(input: SiteLogoBundleInput): Promise<SiteLogoBundleRequest> {
        const cells: Record<string, SiteLogoCellRequest> = {};
        for (const variant of UtilSiteLogo.VARIANTS) {
            const cell = input.cells[variant];
            if (cell === null || cell === 'KEEP') {
                cells[variant] = cell;
            } else if ('file' in cell) {
                try {
                    cells[variant] = { fileId: (await files.upload(cell.file, 'SITE_LOGO')).fileId };
                } catch (e) {
                    if (!(e instanceof ApiError)) throw e;
                    throw new ApiError({ status: e.status, code: e.code, message: e.message, requestId: e.requestId, fields: { [`cells.${variant}`]: e.field('file') ?? e.message } });
                }
            } else {
                cells[variant] = { svgSource: cell.svgSource };
            }
        }
        return { name: input.name, nameInLogo: input.nameInLogo, style: input.style, cells: cells as SiteLogoBundleRequest['cells'] };
    }

    return {
        /** 보관함(묶음 목록)과 보이기 규칙 */
        library: async (): Promise<SiteLogoLibraryResponse> => normalize(await client.get<SiteLogoLibraryRawResponse>(PATH.library)),

        /** 새 묶음을 더한다 — 바뀐 보관함 전체를 돌려준다 */
        async createBundle(input: SiteLogoBundleInput): Promise<SiteLogoLibraryResponse> {
            return normalize(await client.post<SiteLogoLibraryRawResponse>(PATH.bundles, await toRequest(input)));
        },

        /** 올린 묶음을 고친다(이름·칸·이름 포함) — 기본 제공 묶음은 서버가 거절한다(409) */
        async updateBundle(id: string, input: SiteLogoBundleInput): Promise<SiteLogoLibraryResponse> {
            return normalize(await client.put<SiteLogoLibraryRawResponse>(PATH.bundle(id), await toRequest(input)));
        },

        /** 보이기 규칙(기본 로고·기념일·성별·기념일 로고 유형)을 통째로 저장한다 — 응답의 기념일 줄 id 는 새로 매겨진다 */
        saveRules: async (rules: SiteLogoRules): Promise<SiteLogoLibraryResponse> => normalize(await client.put<SiteLogoLibraryRawResponse>(PATH.rules, rules)),
    };
}
