<template>
    <!--
        광고 수동 등록(운영자 모드 — ★-2) — 한 서랍에서 위에서 아래로:
        ① 광고주(사업자 또는 계정 없는 광고주 — AutoComplete + [새 광고주]) ② 종류·기간(남은 칸을 바로 보여 준다 — 꽉 찬 날이 있으면 막힘) · 비중
        ③ 소재(배너 그림 또는 한 줄 문구, 링크, 대체 글) ④ 계약서 사본(필수) ⑤ 금액·입금 확인(필수) → [예약 확정].
        [예약 확정] 은 계약서 사본과 입금 확인이 둘 다 있어야 켜진다(서버도 CHECK). 운영자가 직접 등록하는 건은 늘 수동 결제다.
        서버 호출은 부모가 넘긴 함수로만 한다(광고주 찾기·만들기, 남은 칸, 계약서 올리기). 저장은 `submit`.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '46rem', maxWidth: '100vw' }">
        <template #header>
            <h2 :id="titleId" class="m-0 text-base font-semibold">{{ t('ad.manualTitle') }}</h2>
        </template>

        <form :id="formId" class="flex flex-col gap-6" novalidate @submit.prevent="submit">
            <!-- ① 광고주 -->
            <section class="flex flex-col gap-2">
                <h3 class="m-0 text-sm font-semibold">① {{ t('ad.advertiser') }}</h3>
                <LabeledField :label="t('ad.advertiserPick')" :help="t('ad.advertiserPickHelp')" :error="fieldErrors.advertiserId ?? (tried && !advertiser ? t('ad.advertiserRequired') : '')">
                    <template #default="{ id, describedBy, invalid }">
                        <div class="flex gap-2">
                            <AutoComplete v-model="advertiser" :input-id="id" :suggestions="suggestions" option-label="name" dropdown force-selection :invalid="invalid" :aria-describedby="describedBy" class="min-w-0 flex-1" fluid @complete="onComplete">
                                <template #option="{ option }">
                                    <span class="flex items-center gap-2 text-[13px]">
                                        <Tag :value="t(`ad.advertiserKind.${option.kind}`)" severity="secondary" />{{ option.name }}<span v-if="option.maskedRegNo" class="text-[11px] text-muted-color">{{ option.maskedRegNo }}</span>
                                    </span>
                                </template>
                            </AutoComplete>
                            <Button type="button" :label="t('ad.newAdvertiser')" icon="pi pi-plus" severity="secondary" variant="outlined" @click="advOpen = true" />
                        </div>
                    </template>
                </LabeledField>
            </section>

            <!-- ② 종류·기간 -->
            <section class="flex flex-col gap-2">
                <h3 class="m-0 text-sm font-semibold">② {{ t('ad.typeAndPeriod') }}</h3>
                <div class="grid grid-cols-1 gap-x-3 sm:grid-cols-2">
                    <LabeledField :label="t('ad.type')">
                        <template #default="{ id }"><Select v-model="type" :input-id="id" :options="typeOptions" option-label="label" option-value="value" fluid /></template>
                    </LabeledField>
                    <LabeledField :label="t('ad.weight')" :help="t('ad.weightHelp')">
                        <template #default="{ id }"><InputNumber v-model="weight" :input-id="id" :min="1" :max="10" show-buttons fluid /></template>
                    </LabeledField>
                    <LabeledField :label="t('ad.start')">
                        <template #default="{ id }"><AppDateTimePicker v-model="startAt" :input-id="id" :minute-step="10" :show-zone="false" /></template>
                    </LabeledField>
                    <LabeledField :label="t('ad.end')" :help="t('ad.endExclusive')" :error="fieldErrors.period ?? (periodBad ? t('ad.periodBad') : '')">
                        <template #default="{ id, invalid }"><AppDateTimePicker v-model="endAt" :input-id="id" :minute-step="10" :show-zone="false" :invalid="invalid" /></template>
                    </LabeledField>
                </div>
                <Message v-if="availability && !periodBad" :severity="availability.remaining > 0 ? 'success' : 'error'" :closable="false">
                    <span class="text-[13px]">
                        <i v-if="checking" class="pi pi-spin pi-spinner mr-1 text-[11px]" aria-hidden="true" />
                        {{ availability.remaining > 0 ? t('ad.slotOk', { n: availability.remaining, limit: availability.limit }) : t('ad.slotFull', { dates: availability.fullDates.slice(0, 4).join(', ') }) }}
                    </span>
                </Message>
            </section>

            <!-- ③ 소재 -->
            <section class="flex flex-col gap-2">
                <h3 class="m-0 text-sm font-semibold">③ {{ t('ad.tabCreative') }}</h3>
                <template v-if="UtilAd.isBanner(type)">
                    <div class="flex flex-wrap items-center gap-2">
                        <Button type="button" :label="imageUrl ? t('ad.imageChange') : t('ad.imagePick')" icon="pi pi-image" size="small" severity="secondary" variant="outlined" @click="imageInput?.click()" />
                        <small class="text-xs text-muted-color">{{ t('ad.imageRule') }}</small>
                        <input ref="imageInput" type="file" class="hidden" tabindex="-1" accept="image/jpeg,image/png,image/webp" :aria-label="t('ad.imagePick')" @change="onImage" />
                    </div>
                    <img v-if="imageUrl" :src="imageUrl" :alt="altText" class="h-auto max-h-40 max-w-full self-start rounded border border-(--line)" />
                    <small v-else-if="tried" class="text-xs text-red-600 dark:text-red-400">{{ t('ad.imageRequired') }}</small>
                    <LabeledField :label="t('bizApplication.altText')" :error="tried && !altText.trim() ? t('ad.altRequired') : ''">
                        <template #default="{ id, invalid }"><InputText :id="id" v-model="altText" maxlength="100" :invalid="invalid" fluid /></template>
                    </LabeledField>
                </template>
                <LabeledField v-else :label="t('ad.lineText')" :hint="t('ad.lineTextHint')" :error="tried && !lineText.trim() ? t('ad.lineTextRequired') : ''">
                    <template #default="{ id, invalid }"><InputText :id="id" v-model="lineText" maxlength="40" :invalid="invalid" fluid /></template>
                </LabeledField>
                <LabeledField :label="t('bizApplication.link')" :help="t('ad.linkHelp')" :error="tried && !linkOk ? t('ad.linkRequired') : ''">
                    <template #default="{ id, invalid }">
                        <InputText :id="id" v-model="linkUrl" type="url" maxlength="500" :placeholder="LINK_PLACEHOLDER" :invalid="invalid" fluid />
                    </template>
                </LabeledField>
            </section>

            <!-- ④ 계약서 사본 -->
            <section class="flex flex-col gap-2">
                <h3 class="m-0 text-sm font-semibold">④ {{ t('bizApplication.contract.title') }} <span class="text-red-600">*</span></h3>
                <BizContractFiles :files="contracts" editable :busy="uploading" :error="uploadError" @upload="onUpload" @replace="(b) => onUpload([b.file], b.id)" />
            </section>

            <!-- ⑤ 금액·입금 확인 -->
            <section class="flex flex-col gap-2">
                <h3 class="m-0 text-sm font-semibold">⑤ {{ t('ad.amountAndDeposit') }} <span class="text-red-600">*</span></h3>
                <p v-if="availability" class="m-0 text-xs text-muted-color tabular-nums">{{ t('bizApplication.quoteDay', { price: won(availability.quote.unitPrice), units: availability.quote.units, supply: won(availability.quote.supply) }) }}</p>
                <div class="grid grid-cols-1 gap-x-3 sm:grid-cols-2">
                    <LabeledField :label="t('bizApplication.supply')" :error="tried && !(supply && supply > 0) ? t('bizApplication.supplyRequired') : ''">
                        <template #default="{ id, invalid }"><InputNumber v-model="supply" :input-id="id" :min="0" :max="999_999_999" :suffix="t('bizApplication.wonSuffix')" :invalid="invalid" fluid /></template>
                    </LabeledField>
                    <div class="flex items-center pt-5 text-[13px] tabular-nums">{{ t('bizApplication.totalLine', { supply: won(amount.supply), vat: won(amount.vat), total: won(amount.total) }) }}</div>
                </div>
                <LabeledField v-if="adjusted" :label="t('bizApplication.adjustReason')" :error="tried && !adjustReason.trim() ? t('bizApplication.adjustReasonRequired') : ''">
                    <template #default="{ id, invalid }"><Textarea :id="id" v-model="adjustReason" rows="2" auto-resize maxlength="200" :invalid="invalid" fluid /></template>
                </LabeledField>
                <BizDepositForm v-model="deposit" :contract-total="amount.total" :show-errors="tried" :field-errors="fieldErrors" />
                <LabeledField :label="t('ad.memo')">
                    <template #default="{ id }"><Textarea :id="id" v-model="memo" rows="2" auto-resize maxlength="200" fluid /></template>
                </LabeledField>
            </section>
        </form>

        <template #footer>
            <div class="flex flex-col gap-2">
                <ul class="m-0 flex list-none flex-wrap gap-x-4 gap-y-1 p-0 text-xs">
                    <li v-for="c in conditions" :key="c.key" class="inline-flex items-center gap-1" :class="c.ok ? 'text-green-700 dark:text-green-400' : 'text-muted-color'"><i :class="c.ok ? 'pi pi-check-circle' : 'pi pi-circle'" aria-hidden="true" />{{ c.label }}</li>
                </ul>
                <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
                <div class="flex justify-end gap-2">
                    <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="open = false" />
                    <Button type="submit" :form="formId" :label="t('bizApplication.actConfirmBooking')" icon="pi pi-calendar-plus" :loading="busy" :disabled="!evidenceReady" />
                </div>
            </div>
        </template>

        <AdAdvertiserDialog v-model:visible="advOpen" :busy="advBusy" :error="advError" :field-errors="advFields" @submit="onCreateAdvertiser" />
    </Drawer>
</template>

<script setup lang="ts">
import type { AdAdvertiser, AdAdvertiserCreateRequest, AdAvailability, AdManualCreateRequest, AdType } from '~/composables/ad/api/useAdApi.types';
import type { BizContractFile, BizPeriod } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilAd from '~/utils/util-ad';
import UtilBizApplication, { type BizDepositDraft } from '~/utils/util-biz-application';
import UtilBizApplicationMessages from '~/utils/util-biz-application-messages';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 처음 고를 유형(일정표의 지금 탭) */
    initialType: AdType;
    /** 처음 시작 날짜 `YYYY-MM-DD`(일정표 달력에서 고른 날) — 없으면 내일 */
    initialStart?: string | null;
    searchAdvertisers: (q: string) => Promise<AdAdvertiser[]>;
    createAdvertiser: (body: AdAdvertiserCreateRequest) => Promise<AdAdvertiser>;
    checkAvailability: (type: AdType, period: BizPeriod) => Promise<AdAvailability>;
    uploadContract: (file: File) => Promise<BizContractFile>;
    /** 오류를 ApiError 로(부모의 `useApiErrorOf`) */
    toError: (e: unknown) => { message: string; code: string; fields?: Record<string, string> };
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { initialStart: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ submit: [body: AdManualCreateRequest] }>();

const { t, locale } = useI18n();
const { prefs } = useLayoutPrefs();
const titleId = useId();
const formId = useId();
const won = (n: number) => UtilBizApplicationMessages.won(t, n, locale.value);
const LINK_PLACEHOLDER = 'https://';

const advertiser = ref<AdAdvertiser | null>(null);
const suggestions = ref<AdAdvertiser[]>([]);
const type = ref<AdType>(props.initialType);
const weight = ref<number | null>(5);
const startAt = ref('');
const endAt = ref('');
const imageUrl = ref('');
const altText = ref('');
const lineText = ref('');
const linkUrl = ref('');
const contracts = ref<BizContractFile[]>([]);
const supply = ref<number | null>(null);
const adjustReason = ref('');
const deposit = ref<BizDepositDraft>(UtilBizApplication.emptyDeposit());
const memo = ref('');
const tried = ref(false);
const imageInput = ref<HTMLInputElement | null>(null);
const uploading = ref(false);
const uploadError = ref('');

/** 열 때마다 처음으로 — 시작은 달력에서 고른 날(없으면 내일) 00:00, 7일 */
watch(
    open,
    (v) => {
        if (!v) return;
        const start = props.initialStart ?? UtilBizApplication.addDays(UtilBizApplication.kstToday(), 1);
        advertiser.value = null;
        type.value = props.initialType;
        weight.value = 5;
        startAt.value = UtilBizApplication.kstMidnight(start);
        endAt.value = UtilBizApplication.kstMidnight(UtilBizApplication.addDays(start, 7));
        imageUrl.value = '';
        altText.value = '';
        lineText.value = '';
        linkUrl.value = '';
        contracts.value = [];
        supply.value = null;
        adjustReason.value = '';
        deposit.value = UtilBizApplication.emptyDeposit();
        memo.value = '';
        tried.value = false;
        uploadError.value = '';
    },
    { immediate: true },
);

const typeOptions = computed(() => UtilAd.TYPES.map((v) => ({ value: v, label: t(`bizApplication.adType.${v}`) })));
const period = computed<BizPeriod>(() => ({ startAt: startAt.value, endAt: endAt.value }));
const periodBad = computed(() => !startAt.value || !endAt.value || Date.parse(endAt.value) <= Date.parse(startAt.value));

// 남은 칸 — 유형·기간이 바뀌면 다시 묻는다(서버 호출이라 watch, 0.3초 모아서)
const availability = ref<AdAvailability | null>(null);
const checking = ref(false);
let timer: ReturnType<typeof setTimeout> | null = null;
let seq = 0;
watch(
    [type, startAt, endAt, open],
    () => {
        if (timer) clearTimeout(timer);
        if (!open.value || periodBad.value) return;
        timer = setTimeout(async () => {
            const mine = ++seq;
            checking.value = true;
            try {
                const found = await props.checkAvailability(type.value, { ...period.value });
                if (mine !== seq) return;
                const firstQuote = !availability.value || supply.value === null || supply.value === availability.value.quote.supply;
                availability.value = found;
                if (firstQuote) supply.value = found.quote.supply;
            } catch {
                if (mine === seq) availability.value = null;
            } finally {
                if (mine === seq) checking.value = false;
            }
        }, 300);
    },
    { immediate: true },
);
onBeforeUnmount(() => timer && clearTimeout(timer));

const amount = computed(() => UtilBizApplication.amountOf(supply.value ?? 0));
const adjusted = computed(() => Boolean(availability.value) && (supply.value ?? 0) !== availability.value!.quote.supply);
const linkOk = computed(() => /^https?:\/\/\S+\.\S+/.test(linkUrl.value.trim()));
const creativeOk = computed(() => (UtilAd.isBanner(type.value) ? Boolean(imageUrl.value) && Boolean(altText.value.trim()) : Boolean(lineText.value.trim())) && linkOk.value);
const activeContracts = computed(() => contracts.value.filter((c) => !c.replacedAt));
const depositOk = computed(() => Object.keys(UtilBizApplication.depositProblems(deposit.value, amount.value.total)).length === 0);
/** 증빙 둘 — 이것이 없으면 [예약 확정] 이 꺼진다 */
const evidenceReady = computed(() => activeContracts.value.length > 0 && depositOk.value);
const conditions = computed(() => [
    { key: 'adv', ok: Boolean(advertiser.value), label: t('ad.condAdvertiser') },
    { key: 'slot', ok: Boolean(availability.value && availability.value.remaining > 0 && !periodBad.value), label: t('ad.condSlot') },
    { key: 'creative', ok: creativeOk.value, label: t('ad.condCreative') },
    { key: 'contract', ok: activeContracts.value.length > 0, label: t('bizApplication.condContract', { n: activeContracts.value.length }) },
    { key: 'deposit', ok: depositOk.value, label: t('bizApplication.condDeposit') },
]);

async function onComplete(event: { query: string }): Promise<void> {
    try {
        suggestions.value = await props.searchAdvertisers(event.query);
    } catch {
        suggestions.value = [];
    }
}

// 새 광고주
const advOpen = ref(false);
const advBusy = ref(false);
const advError = ref('');
const advFields = ref<Record<string, string>>({});
async function onCreateAdvertiser(body: AdAdvertiserCreateRequest): Promise<void> {
    advBusy.value = true;
    advError.value = '';
    advFields.value = {};
    try {
        advertiser.value = await props.createAdvertiser(body);
        advOpen.value = false;
    } catch (e) {
        const failure = props.toError(e);
        if (failure.code === 'VALIDATION' && failure.fields) advFields.value = failure.fields;
        else advError.value = failure.message;
    } finally {
        advBusy.value = false;
    }
}

// 소재 그림 — 목업은 브라우저에서 읽어 미리 보인다(서버가 붙으면 소재 올리기 API 로)
function onImage(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => (imageUrl.value = String(reader.result ?? ''));
    reader.readAsDataURL(file);
    if (!altText.value) altText.value = file.name.replace(/\.[^.]+$/, '');
}

// 계약서 사본 — 고르면 바로 올린다(원본 보존 — 바꾸기는 옛 파일에 「대체됨」)
async function onUpload(files: File[], replaceId?: string): Promise<void> {
    uploading.value = true;
    uploadError.value = '';
    try {
        const added: BizContractFile[] = [];
        for (const f of files) added.push(await props.uploadContract(f));
        const now = new Date().toISOString();
        contracts.value = [...contracts.value.map((c) => (c.id === replaceId ? { ...c, replacedAt: now, replacedBy: c.uploadedBy } : c)), ...added];
    } catch (e) {
        uploadError.value = props.toError(e).message;
    } finally {
        uploading.value = false;
    }
}

function submit(): void {
    tried.value = true;
    if (props.busy || !advertiser.value || periodBad.value || !availability.value || availability.value.remaining <= 0 || !creativeOk.value) return;
    if (!evidenceReady.value || !(supply.value && supply.value > 0) || (adjusted.value && !adjustReason.value.trim())) return;
    const d = deposit.value;
    const banner = UtilAd.isBanner(type.value);
    emit('submit', {
        advertiserId: advertiser.value.id,
        type: type.value,
        period: { ...period.value },
        weight: weight.value ?? 5,
        creative: { imageUrl: banner ? imageUrl.value : null, text: banner ? null : lineText.value.trim(), linkUrl: linkUrl.value.trim(), altText: banner ? altText.value.trim() : null },
        supply: supply.value,
        adjustReason: adjusted.value ? adjustReason.value.trim() : undefined,
        contractFileIds: activeContracts.value.map((c) => c.id),
        deposit: { amount: d.amount ?? 0, depositedOn: d.depositedOn, depositorName: d.depositorName.trim(), mismatchReason: d.amount !== amount.value.total ? d.mismatchReason.trim() : undefined },
        memo: memo.value.trim(),
    });
}
</script>
