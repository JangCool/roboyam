import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import type { GadgetPageResponse } from '~/composables/page/api/useGadgetPageApi.types';

/**
 * 공개 가젯 페이지 API — 서버 주소 문자열(`/api/pages/{key}`)이 있는 **유일한 곳**이다. 상태는 없다. 로그인 없이 된다(로그인하면 글 줄의 차단·메모 등이 입혀진다 — 홈과 같다).
 * 응답 모양은 `useGadgetPageApi.types.ts`. 계약 `docs/features/gadget-page-contract.md` 「API」(서버 `GadgetPageController`, `Cache-Control: no-store`) · 404 `GADGET_PAGE_NOT_FOUND`.
 * 배치는 서버의 지금 사이트 테마로 정해진다(요청에 싣지 않는다).
 *
 * 관리자 미리보기(`preview`)면 홈 가젯 응답도 같이 받아 `extra` 로 준다 — 관리자가 이 페이지에 없는 종류를 새로 넣었을 때 그릴 바탕(`UtilAdminPreview.applyHome`).
 */
const PATH = {
    page: (key: string) => `/api/pages/${encodeURIComponent(key)}`,
};

export function useGadgetPageApi() {
    // CSRF 쿠키를 다시 받는 호출 — setup 문맥에서 미리 잡아 둔다(콜백 안에서 컴포저블을 부르지 않는다)
    const auth = useAuthApi();
    const client = useApiClient({ refreshCsrf: () => auth.me() });
    const home = useHomeGadgetApi();

    return {
        /** 페이지 하나 — 머리 + 배치 + 가젯마다 그 데이터(미리보기면 홈 가젯을 바탕으로 같이) */
        page: async (key: string, options: { signal?: AbortSignal; preview?: boolean } = {}): Promise<{ res: GadgetPageResponse; extra: HomeGadget[] }> => {
            const [res, extra] = await Promise.all([
                client.get<GadgetPageResponse>(PATH.page(key), { signal: options.signal }),
                options.preview ? home.gadgets(options.signal).then((r) => r.gadgets).catch(() => [] as HomeGadget[]) : Promise.resolve([] as HomeGadget[]),
            ]);
            return { res, extra };
        },
    };
}
