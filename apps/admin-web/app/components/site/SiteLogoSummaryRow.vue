<template>
    <!--
        일반 화면 「사이트 기본 정보」의 「로고」 줄 — 오늘 보이는 로고 미리보기(작게) + 한 줄 설명 + 「로고 관리」 단추(큰 모달을 연다 — 사용자 최종 결정 2026-11-18: 별도 화면이 아니라 모달).
        로고 설정은 모달 안에서 한다. 요약은 저장된 보이기 규칙 기준이다(계산 `UtilSiteLogoRule.todayRule` — 모달의 「오늘 보이는 로고」와 같다).
        기본 정보와 따로 읽는다 — 못 불러와도 이름·소개 입력은 쓸 수 있고 이 줄에만 오류·다시 시도가 보인다. 서버 `GET /api/admin/site/logo-library`(`useSiteLogoLibraryApi`).
    -->
    <div role="group" :aria-labelledby="titleId" class="flex flex-col gap-2">
        <h3 :id="titleId" class="text-sm font-medium">{{ t('site.logoLabel') }}</h3>

        <div class="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-xl border border-(--line) p-3">
            <div v-if="loading && !library" class="min-w-0 flex-1 basis-64" role="status" :aria-label="t('common.loading')">
                <Skeleton height="2rem" width="min(20rem, 100%)" />
            </div>

            <div v-else-if="!library" role="alert" class="flex min-w-0 flex-1 basis-64 flex-wrap items-center gap-x-2 gap-y-1 text-xs text-red-600 dark:text-red-400">
                <span>{{ t('siteLogo.loadFailed') }}<template v-if="loadError"> — {{ loadError.message }}</template></span>
                <Button type="button" :label="t('common.retry')" icon="pi pi-refresh" size="small" variant="text" @click="load" />
            </div>

            <div v-else class="flex min-w-0 flex-1 basis-64 flex-wrap items-center gap-x-3 gap-y-2">
                <span class="flex h-10 w-32 shrink-0 items-center justify-center rounded-lg border border-(--line) bg-surface-0 px-2 text-surface-900">
                    <img v-if="preview" :src="preview" alt="" class="block h-6 w-auto max-w-full object-contain" />
                    <i v-else class="pi pi-image opacity-60" aria-hidden="true" />
                </span>
                <div class="min-w-0 flex-1 basis-48">
                    <p class="text-sm">{{ line }}</p>
                    <p class="text-xs leading-relaxed text-muted-color">{{ t('siteLogo.summaryHelp') }}</p>
                </div>
            </div>

            <Button type="button" :label="t('siteLogo.manageButton')" icon="pi pi-images" size="small" severity="secondary" variant="outlined" class="shrink-0" aria-haspopup="dialog" @click="openManage" />
        </div>

        <!--
            「로고」 모달 — 로고 보관함 + 보이기 규칙(`SiteLogoLibrarySettings`). 열 때마다 본문을 새로 만든다(`v-if`) — 저장하지 않은 규칙 변경은 닫으면 버려진다(확인 창 없음).
            아래에는 [닫기]만 있다(규칙의 [되돌리기]·[저장]은 그 구획 안). 내용이 길면 모달 본문이 세로로 넘겨진다(PrimeVue Dialog 본문 `overflow-y: auto` + 최대 높이).
            겹친 창(묶음 고치기·기념일 규칙 줄)이 열려 있는 동안에는 이 모달의 Esc 닫기를 끈다 — PrimeVue Dialog 는 창마다 문서에 Esc 를 걸어 Esc 한 번에 둘 다 닫히기 때문이다.
            닫으면 이 줄의 요약을 저장된 규칙으로 다시 받는다.
        -->
        <Dialog :visible="manageOpen" modal :draggable="false" :close-on-escape="!overlayOpen" :header="t('siteLogo.pageTitle')" :style="{ width: 'min(64rem, calc(100vw - 2rem))' }" @update:visible="onManageVisible">
            <div v-if="manageOpen" class="flex flex-col gap-4">
                <Message v-if="!canManage" severity="info" :closable="false" size="small">{{ t('site.readOnly') }}</Message>
                <SiteLogoLibrarySettings @overlay="(open: boolean) => (overlayOpen = open)" />
            </div>
            <template #footer>
                <Button type="button" :label="t('common.close')" severity="secondary" variant="text" @click="closeManage" />
            </template>
        </Dialog>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { SiteLogoLibraryResponse } from '~/composables/site/api/useSiteLogoLibraryApi.types';
import UtilSiteLogoRule from '~/utils/util-site-logo-rule';

const { t } = useI18n();
const api = useSiteLogoLibraryApi();
const toApiError = useApiErrorOf();
const { today, holidays, loadHolidays } = useSiteLogoHolidays();
const titleId = useId();

const library = ref<SiteLogoLibraryResponse | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        library.value = await api.library();
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

/** 오늘 걸린 기념일 줄(저장된 규칙 기준) — 없으면 평소 로고 */
const todayRule = computed(() => (library.value ? UtilSiteLogoRule.todayRule(library.value.rules, today.value, holidays.value) : null));
/** 오늘 보일 묶음 — 기념일 묶음을 가리키는 줄이면 평소 로고의 벌에 맞는 짝(`UtilSiteLogoRule.matchStyle`) */
const todayBundle = computed(() => (library.value ? UtilSiteLogoRule.todayBundle(library.value.rules, library.value.bundles, today.value, holidays.value).bundle : null));
const preview = computed(() => (todayBundle.value ? UtilSiteLogoRule.cardPreview(todayBundle.value.logos) : null));
const line = computed(() => {
    const bundle = todayBundle.value?.name ?? t('siteLogo.bundleMissing');
    return todayRule.value ? t('siteLogo.todayAnniversary', { rule: todayRule.value.name, bundle }) : t('siteLogo.todayDefault', { bundle });
});

/* ── 「로고」 모달 ── */

const { can } = useAdminPermission();
const canManage = computed(() => can('site.manage'));
const manageOpen = ref(false);
/** 모달 위에 겹친 창(묶음 고치기·기념일 규칙 줄)이 열려 있다 — 그동안 이 모달은 Esc 로 닫히지 않는다 */
const overlayOpen = ref(false);

function openManage(): void {
    overlayOpen.value = false;
    manageOpen.value = true;
}

/** 닫는다 — 저장하지 않은 규칙 변경은 버리고, 이 줄의 요약을 저장된 규칙으로 다시 받는다 */
function closeManage(): void {
    manageOpen.value = false;
    overlayOpen.value = false;
    void load();
}

function onManageVisible(visible: boolean): void {
    if (!visible) closeManage();
}

void load();
void loadHolidays();
</script>
