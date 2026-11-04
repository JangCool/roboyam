/**
 * 방문 신호 — 화면이 바뀔 때(첫 화면 포함) 공용 레이어 `@roboyam/analytics` 가 `POST /api/visits` 를 보낸다(`docs/features/site-analytics-contract.md`).
 * 신호를 만들고 보내는 일(방문자 ID·세션·2초 겹침·유입·보내는 곳)은 모두 레이어가 한다 — 이 앱은 아래 셋만 정해 넘긴다.
 *
 *  - **켤지**: 앱 시작 때 이미 받는 공개 사이트 정보(`useSiteInfo` — `GET /api/site/info`, `plugins/02.site-info.client.ts`)의 `analytics` 칸.
 *    `enabled` 이고 방문 항목 하나라도 켜졌을 때만 보낸다(`UtilVisitSignal.isCollecting`). 칸이 없으면(옛 서버)·못 받았으면 보내지 않는다. 같은 요청을 또 보내지 않는다(받는 중이면 그 요청을 기다린다).
 *    설치가 끝난 것(`installed`)을 알기 전에는 사이트 정보를 묻지 않고 보내지 않는다. 운영자가 스위치를 바꾸면 사용자가 화면을 새로 열 때부터 따른다.
 *  - **뺄 창**: 관리자 미리보기(관리자 테마 화면의 iframe — `UtilAdminPreview.isPreviewWindow`, 앱이 뜰 때의 주소로 한 번 본다). 로그인 여부와 상관없이 같은 규칙이다.
 *  - **화면 종류**: `UtilVisitKind.of`(이 앱의 라우트 표).
 *  - **환경 분류값**(2단계): 사이트 정보의 `analytics.environment` 가 켜졌으면 세션 첫 페이지 신호에 `env`(브라우저·OS·화면 너비 구간)를 싣는다.
 *  - 이용자가 처리방침 화면에서 수집을 거부했으면 레이어가 스스로 보내지 않는다(`UtilVisitStore.optedOut`).
 *
 * 파일 이름의 `04.` 는 순서다 — 사이트 정보(`02.`)·관리자 미리보기(`03.`) 다음.
 */
import UtilVisitSignal from '#layers/analytics/utils/util-visit-signal';
import UtilAdminPreview from '~/utils/util-admin-preview';
import UtilVisitKind from '~/utils/util-visit-kind';

export default defineNuxtPlugin(() => {
    const preview = UtilAdminPreview.isPreviewWindow(window.parent !== window, window.location.search);
    if (preview) return;

    const install = useInstallState();
    const { ensureLoaded } = useSiteInfo();

    startVisitTracking({
        collecting: async () => {
            // 설치가 끝난 것을 안 뒤에만 묻는다 — 설치 전에 물으면 503 이 사이트 정보 상태에 null 로 남는다
            if (install.phase.value !== 'installed') return false;
            const info = await ensureLoaded();
            return UtilVisitSignal.isCollecting(info?.analytics);
        },
        // 환경 분류값(2단계) — 사이트 정보의 `analytics.environment`(이미 받은 값)
        environment: async () => {
            const info = await ensureLoaded();
            return info?.analytics?.environment === true;
        },
        classify: (path) => UtilVisitKind.of(path),
    });
});
