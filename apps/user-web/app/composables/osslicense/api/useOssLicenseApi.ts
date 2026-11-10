import type { OssCollectedFile, OssNoticePublic } from '~/composables/osslicense/api/useOssLicenseApi.types';

/**
 * 오픈소스 라이선스 고지 — 주소(`/oss-licenses.json` · `/api/site/oss-licenses`)가 있는 **유일한 곳**이다. 상태는 없다. 계약: `docs/features/oss-licenses-contract.md`.
 * 비로그인도 받는다. 자동 수집 목록은 정적 파일(앱 `public/` — 빌드 때 `scripts/collect-licenses.mjs` 가 만든다), 안내 문구·직접 추가 항목은 서버(사본 60초 · ETag).
 */
const PATH = {
    collected: '/oss-licenses.json',
    notice: '/api/site/oss-licenses',
};

export function useOssLicenseApi() {
    const client = useApiClient();

    return {
        /** 자동 수집 목록(정적 파일 — 공통 응답 틀이 없어 그대로 받는다) */
        collected: (signal?: AbortSignal): Promise<OssCollectedFile> => client.get<OssCollectedFile>(PATH.collected, { signal }),
        /** 안내 문구 + 숨기지 않은 직접 추가 항목 */
        notice: (signal?: AbortSignal): Promise<OssNoticePublic> => client.get<OssNoticePublic>(PATH.notice, { signal }),
    };
}
