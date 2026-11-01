<template>
    <!--
        광고 › 광고 자리 설정(`/ads/placements`) — 공개 화면의 광고 자리 셋(02-ad.md 7-2·7-3, 계약 `docs/features/ad-contract.md` 「공개 노출 목업」).
          ① 글 보기 — 켜기 · 유형(한 줄·띠 배너·네모 배너) · 위치(글 카드와 댓글 카드 사이 / 본문 바로 아래)
          ② 날개 배너 — 왼쪽·오른쪽 켜기 · 보일 화면(홈·가젯 페이지·게시판 목록·글 보기)
          ③ 게시판 목록 한 줄 — 켜기 · 일반 글 몇 번째 뒤(1~20)
          ④ 19금 — 19금 게시판·19금 글에도 광고 보이기(기본 끔. 끄면 날개까지 모두 빠짐 · 게시판 「광고 숨김」·소모임은 늘 빠짐)
        짜임(사용자 결정 2026-11-01): 묶음 넷 = 탭 넷(PrimeVue Tabs — 주소 `?tab=post|wings|list|adult`, 모르는 값은 첫 탭). 왼쪽은 탭 단추 + 고른 탭의 폼, 오른쪽은 미리보기 한 곳(lg 이상 sticky, 좁은 화면은 폼 아래).
          미리보기 도식(`AdPlacement*Preview` — 공개 화면 짜임을 회색 상자로 단순하게 그리고 광고 자리만 주 색상)은 고른 탭의 것으로 바뀌고, 저장 전 폼 값을 바로 따라 그린다(저장된 값과 다르면 「저장 전」).
          폼 값은 이 화면(`form`)에 있고 탭 패널은 숨기기만 해서 탭을 바꿔도 입력이 남는다. 탭 단추에 저장 전이면 주황 점, 서버가 거절한 칸이 있으면 빨간 점. 저장이 칸 오류로 실패하면 지금 탭에 오류가 없을 때 첫 오류 탭으로 옮긴다.
        화면 하나로 한 번에 저장한다(`PUT` 한 번 — 아래 떠 있는 저장 줄, `forms.md` 2-4). 홈·가젯 페이지·게시판 레이아웃의 광고는 가젯(가젯 편집기)이라 여기 없다.
        권한: 화면 `ad.read` · 저장 `ad.settings`(없으면 입력이 잠기고 저장 줄 대신 읽기 전용 안내). 막는 것은 서버다.
        서버가 거절한 칸(400 `fields.postInline.adType` 등)은 그 칸 아래, 빠진 묶음(`fields.postInline` 등)은 그 묶음 위, 나머지는 저장 줄에 서버 문구 그대로.
        공개 화면은 서버 사본(20초) 뒤에 따라온다. 바꾼 값은 서버가 감사(`AD_PLACEMENT_UPDATE`, 전→후)에 남긴다.
    -->
    <div class="flex min-w-0 flex-col gap-4">
        <PageTitle :title="t('ad.placementsTitle')" :subtitle="t('ad.placementsSubtitle')" />

        <!-- 읽는 중 -->
        <AppGadget v-if="loading && !saved" :title="t('ad.postInlineSection')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 3" :key="n" height="3rem" />
            </div>
        </AppGadget>

        <!-- 읽기 실패 -->
        <AppGadget v-else-if="!saved || !form" :title="t('ad.postInlineSection')">
            <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="t('ad.placementsLoadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate data-testid="ad-placements-form" @submit.prevent="save">
            <Message v-if="!canEdit" severity="info" :closable="false" size="small">{{ t('ad.placementsReadOnly') }}</Message>

            <!-- 두 칸 — 왼쪽 탭(묶음 넷) + 고른 탭의 폼 · 오른쪽 고른 탭의 미리보기(넓은 화면에서 스크롤을 따라 붙는다). 좁은 화면(lg 미만)은 미리보기가 폼 아래로 -->
            <div class="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(18rem,24rem)]">
                <div class="flex min-w-0 flex-col gap-3">
                    <div class="min-w-0 overflow-hidden rounded-xl border border-(--line) bg-(--p-content-background)" data-testid="ad-placements-tabs">
                        <Tabs :value="tab" scrollable class="min-w-0" @update:value="(v: string | number) => selectTab(String(v))">
                            <TabList :aria-label="t('ad.placementsTabs')">
                                <Tab v-for="item in TABS" :key="item.key" :value="item.key" :data-testid="`ad-placements-tab-${item.key}`">
                                    <span class="inline-flex items-center gap-1.5">
                                        {{ t(item.label) }}
                                        <template v-if="invalidOf(item.section)">
                                            <span class="inline-block size-1.5 shrink-0 rounded-full bg-red-500" aria-hidden="true" data-testid="ad-placements-tab-invalid" />
                                            <span class="sr-only">{{ t('ad.placementsTabInvalid', { n: invalidOf(item.section) }) }}</span>
                                        </template>
                                        <template v-else-if="changes[item.section] > 0">
                                            <span class="inline-block size-1.5 shrink-0 rounded-full bg-amber-500" aria-hidden="true" data-testid="ad-placements-tab-unsaved" />
                                            <span class="sr-only">{{ t('ad.placementsTabUnsaved') }}</span>
                                        </template>
                                    </span>
                                </Tab>
                            </TabList>
                            <TabPanels>
                                <!-- ① 글 보기 -->
                                <TabPanel value="post">
                                    <div class="flex min-w-0 flex-col gap-4">
                                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('ad.postInlineLead') }}</p>
                                        <Message v-if="errorOf('postInline')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('postInline') }}</Message>
                                        <div class="flex items-center gap-2">
                                            <ToggleSwitch v-model="form.postInline.enabled" :input-id="`${idBase}-inline-on`" :disabled="locked" @update:model-value="clearServer('postInline.enabled')" />
                                            <label :for="`${idBase}-inline-on`" class="text-sm">{{ t('ad.postInlineEnabled') }}</label>
                                        </div>
                                        <div class="grid gap-x-8 gap-y-4 md:grid-cols-2 lg:grid-cols-1 xl:grid-cols-2" :class="{ 'opacity-60': !form.postInline.enabled }">
                                            <LabeledField :label="t('ad.postInlineType')" :help="form.postInline.adType === 'STRIP_BANNER' ? t('ad.postInlineTypeHint') : ''" :error="errorOf('postInline.adType')">
                                                <template #default="{ id, describedBy, invalid }">
                                                    <Select
                                                        v-model="form.postInline.adType"
                                                        :input-id="id"
                                                        :options="inlineTypeOptions"
                                                        option-label="label"
                                                        option-value="value"
                                                        :disabled="locked"
                                                        :invalid="invalid"
                                                        :aria-describedby="describedBy"
                                                        fluid
                                                        @change="clearServer('postInline.adType')"
                                                    />
                                                </template>
                                            </LabeledField>
                                            <LabeledField :label="t('ad.postInlinePosition')" :error="errorOf('postInline.position')">
                                                <template #default="{ id, describedBy, invalid }">
                                                    <Select
                                                        v-model="form.postInline.position"
                                                        :input-id="id"
                                                        :options="inlinePositionOptions"
                                                        option-label="label"
                                                        option-value="value"
                                                        :disabled="locked"
                                                        :invalid="invalid"
                                                        :aria-describedby="describedBy"
                                                        fluid
                                                        @change="clearServer('postInline.position')"
                                                    />
                                                </template>
                                            </LabeledField>
                                        </div>
                                    </div>
                                </TabPanel>

                                <!-- ② 날개 배너 -->
                                <TabPanel value="wings">
                                    <div class="flex min-w-0 flex-col gap-4">
                                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('ad.wingsLead') }}</p>
                                        <Message v-if="errorOf('wings')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('wings') }}</Message>
                                        <div class="flex flex-wrap items-center gap-x-6 gap-y-3">
                                            <div class="flex items-center gap-2">
                                                <ToggleSwitch v-model="form.wings.left" :input-id="`${idBase}-wing-left`" :disabled="locked" @update:model-value="clearServer('wings.left')" />
                                                <label :for="`${idBase}-wing-left`" class="text-sm">{{ t('ad.wingsLeft') }}</label>
                                            </div>
                                            <div class="flex items-center gap-2">
                                                <ToggleSwitch v-model="form.wings.right" :input-id="`${idBase}-wing-right`" :disabled="locked" @update:model-value="clearServer('wings.right')" />
                                                <label :for="`${idBase}-wing-right`" class="text-sm">{{ t('ad.wingsRight') }}</label>
                                            </div>
                                        </div>
                                        <fieldset class="m-0 flex flex-col gap-2 border-0 p-0" :class="{ 'opacity-60': !form.wings.left && !form.wings.right }" :aria-describedby="`${idBase}-screens-msg`">
                                            <legend class="mb-1 p-0 text-sm font-medium">{{ t('ad.wingsScreens') }}</legend>
                                            <div class="flex flex-wrap gap-x-5 gap-y-2">
                                                <div v-for="screen in UtilAd.WING_SCREENS" :key="screen" class="flex items-center gap-2">
                                                    <Checkbox v-model="form.wings.screens" :value="screen" :input-id="`${idBase}-screen-${screen}`" :disabled="locked" :invalid="!!errorOf('wings.screens')" @update:model-value="clearServer('wings.screens')" />
                                                    <label :for="`${idBase}-screen-${screen}`" class="text-sm">{{ t(`ad.wingScreen.${screen}`) }}</label>
                                                </div>
                                            </div>
                                            <p :id="`${idBase}-screens-msg`" class="m-0 text-xs leading-relaxed" :class="errorOf('wings.screens') ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="errorOf('wings.screens') ? 'alert' : undefined">
                                                {{ errorOf('wings.screens') || t('ad.wingsScreensHint') }}
                                            </p>
                                        </fieldset>
                                    </div>
                                </TabPanel>

                                <!-- ③ 게시판 목록 한 줄 -->
                                <TabPanel value="list">
                                    <div class="flex min-w-0 flex-col gap-4">
                                        <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('ad.boardListLead') }}</p>
                                        <Message v-if="errorOf('boardList')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('boardList') }}</Message>
                                        <div class="flex items-center gap-2">
                                            <ToggleSwitch v-model="form.boardList.enabled" :input-id="`${idBase}-feed-on`" :disabled="locked" @update:model-value="clearServer('boardList.enabled')" />
                                            <label :for="`${idBase}-feed-on`" class="text-sm">{{ t('ad.boardListEnabled') }}</label>
                                        </div>
                                        <div class="max-w-xs" :class="{ 'opacity-60': !form.boardList.enabled }">
                                            <LabeledField :label="t('ad.boardListAfter')" :help="t('ad.boardListAfterHelp', { min: UtilAd.FEED_AFTER.min, max: UtilAd.FEED_AFTER.max })" :error="errorOf('boardList.after')">
                                                <template #default="{ id, describedBy, invalid }">
                                                    <InputGroup>
                                                        <InputNumber
                                                            v-model="afterModel"
                                                            :input-id="id"
                                                            :min="UtilAd.FEED_AFTER.min"
                                                            :max="UtilAd.FEED_AFTER.max"
                                                            :allow-empty="false"
                                                            show-buttons
                                                            :readonly="locked"
                                                            :invalid="invalid"
                                                            :aria-describedby="describedBy"
                                                            input-class="w-16 text-right tabular-nums"
                                                        />
                                                        <InputGroupAddon>{{ t('ad.boardListAfterSuffix') }}</InputGroupAddon>
                                                    </InputGroup>
                                                </template>
                                            </LabeledField>
                                        </div>
                                    </div>
                                </TabPanel>

                                <!-- ④ 19금 게시판 -->
                                <TabPanel value="adult">
                                    <div class="flex min-w-0 flex-col gap-4">
                                        <Message v-if="errorOf('adult')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('adult') }}</Message>
                                        <div class="flex items-start gap-2">
                                            <ToggleSwitch v-model="form.adult.enabled" :input-id="`${idBase}-adult-on`" :disabled="locked" class="shrink-0" :aria-describedby="`${idBase}-adult-help`" @update:model-value="clearServer('adult.enabled')" />
                                            <label :for="`${idBase}-adult-on`" class="flex flex-col gap-0.5">
                                                <span class="text-sm">{{ t('ad.adultEnabled') }}</span>
                                                <span :id="`${idBase}-adult-help`" class="text-xs leading-relaxed" :class="errorOf('adult.enabled') ? 'text-red-600 dark:text-red-400' : 'text-muted-color'" :role="errorOf('adult.enabled') ? 'alert' : undefined">
                                                    {{ errorOf('adult.enabled') || t('ad.adultHelp') }}
                                                </span>
                                            </label>
                                        </div>
                                    </div>
                                </TabPanel>
                            </TabPanels>
                        </Tabs>
                    </div>

                    <p class="m-0 text-xs text-muted-color" data-testid="ad-placements-saved-at">
                        {{ saved.updatedAt ? t('ad.placementsLastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy?.name ?? '—' }) : t('ad.placementsNeverSaved') }}
                    </p>
                </div>

                <!-- 고른 탭의 미리보기 한 곳 — 셸의 위쪽 막대는 스크롤 영역(.app-content) 밖이라 작은 여유(top-4)만 두고 붙인다 -->
                <aside class="min-w-0 lg:sticky lg:top-4" :aria-label="t('ad.placementsPreviewOf', { section: t(current.label) })" data-testid="ad-placements-preview-pane">
                    <AdPlacementPostPreview v-if="tab === 'post'" :enabled="form.postInline.enabled" :ad-type="form.postInline.adType" :position="form.postInline.position" :unsaved="changes.postInline > 0" />
                    <AdPlacementWingsPreview v-else-if="tab === 'wings'" :left="form.wings.left" :right="form.wings.right" :screens="form.wings.screens" :unsaved="changes.wings > 0" />
                    <AdPlacementListPreview v-else-if="tab === 'list'" :enabled="form.boardList.enabled" :after="form.boardList.after" :unsaved="changes.boardList > 0" />
                    <AdPlacementAdultPreview v-else :form="form" :unsaved="changes.adult > 0" />
                </aside>
            </div>

            <StickySaveBar v-if="canEdit" submit revertable :dirty="dirty" :saving="saving" :count="changeCount" :error-count="errorCount" :error="formError" @revert="revert" />
        </form>
    </div>
</template>

<script setup lang="ts">
import type { AdPlacementsRequest, AdPlacementsView } from '~/composables/ad/api/useAdApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAd from '~/utils/util-ad';

type Section = 'postInline' | 'wings' | 'boardList' | 'adult';
type PlacementTab = 'post' | 'wings' | 'list' | 'adult';

/** 탭 넷(주소 `?tab=` 의 값 · 묶음 · 이름) — 차례가 화면 차례이고, 저장 실패 때 첫 오류 탭을 고르는 차례다 */
const TABS: readonly { key: PlacementTab; section: Section; label: string }[] = [
    { key: 'post', section: 'postInline', label: 'ad.postInlineSection' },
    { key: 'wings', section: 'wings', label: 'ad.wingsSection' },
    { key: 'list', section: 'boardList', label: 'ad.boardListSection' },
    { key: 'adult', section: 'adult', label: 'ad.adultSection' },
];
const DEFAULT_TAB = TABS[0]!;

const { t } = useI18n();
useSeoMeta({ title: () => t('ad.placementsTitle') });

const route = useRoute();
const router = useRouter();

/** 주소의 탭 값 — 모르는 값·없으면 첫 탭 */
function tabOf(value: unknown): PlacementTab {
    return TABS.find((item) => item.key === value)?.key ?? DEFAULT_TAB.key;
}

const tab = ref<PlacementTab>(tabOf(route.query.tab));
const current = computed(() => TABS.find((item) => item.key === tab.value) ?? DEFAULT_TAB);

/** 탭을 바꾼다 — 폼 값은 부모(`form`)에 있고 탭 패널은 숨기기만 해서 입력은 그대로 남는다. 고른 탭은 주소에 남겨 새로고침해도 유지한다 */
function selectTab(value: string): void {
    const next = tabOf(value);
    if (next === tab.value) return;
    tab.value = next;
    void router.replace({ query: { ...route.query, tab: next } });
}

const api = useAdApi();
const toApiError = useApiErrorOf();
const toast = useAppToast();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { confirm } = useConfirmDialog();

const idBase = useId();

/** 저장할 수 있나(정의: util-permission-catalog.ts) */
const canEdit = computed(() => can('ad.settings'));

const saved = ref<AdPlacementsView | null>(null);
const form = ref<AdPlacementsRequest | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
const serverErrors = ref<Record<string, string>>({});

const locked = computed(() => !canEdit.value || saving.value);

const inlineTypeOptions = computed(() => UtilAd.INLINE_TYPES.map((value) => ({ value, label: t(`ad.postInlineTypeOption.${value}`) })));
const inlinePositionOptions = computed(() => UtilAd.INLINE_POSITIONS.map((value) => ({ value, label: t(`ad.postInlinePositionOption.${value}`) })));

/** `InputNumber` 는 지우는 중에 null 을 줄 수 있다 — 칸 값은 늘 숫자로 둔다 */
const afterModel = computed<number | null>({
    get: () => form.value?.boardList.after ?? null,
    set: (value) => {
        if (!form.value || value === null) return;
        form.value.boardList.after = value;
        clearServer('boardList.after');
    },
});

const changes = computed(() => (form.value && saved.value ? UtilAd.placementChanges(form.value, saved.value) : { postInline: 0, wings: 0, boardList: 0, adult: 0 }));
const changeCount = computed(() => changes.value.postInline + changes.value.wings + changes.value.boardList + changes.value.adult);
const dirty = computed(() => changeCount.value > 0);
const errorCount = computed(() => Object.keys(serverErrors.value).length);

function errorOf(key: string): string {
    return serverErrors.value[key] ?? '';
}

/** 묶음의 오류 칸 수(묶음 자체 오류 포함) */
function invalidOf(section: Section): number {
    return Object.keys(serverErrors.value).filter((key) => key === section || key.startsWith(`${section}.`)).length;
}

function clearServer(key: string): void {
    if (!(key in serverErrors.value)) return;
    const { [key]: _removed, ...rest } = serverErrors.value;
    serverErrors.value = rest;
}

onMounted(() => {
    void load();
});

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.placements());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

function applySaved(view: AdPlacementsView): void {
    saved.value = view;
    form.value = UtilAd.placementsForm(view);
    serverErrors.value = {};
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

async function save(): Promise<void> {
    if (!canEdit.value || saving.value || !dirty.value || !form.value) return;
    // 반응형 객체를 그대로 보내지 않는다 — 새로 만든 평범한 객체
    const body = UtilAd.placementsForm(toRaw(form.value));
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        applySaved(await api.savePlacements(body));
        toast.success(t('ad.placementsSaved'));
    } catch (e) {
        const failure = toApiError(e);
        const fields: Record<string, string> = {};
        if (failure.code === 'VALIDATION') {
            for (const key of UtilAd.PLACEMENT_FIELDS) {
                const message = failure.field(key);
                if (message) fields[key] = message;
            }
        }
        serverErrors.value = fields;
        formError.value = Object.keys(fields).length ? t('ad.placementsFieldsRejected') : failure.message;
        // 거절된 칸이 지금 탭에 없으면 첫 오류 탭으로 옮긴다(다른 오류 탭은 탭 단추의 빨간 점)
        const firstInvalid = TABS.find((item) => invalidOf(item.section) > 0);
        if (firstInvalid && invalidOf(current.value.section) === 0) selectTab(firstInvalid.key);
    } finally {
        saving.value = false;
    }
}

// 저장하지 않은 변경이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async () => {
    if (!dirty.value) return true;
    return confirm({ title: t('ad.placementsLeaveTitle'), message: t('ad.placementsLeaveMessage'), confirmLabel: t('ad.placementsLeaveConfirm'), cancelLabel: t('ad.placementsLeaveCancel'), tone: 'danger' });
});
</script>
