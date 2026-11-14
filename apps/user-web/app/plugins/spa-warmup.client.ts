import UtilBootData from '~/utils/util-boot-data';
import UtilGadgetRegistry from '~/utils/util-gadget-registry';
import UtilTheme from '~/utils/util-theme';

/**
 * 첫 로딩 조각 미리 받기(사용자 결정 2026-11-14 「권하는 순서」 2번) — 앱이 뜨자마자 지금 테마의 셸과, 홈이면 홈 틀·가젯 코드를 **동시에** 받기 시작한다.
 *
 * 그대로 두면 「셸 → 화면 → 홈 틀 → 가젯」 순서로 앞 조각이 그려져야 다음 조각을 요청해, 느린 회선에서 조각 받기가 여러 차례로 나뉘었다(측정: 4.3초 → 10.9초 → 13.0초).
 * 테마·홈 가젯 종류는 서버가 `index.html` 에 실어 온 첫 화면 데이터(`UtilBootData` — `/api/site/theme`·`/api/home/gadgets`)로 안다. 데이터가 없으면(개발 서버·관리자 미리보기) 아무것도 하지 않는다.
 * 받은 코드는 브라우저의 모듈 캐시에 남아, 화면이 그 컴포넌트를 그릴 때 바로 쓴다(`UtilTheme.resolve` · `UtilGadgetRegistry.componentOf`).
 */
export default defineNuxtPlugin({
    name: 'spa-warmup',
    order: -25,
    setup() {
        const theme = (UtilBootData.peek('/api/site/theme') as { data?: { theme?: unknown } } | null)?.data?.theme;
        if (theme === undefined) return;
        const themeId = UtilTheme.normalize(theme);
        UtilTheme.preload(themeId, 'shell');
        const path = window.location.pathname;
        if (path !== '/' && path !== '/index.html') return;
        UtilTheme.preload(themeId, 'home');
        const gadgets = (UtilBootData.peek('/api/home/gadgets') as { data?: { gadgets?: { type?: unknown }[] } } | null)?.data?.gadgets ?? [];
        UtilGadgetRegistry.preload(gadgets.map((g) => g.type).filter((t): t is string => typeof t === 'string'));
    },
});
