<template>
    <!--
        핫딜 › 핫딜 설정(`/hotdeals/settings`) — 회원 핫딜 1차의 숫자 규칙(계약 「핫딜 설정」 표, 05 §3). 포인트 상점 설정과 같은 모양:
        구획 카드(`AppGadget` — 저장 전 바뀐 칸 수가 구역 내비에 점) + 칸(`LabeledField` + 숫자 `InputText` — `InputNumber` 는 범위 밖 값을 말없이 고친다) + 범위 도움말(0 의 뜻 「없음/끔」) + 하단 떠 있는 저장 줄(`StickySaveBar`, 화면 하나로 저장).
        서버 호출은 `useHotdealSettingsApi`(`/api/admin/hotdeal-settings`) · `useHotdealSiteAffiliateApi`(`/api/admin/hotdeal-site-affiliates`).
        - 종료 요청: 문턱 · 유예 시간(0 = 자동 종료 안 함) · 하루 요청 한도 · 같은 작성자 글 한도/기간(0 = 없음) · keep 최대(0 = 없음) · 요청자 보상 포인트(**읽기 전용** — 포인트 정책이 정본, 링크)
        - 글쓰기: 회원 하루 글 수(0 = 없음) · 같은 판매처 간격(분, 0 = 없음) · 마감 없는 딜 기간 종료(일, 0 = 끔)
        - 가격: 금액 상한(천 단위 쉼표 — 칸을 떠날 때 쉼표를 다시 붙인다) · 할인율 경고 % / 인기 표시: HOT 추천 수
        - 위장 홍보(회원): 같은 판매처 글 수(0 = 끔) · 기간 / 위장 홍보(판매처): 서로 다른 회원 수(0 = 끔) · 기간 · 다른 핫딜 비율 %
        - 상품 이미지: 링크 OG 가져오기 켬/끔(`ogFetchEnabled`). OG 보안 값(시간 제한·크기·동시 수)은 여기 두지 않는다(서버 규칙 1).
        - 제휴 링크(D5): 방식 `affiliateMode` 라디오 카드(개인 제휴 허용 — 기본 / 사이트 제휴). 고른 값은 **초안**(바뀐 칸 1개로 세고 떠 있는 저장 줄로 저장)이고,
          [저장]을 누를 때 방식이 바뀌었으면 먼저 확인 창을 연다 — 방향별 문구 + `affiliate-impact?mode=` 영향 글 수(진행 중 개인 제휴 N · 그중 사이트 제휴 몰 M · 나머지 N−M 원래 링크) + 기존 글 처리 설명
          (+ 켠 몰이 없는데 사이트 제휴로 바꾸면 경고, 다른 바뀐 칸도 같이 저장된다는 줄). [바꾸고 저장]이 화면 전체를 저장한다.
          「사이트 제휴 몰」 표는 **고른 방식이 사이트 제휴일 때만** 보인다(저장 전에도 보여 몰을 미리 넣어 둘 수 있다). 표는 따로 바로 저장한다(추가·고치기 `HotdealSiteAffiliateDialog`, 켬/끔 ToggleSwitch, 지우기 확인).
        - 권한: 조회·저장 `hotdeal.manage`(없으면 칸 `readonly` + 읽기 전용 안내, 몰 표는 단추 없이 보기만). 저장하지 않고 나가면 묻는다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('hotdeal.settings.title')" :subtitle="t('hotdeal.settings.subtitle')" />

        <AppGadget v-if="loading && !saved" :title="t('hotdeal.settings.endSection')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 5" :key="n" height="3rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!saved" :title="t('hotdeal.settings.endSection')">
            <ErrorState :code="loadError?.status === 403 ? '403' : '!'" :title="t('hotdeal.settings.loadFailed')" :description="loadError?.message" :retry="loadError?.status !== 403" :retrying="loading" @retry="load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="save">
            <AppGadget
                v-for="section in SECTIONS"
                :key="section.key"
                :title="t(`hotdeal.settings.${section.key}Section`)"
                :anchor="`hotdeal-${section.key}`"
                :changes="changesOf(section.fields) + (section.key === 'image' && ogChanged ? 1 : 0) + (section.key === 'affiliate' && modeChanged ? 1 : 0)"
                :invalid="invalidOf(section.fields)"
            >
                <div class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t(`hotdeal.settings.${section.key}Lead`) }}</p>
                    <div class="grid gap-4 sm:grid-cols-2">
                        <LabeledField v-for="field in section.fields" :key="field" :label="t(`hotdeal.settings.field.${field}`)" :hint="UtilHotdealMessages.settingRange(t, spec(field))" :error="errorOf(field)" :help="t(`hotdeal.settings.help.${field}`)">
                            <template #default="{ id, describedBy, invalid }">
                                <div class="flex flex-wrap items-center gap-2">
                                    <InputText
                                        :id="id"
                                        :model-value="texts[field]"
                                        :name="field"
                                        inputmode="numeric"
                                        autocomplete="off"
                                        :readonly="!canManage || saving"
                                        :invalid="invalid"
                                        :aria-describedby="describedBy"
                                        class="text-right tabular-nums"
                                        :class="spec(field).money ? 'w-40' : 'w-24'"
                                        @update:model-value="(v) => onInput(field, v)"
                                        @blur="onBlur(field)"
                                    />
                                    <span class="text-sm text-muted-color">{{ t(`hotdeal.settings.unit.${field}`) }}</span>
                                    <Tag v-if="spec(field).zero && UtilHotdeal.parseInt(texts[field]) === 0" :value="t(`hotdeal.settings.zeroTag.${spec(field).zero}`)" severity="secondary" class="text-[11px]!" />
                                    <span v-if="UtilHotdeal.parseInt(texts[field]) !== saved[field]" class="text-xs text-amber-700 dark:text-amber-400">{{ t('hotdeal.settings.savedValue', { value: UtilHotdeal.formatSetting(field, saved[field]) }) }}</span>
                                </div>
                            </template>
                        </LabeledField>

                        <!-- 보상 포인트 — 포인트 정책이 정본이라 여기서는 읽기만 -->
                        <div v-if="section.key === 'end'" class="flex flex-col gap-1.5">
                            <span class="text-sm font-medium">{{ t('hotdeal.settings.rewardLabel') }}</span>
                            <p class="m-0 text-sm tabular-nums">{{ t('hotdeal.settings.rewardValue', { n: saved.rewardPoints, daily: saved.rewardDailyLimit }) }}</p>
                            <p class="m-0 text-xs leading-relaxed text-muted-color">
                                {{ t('hotdeal.settings.rewardHelp') }}
                                <NuxtLink v-if="can('point.read')" to="/points/policy" class="text-primary hover:underline">{{ t('hotdeal.settings.rewardLink') }}</NuxtLink>
                            </p>
                        </div>

                        <!-- 상품 이미지 — 켬/끔 -->
                        <div v-if="section.key === 'image'" class="flex flex-col gap-1.5 sm:col-span-2">
                            <div class="flex items-center gap-3">
                                <ToggleSwitch v-model="ogFetchEnabled" :input-id="`${uid}-og`" :disabled="!canManage || saving" :aria-describedby="`${uid}-og-help`" />
                                <label :for="`${uid}-og`" class="text-sm font-medium">{{ t('hotdeal.settings.field.ogFetchEnabled') }}</label>
                                <span v-if="ogChanged" class="text-xs text-amber-700 dark:text-amber-400">{{ t('hotdeal.settings.savedValue', { value: t(saved.ogFetchEnabled ? 'hotdeal.settings.on' : 'hotdeal.settings.off') }) }}</span>
                            </div>
                            <p :id="`${uid}-og-help`" class="m-0 text-xs leading-relaxed text-muted-color">{{ t(ogFetchEnabled ? 'hotdeal.settings.help.ogFetchEnabledOn' : 'hotdeal.settings.help.ogFetchEnabledOff') }}</p>
                        </div>

                        <!-- 제휴 링크 — 방식(초안, 저장 때 확인) + 사이트 제휴 몰 표(바로 저장) -->
                        <div v-if="section.key === 'affiliate'" class="flex flex-col gap-3 sm:col-span-2">
                            <div role="radiogroup" :aria-label="t('hotdeal.settings.field.affiliateMode')" class="grid gap-3 sm:grid-cols-2">
                                <label
                                    v-for="mode in UtilHotdeal.AFFILIATE_MODES"
                                    :key="mode"
                                    class="flex gap-3 rounded-lg border p-3"
                                    :class="[affiliateMode === mode ? 'border-primary bg-primary-50 dark:bg-primary-950' : 'border-(--line)', canManage && !saving ? 'cursor-pointer' : '']"
                                >
                                    <RadioButton v-model="affiliateMode" :value="mode" :name="`${uid}-affiliate`" :disabled="!canManage || saving" class="mt-0.5 shrink-0" />
                                    <span class="flex min-w-0 flex-col gap-1">
                                        <span class="flex flex-wrap items-center gap-1.5 text-sm font-medium">
                                            {{ t(`hotdeal.settings.affiliateChoice.${mode}`) }}
                                            <Tag v-if="mode === 'MEMBER'" :value="t('hotdeal.settings.affiliateDefault')" severity="secondary" class="text-[11px]!" />
                                        </span>
                                        <span class="text-xs leading-relaxed text-muted-color">{{ t(`hotdeal.settings.affiliateChoiceHelp.${mode}`) }}</span>
                                    </span>
                                </label>
                            </div>
                            <p class="m-0 flex flex-wrap items-center gap-x-2 gap-y-1 text-xs leading-relaxed text-muted-color">
                                <span class="flex items-center gap-1.5"><i class="pi pi-info-circle text-[11px]" aria-hidden="true" />{{ t('hotdeal.settings.affiliateSaveNote') }}</span>
                                <span v-if="modeChanged" class="text-amber-700 dark:text-amber-400">{{ t('hotdeal.settings.savedValue', { value: t(`hotdeal.settings.affiliateChoice.${saved.affiliateMode}`) }) }}</span>
                            </p>

                            <!-- 사이트 제휴 몰 — 고른 방식이 사이트 제휴일 때만 -->
                            <section v-if="affiliateMode === 'SITE'" class="flex min-w-0 flex-col gap-2 border-t border-(--line) pt-4" :aria-labelledby="`${uid}-stores`">
                                <div class="flex flex-wrap items-center justify-between gap-2">
                                    <h3 :id="`${uid}-stores`" class="m-0 text-sm font-medium">{{ t('hotdeal.siteAffiliate.title') }}</h3>
                                    <Button v-if="canManage" type="button" icon="pi pi-plus" :label="t('hotdeal.siteAffiliate.add')" size="small" severity="secondary" variant="outlined" :disabled="!storesLoaded || storeActing" @click="openStore(null)" />
                                </div>
                                <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('hotdeal.siteAffiliate.lead') }}</p>
                                <Message v-if="saved.affiliateMode !== 'SITE'" severity="info" size="small" :closable="false" icon="pi pi-info-circle">{{ t('hotdeal.siteAffiliate.draftNote') }}</Message>

                                <div class="min-w-0 overflow-hidden rounded-lg border border-(--line)">
                                    <div v-if="storesLoading && !storesLoaded" class="flex flex-col gap-2 p-3" role="status" :aria-label="t('common.loading')" aria-busy="true">
                                        <Skeleton v-for="n in 2" :key="n" height="2.25rem" />
                                    </div>
                                    <ErrorState v-else-if="storesError" :title="t('hotdeal.siteAffiliate.loadFailed')" :description="storesError.message" retry :retrying="storesLoading" @retry="loadStores" />
                                    <DataTable v-else :value="stores" data-key="id" :size="prefs.density === 'compact' ? 'small' : undefined" row-hover scrollable class="min-w-0">
                                        <template #empty>
                                            <EmptyState icon="pi pi-link" :title="t('hotdeal.siteAffiliate.empty')" :description="t('hotdeal.siteAffiliate.emptyHint')">
                                                <Button v-if="canManage" type="button" icon="pi pi-plus" :label="t('hotdeal.siteAffiliate.add')" size="small" @click="openStore(null)" />
                                            </EmptyState>
                                        </template>
                                        <Column :header="t('hotdeal.siteAffiliate.col.domain')" style="min-width: 9rem">
                                            <template #body="{ data }">
                                                <span class="font-mono text-[13px] break-all" :class="data.enabled ? '' : 'text-muted-color'">{{ data.domain }}</span>
                                            </template>
                                        </Column>
                                        <Column :header="t('hotdeal.siteAffiliate.col.program')" style="min-width: 8rem">
                                            <template #body="{ data }">
                                                <span class="text-[13px] break-words" :class="data.enabled ? '' : 'text-muted-color'">{{ data.programName }}</span>
                                            </template>
                                        </Column>
                                        <Column :header="t('hotdeal.siteAffiliate.col.transform')" style="min-width: 14rem">
                                            <template #body="{ data }">
                                                <span class="flex min-w-0 flex-col items-start gap-1">
                                                    <Tag :value="t(`hotdeal.siteAffiliate.transformType.${data.transform.type}`)" severity="secondary" class="text-[11px]!" />
                                                    <code class="block max-w-[22rem] truncate text-[11px] text-muted-color" :title="transformText(data)">{{ transformText(data) }}</code>
                                                </span>
                                            </template>
                                        </Column>
                                        <Column :header="t('hotdeal.siteAffiliate.col.enabled')" style="min-width: 5rem">
                                            <template #body="{ data }">
                                                <ToggleSwitch
                                                    :model-value="data.enabled"
                                                    :disabled="!canManage || storeActing"
                                                    :aria-label="t('hotdeal.siteAffiliate.toggleLabel', { domain: data.domain })"
                                                    @update:model-value="(v: boolean) => toggleStore(data, v)"
                                                />
                                            </template>
                                        </Column>
                                        <Column v-if="canManage" style="width: 6rem">
                                            <template #header><span class="sr-only">{{ t('hotdeal.siteAffiliate.col.actions') }}</span></template>
                                            <template #body="{ data }">
                                                <span class="flex justify-end gap-0.5">
                                                    <Button v-tooltip.top="t('hotdeal.siteAffiliate.edit')" type="button" icon="pi pi-pencil" size="small" severity="secondary" variant="text" rounded :aria-label="t('hotdeal.siteAffiliate.editLabel', { domain: data.domain })" :disabled="storeActing" @click="openStore(data)" />
                                                    <Button v-tooltip.top="t('hotdeal.siteAffiliate.remove')" type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :aria-label="t('hotdeal.siteAffiliate.removeLabel', { domain: data.domain })" :disabled="storeActing" @click="removeStore(data)" />
                                                </span>
                                            </template>
                                        </Column>
                                    </DataTable>
                                </div>
                                <p class="m-0 text-xs text-muted-color">{{ t('hotdeal.siteAffiliate.immediateNote') }}</p>
                            </section>
                        </div>
                    </div>
                </div>
            </AppGadget>

            <p class="m-0 text-xs text-muted-color">
                {{ saved.updatedAt ? t('hotdeal.settings.lastSaved', { at: time.full(saved.updatedAt), by: saved.updatedBy ?? '—' }) : t('hotdeal.settings.neverSaved') }}
            </p>

            <StickySaveBar v-if="canManage" submit revertable :dirty="changed > 0" :saving="saving" :disabled="!canSave" :count="changed" :error-count="invalidOf(FIELDS)" :error="formError" @revert="revert" />
            <p v-else class="flex items-center gap-1.5 text-xs text-muted-color"><i class="pi pi-lock" aria-hidden="true" />{{ t('hotdeal.settings.readOnly') }}</p>
        </form>

        <!-- 제휴 방식 전환 확인 — [저장] 때 방식이 바뀌었으면 -->
        <Dialog v-model:visible="modeConfirmOpen" modal :draggable="false" :header="t('hotdeal.settings.affiliateConfirm.title', { mode: t(`hotdeal.settings.affiliateChoice.${affiliateMode}`) })" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }">
            <div class="flex flex-col gap-3 text-sm leading-relaxed">
                <p class="m-0 font-medium">{{ t(affiliateMode === 'SITE' ? 'hotdeal.settings.affiliateConfirm.toSite' : 'hotdeal.settings.affiliateConfirm.toMember') }}</p>

                <div class="rounded-lg border border-(--line) p-3" role="status" aria-live="polite" :aria-busy="impactLoading">
                    <div v-if="impactLoading" class="flex flex-col gap-2" :aria-label="t('common.loading')">
                        <Skeleton height="1.1rem" width="80%" />
                        <Skeleton height="1.1rem" width="60%" />
                    </div>
                    <p v-else-if="impactError" class="m-0 text-[13px] text-red-600 dark:text-red-400">{{ t('hotdeal.settings.affiliateConfirm.impactFailed', { reason: impactError }) }}</p>
                    <ul v-else-if="impact" class="m-0 flex list-disc flex-col gap-1 pl-4 text-[13px]">
                        <template v-if="impact.mode === 'SITE'">
                            <li v-if="impact.memberAffiliateActive === 0 && impact.siteStoreActive === 0" class="list-none -ml-4 text-muted-color">{{ t('hotdeal.settings.affiliateConfirm.impactNone') }}</li>
                            <template v-else>
                                <li class="tabular-nums">{{ t('hotdeal.settings.affiliateConfirm.impactToSite', { n: impact.memberAffiliateActive, m: impact.siteStoreMatched }) }}</li>
                                <li v-if="impact.memberAffiliateActive > impact.siteStoreMatched" class="tabular-nums">{{ t('hotdeal.settings.affiliateConfirm.impactToSiteRest', { n: impact.memberAffiliateActive - impact.siteStoreMatched }) }}</li>
                                <li class="tabular-nums text-muted-color">{{ t('hotdeal.settings.affiliateConfirm.impactToSiteAll', { n: impact.siteStoreActive }) }}</li>
                            </template>
                        </template>
                        <template v-else>
                            <li v-if="impact.memberAffiliateActive === 0 && impact.siteStoreActive === 0" class="list-none -ml-4 text-muted-color">{{ t('hotdeal.settings.affiliateConfirm.impactNone') }}</li>
                            <template v-else>
                                <li class="tabular-nums">{{ t('hotdeal.settings.affiliateConfirm.impactToMember', { n: impact.memberAffiliateActive }) }}</li>
                                <li class="tabular-nums text-muted-color">{{ t('hotdeal.settings.affiliateConfirm.impactToMemberSite', { n: impact.siteStoreActive }) }}</li>
                            </template>
                        </template>
                    </ul>
                </div>

                <p class="m-0 text-[13px] text-muted-color">{{ t(affiliateMode === 'SITE' ? 'hotdeal.settings.affiliateConfirm.existingSite' : 'hotdeal.settings.affiliateConfirm.existingMember') }}</p>
                <Message v-if="affiliateMode === 'SITE' && storesLoaded && enabledStoreCount === 0" severity="warn" size="small" :closable="false" icon="pi pi-exclamation-triangle">{{ t('hotdeal.settings.affiliateConfirm.noStores') }}</Message>
                <p v-if="changed > 1" class="m-0 text-xs text-muted-color">{{ t('hotdeal.settings.affiliateConfirm.otherChanges', { n: changed - 1 }) }}</p>
            </div>
            <template #footer>
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving" @click="modeConfirmOpen = false" />
                <Button type="button" :label="t('hotdeal.settings.affiliateConfirm.confirm')" :disabled="impactLoading" :loading="saving" @click="confirmModeSave" />
            </template>
        </Dialog>

        <HotdealSiteAffiliateDialog v-model:visible="storeOpen" :item="storeTarget" :busy="storeActing" :error="storeError" :field-errors="storeFieldErrors" @submit="saveStore" @clear-field="clearStoreField" />
    </div>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { AffiliateMode } from '~/composables/hotdeal/api/useHotdealApi.types';
import type { HotdealAffiliateImpact, HotdealSettingsField, HotdealSettingsView } from '~/composables/hotdeal/api/useHotdealSettingsApi.types';
import type { SiteAffiliateStore, SiteAffiliateStoreRequest } from '~/composables/hotdeal/api/useHotdealSiteAffiliateApi.types';
import UtilHotdeal from '~/utils/util-hotdeal';
import UtilHotdealMessages from '~/utils/util-hotdeal-messages';

const { t } = useI18n();
useSeoMeta({ title: () => t('hotdeal.settings.title') });

const api = useHotdealSettingsApi();
const storeApi = useHotdealSiteAffiliateApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { confirm } = useConfirmDialog();
const { can } = useAdminPermission();
const { prefs } = useLayoutPrefs();
const perm = useHotdealPermission();
const canManage = perm.canManage;
const uid = useId();

type SectionKey = 'end' | 'post' | 'price' | 'hot' | 'suspicion' | 'storeSuspicion' | 'image' | 'affiliate';

const SECTIONS: readonly { key: SectionKey; fields: readonly HotdealSettingsField[] }[] = [
    { key: 'end', fields: ['endRequestThreshold', 'endGraceHours', 'endRequestDailyLimit', 'endRequestPerAuthorLimit', 'endRequestPerAuthorDays', 'keepMaxCount'] },
    { key: 'post', fields: ['memberDailyPostLimit', 'sameStoreIntervalMinutes', 'autoExpireDays'] },
    { key: 'price', fields: ['priceMaxWon', 'discountWarnPercent'] },
    { key: 'hot', fields: ['hotLikeThreshold'] },
    { key: 'suspicion', fields: ['suspicionMinPosts', 'suspicionWindowDays'] },
    { key: 'storeSuspicion', fields: ['storeSuspicionMembers', 'storeSuspicionWindowDays', 'storeSuspicionOtherRatioPercent'] },
    { key: 'image', fields: [] },
    { key: 'affiliate', fields: [] },
];
const FIELDS: readonly HotdealSettingsField[] = UtilHotdeal.SETTINGS.map((s) => s.field);

function spec(field: HotdealSettingsField) {
    return UtilHotdeal.settingSpec(field);
}

const saved = ref<HotdealSettingsView | null>(null);
const texts = reactive(Object.fromEntries(FIELDS.map((f) => [f, ''])) as Record<HotdealSettingsField, string>);
const ogFetchEnabled = ref(true);
/** 제휴 링크 방식 — 초안(저장 때 바뀌었으면 확인 창) */
const affiliateMode = ref<AffiliateMode>('MEMBER');
const serverErrors = ref<Partial<Record<HotdealSettingsField, string>>>({});
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');

function problemOf(field: HotdealSettingsField) {
    return UtilHotdeal.settingProblem(field, texts[field]);
}
function errorOf(field: HotdealSettingsField): string {
    const p = problemOf(field);
    return p ? UtilHotdealMessages.settingProblem(t, p, spec(field)) : (serverErrors.value[field] ?? '');
}
function changesOf(fields: readonly HotdealSettingsField[]): number {
    const s = saved.value;
    return s ? fields.filter((f) => UtilHotdeal.parseInt(texts[f]) !== s[f]).length : 0;
}
function invalidOf(fields: readonly HotdealSettingsField[]): number {
    return fields.filter((f) => errorOf(f)).length;
}

const ogChanged = computed(() => saved.value !== null && ogFetchEnabled.value !== saved.value.ogFetchEnabled);
const modeChanged = computed(() => saved.value !== null && affiliateMode.value !== saved.value.affiliateMode);
const changed = computed(() => (saved.value ? UtilHotdeal.changedCount(texts, saved.value) + (ogChanged.value ? 1 : 0) + (modeChanged.value ? 1 : 0) : 0));
const canSave = computed(() => canManage.value && changed.value > 0 && FIELDS.every((f) => !problemOf(f)));

function onInput(field: HotdealSettingsField, value: string | undefined): void {
    texts[field] = value ?? '';
    const next = { ...serverErrors.value };
    delete next[field];
    serverErrors.value = next;
}

/** 금액 칸은 떠날 때 천 단위 쉼표를 다시 붙인다(읽을 수 없는 글자는 그대로 둔다) */
function onBlur(field: HotdealSettingsField): void {
    if (!spec(field).money) return;
    const n = UtilHotdeal.parseInt(texts[field]);
    if (n !== null) texts[field] = UtilHotdeal.formatSetting(field, n);
}

function applySaved(view: HotdealSettingsView): void {
    saved.value = view;
    for (const f of FIELDS) texts[f] = UtilHotdeal.formatSetting(f, view[f]);
    ogFetchEnabled.value = view.ogFetchEnabled;
    affiliateMode.value = view.affiliateMode;
    serverErrors.value = {};
    formError.value = '';
}

function revert(): void {
    if (saved.value) applySaved(saved.value);
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        applySaved(await api.get());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}
onMounted(() => {
    void load();
    void loadStores();
});

/** [저장] — 제휴 방식이 바뀌었으면 먼저 확인 창(영향 글 수), 아니면 바로 저장 */
async function save(): Promise<void> {
    if (saving.value || !canSave.value) return;
    if (modeChanged.value) {
        void openModeConfirm();
        return;
    }
    await persist();
}

async function persist(): Promise<boolean> {
    const values = UtilHotdeal.settingValues(texts);
    if (!values) return false;
    saving.value = true;
    formError.value = '';
    serverErrors.value = {};
    try {
        applySaved(await api.save({ ...values, ogFetchEnabled: ogFetchEnabled.value, affiliateMode: affiliateMode.value }));
        toast.success(t('hotdeal.settings.saved'));
        return true;
    } catch (e) {
        const failure = toApiError(e);
        const fields = failure.code === 'VALIDATION' ? (failure.fields ?? {}) : {};
        const mine = Object.fromEntries(Object.entries(fields).filter(([k]) => (FIELDS as readonly string[]).includes(k))) as Partial<Record<HotdealSettingsField, string>>;
        if (Object.keys(mine).length) {
            serverErrors.value = mine;
            formError.value = t('hotdeal.settings.saveFieldsRejected');
        } else formError.value = `${t('hotdeal.settings.saveFailed')} — ${fields.ogFetchEnabled ?? fields.affiliateMode ?? failure.message}`;
        return false;
    } finally {
        saving.value = false;
    }
}

/* ── 제휴 방식 전환 확인 ── */

const modeConfirmOpen = ref(false);
const impact = ref<HotdealAffiliateImpact | null>(null);
const impactLoading = ref(false);
const impactError = ref('');

async function openModeConfirm(): Promise<void> {
    const target = affiliateMode.value;
    impact.value = null;
    impactError.value = '';
    impactLoading.value = true;
    modeConfirmOpen.value = true;
    try {
        const found = await api.affiliateImpact(target);
        if (affiliateMode.value === target) impact.value = found;
    } catch (e) {
        impactError.value = toApiError(e).message;
    } finally {
        impactLoading.value = false;
    }
}

/** 확인 창의 [바꾸고 저장] — 저장이 실패하면 창을 닫고 떠 있는 저장 줄에 까닭을 보인다 */
async function confirmModeSave(): Promise<void> {
    if (saving.value || impactLoading.value) return;
    await persist();
    modeConfirmOpen.value = false;
}

/* ── 사이트 제휴 몰(바로 저장) ── */

const stores = ref<SiteAffiliateStore[]>([]);
const storesLoaded = ref(false);
const storesLoading = ref(false);
const storesError = ref<ApiError | null>(null);
const enabledStoreCount = computed(() => stores.value.filter((s) => s.enabled).length);

async function loadStores(): Promise<void> {
    storesLoading.value = true;
    storesError.value = null;
    try {
        stores.value = (await storeApi.list()).items;
        storesLoaded.value = true;
    } catch (e) {
        storesError.value = toApiError(e);
    } finally {
        storesLoading.value = false;
    }
}

/** 표에 보일 변환 글자 — 링크 틀 그대로 · 붙일 쿼리는 `?` 를 붙여 */
function transformText(store: SiteAffiliateStore): string {
    return store.transform.type === 'TEMPLATE' ? store.transform.template : `?${store.transform.query}`;
}

const storeActing = ref(false);
const storeOpen = ref(false);
const storeTarget = ref<SiteAffiliateStore | null>(null);
const storeError = ref('');
const storeFieldErrors = ref<Record<string, string>>({});
const STORE_FIELDS = ['domain', 'programName', 'template', 'query'];

function openStore(item: SiteAffiliateStore | null): void {
    if (storeActing.value || !canManage.value) return;
    storeTarget.value = item;
    storeError.value = '';
    storeFieldErrors.value = {};
    storeOpen.value = true;
}

function clearStoreField(field: string): void {
    const next = { ...storeFieldErrors.value };
    delete next[field];
    storeFieldErrors.value = next;
}

async function saveStore(body: SiteAffiliateStoreRequest): Promise<void> {
    if (storeActing.value) return;
    storeActing.value = true;
    storeError.value = '';
    storeFieldErrors.value = {};
    const target = storeTarget.value;
    try {
        const done = target ? await storeApi.update(target.id, body) : await storeApi.create(body);
        storeOpen.value = false;
        toast.success(t(target ? 'hotdeal.siteAffiliate.updated' : 'hotdeal.siteAffiliate.created', { domain: done.domain }));
        await loadStores();
    } catch (e) {
        const failure = toApiError(e);
        const fields = failure.fields ?? {};
        const known = Object.fromEntries(Object.entries(fields).filter(([k]) => STORE_FIELDS.includes(k)));
        const other = Object.entries(fields)
            .filter(([k]) => !STORE_FIELDS.includes(k))
            .map(([, v]) => v);
        storeFieldErrors.value = known;
        // 칸에 붙은 오류만 있으면 창 안 한 줄은 비운다(중복 409 는 도메인 칸 아래)
        storeError.value = Object.keys(known).length && !other.length ? '' : [failure.message, ...other].join(' ');
    } finally {
        storeActing.value = false;
    }
}

async function toggleStore(store: SiteAffiliateStore, enabled: boolean): Promise<void> {
    if (storeActing.value || !canManage.value) return;
    storeActing.value = true;
    try {
        const done = await storeApi.update(store.id, { domain: store.domain, programName: store.programName, transform: { ...store.transform }, enabled });
        stores.value = stores.value.map((s) => (s.id === done.id ? done : s));
        toast.success(t(enabled ? 'hotdeal.siteAffiliate.toggledOn' : 'hotdeal.siteAffiliate.toggledOff', { domain: done.domain }));
    } catch (e) {
        toast.error(t('hotdeal.siteAffiliate.toggleFailed', { reason: toApiError(e).message }), 8000);
        await loadStores();
    } finally {
        storeActing.value = false;
    }
}

async function removeStore(store: SiteAffiliateStore): Promise<void> {
    if (storeActing.value) return;
    const ok = await confirm({ title: t('hotdeal.siteAffiliate.removeTitle', { domain: store.domain }), message: t('hotdeal.siteAffiliate.removeMessage'), confirmLabel: t('hotdeal.siteAffiliate.remove'), tone: 'danger' });
    if (!ok) return;
    storeActing.value = true;
    try {
        await storeApi.remove(store.id);
        toast.success(t('hotdeal.siteAffiliate.removed', { domain: store.domain }));
    } catch (e) {
        toast.error(t('hotdeal.siteAffiliate.removeFailed', { reason: toApiError(e).message }), 8000);
    } finally {
        storeActing.value = false;
        await loadStores();
    }
}

onBeforeRouteLeave(async () => {
    if (changed.value === 0) return true;
    return confirm({ title: t('hotdeal.settings.leaveTitle'), message: t('hotdeal.settings.leaveMessage'), confirmLabel: t('hotdeal.settings.leaveConfirm'), cancelLabel: t('common.cancel'), tone: 'danger' });
});
</script>
