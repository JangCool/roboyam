<template>
    <!--
        광고 › 광고 자리 설정(`/ads/placements`) — 공개 화면의 광고 자리 셋(02-ad.md 7-2·7-3, 계약 `docs/features/ad-contract.md` 「공개 노출 목업」).
          ① 글 보기 — 켜기 · 유형(한 줄·띠 배너·네모 배너) · 위치(글 카드와 댓글 카드 사이 / 본문 바로 아래)
          ② 날개 배너 — 왼쪽·오른쪽 켜기 · 보일 화면(홈·가젯 페이지·게시판 목록·글 보기)
          ③ 게시판 목록 한 줄 — 켜기 · 일반 글 몇 번째 뒤(1~20)
          ④ 19금 — 19금 게시판·19금 글에도 광고 보이기(기본 끔. 끄면 날개까지 모두 빠짐 · 게시판 「광고 숨김」·소모임은 늘 빠짐)
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

            <!-- ① 글 보기 -->
            <AppGadget :title="t('ad.postInlineSection')" anchor="ad-post-inline" :changes="changes.postInline" :invalid="invalidOf('postInline')">
                <div class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('ad.postInlineLead') }}</p>
                    <Message v-if="errorOf('postInline')" severity="error" variant="simple" size="small" :closable="false" role="alert">{{ errorOf('postInline') }}</Message>
                    <div class="flex items-center gap-2">
                        <ToggleSwitch v-model="form.postInline.enabled" :input-id="`${idBase}-inline-on`" :disabled="locked" @update:model-value="clearServer('postInline.enabled')" />
                        <label :for="`${idBase}-inline-on`" class="text-sm">{{ t('ad.postInlineEnabled') }}</label>
                    </div>
                    <div class="grid gap-x-8 gap-y-4 md:grid-cols-2" :class="{ 'opacity-60': !form.postInline.enabled }">
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
            </AppGadget>

            <!-- ② 날개 배너 -->
            <AppGadget :title="t('ad.wingsSection')" anchor="ad-wings" :changes="changes.wings" :invalid="invalidOf('wings')">
                <div class="flex flex-col gap-4">
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
            </AppGadget>

            <!-- ③ 게시판 목록 한 줄 -->
            <AppGadget :title="t('ad.boardListSection')" anchor="ad-board-list" :changes="changes.boardList" :invalid="invalidOf('boardList')">
                <div class="flex flex-col gap-4">
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
            </AppGadget>

            <!-- ④ 19금 게시판 -->
            <AppGadget :title="t('ad.adultSection')" anchor="ad-adult" :changes="changes.adult" :invalid="invalidOf('adult')">
                <div class="flex flex-col gap-4">
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
            </AppGadget>

            <p class="m-0 text-xs text-muted-color" data-testid="ad-placements-saved-at">
                {{ saved.updatedAt ? t('ad.placementsLastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy?.name ?? '—' }) : t('ad.placementsNeverSaved') }}
            </p>

            <StickySaveBar v-if="canEdit" submit revertable :dirty="dirty" :saving="saving" :count="changeCount" :error-count="errorCount" :error="formError" @revert="revert" />
        </form>
    </div>
</template>

<script setup lang="ts">
import type { AdPlacementsRequest, AdPlacementsView } from '~/composables/ad/api/useAdApi.types';
import type { ApiError } from '~/composables/common/useApiClient';
import UtilAd from '~/utils/util-ad';

type Section = 'postInline' | 'wings' | 'boardList' | 'adult';

const { t } = useI18n();
useSeoMeta({ title: () => t('ad.placementsTitle') });

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
