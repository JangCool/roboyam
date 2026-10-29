<template>
    <!--
        테마(`/settings/theme/{layout|menu}`, 사용자 결정 2026-10-28 — 서버 연결 2026-10-29) — 설정 메뉴 「테마」 묶음의 「레이아웃」 항목(`/settings/theme`). 메뉴 설정은 설정 메뉴에 따로 두지 않고 이 화면의 탭으로만 연다. 탭 이름 없이 오면 레이아웃.
        흐름: **테마를 고른다 → 그 테마의 레이아웃(홈 구성 …)이 보인다 → 그 상태에서 메뉴 설정을 정한다.** 레이아웃·메뉴 설정은 테마마다 따로 저장된다.
        ① 테마(`site-theme`) — 서버 목록(기본 · 다음식 · 네이버식 · 로보얌, `SiteThemes.IDS`) 라디오 카드. 이름·설명은 `site.themeName_<id>`·`site.themeDesc_<id>`, 지금 사이트 테마에 「사용 중」.
          카드를 고르면 아래 탭이 그 테마의 것으로 바뀐다(주소 `?theme=` 에 남는다, 아래에 저장하지 않은 변경이 있으면 묻는다). 사이트에 적용하는 것은 [테마 적용](`PUT /api/admin/site/theme` — 테마 id 만).
        배치: 넓은 화면(lg 이상)은 두 칸 — 왼쪽 위 ① 테마(폭 22.5rem, 창 위에 붙는다, 카드는 세로 목록·설명 두 줄) · 가운데 ②. 좁은 화면은 위아래.
        ② 레이아웃 — 페이지 종류 줄(홈 · 게시판 「준비 중」 · 페이지) + 보기(실제 화면 · 편집) + [메뉴 설정] 단추.
          페이지(가젯 페이지, 사용자 결정 2026-10-29) — 홈처럼 가젯 격자로 꾸미는 공개 페이지(`/p/{key}`)를 여러 개 만들고 고른다(`GadgetPagePicker` — 목록 · 만들기 · 정보 고치기 · 지우기).
            고른 페이지를 홈과 같은 실제 화면 편집(같은 `ThemeLivePreview` + `HomeLayoutEditor`)으로 꾸미고, 배치는 홈처럼 테마마다 따로다. 종류·페이지는 주소 `?kind=page&page=<id>` 에 남는다.
          보기(처음) · 편집 — **둘 다 실제 화면**이다(`ThemeLivePreview` — 공개 사이트 iframe, 사용자 결정 2026-10-28: 미리보기도 편집도 실제 공개 화면에서).
            고른 테마 · 메뉴 설정(drawer 가 열려 있으면 입력 중인 값) · 홈 구성(저장 전 값)을 그대로 그리고, 그 안의 윗줄·메뉴를 누르면 메뉴 설정 drawer 가 열린다.
            편집이면 실제 홈 격자의 가젯 위에 손잡이가 얹혀 끌어 옮기기 · 오른쪽 아래 모서리로 크기 · ×로 빼기를 하고(좌표 입력 칸은 없다), 그 일을 홈 구성 편집 도구(`HomeLayoutEditor` — 배치의 주인)가
            계산해 다시 보낸다. 편집 도구 줄(칸 수 · 테마 기본 배치로 · 가젯 넣기)은 편집일 때만 보이고, 저장 줄은 늘 있다.
        ③ 메뉴 설정 drawer — 미리보기의 메뉴 자리를 누르거나 [메뉴 설정]을 누르면 오른쪽에 열린다(바탕을 막지 않아 미리보기가 같이 보인다). 내용은 `ThemeMenuSettings`(메뉴 배치 · 메뉴 모양, 테마마다 따로 저장).
          값을 바꾸면 미리보기가 바로 따르고, 저장하지 않고 닫으면 미리보기는 저장 값으로 돌아간다(입력은 버린다). 주소 `/settings/theme/menu` 로 오면 drawer 를 연 채 시작한다.
          홈 구성을 저장하지 않은 채 화면을 나가면 편집기의 `onBeforeRouteLeave` 가 묻는다.
        권한: 메뉴·조회 `site.read`, 바꾸기·저장 `site.manage`(없으면 테마 적용 단추가 없고, 카드는 어느 테마를 볼지만 고른다). 막는 것은 서버다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('site.themePageTitle')" :subtitle="t('site.themePageSubtitle')" />

        <Message v-if="!canManage" severity="info" size="small" :closable="false">{{ t('site.readOnly') }}</Message>

        <!-- 두 칸 — 왼쪽 위 테마 고르기(넓은 화면에서 창 위에 붙는다) · 가운데 고른 테마의 레이아웃·메뉴 설정. 좁은 화면은 위아래로 -->
        <div class="grid items-start gap-4 lg:grid-cols-[22.5rem_minmax(0,1fr)]">
            <!-- ① 테마 -->
            <AppGadget class="lg:sticky lg:top-14" :title="t('site.themeTitle')" anchor="site-theme" :changes="themeChanged ? 1 : 0" :invalid="themeError ? 1 : 0">
                <div v-if="loading && !siteSaved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                    <Skeleton height="4rem" />
                </div>

                <ErrorState v-else-if="!siteSaved" :title="t('site.themeLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />

                <div v-else class="flex flex-col gap-4">
                    <div class="flex flex-col gap-1.5">
                        <span :id="themeGroupId" class="text-sm font-medium">{{ t('site.themeGroupLabel') }}</span>
                        <div role="radiogroup" :aria-labelledby="themeGroupId" :aria-describedby="`${themeGroupId}-hint`" class="flex flex-col gap-2" data-testid="theme-cards">
                            <label
                                v-for="id in siteSaved.themes"
                                :key="id"
                                class="flex cursor-pointer gap-2.5 rounded-lg border px-3 py-2.5"
                                :class="theme === id ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)'"
                            >
                                <RadioButton
                                    :model-value="theme"
                                    :value="id"
                                    :name="`${themeGroupId}-theme`"
                                    :disabled="themeSaving"
                                    class="mt-0.5 shrink-0"
                                    @update:model-value="(v: unknown) => selectTheme(String(v))"
                                />
                                <span class="flex min-w-0 flex-col gap-1">
                                    <span class="flex flex-wrap items-center gap-2 text-sm font-medium">
                                        {{ themeName(id) }}
                                        <Tag v-if="id === siteSaved.theme" :value="t('site.themeCurrent')" severity="secondary" />
                                    </span>
                                    <span v-if="themeDesc(id)" class="line-clamp-2 text-xs leading-relaxed text-muted-color" :title="themeDesc(id)">{{ themeDesc(id) }}</span>
                                </span>
                            </label>
                        </div>
                        <p :id="`${themeGroupId}-hint`" class="m-0 text-xs text-muted-color">
                            {{ t('site.themePickHint') }}
                        </p>
                        <p v-if="themeError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">
                            {{ themeError }}
                        </p>
                    </div>

                    <div v-if="canManage" class="flex flex-col gap-2">
                        <Button type="button" :label="t('site.themeApply')" icon="pi pi-check" size="small" :loading="themeSaving" :disabled="!themeChanged" @click="saveTheme" />
                        <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" size="small" :disabled="themeSaving || !themeChanged" @click="selectTheme(siteSaved.theme)" />
                    </div>
                </div>
            </AppGadget>

            <!-- ② 고른 테마의 레이아웃 — 실제 화면(공개 사이트 iframe) · 편집(홈 구성 격자) -->
            <div v-if="theme" class="flex min-w-0 flex-col gap-4">
                <div class="flex flex-wrap items-center gap-2">
                    <!-- 페이지 종류 — 홈 · 페이지(가젯 페이지). 게시판 레이아웃은 자리만 둔다 -->
                    <div role="group" :aria-label="t('site.layoutPageKinds')" class="flex flex-wrap items-center gap-2" data-testid="layout-kinds">
                        <span class="mr-1 text-sm font-medium">{{ t('site.layoutPageKinds') }}</span>
                        <Button
                            v-for="item in KINDS"
                            :key="item.kind"
                            type="button"
                            :label="t(item.labelKey)"
                            :icon="item.icon"
                            size="small"
                            :severity="layoutKind === item.kind ? undefined : 'secondary'"
                            :variant="layoutKind === item.kind ? undefined : 'outlined'"
                            :aria-pressed="layoutKind === item.kind"
                            @click="selectKind(item.kind)"
                        />
                        <Button
                            v-for="kind in SOON_KINDS"
                            :key="kind"
                            type="button"
                            :label="t(`site.layoutKind_${kind}`)"
                            size="small"
                            severity="secondary"
                            variant="outlined"
                            disabled
                            :badge="t('site.layoutSoon')"
                            badge-severity="secondary"
                        />
                    </div>
                    <span class="flex-1" />
                    <SelectButton
                        v-model="viewMode"
                        :options="viewOptions"
                        option-label="label"
                        option-value="value"
                        :allow-empty="false"
                        size="small"
                        :aria-label="t('site.viewModeLabel')"
                        data-testid="layout-view-mode"
                    />
                    <Button type="button" :label="t('site.themeTabMenu')" icon="pi pi-bars" size="small" severity="secondary" variant="outlined" data-testid="open-menu-settings" @click="openMenu" />
                </div>
                <!-- 가젯 페이지 고르기(페이지일 때) — 목록 · 만들기 · 정보 고치기 -->
                <GadgetPagePicker v-if="layoutKind === 'PAGE'" :model-value="pageId" :can-manage="canManage" :before-switch="confirmDiscard" @update:model-value="selectPage" @update:page="(p) => (page = p)" />
                <!-- 홈·페이지 구성 편집 도구(칸 수 · 기본 배치로 · 가젯 넣기 · 저장 줄) — 배치의 주인. 「보기」일 때는 도구 줄만 숨는다 -->
                <HomeLayoutEditor
                    v-if="layoutKind === 'HOME' || page"
                    ref="editor"
                    :theme="theme"
                    :page="layoutKind === 'PAGE' ? page : null"
                    :hidden="viewMode !== 'edit'"
                    @dirty="(v) => (childDirty = v)"
                    @change="(v) => (liveHome = v)"
                >
                    <!-- 실제 화면 — 보기도 편집도 여기서(편집이면 실제 가젯 위에 끌기·크기·빼기 손잡이) -->
                    <ThemeLivePreview
                        :theme="theme"
                        :menu="previewMenu"
                        :home="liveHome"
                        :path="previewPath"
                        :page="previewPage"
                        :editing="viewMode === 'edit' && canManage"
                        @edit-menu="openMenu"
                        @layout-op="(op) => editor?.applyOp(op)"
                    />
                </HomeLayoutEditor>
            </div>
        </div>

        <!-- ③ 메뉴 설정 drawer — 바탕을 막지 않아 미리보기가 같이 보인다 -->
        <Drawer
            v-model:visible="menuOpen"
            position="right"
            :modal="false"
            :dismissable="false"
            :header="t('site.menuDrawerTitle', { theme: themeName(theme) })"
            class="w-full! md:w-[34rem]!"
            data-testid="menu-drawer"
            @hide="onMenuHide"
        >
            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('site.menuDrawerLead') }}</p>
            <ThemeMenuSettings v-if="theme" :theme="theme" @dirty="(v) => (menuDirty = v)" @preview="(v) => (liveMenu = v)" @saved="onMenuSaved" />
        </Drawer>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { SiteThemeMenuFields } from '~/composables/site/api/useSiteSettingsApi.types';
import type { SiteThemeResponse } from '~/composables/site/api/useSiteSettingsApi.types';
import type { HomeLayoutBody } from '~/composables/home/api/useHomeLayoutApi.types';
import type { HomeLayoutOp } from '~/components/theme/HomeLayoutEditor.vue';
import type { GadgetPage } from '~/composables/page/api/useGadgetPageApi.types';
import UtilGadgetPage from '~/utils/util-gadget-page';

const { t, te } = useI18n();
const route = useRoute();
const router = useRouter();

const api = useSiteSettingsApi();
const { setFromServer: setUserWebOrigin } = useUserWebOrigin();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { localizedName } = useLocalizedName();

/** 이 화면에서 하는 일에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('site.manage'));

/** 레이아웃 종류 — 홈 · 가젯 페이지 */
type LayoutKind = 'HOME' | 'PAGE';
const KINDS: readonly { kind: LayoutKind; labelKey: string; icon: string }[] = [
    { kind: 'HOME', labelKey: 'site.layoutKindHome', icon: 'pi pi-home' },
    { kind: 'PAGE', labelKey: 'site.layoutKind_PAGE', icon: 'pi pi-th-large' },
];
/** 자리만 둔 레이아웃 종류(게시판) */
const SOON_KINDS = ['BOARD'] as const;

useSeoMeta({ title: () => t('site.themePageTitle') });

const siteSaved = ref<SiteThemeResponse | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
/** 고른 테마 — 아래 탭의 대상이자 [테마 적용]으로 사이트에 저장할 값 */
const theme = ref('');
const themeSaving = ref(false);
/** 저장 거절 문구(`fields.theme`·`fields.site`) 또는 그 밖의 저장 오류 — 다른 것을 고르면 지운다 */
const themeError = ref('');
const themeGroupId = useId();
/** 홈·페이지 구성에 저장하지 않은 변경이 있는가 — 테마·종류·페이지를 바꾸기 전에 묻는다 */
const childDirty = ref(false);

/* ── 레이아웃 종류 · 가젯 페이지 ── */

/** 지금 보는 레이아웃 종류 — 주소 `?kind=page` 면 페이지 */
const layoutKind = ref<LayoutKind>(route.query.kind === 'page' ? 'PAGE' : 'HOME');
/** 고른 가젯 페이지 ID(주소 `?page=`) · 그 페이지(고르기 줄이 알려 준다) */
const pageId = ref<string | null>(typeof route.query.page === 'string' ? route.query.page : null);
const page = ref<GadgetPage | null>(null);

/** 실제 화면 주소 — 홈 `/` · 페이지 `/p/{key}` */
const previewPath = computed(() => (layoutKind.value === 'PAGE' && page.value ? UtilGadgetPage.path(page.value.key) : '/'));
/** 실제 화면의 페이지 머리 */
const previewPage = computed(() =>
    layoutKind.value === 'PAGE' && page.value ? { name: localizedName(page.value.name, page.value.nameEn), description: page.value.description, writeButton: page.value.writeButton } : null,
);

/** 저장하지 않은 배치가 있으면 버릴지 묻는다 */
async function confirmDiscard(): Promise<boolean> {
    if (!childDirty.value) return true;
    const ok = await confirm({ title: t('homeLayout.switchTitle'), message: t('homeLayout.switchPageMessage'), confirmLabel: t('homeLayout.switchConfirm'), cancelLabel: t('homeLayout.stay'), tone: 'danger' });
    if (ok) childDirty.value = false;
    return ok;
}

async function selectKind(kind: LayoutKind): Promise<void> {
    if (kind === layoutKind.value || !(await confirmDiscard())) return;
    liveHome.value = null;
    layoutKind.value = kind;
    void router.replace({ query: { ...route.query, kind: kind === 'PAGE' ? 'page' : undefined } });
}

function selectPage(id: string | null): void {
    if (id === pageId.value) return;
    liveHome.value = null;
    childDirty.value = false;
    pageId.value = id;
    void router.replace({ query: { ...route.query, page: id ?? undefined } });
}

/* ── 실제 화면 · 편집 · 메뉴 설정 drawer ── */

/** 보기 · 편집 — 둘 다 실제 화면(공개 사이트 iframe)이다. 편집이면 실제 홈 격자에 손잡이가 얹히고 도구 줄이 보인다 */
const viewMode = ref<'view' | 'edit'>('view');
/** 홈 구성 편집 도구 — 실제 화면이 알린 일(옮기기·크기·빼기)을 넘긴다 */
const editor = useTemplateRef<{ applyOp: (op: HomeLayoutOp) => void }>('editor');
const viewOptions = computed(() => [
    { value: 'view', label: t('site.viewModeLive') },
    { value: 'edit', label: t('site.viewModeEdit') },
]);
/** 편집기의 홈 구성(저장 전 포함) — 실제 화면이 따른다 */
const liveHome = ref<HomeLayoutBody | null>(null);
/** 메뉴 설정 drawer — 주소가 `/settings/theme/menu` 면 연 채 시작한다 */
const menuOpen = ref(route.params.tab === 'menu');
/** drawer 의 메뉴 설정에 저장하지 않은 변경이 있는가 */
const menuDirty = ref(false);
/** 고른 테마의 저장된 메뉴 설정(실제 화면 바탕) */
const savedMenu = ref<SiteThemeMenuFields | null>(null);
/** drawer 에서 입력 중인 메뉴 설정 — 열려 있을 때만 실제 화면이 따른다 */
const liveMenu = ref<SiteThemeMenuFields | null>(null);
/** 실제 화면에 보낼 메뉴 설정 */
const previewMenu = computed(() => (menuOpen.value ? liveMenu.value : null) ?? savedMenu.value);

/** 고른 테마의 저장된 메뉴 설정을 읽는다(미리보기 바탕) — 못 읽으면 미리보기 없이 편집기만 */
async function loadSavedMenu(id: string): Promise<void> {
    try {
        const res = await api.theme(id);
        if (id !== theme.value) return;
        savedMenu.value = { menuPosition: res.menuPosition, topItems: res.topItems, barGroupIds: [...res.barGroupIds], menuWidth: res.menuWidth, menuOverflow: res.menuOverflow, menuStyle: { ...res.menuStyle } };
    } catch {
        savedMenu.value = null;
    }
}

watch(theme, (id) => {
    liveMenu.value = null;
    liveHome.value = null;
    if (id) void loadSavedMenu(id);
});

function openMenu(): void {
    menuOpen.value = true;
}

/** drawer 를 닫았다 — 입력 중이던 값은 버리고 미리보기는 저장 값으로 */
function onMenuHide(): void {
    liveMenu.value = null;
    menuDirty.value = false;
}

function onMenuSaved(value: SiteThemeMenuFields): void {
    savedMenu.value = value;
}

const themeChanged = computed(() => siteSaved.value !== null && theme.value !== siteSaved.value.theme);

/** 테마 이름 — id 로 i18n 키를 찾는다. 화면이 아직 모르는 id(서버에 먼저 생긴 테마)는 id 그대로 */
function themeName(id: string): string {
    const key = `site.themeName_${id}`;
    return te(key) ? t(key) : id;
}

/** 테마 설명 — 키가 없으면 비운다 */
function themeDesc(id: string): string {
    const key = `site.themeDesc_${id}`;
    return te(key) ? t(key) : '';
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        const res = await api.theme();
        siteSaved.value = res;
        setUserWebOrigin(res.publicUrl);
        const asked = typeof route.query.theme === 'string' ? route.query.theme : '';
        theme.value = res.themes.includes(asked) ? asked : res.theme;
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

/** 카드를 고른다 — 아래 탭이 그 테마의 것으로 바뀐다(저장하지 않은 변경이 있으면 묻는다) */
async function selectTheme(id: string): Promise<void> {
    if (id === theme.value) return;
    if (
        (childDirty.value || menuDirty.value) &&
        !(await confirm({
            title: t('homeLayout.switchTitle'),
            message: t('homeLayout.switchMessage'),
            confirmLabel: t('homeLayout.switchConfirm'),
            cancelLabel: t('homeLayout.stay'),
            tone: 'danger',
        }))
    )
        return;
    themeError.value = '';
    childDirty.value = false;
    menuDirty.value = false;
    theme.value = id;
    void router.replace({ query: { ...route.query, theme: id } });
}

/** 고른 테마를 사이트에 적용한다(테마 id 만 — 레이아웃·메뉴 설정은 테마마다 따로 저장돼 있다) */
async function saveTheme(): Promise<void> {
    const saved = siteSaved.value;
    if (!saved || themeSaving.value || !canManage.value || !themeChanged.value) return;
    themeSaving.value = true;
    themeError.value = '';
    try {
        siteSaved.value = await api.saveTheme({ theme: theme.value });
        toast.success(t('site.themeSaved'));
    } catch (e) {
        const error = toApiError(e);
        // 칸 문구(`fields.theme`) · 설정 행 없음(`fields.site`) · 권한·네트워크 — 서버 문구 그대로
        themeError.value = error.field('theme') ?? error.field('site') ?? error.message;
    } finally {
        themeSaving.value = false;
    }
}

onMounted(() => void load());
</script>
