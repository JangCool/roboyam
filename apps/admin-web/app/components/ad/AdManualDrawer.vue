<template>
    <!--
        광고 수동 등록(운영자 모드 — 04-ia-review §2 「수동 등록도 심사」) — 한 서랍에서 위에서 아래로:
        ① 광고주(사업자 또는 계정 없는 광고주 — AutoComplete + [새 광고주]) ② 종류·기간(남은 칸을 바로 보여 준다 — 꽉 찬 날이 있으면 막힘) · 비중
        ③ 소재(배너 그림 또는 한 줄 문구, 링크, 대체 글) ④ 계약 금액(제안 — 승인 때 확정) · 계약서 사본(선택 — 오프라인으로 먼저 받았을 때) → [심사 요청].
        등록하면 바로 확정되지 않고 「광고 › 광고 심사」 심사 대기(`SUBMITTED`)로 들어간다. 승인(수동 결제 고정) → 계약서 사본 + 입금 확인 → [예약 확정] 은 광고 심사 화면에서 한다.
        서버 호출은 부모가 넘긴 함수로만 한다(광고주 찾기·만들기, 남은 칸, 배너 이미지 올리기, 계약서 올리기). 저장은 `submit`.
        종류는 광고 종류(배너 넷·한 줄)만 — 핫딜 노출 자리는 서버가 받지 않는다. 배너 이미지는 고르면 바로 올리고(`imageFileId`), 계약서 사본도 고르면 한 장씩 올려 등록 때 붙인다.
    -->
    <Drawer v-model:visible="open" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '46rem', maxWidth: '100vw' }">
        <template #header>
            <h2 :id="titleId" class="m-0 text-base font-semibold">{{ t('ad.manualTitle') }}</h2>
        </template>

        <form :id="formId" class="flex flex-col gap-6" novalidate @submit.prevent="submit">
            <!-- 안내 — 한글이 낱자로 끊기지 않게 낱말 단위로 줄을 바꾼다 -->
            <Message severity="info" :closable="false" class="mt-1">
                <p class="m-0 break-keep py-0.5 text-[13px] leading-relaxed">{{ t('ad.manualReviewNote') }}</p>
            </Message>

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
                    <LabeledField :label="t('ad.weight')" :help="t('ad.weightHelp')" :error="fieldErrors.weight ?? ''">
                        <template #default="{ id, invalid }"><InputNumber v-model="weight" :input-id="id" :min="1" :max="10" show-buttons :invalid="invalid" fluid /></template>
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
                        <Button type="button" :label="imageUrl ? t('ad.imageChange') : t('ad.imagePick')" icon="pi pi-image" size="small" severity="secondary" variant="outlined" :loading="imageUploading" @click="imageInput?.click()" />
                        <small class="text-xs text-muted-color">{{ t('ad.imageRule') }}</small>
                        <input ref="imageInput" type="file" class="hidden" tabindex="-1" :accept="IMAGE_ACCEPT" :aria-label="t('ad.imagePick')" @change="onImage" />
                    </div>
                    <img v-if="imageUrl" :src="imageUrl" :alt="altText" class="h-auto max-h-40 max-w-full self-start rounded border border-(--line)" />
                    <small v-if="imageError" class="text-xs text-red-600 dark:text-red-400" role="alert">{{ imageError }}</small>
                    <LabeledField :label="t('bizApplication.altText')" :error="fieldErrors['creative.altText'] ?? (tried && !altText.trim() ? t('ad.altRequired') : '')">
                        <template #default="{ id, invalid }"><InputText :id="id" v-model="altText" maxlength="100" :invalid="invalid" fluid /></template>
                    </LabeledField>
                </template>
                <LabeledField v-else :label="t('ad.lineText')" :hint="t('ad.lineTextHint')" :error="fieldErrors['creative.text'] ?? (tried && !lineText.trim() ? t('ad.lineTextRequired') : '')">
                    <template #default="{ id, invalid }"><InputText :id="id" v-model="lineText" maxlength="40" :invalid="invalid" fluid /></template>
                </LabeledField>
                <LabeledField :label="t('bizApplication.link')" :help="t('ad.linkHelp')" :error="fieldErrors['creative.linkUrl'] ?? (tried && !linkOk ? t('ad.linkRequired') : '')">
                    <template #default="{ id, invalid }">
                        <InputText :id="id" v-model="linkUrl" type="url" maxlength="500" :placeholder="LINK_PLACEHOLDER" :invalid="invalid" fluid />
                    </template>
                </LabeledField>
            </section>

            <!-- ④ 계약 금액(제안)·계약서 사본(선택) — 입금 확인은 승인 뒤 광고 심사 화면에서 -->
            <section class="flex flex-col gap-2">
                <h3 class="m-0 text-sm font-semibold">④ {{ t('ad.amountAndContract') }}</h3>
                <p v-if="availability" class="m-0 text-xs text-muted-color tabular-nums">{{ t('bizApplication.quoteDay', { price: won(availability.quote.unitPrice), units: availability.quote.units, supply: won(availability.quote.supply) }) }}</p>
                <div class="grid grid-cols-1 gap-x-3 sm:grid-cols-2">
                    <LabeledField :label="t('bizApplication.supply')" :help="t('ad.supplyProposalHelp')" :error="fieldErrors.supply ?? (tried && !(supply && supply > 0) ? t('bizApplication.supplyRequired') : '')">
                        <template #default="{ id, describedBy, invalid }"><InputNumber v-model="supply" :input-id="id" :min="0" :max="999_999_999" :suffix="t('bizApplication.wonSuffix')" :invalid="invalid" :aria-describedby="describedBy" fluid /></template>
                    </LabeledField>
                    <div class="flex items-center pt-5 text-[13px] tabular-nums">{{ t('bizApplication.totalLine', { supply: won(amount.supply), vat: won(amount.vat), total: won(amount.total) }) }}</div>
                </div>
                <div class="flex flex-col gap-1">
                    <span class="text-[13px] font-medium">{{ t('bizApplication.contract.title') }} <span class="text-xs font-normal text-muted-color">{{ t('ad.contractOptional') }}</span></span>
                    <BizContractFiles :files="contracts" editable :busy="uploading" :error="uploadError || (fieldErrors.contractFileIds ?? '')" @upload="onUpload" @replace="(b) => onUpload([b.file], b.id)" />
                </div>
                <LabeledField :label="t('ad.memo')" :help="t('ad.memoHelp')" :error="fieldErrors.memo ?? ''">
                    <template #default="{ id, invalid }"><Textarea :id="id" v-model="memo" rows="2" auto-resize maxlength="300" :invalid="invalid" fluid /></template>
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
                    <Button type="submit" :form="formId" :label="t('ad.actRequestReview')" icon="pi pi-send" :loading="busy" :disabled="!requiredReady || uploadBusy" />
                </div>
            </div>
        </template>

        <AdAdvertiserDialog v-model:visible="advOpen" :busy="advBusy" :error="advError" :field-errors="advFields" @submit="onCreateAdvertiser" />
    </Drawer>
</template>

<script setup lang="ts">
import type { AdAdvertiser, AdAdvertiserCreateRequest, AdAvailability, AdImage, AdManualCreateRequest, AdType } from '~/composables/ad/api/useAdApi.types';
import type { BizContractFile, BizPeriod } from '~/composables/bizapplication/api/useBizApplicationApi.types';
import UtilAd from '~/utils/util-ad';
import UtilBizApplication from '~/utils/util-biz-application';
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
    /** 배너 이미지 올리기(PNG·JPG·GIF·WEBP 2MB) — 돌아온 `id` 가 `creative.imageFileId` */
    uploadImage: (file: File) => Promise<AdImage>;
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
/** 서버 `AdImageUploader` 와 같게 — PNG·JPG·GIF·WEBP, 2MB */
const IMAGE_ACCEPT = 'image/png,image/jpeg,image/gif,image/webp';
const IMAGE_MAX_MB = 2;

/** 운영자 등록이 되는 유형만(핫딜 노출 자리 탭에서 열리면 넓은 배너로) */
const firstType = () => (UtilAd.isManualType(props.initialType) ? props.initialType : UtilAd.MANUAL_TYPES[0]!);

const advertiser = ref<AdAdvertiser | null>(null);
const suggestions = ref<AdAdvertiser[]>([]);
const type = ref<AdType>(firstType());
const weight = ref<number | null>(5);
const startAt = ref('');
const endAt = ref('');
/** 올린 배너 이미지 — `id` 는 등록 본문, `url` 은 미리보기 */
const imageFileId = ref('');
const imageUrl = ref('');
const imageUploading = ref(false);
const imageProblem = ref('');
const altText = ref('');
const lineText = ref('');
const linkUrl = ref('');
const contracts = ref<BizContractFile[]>([]);
const supply = ref<number | null>(null);
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
        type.value = firstType();
        weight.value = 5;
        startAt.value = UtilBizApplication.kstMidnight(start);
        endAt.value = UtilBizApplication.kstMidnight(UtilBizApplication.addDays(start, 7));
        imageFileId.value = '';
        imageUrl.value = '';
        imageProblem.value = '';
        altText.value = '';
        lineText.value = '';
        linkUrl.value = '';
        contracts.value = [];
        supply.value = null;
        memo.value = '';
        tried.value = false;
        uploadError.value = '';
    },
    { immediate: true },
);

const typeOptions = computed(() => UtilAd.MANUAL_TYPES.map((v) => ({ value: v, label: t(`bizApplication.adType.${v}`) })));
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
const linkOk = computed(() => /^https?:\/\/\S+\.\S+/.test(linkUrl.value.trim()));
const creativeOk = computed(() => (UtilAd.isBanner(type.value) ? Boolean(imageFileId.value) && Boolean(altText.value.trim()) : Boolean(lineText.value.trim())) && linkOk.value);
const activeContracts = computed(() => contracts.value.filter((c) => !c.replacedAt));
/** 이미지·계약서를 올리는 중에는 등록을 막는다(돌아온 id 가 본문에 들어간다) */
const uploadBusy = computed(() => uploading.value || imageUploading.value);
/** 배너 그림 칸 오류 — 고를 때 문제(형식·크기·올리기 실패) → 서버 `fields.creative.imageFileId` → 비었음 */
const imageError = computed(() => imageProblem.value || props.fieldErrors['creative.imageFileId'] || (tried.value && !imageFileId.value ? t('ad.imageRequired') : ''));
const slotOk = computed(() => Boolean(availability.value && availability.value.remaining > 0 && !periodBad.value));
/** 심사 요청에 필요한 것 — 계약서·입금은 승인 뒤라 여기서 막지 않는다 */
const requiredReady = computed(() => Boolean(advertiser.value) && slotOk.value && creativeOk.value && Boolean(supply.value && supply.value > 0));
const conditions = computed(() => [
    { key: 'adv', ok: Boolean(advertiser.value), label: t('ad.condAdvertiser') },
    { key: 'slot', ok: slotOk.value, label: t('ad.condSlot') },
    { key: 'creative', ok: creativeOk.value, label: t('ad.condCreative') },
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

// 소재 그림 — 고르면 바로 올리고 돌아온 id(본문)·주소(미리보기)를 쓴다. 바꾸면 새로 올린다(먼저 올린 그림은 어느 건에도 붙지 않는다)
async function onImage(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!IMAGE_ACCEPT.split(',').includes(file.type)) {
        imageProblem.value = t('ad.imageBadType');
        return;
    }
    if (file.size > IMAGE_MAX_MB * 1024 * 1024) {
        imageProblem.value = t('ad.imageTooBig', { mb: IMAGE_MAX_MB });
        return;
    }
    imageUploading.value = true;
    imageProblem.value = '';
    try {
        const uploaded = await props.uploadImage(file);
        imageFileId.value = uploaded.id;
        imageUrl.value = uploaded.url;
        if (!altText.value) altText.value = file.name.replace(/\.[^.]+$/, '').slice(0, 100);
    } catch (e) {
        const failure = props.toError(e);
        imageProblem.value = failure.fields?.file ?? failure.message;
    } finally {
        imageUploading.value = false;
    }
}

// 계약서 사본 — 고르면 한 장씩 바로 올리고 서버가 준 파일을 목록에 둔다(등록 때 `contractFileIds` 로 붙는다).
// [바꾸기] 는 아직 어느 건에도 붙지 않은 사본이라 목록에서 빼고 새 파일을 더한다(뺀 사본은 붙지 않는다).
async function onUpload(files: File[], replaceId?: string): Promise<void> {
    uploading.value = true;
    uploadError.value = '';
    try {
        for (const f of files) {
            const added = await props.uploadContract(f);
            contracts.value = [...contracts.value.filter((c) => c.id !== replaceId), added];
        }
    } catch (e) {
        const failure = props.toError(e);
        uploadError.value = failure.fields?.file ?? failure.message;
    } finally {
        uploading.value = false;
    }
}

function submit(): void {
    tried.value = true;
    if (props.busy || uploadBusy.value || !requiredReady.value || !advertiser.value || !supply.value) return;
    const banner = UtilAd.isBanner(type.value);
    emit('submit', {
        advertiserId: advertiser.value.id,
        type: type.value,
        period: { ...period.value },
        weight: weight.value ?? 5,
        creative: { imageFileId: banner ? imageFileId.value : null, text: banner ? null : lineText.value.trim(), linkUrl: linkUrl.value.trim(), altText: banner ? altText.value.trim() : null },
        supply: supply.value,
        contractFileIds: activeContracts.value.map((c) => c.id),
        memo: memo.value.trim(),
    });
}
</script>
