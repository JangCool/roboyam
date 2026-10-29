<template>
    <!--
        테마 하나의 메뉴 설정 — 테마 화면(`pages/settings/theme/[[tab]].vue`)의 「메뉴 설정」 탭이 쓴다(사용자 결정 2026-10-28: 메뉴 설정은 테마마다 따로, 테마 아래).
        구획 둘, **저장 단추는 구획마다 따로**다.
          ① 메뉴 배치(`menu-layout`) — 메뉴 위치 · 최상위 항목 위치 · 상단 메뉴 폭 · 메뉴가 넘칠 때 · 섞인 배치(상단+사이드)일 때만 상단 띠에 둘 그룹(공개 메뉴 `main` 의 최상위 그룹 — 메뉴 관리 API, `layout.read` 없으면 안내만).
            공개 사이트 메뉴 위치는 회원 보기 설정 > 이 값 > 테마 기본 순.
          ② 메뉴 모양(`menu-style`) 7칸: 상단 메뉴 그룹 표시 · 사이드 그룹 · 상단 메뉴 크기 · 모바일 상단 메뉴 · 메뉴 설명 줄 · 메뉴 붙이기 · 모바일 상단 바 모양(값 목록 `SITE_MENU_STYLE_VALUES`).
        「테마 기본(…)」의 괄호는 `theme`(편집하는 테마)의 기본값이다. 지금 배치에서 쓰이지 않는 칸은 안내 한 줄.
        한 구획을 저장할 때 다른 구획 칸은 저장된 값 그대로 실어 보내고, 다른 구획에 입력 중이던 값은 그대로 둔다. `theme` 이 바뀌면 그 테마의 값을 다시 읽는다(저장하지 않은 변경 확인은 화면이 `dirty` 이벤트를 받아 먼저 한다).
        저장은 테마마다 `PUT /api/admin/site/themes/{id}/menu`(`useSiteSettingsApi().saveThemeMenu`) — 공개 사이트는 지금 테마의 값을 서버 사본(20초) + 브라우저 캐시(60초) 뒤에 받는다.
        권한: 변경 `site.manage` — 없으면 저장 줄이 사라지고 고르기는 막힌다. 감추는 것은 편의이고 막는 것은 서버다.
    -->
    <div class="flex flex-col gap-4">
    <div v-if="loading && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
        <Skeleton height="2.25rem" width="min(36rem, 100%)" />
        <Skeleton height="12rem" />
    </div>

    <ErrorState v-else-if="!saved" :title="t('site.menuLoadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />

    <template v-else>
        <!-- ① 메뉴 배치 -->
        <AppGadget :title="t('site.menuLayoutHeading')" anchor="menu-layout" :changes="layoutChanged ? 1 : 0" :invalid="menuError ? 1 : 0">
            <div class="flex flex-col gap-4">
                <!-- 메뉴 위치 — 공개 사이트 데스크톱 메뉴를 둘 곳 -->
                <div class="flex flex-col gap-1.5">
                    <span :id="menuGroupId" class="text-sm font-medium">{{ t('site.menuPositionLabel') }}</span>
                    <div
                        role="radiogroup"
                        :aria-labelledby="menuGroupId"
                        :aria-describedby="menuError ? `${menuGroupId}-hint ${menuGroupId}-msg` : `${menuGroupId}-hint`"
                        class="flex flex-wrap gap-x-5 gap-y-2"
                        data-testid="site-menu-position"
                    >
                        <label v-for="option in menuOptions" :key="option.value" class="flex items-center gap-2 text-sm" :class="canManage && !layoutSaving ? 'cursor-pointer' : ''">
                            <RadioButton v-model="menuInput" :value="option.value" :name="`${menuGroupId}-menu`" :disabled="!canManage || layoutSaving" />
                            <span>{{ option.label }}</span>
                            <Tag v-if="option.value === saved.menuPosition" :value="t('site.themeCurrent')" severity="secondary" />
                        </label>
                    </div>
                    <p :id="`${menuGroupId}-hint`" class="m-0 text-xs text-muted-color">{{ t('site.menuPositionHint') }}</p>
                    <p v-if="menuError" :id="`${menuGroupId}-msg`" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ menuError }}</p>
                </div>

                <!-- 최상위 항목 위치 — 그룹 밖에 놓인 항목(홈·인기글 …)을 로고 옆 링크 줄에 둘지, 메뉴 안에 둘지 -->
                <div class="flex flex-col gap-1.5">
                    <span :id="topGroupId" class="text-sm font-medium">{{ t('site.topItemsLabel') }}</span>
                    <div role="radiogroup" :aria-labelledby="topGroupId" :aria-describedby="`${topGroupId}-hint`" class="flex flex-wrap gap-x-5 gap-y-2" data-testid="site-top-items">
                        <label v-for="option in topOptions" :key="option.value" class="flex items-center gap-2 text-sm" :class="canManage && !layoutSaving ? 'cursor-pointer' : ''">
                            <RadioButton v-model="topInput" :value="option.value" :name="`${topGroupId}-top`" :disabled="!canManage || layoutSaving" />
                            <span>{{ option.label }}</span>
                            <Tag v-if="option.value === saved.topItems" :value="t('site.themeCurrent')" severity="secondary" />
                        </label>
                    </div>
                    <p :id="`${topGroupId}-hint`" class="m-0 text-xs text-muted-color">{{ t('site.topItemsHint') }}</p>
                </div>

                <!-- 상단 메뉴 폭 — 상단 메뉴가 있는 배치에서만 의미가 있다 -->
                <div class="flex flex-col gap-1.5">
                    <span :id="widthGroupId" class="text-sm font-medium">{{ t('site.menuWidthLabel') }}</span>
                    <div
                        role="radiogroup"
                        :aria-labelledby="widthGroupId"
                        :aria-describedby="sideOnlyMenu ? `${widthGroupId}-hint ${widthGroupId}-side` : `${widthGroupId}-hint`"
                        class="flex flex-wrap gap-x-5 gap-y-2"
                        data-testid="site-menu-width"
                    >
                        <label v-for="option in widthOptions" :key="option.value" class="flex items-center gap-2 text-sm" :class="canManage && !layoutSaving ? 'cursor-pointer' : ''">
                            <RadioButton v-model="widthInput" :value="option.value" :name="`${widthGroupId}-width`" :disabled="!canManage || layoutSaving" />
                            <span>{{ option.label }}</span>
                            <Tag v-if="option.value === saved.menuWidth" :value="t('site.themeCurrent')" severity="secondary" />
                        </label>
                    </div>
                    <p :id="`${widthGroupId}-hint`" class="m-0 text-xs text-muted-color">{{ t('site.menuWidthHint') }}</p>
                    <!-- 지금 고른 메뉴 위치(테마 기본 포함)에 상단 메뉴가 없으면 — 회원이 보기 설정에서 상단을 고를 때만 쓰인다고 한 줄 더 -->
                    <p v-if="sideOnlyMenu" :id="`${widthGroupId}-side`" class="m-0 text-xs text-muted-color" data-testid="site-menu-width-side">{{ t('site.menuWidthSideOnly') }}</p>
                </div>

                <!-- 메뉴가 넘칠 때 — 가로 메뉴 줄(상단 메뉴·로고 옆 링크 줄)에 항목이 많아 넘칠 때 좌우로 넘기는 표시 -->
                <div class="flex flex-col gap-1.5">
                    <span :id="overflowGroupId" class="text-sm font-medium">{{ t('site.menuOverflowLabel') }}</span>
                    <div role="radiogroup" :aria-labelledby="overflowGroupId" :aria-describedby="`${overflowGroupId}-hint`" class="flex flex-wrap gap-x-5 gap-y-2" data-testid="site-menu-overflow">
                        <label v-for="option in overflowOptions" :key="option.value" class="flex items-center gap-2 text-sm" :class="canManage && !layoutSaving ? 'cursor-pointer' : ''">
                            <RadioButton v-model="overflowInput" :value="option.value" :name="`${overflowGroupId}-overflow`" :disabled="!canManage || layoutSaving" />
                            <span>{{ option.label }}</span>
                            <Tag v-if="option.value === saved.menuOverflow" :value="t('site.themeCurrent')" severity="secondary" />
                        </label>
                    </div>
                    <p :id="`${overflowGroupId}-hint`" class="m-0 text-xs text-muted-color">{{ t('site.menuOverflowHint') }}</p>
                </div>

                <!-- 상단 띠에 둘 그룹 — 메뉴 위치가 「상단 + 사이드」일 때만 쓰인다. 목록은 공개 메뉴(main)의 최상위 그룹(메뉴 관리 API, layout.read) -->
                <fieldset v-if="mixedMenu" class="m-0 flex flex-col gap-1.5 border-0 p-0" :aria-describedby="`${barGroupId}-hint`" data-testid="site-bar-groups">
                    <legend class="mb-1.5 p-0 text-sm font-medium">{{ t('site.barGroupsLabel') }}</legend>
                    <div v-if="barGroupsLoading" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
                        <Skeleton height="1.25rem" width="min(16rem, 100%)" />
                        <Skeleton height="1.25rem" width="min(12rem, 100%)" />
                    </div>
                    <p v-else-if="!canReadMenu" class="m-0 text-xs text-muted-color">{{ t('site.barGroupsNoPermission') }}</p>
                    <p v-else-if="barGroupsError" class="m-0 flex flex-wrap items-center gap-2 text-xs text-red-600 dark:text-red-400" role="alert">
                        <span>{{ barGroupsError }}</span>
                        <Button type="button" :label="t('common.retry')" size="small" severity="secondary" variant="text" @click="loadBarGroups" />
                    </p>
                    <p v-else-if="barGroups.length === 0" class="m-0 text-xs text-muted-color">{{ t('site.barGroupsEmpty') }}</p>
                    <div v-else class="flex flex-wrap gap-x-5 gap-y-2">
                        <label v-for="group in barGroups" :key="group.id" class="flex items-center gap-2 text-sm" :class="canManage && !layoutSaving ? 'cursor-pointer' : ''">
                            <Checkbox v-model="barInput" :value="group.id" :name="`${barGroupId}-bar`" :disabled="!canManage || layoutSaving" />
                            <span>{{ group.label }}</span>
                        </label>
                    </div>
                    <p :id="`${barGroupId}-hint`" class="m-0 text-xs text-muted-color">{{ t('site.barGroupsHint') }}</p>
                </fieldset>

                <!-- 차례의 주인은 메뉴 관리(사용자 결정 2026-10-29) — 이 설정은 놓을 자리만 정한다 -->
                <p class="m-0 text-xs text-muted-color" data-testid="site-menu-order-hint">
                    {{ t('site.menuOrderHint') }}
                    <NuxtLink to="/menus/public" class="font-medium text-primary underline-offset-2 hover:underline">{{ t('site.menuOrderLink') }}</NuxtLink>
                </p>

                <Message v-if="layoutFormError" severity="error" variant="simple" size="small">{{ layoutFormError }}</Message>

                <div v-if="canManage" class="flex flex-wrap items-center justify-end gap-2">
                    <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="layoutSaving || !layoutChanged" @click="fillLayout(saved)" />
                    <Button type="button" :label="t('common.save')" icon="pi pi-check" :loading="layoutSaving" :disabled="!layoutChanged" @click="saveLayout" />
                </div>
            </div>
        </AppGadget>

        <!-- ② 메뉴 모양 — 예전에는 테마에 고정이던 메뉴 동작 7칸. 지금 배치(메뉴 위치 — 테마 기본 포함, 저장 전 입력 기준)에서 쓰이지 않는 칸은 안내 한 줄을 더한다 -->
        <AppGadget :title="t('site.menuStyleHeading')" anchor="menu-style" :changes="styleChanged ? 1 : 0">
            <div class="flex flex-col gap-4">
                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('site.menuStyleLead') }}</p>

                <div v-for="field in styleFields" :key="field.key" class="flex flex-col gap-1.5">
                    <span :id="`${styleGroupId}-${field.key}`" class="text-sm font-medium">{{ field.label }}</span>
                    <div
                        role="radiogroup"
                        :aria-labelledby="`${styleGroupId}-${field.key}`"
                        :aria-describedby="field.note ? `${styleGroupId}-${field.key}-hint ${styleGroupId}-${field.key}-note` : `${styleGroupId}-${field.key}-hint`"
                        class="flex flex-wrap gap-x-5 gap-y-2"
                        :data-testid="`site-menu-style-${field.key}`"
                    >
                        <label v-for="option in field.options" :key="option.value" class="flex items-center gap-2 text-sm" :class="canManage && !styleSaving ? 'cursor-pointer' : ''">
                            <RadioButton
                                :model-value="styleInput[field.key]"
                                :value="option.value"
                                :name="`${styleGroupId}-${field.key}`"
                                :disabled="!canManage || styleSaving"
                                @update:model-value="(value: unknown) => setStyle(field.key, value)"
                            />
                            <span>{{ option.label }}</span>
                            <Tag v-if="option.value === saved.menuStyle[field.key]" :value="t('site.themeCurrent')" severity="secondary" />
                        </label>
                    </div>
                    <p :id="`${styleGroupId}-${field.key}-hint`" class="m-0 text-xs text-muted-color">{{ field.hint }}</p>
                    <p v-if="field.note" :id="`${styleGroupId}-${field.key}-note`" class="m-0 text-xs text-muted-color" :data-testid="`site-menu-style-${field.key}-note`">{{ field.note }}</p>
                </div>

                <Message v-if="styleFormError" severity="error" variant="simple" size="small">{{ styleFormError }}</Message>

                <div v-if="canManage" class="flex flex-wrap items-center justify-end gap-2">
                    <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="styleSaving || !styleChanged" @click="fillStyle(saved)" />
                    <Button type="button" :label="t('common.save')" icon="pi pi-check" :loading="styleSaving" :disabled="!styleChanged" @click="saveStyle" />
                </div>
            </div>
        </AppGadget>
    </template>
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { MenuFolderDto } from '~/composables/menu/api/useMenuApi.types';
import type {
    SiteMenuOverflow,
    SiteMenuOverflowSetting,
    SiteMenuPositionSetting,
    SiteMenuStyleKey,
    SiteMenuStyleSetting,
    SiteMenuWidth,
    SiteMenuWidthSetting,
    SiteThemeMenuPosition,
    SiteThemeResponse,
    SiteTopItemsPosition,
    SiteTopItemsSetting,
} from '~/composables/site/api/useSiteSettingsApi.types';
import { SITE_MENU_STYLE_VALUES } from '~/composables/site/api/useSiteSettingsApi.types';
import type { SiteThemeMenuFields } from '~/composables/site/api/useSiteSettingsApi.types';

interface Props {
    /** 메뉴 설정을 편집할 테마 id */
    theme: string;
}
const props = defineProps<Props>();
const emit = defineEmits<{
    (e: 'dirty', value: boolean): void;
    /** 입력 중인 메뉴 설정(저장 전 포함) — 화면의 페이지 미리보기가 바로 따른다 */
    (e: 'preview', value: SiteThemeMenuFields): void;
    /** 한 구획을 저장했다 — 저장된 메뉴 설정 */
    (e: 'saved', value: SiteThemeMenuFields): void;
}>();

const { t } = useI18n();

const api = useSiteSettingsApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const { can } = useAdminPermission();

/** 이 화면에서 하는 일에 필요한 권한(정의: util-permission-catalog.ts) — 숨기는 것은 편의이고 막는 것은 서버다 */
const canManage = computed(() => can('site.manage'));

/** 서버가 준 값(저장된 값). `null` = 아직 못 받았다 */
const saved = ref<SiteThemeResponse | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);

/* ── ① 메뉴 배치 ─────────────────────────── */

const layoutSaving = ref(false);
/** 칸에 붙지 않는 저장 오류(`fields.site` 등) */
const layoutFormError = ref('');
/** 메뉴 위치 고르기(`THEME` = 테마 기본 따름) */
const menuInput = ref<SiteMenuPositionSetting>('THEME');
/** 서버가 거절한 메뉴 위치 문구(`fields.menuPosition`, 서버 후보) — 다른 것을 고르면 지운다 */
const menuError = ref('');
const menuGroupId = useId();
/** 최상위 항목 위치 고르기(THEME = 테마 기본 따름) */
const topInput = ref<SiteTopItemsSetting>('THEME');
const topGroupId = useId();
/** 상단 메뉴 폭 고르기(THEME = 테마 기본 따름) */
const widthInput = ref<SiteMenuWidthSetting>('THEME');
const widthGroupId = useId();
/** 메뉴가 넘칠 때의 표시 고르기(THEME = 테마 기본 따름) */
const overflowInput = ref<SiteMenuOverflowSetting>('THEME');
const overflowGroupId = useId();
/** 상단 띠에 둘 최상위 그룹 ID(섞인 배치에서만 쓰인다) */
const barInput = ref<string[]>([]);
const barGroupId = useId();

/** 공개 메뉴(main)의 최상위 그룹 — 상단 띠 고르기 목록. 메뉴 관리 API(useMenuApi().tree)를 읽는다(layout.read) */
const menuApi = useMenuApi();
const { localizedName } = useLocalizedName();
const canReadMenu = computed(() => can('layout.read'));
const barGroups = ref<{ id: string; label: string }[]>([]);
const barGroupsLoading = ref(false);
const barGroupsError = ref('');
/** 메뉴 위치가 섞인 배치(상단 + 사이드)인가 — 상단 띠 그룹 고르기를 보인다(테마 기본은 섞인 배치가 아니다) */
const mixedMenu = computed(() => menuInput.value === 'TOP_LEFT' || menuInput.value === 'TOP_RIGHT');

/** 같은 ID 묶음인가(차례 무시) */
function sameIds(a: readonly string[], b: readonly string[]): boolean {
    return a.length === b.length && a.every((v) => b.includes(v));
}

const layoutChanged = computed(
    () =>
        saved.value !== null &&
        (menuInput.value !== saved.value.menuPosition ||
            topInput.value !== saved.value.topItems ||
            widthInput.value !== saved.value.menuWidth ||
            overflowInput.value !== saved.value.menuOverflow ||
            !sameIds(barInput.value, saved.value.barGroupIds)),
);

watch(menuInput, () => (menuError.value = ''));

/** 「테마 기본」 괄호 — 기본값을 모르는 테마면 괄호 없이 */
function themeOption(name: string | undefined): string {
    return name ? t('site.menuPositionThemeWith', { position: name }) : t('site.menuPositionTheme');
}

/** 메뉴 위치 이름 */
const POSITION_KEYS: Record<SiteThemeMenuPosition, string> = {
    TOP: 'site.menuPositionTop',
    LEFT: 'site.menuPositionLeft',
    RIGHT: 'site.menuPositionRight',
    TOP_LEFT: 'site.menuPositionTopLeft',
    TOP_RIGHT: 'site.menuPositionTopRight',
};
function positionName(position: SiteThemeMenuPosition): string {
    return t(POSITION_KEYS[position]);
}

/** 최상위 항목 위치 이름 */
function topItemsName(position: SiteTopItemsPosition): string {
    return t(position === 'LOGO' ? 'site.topItemsLogo' : 'site.topItemsMenu');
}

/** 상단 메뉴 폭 이름 */
function widthName(width: SiteMenuWidth): string {
    return t(width === 'FULL' ? 'site.menuWidthFull' : 'site.menuWidthContent');
}

/** 넘침 표시 이름 */
function overflowName(overflow: SiteMenuOverflow): string {
    return t(overflow === 'ARROWS' ? 'site.menuOverflowArrows' : 'site.menuOverflowScrollbar');
}

/** 메뉴 위치 고르기 — 「테마 기본」은 편집하는 테마의 기본값을 괄호로 */
const menuOptions = computed<{ value: SiteMenuPositionSetting; label: string }[]>(() => {
    const fallback = saved.value?.themeMenuDefaults[props.theme];
    return [
        { value: 'THEME', label: themeOption(fallback && positionName(fallback)) },
        { value: 'TOP', label: positionName('TOP') },
        { value: 'LEFT', label: positionName('LEFT') },
        { value: 'RIGHT', label: positionName('RIGHT') },
        { value: 'TOP_LEFT', label: positionName('TOP_LEFT') },
        { value: 'TOP_RIGHT', label: positionName('TOP_RIGHT') },
    ];
});

/** 최상위 항목 위치 고르기 */
const topOptions = computed<{ value: SiteTopItemsSetting; label: string }[]>(() => {
    const fallback = saved.value?.themeTopItemsDefaults[props.theme];
    return [
        { value: 'THEME', label: themeOption(fallback && topItemsName(fallback)) },
        { value: 'LOGO', label: topItemsName('LOGO') },
        { value: 'MENU', label: topItemsName('MENU') },
    ];
});

/** 상단 메뉴 폭 고르기 */
const widthOptions = computed<{ value: SiteMenuWidthSetting; label: string }[]>(() => {
    const fallback = saved.value?.themeMenuWidthDefaults[props.theme];
    return [
        { value: 'THEME', label: themeOption(fallback && widthName(fallback)) },
        { value: 'CONTENT', label: widthName('CONTENT') },
        { value: 'FULL', label: widthName('FULL') },
    ];
});

/** 넘침 표시 고르기 */
const overflowOptions = computed<{ value: SiteMenuOverflowSetting; label: string }[]>(() => {
    const fallback = saved.value?.themeMenuOverflowDefaults[props.theme];
    return [
        { value: 'THEME', label: themeOption(fallback && overflowName(fallback)) },
        { value: 'SCROLLBAR', label: overflowName('SCROLLBAR') },
        { value: 'ARROWS', label: overflowName('ARROWS') },
    ];
});

/** 고른 메뉴 위치(「테마 기본」이면 편집하는 테마의 기본값) — 기본값을 모르면 undefined */
const effectivePosition = computed(() => (menuInput.value === 'THEME' ? saved.value?.themeMenuDefaults[props.theme] : menuInput.value));
/** 고른 메뉴 위치에 상단 메뉴가 없는가 — 사이드 단독이면 상단 메뉴 폭은 회원이 상단을 고를 때만 쓰인다. 기본값을 모르면 false */
const sideOnlyMenu = computed(() => effectivePosition.value === 'LEFT' || effectivePosition.value === 'RIGHT');
/** 고른 메뉴 위치가 상단 + 사이드가 아닌가 — 사이드 그룹 칸은 섞인 배치에서만 쓰인다. 기본값을 모르면 false */
const notMixedMenu = computed(() => effectivePosition.value !== undefined && effectivePosition.value !== 'TOP_LEFT' && effectivePosition.value !== 'TOP_RIGHT');

/** 상단 띠 고르기 목록을 받는다 — 공개 메뉴 main 트리의 최상위 폴더. 권한이 없으면 부르지 않는다 */
async function loadBarGroups(): Promise<void> {
    if (!canReadMenu.value) return;
    barGroupsLoading.value = true;
    barGroupsError.value = '';
    try {
        const tree = await menuApi.tree('main');
        barGroups.value = tree.nodes
            .filter((node): node is MenuFolderDto & { id: string } => node.kind === 'FOLDER' && node.id !== null)
            .map((node) => ({ id: node.id, label: localizedName(node.label ?? '', node.nameEn) || node.id }));
    } catch (e) {
        barGroupsError.value = toApiError(e).message;
    } finally {
        barGroupsLoading.value = false;
    }
}

/* ── ② 메뉴 모양 7칸(운영자 > 테마 기본) ── */

const styleSaving = ref(false);
const styleFormError = ref('');
/** 메뉴 모양 고르기(칸마다 THEME = 테마 기본 따름) */
const styleInput = ref<SiteMenuStyleSetting>({
    menuGroupStyle: 'THEME',
    sideGroupStyle: 'THEME',
    menuSize: 'THEME',
    mobileMenu: 'THEME',
    menuDescription: 'THEME',
    stickyMenu: 'THEME',
    mobileHeader: 'THEME',
});
const styleGroupId = useId();
/** 화면에 보일 칸 차례 */
const STYLE_KEYS = Object.keys(SITE_MENU_STYLE_VALUES) as SiteMenuStyleKey[];

const styleChanged = computed(() => saved.value !== null && STYLE_KEYS.some((key) => styleInput.value[key] !== saved.value?.menuStyle[key]));

/** 칸 하나를 바꾼다(라디오 값은 그 칸의 값 목록 또는 THEME 만 온다) */
function setStyle(key: SiteMenuStyleKey, value: unknown): void {
    styleInput.value = { ...styleInput.value, [key]: value } as SiteMenuStyleSetting;
}

/** 값 이름 — 칸·값으로 i18n 키를 찾는다(`site.menuStyleValue_<칸>_<값>`) */
function styleValueName(key: SiteMenuStyleKey, value: string): string {
    return t(`site.menuStyleValue_${key}_${value}`);
}

/** 지금 배치에서 쓰이지 않는 칸의 안내 — 상단 메뉴가 없으면(그룹 표시·크기), 상단 + 사이드가 아니면(사이드 그룹) */
function styleNote(key: SiteMenuStyleKey): string | null {
    if ((key === 'menuGroupStyle' || key === 'menuSize') && sideOnlyMenu.value) return t('site.menuWidthSideOnly');
    if (key === 'sideGroupStyle' && notMixedMenu.value) return t('site.menuStyleNotMixed');
    return null;
}

/** 메뉴 모양 칸들 — 「테마 기본」은 편집하는 테마의 기본값을 괄호로 */
const styleFields = computed(() => {
    const defaults = saved.value?.themeMenuStyleDefaults[props.theme];
    return STYLE_KEYS.map((key) => ({
        key,
        label: t(`site.menuStyleLabel_${key}`),
        hint: t(`site.menuStyleHint_${key}`),
        note: styleNote(key),
        options: [
            { value: 'THEME', label: themeOption(defaults && styleValueName(key, defaults[key])) },
            ...SITE_MENU_STYLE_VALUES[key].map((value) => ({ value, label: styleValueName(key, value) })),
        ],
    }));
});

/* ── 읽기·저장 ─────────────────────────── */

function fillLayout(res: SiteThemeResponse): void {
    menuInput.value = res.menuPosition;
    topInput.value = res.topItems;
    widthInput.value = res.menuWidth;
    overflowInput.value = res.menuOverflow;
    barInput.value = [...res.barGroupIds];
    menuError.value = '';
    layoutFormError.value = '';
}

function fillStyle(res: SiteThemeResponse): void {
    styleInput.value = { ...res.menuStyle };
    styleFormError.value = '';
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        const res = await api.theme(props.theme);
        saved.value = res;
        fillLayout(res);
        fillStyle(res);
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

/** 저장 본문 — 저장하지 않는 구획 칸은 저장된 값 그대로 */
function body(base: SiteThemeResponse, section: 'layout' | 'style') {
    // 메뉴에서 사라진 그룹 ID 는 저장하지 않는다(목록을 받았을 때만 거른다 — 못 받았으면 그대로 둔다)
    const known = new Set(barGroups.value.map((group) => group.id));
    const bar = section === 'layout' ? barInput.value : base.barGroupIds;
    const barGroupIds = barGroups.value.length ? bar.filter((v) => known.has(v)) : [...bar];
    return {
        menuPosition: section === 'layout' ? menuInput.value : base.menuPosition,
        topItems: section === 'layout' ? topInput.value : base.topItems,
        barGroupIds,
        menuWidth: section === 'layout' ? widthInput.value : base.menuWidth,
        menuOverflow: section === 'layout' ? overflowInput.value : base.menuOverflow,
        menuStyle: { ...(section === 'style' ? styleInput.value : base.menuStyle) },
    };
}

async function saveLayout(): Promise<void> {
    const base = saved.value;
    if (!base || layoutSaving.value || !canManage.value || !layoutChanged.value) return;
    layoutSaving.value = true;
    menuError.value = '';
    layoutFormError.value = '';
    try {
        const res = await api.saveThemeMenu(props.theme, body(base, 'layout'));
        saved.value = res;
        fillLayout(res);
        emitSaved(res);
        toast.success(t('site.menuLayoutSaved'));
    } catch (e) {
        const error = toApiError(e);
        menuError.value = error.field('menuPosition') ?? '';
        // 칸에 붙지 않는 오류(설정 행 없음 `fields.site`·권한·네트워크)는 구획 안 한 줄 — 서버 문구 그대로
        if (!menuError.value) layoutFormError.value = error.field('site') ?? error.message;
    } finally {
        layoutSaving.value = false;
    }
}

async function saveStyle(): Promise<void> {
    const base = saved.value;
    if (!base || styleSaving.value || !canManage.value || !styleChanged.value) return;
    styleSaving.value = true;
    styleFormError.value = '';
    try {
        const res = await api.saveThemeMenu(props.theme, body(base, 'style'));
        saved.value = res;
        fillStyle(res);
        emitSaved(res);
        toast.success(t('site.menuStyleSaved'));
    } catch (e) {
        const error = toApiError(e);
        styleFormError.value = error.field('site') ?? error.message;
    } finally {
        styleSaving.value = false;
    }
}

onMounted(() => void loadBarGroups());
// 테마가 바뀌면 그 테마의 값을 읽는다 · 저장하지 않은 변경 여부를 화면에 알린다
watch(() => props.theme, () => void load(), { immediate: true });
watch(() => layoutChanged.value || styleChanged.value, (value) => emit('dirty', value), { immediate: true });
/** 입력 중인 값 — 읽기 전(저장 값 없음)에는 보내지 않는다 */
watch(
    () => ({ menuPosition: menuInput.value, topItems: topInput.value, barGroupIds: [...barInput.value], menuWidth: widthInput.value, menuOverflow: overflowInput.value, menuStyle: { ...styleInput.value } }),
    (value) => {
        if (saved.value) emit('preview', value);
    },
    { deep: true },
);

function emitSaved(res: SiteThemeResponse): void {
    emit('saved', { menuPosition: res.menuPosition, topItems: res.topItems, barGroupIds: [...res.barGroupIds], menuWidth: res.menuWidth, menuOverflow: res.menuOverflow, menuStyle: { ...res.menuStyle } });
}
</script>
