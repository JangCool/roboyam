<template>
    <!--
        팝업 — 사이트에 들어오면 화면 가운데 뜨는 이벤트·프로모션 창(운영자 관리자 「팝업 관리」). 계약 `docs/features/site-popup-contract.md`.
          기본 레이아웃에 한 번 놓는다. 메뉴 줄 아래 한 줄인 「공지 띠」(`SiteNoticeBar`)와 다른 기능이다.
        어디에 뜨나: 서버가 준 목록(노출 켬·기간 안) 가운데 보일 곳(홈 화면만 = `/` · 모든 화면)·기기(폭 768px 이상 PC, 미만 모바일)에 맞고 닫지 않은 것(`UtilSitePopup.visible`).
        **띄울 창**(운영자가 팝업마다 창 1~3 — 사용자 결정 2026-11-08): 같은 창 번호끼리 한 창(`SitePopupWindow`)에서 슬라이드, 번호가 다르면
          PC 는 운영자가 고른 여러 창 배치(응답 `layout`)대로 가로로 나란히 또는 위아래로 쌓기(창 번호 차례, 화면을 넘치면 안에서 스크롤) · 모바일은 창을 하나씩 차례로(창 1 을 닫으면 창 2 가 뜬다).
        창의 「보지 않기」 → 그 장만 브라우저에 기록하고 빠진다 · 창의 「닫기」 → 이번 방문(앱이 열린 동안) 그 창만 닫는다. Esc·바깥(어두운 막)은 보이는 창 전부를 닫는다.
          링크를 누르면 그 창을 닫고 이동한다(바깥 주소는 새 창).
        「보지 않기」 기록은 이 브라우저(`UtilSitePopup` — localStorage `irobit:site-popups`)에만 — 운영자가 팝업 내용을 고치면(내용 표식 `version`) 다시 보인다. 저장소를 못 쓰면 이번 방문 동안만 빠진다.
        **캐시 영역 미리보기**(관리자 캐시 관리의 iframe, `cachePreview=1` — `useAdminPreview().cacheMode`, 2026-11-08): 기본으로 띄우지 않고, 관리자가 고른 캐시가 `site.popups` 일 때만 띄운다(겹침 층이 강조).
          그때는 이 브라우저의 「보지 않기」 기록·이번 방문에 닫은 것을 보지 않고(처음 온 사람이 보는 팝업) 닫기·보지 않기·링크 이동도 하지 않는다(저장 값을 건드리지 않는다).
          숨겨 둔 동안에도 띄울 창 수를 데이터로 알려(`cacheDataCounts['site.popups']`) 관리자의 「이 쪽에서 찾은 영역」에 남는다. 테마 미리보기·공개 화면은 그대로다.
    -->
    <Dialog
        :visible="open"
        modal
        dismissable-mask
        :show-header="false"
        :draggable="false"
        :style="isDesktop ? { width: 'auto', maxWidth: 'calc(100vw - 2rem)' } : { width: 'min(400px, calc(100vw - 2.5rem))' }"
        :pt="{ root: { class: 'overflow-visible! border-0! bg-transparent! shadow-none!' }, content: { class: 'p-0! overflow-visible! bg-transparent!' }, mask: { class: 'bg-black/55!' } }"
        :aria-label="t('popup.label')"
        data-testid="site-popup"
        data-cache-area="site.popups"
        @update:visible="(v: boolean) => !v && closeAll()"
    >
        <div v-if="isDesktop" class="flex max-h-[calc(100dvh-2rem)] items-center gap-4 overflow-auto" :class="vertical ? 'flex-col justify-start py-1' : 'justify-center'">
            <SitePopupWindow v-for="group in windows" :key="group.no" :items="group.items" class="w-[400px]! shrink-0" @close="closeWindow" @hide="hide" @go="go" />
        </div>
        <SitePopupWindow v-else-if="windows[0]" :key="windows[0].no" :items="windows[0].items" @close="closeWindow" @hide="hide" @go="go" />
    </Dialog>
</template>

<script setup lang="ts">
import type { PublicSitePopup, SitePopupHideOption } from '~/composables/popup/api/useSitePopupApi.types';
import UtilSitePopup, { type SitePopupHideRecords } from '~/utils/util-site-popup';

/** 캐시 관리의 영역 키(`data-cache-area`) */
const POPUP_AREA = 'site.popups';

const { t } = useI18n();
const route = useRoute();
const isDesktop = useIsDesktop();
const { popups, closedIds, ensureLoaded, closeForVisit } = useSitePopups();
/** 캐시 영역 미리보기 — 고른 캐시가 팝업일 때만 띄우고, 저장 값은 읽지도 쓰지도 않는다 */
const { cacheMode, cache, cacheDataCounts } = useAdminPreview();
void ensureLoaded();

/** 보지 않기 기록 — 마운트 뒤 저장소에서 읽는다. 읽기 전에는 띄우지 않는다(닫은 팝업이 잠깐 비치지 않게) */
const records = ref<SitePopupHideRecords>({});
const storageRead = ref(false);
/** 판정 기준 시각 — 창을 연 동안 기간이 끝나도 다시 계산하지 않는다 */
const now = ref(0);

onMounted(() => {
    try {
        records.value = UtilSitePopup.parse(window.localStorage.getItem(UtilSitePopup.STORAGE_KEY));
    } catch {
        records.value = {};
    }
    now.value = Date.now();
    storageRead.value = true;
});

/** 지금 화면·기기에 띄울 팝업(서버 차례) */
const list = computed(() => {
    const value = popups.value;
    if (!storageRead.value || !value) return [];
    const preview = cacheMode.value;
    return UtilSitePopup.visible(value.items, { home: route.path === '/', device: isDesktop.value ? 'PC' : 'MOBILE', closed: preview ? [] : closedIds.value, records: preview ? {} : records.value, now: now.value });
});
/** 창 번호별로 묶은 것(창 번호 차례, 창 안은 목록 차례) */
const windows = computed(() => UtilSitePopup.windows(list.value));
const open = computed(() => windows.value.length > 0 && (!cacheMode.value || (cache.value?.keys.includes(POPUP_AREA) ?? false)));

// 캐시 영역 미리보기 — 숨겨 둔 동안에도 띄울 창 수를 알린다(관리자의 「이 쪽에서 찾은 영역」). 미리보기가 아니면 아무것도 하지 않는다
watch(
    () => (cacheMode.value ? windows.value.length : null),
    (count) => {
        if (count === null) return;
        cacheDataCounts.value = { ...cacheDataCounts.value, [POPUP_AREA]: count };
    },
    { immediate: true },
);
/** PC 에서 창을 위아래로 쌓는다(운영자 설정) */
const vertical = computed(() => popups.value?.layout === 'VERTICAL');

/* ── 닫기 ── */

/** 창 하나의 「닫기」 — 이번 방문 동안 그 창의 팝업만 닫는다 */
function closeWindow(items: PublicSitePopup[]): void {
    if (cacheMode.value) return;
    closeForVisit(items.map((item) => item.id));
}

/** Esc·바깥 — 지금 보이는 창 전부를 이번 방문 동안 닫는다(「모든 화면」 팝업도 다른 화면에서 다시 뜨지 않는다). 모바일은 지금 창만 보이므로 그 창만 */
function closeAll(): void {
    if (cacheMode.value) return;
    const shown = isDesktop.value ? windows.value : windows.value.slice(0, 1);
    closeForVisit(shown.flatMap((group) => group.items.map((item) => item.id)));
}

/** 「보지 않기」 — 그 장만 이 브라우저에 기록하고 빠진다 */
function hide(item: PublicSitePopup): void {
    if (cacheMode.value) return;
    if (item.hideOption === 'NONE') return;
    const at = Date.now();
    const next = UtilSitePopup.prune({ ...records.value, [item.id]: UtilSitePopup.record(item.version, item.hideOption as Exclude<SitePopupHideOption, 'NONE'>, at) }, popups.value?.items.map((row) => row.id) ?? [], at);
    try {
        window.localStorage.setItem(UtilSitePopup.STORAGE_KEY, JSON.stringify(next));
        records.value = next;
    } catch {
        // 저장소를 못 쓴다 — 이번 방문 동안만 뺀다
        closeForVisit([item.id]);
    }
}

/** 링크로 간다 — 그 창은 이번 방문 동안 닫는다. 바깥 주소는 `<a target=_blank>` 가 연다 */
function go(item: PublicSitePopup): void {
    if (cacheMode.value) return;
    const group = windows.value.find((w) => w.items.some((row) => row.id === item.id));
    closeForVisit(group ? group.items.map((row) => row.id) : [item.id]);
    if (UtilSitePopup.linkKind(item.link) === 'internal' && item.link) void navigateTo(item.link);
}
</script>
