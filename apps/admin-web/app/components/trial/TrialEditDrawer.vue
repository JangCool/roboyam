<template>
    <!--
        체험단 모집 만들기·고치기 서랍(오른쪽, trial-group-contract 「모집 · 신청 · 뽑기」·「제공형 · 대여형」·「구현 결정」 10) — 모집 목록 [모집 만들기]·상세 [고치기]에서 연다.
        칸: 광고주 · 제목 · 상품 이름 · 제품값 · 형태(제공형은 제품값이 설정 상한을 넘으면 막고 「대여형으로만」 안내) · 사용기 게시판(「체험단 사용기 받기」 켠 게시판) · 인원 · 리뷰어 몫(%) ·
          리뷰어 먼저 신청(금·은 시간 — 동은 모두와 함께) · 신청 시작(일반)·마감 · 사용기 기한·(대여형) 반납 기한 · 보내는 쪽(광고주 — 회원 동의 / 운영자가 대신) · 설명 · 대표 그림.
        만들면 준비(DRAFT)로 시작하고, 모집 열기·신청 마감·끝내기·취소는 모집 상세 머리의 단추(서버 `action`)다.
        오른쪽(넓은 화면): 리뷰어 몫·일반 몫 인원, 먼저 신청 시각, 금액 안내(요금표 값으로 뽑기 뒤 확정).
        검사는 `UtilTrial.problems`(서버 CHECK 와 같은 범위 — 판정은 서버). 서버 칸 오류(`400 fields.<칸>`)는 칸 아래 그대로. 서버를 부르지 않는다 — 값은 `save` 로 올린다.
    -->
    <Drawer :visible="open" position="right" :aria-labelledby="titleId" :style="{ width: '52rem', maxWidth: '100vw' }" @update:visible="(v: boolean) => emit('update:open', v)">
        <template #header>
            <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ trial ? t('trial.drawer.editTitle') : t('trial.drawer.createTitle') }}</h2>
        </template>

        <form v-if="options" :id="formId" class="grid min-w-0 gap-6 pb-4 lg:grid-cols-[minmax(0,1fr)_15rem]" novalidate @submit.prevent="submit">
            <div class="flex min-w-0 flex-col gap-4">
                <!-- 고를 거리를 못 받았다(권한 없음 403 등) — 빈 후보·기본값으로 그린다 -->
                <Message v-if="options.unavailable.length" severity="warn" variant="simple" size="small">
                    <span v-for="src in options.unavailable" :key="src" class="block">{{ t(`trial.drawer.unavailable.${src}`) }}</span>
                </Message>
                <section class="grid gap-3 sm:grid-cols-2">
                    <LabeledField :label="t('trial.drawer.advertiser')" :error="errorOf('advertiserId')">
                        <template #default="{ id, describedBy, invalid }">
                            <Select v-model="form.advertiserId" :input-id="id" :options="advertisers" option-label="name" option-value="id" filter class="w-full" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('trial.drawer.reviewBoard')" :help="t('trial.drawer.reviewBoardHelp')" :error="errorOf('reviewBoardId')">
                        <template #default="{ id, describedBy, invalid }">
                            <Select v-model="form.reviewBoardId" :input-id="id" :options="reviewBoards" option-label="name" option-value="id" class="w-full" :invalid="invalid" :aria-describedby="describedBy" :empty-message="t('trial.drawer.noReviewBoard')" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('trial.drawer.title')" class="sm:col-span-2" :error="errorOf('title')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.title" :maxlength="UtilTrial.LIMITS.titleMax" class="w-full" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('trial.drawer.productName')" :error="errorOf('productName')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputText :id="id" v-model="form.productName" :maxlength="UtilTrial.LIMITS.productNameMax" class="w-full" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('trial.drawer.productPrice')" :error="errorOf('productPrice')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputGroup>
                                <InputNumber v-model="form.productPrice" :input-id="id" :min="0" :max-fraction-digits="0" :allow-empty="false" fluid :invalid="invalid" :aria-describedby="describedBy" input-class="text-right tabular-nums" />
                                <InputGroupAddon>{{ t('trial.wonSuffix') }}</InputGroupAddon>
                            </InputGroup>
                        </template>
                    </LabeledField>
                </section>

                <!-- 형태 · 보내는 쪽 -->
                <section class="grid gap-3 sm:grid-cols-2">
                    <fieldset class="m-0 flex flex-col gap-1.5 border-0 p-0">
                        <legend class="mb-1 text-sm font-medium">{{ t('trial.drawer.form') }}</legend>
                        <label class="flex cursor-pointer items-start gap-2 text-sm" :class="giveBlocked ? 'opacity-60' : ''">
                            <RadioButton v-model="form.form" value="GIVE" :input-id="`${formId}-give`" name="trial-form" :disabled="giveBlocked" class="mt-0.5" />
                            <span>{{ t('trial.form.GIVE') }}<span class="block text-xs text-muted-color">{{ t('trial.drawer.giveHelp', { limit: won(options.givePriceLimit) }) }}</span></span>
                        </label>
                        <label class="flex cursor-pointer items-start gap-2 text-sm">
                            <RadioButton v-model="form.form" value="LEND" :input-id="`${formId}-lend`" name="trial-form" class="mt-0.5" />
                            <span>{{ t('trial.form.LEND') }}<span class="block text-xs text-muted-color">{{ t('trial.drawer.lendHelp') }}</span></span>
                        </label>
                        <small v-if="errorOf('form')" class="text-xs text-red-600 dark:text-red-400">{{ errorOf('form') }}</small>
                    </fieldset>
                    <fieldset class="m-0 flex flex-col gap-1.5 border-0 p-0">
                        <legend class="mb-1 text-sm font-medium">{{ t('trial.drawer.shipper') }}</legend>
                        <label v-for="s in (['ADVERTISER', 'OPERATOR'] as const)" :key="s" class="flex cursor-pointer items-start gap-2 text-sm">
                            <RadioButton v-model="form.shipper" :value="s" :input-id="`${formId}-${s}`" name="trial-shipper" class="mt-0.5" />
                            <span>{{ t(`trial.shipper.${s}`) }}<span class="block text-xs text-muted-color">{{ t(`trial.drawer.shipperHelp.${s}`) }}</span></span>
                        </label>
                    </fieldset>
                </section>

                <!-- 인원 · 리뷰어 -->
                <section class="grid gap-3 sm:grid-cols-4">
                    <LabeledField :label="t('trial.drawer.recruitCount')" :error="errorOf('recruitCount')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="form.recruitCount" :input-id="id" :min="1" :max="UtilTrial.LIMITS.recruitMax" :allow-empty="false" fluid :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('trial.drawer.reviewerShare')" :error="errorOf('reviewerShare')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputGroup>
                                <InputNumber v-model="form.reviewerShare" :input-id="id" :min="0" :max="100" :allow-empty="false" fluid :invalid="invalid" :aria-describedby="describedBy" />
                                <InputGroupAddon>%</InputGroupAddon>
                            </InputGroup>
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('trial.drawer.goldHours')" :error="errorOf('reviewerGoldHours')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="form.reviewerGoldHours" :input-id="id" :min="0" :max="UtilTrial.LIMITS.hoursMax" :allow-empty="false" fluid :suffix="t('trial.hoursSuffix')" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('trial.drawer.silverHours')" :error="errorOf('reviewerSilverHours')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="form.reviewerSilverHours" :input-id="id" :min="0" :max="UtilTrial.LIMITS.hoursMax" :allow-empty="false" fluid :suffix="t('trial.hoursSuffix')" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                </section>

                <!-- 기간 · 기한 -->
                <section class="grid gap-3 sm:grid-cols-2">
                    <LabeledField :label="t('trial.drawer.applyStart')" :help="t('trial.drawer.applyStartHelp')" :error="errorOf('applyStartAt')">
                        <template #default="{ id, describedBy, invalid }">
                            <AppDateTimePicker v-model="form.applyStartAt" :input-id="id" :minute-step="10" :invalid="invalid" :described-by="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('trial.drawer.applyEnd')" :error="errorOf('applyEndAt')">
                        <template #default="{ id, describedBy, invalid }">
                            <AppDateTimePicker v-model="form.applyEndAt" :input-id="id" :minute-step="10" :invalid="invalid" :described-by="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('trial.drawer.reviewDays')" :help="t('trial.drawer.reviewDaysHelp')" :error="errorOf('reviewDays')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber v-model="form.reviewDays" :input-id="id" :min="1" :max="UtilTrial.LIMITS.daysMax" :allow-empty="false" fluid :suffix="t('trial.daysSuffix')" :invalid="invalid" :aria-describedby="describedBy" />
                        </template>
                    </LabeledField>
                    <LabeledField v-if="form.form === 'LEND'" :label="t('trial.drawer.returnDays')" :help="t('trial.drawer.returnDaysHelp')" :error="errorOf('returnDays')">
                        <template #default="{ id, describedBy, invalid }">
                            <InputNumber :model-value="form.returnDays" :input-id="id" :min="1" :max="UtilTrial.LIMITS.daysMax" fluid :suffix="t('trial.daysSuffix')" :invalid="invalid" :aria-describedby="describedBy" @update:model-value="(v: number | null) => (form.returnDays = v)" />
                        </template>
                    </LabeledField>
                </section>

                <LabeledField :label="t('trial.drawer.description')" :hint="`${form.description.length}/${UtilTrial.LIMITS.descriptionMax}`" :error="errorOf('description')">
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="form.description" rows="5" auto-resize fluid :maxlength="UtilTrial.LIMITS.descriptionMax" :invalid="invalid" :aria-describedby="describedBy" />
                    </template>
                </LabeledField>

                <!-- 대표 그림(서버 `POST /api/admin/trials/images` → `imageKey`) — 올리기는 부모가 한다 -->
                <div class="flex flex-col gap-1.5">
                    <span class="text-sm font-medium">{{ t('trial.drawer.cover') }}</span>
                    <div class="flex flex-wrap items-center gap-2">
                        <img v-if="coverUrl" :src="coverUrl" alt="" class="h-16 w-28 rounded object-cover" />
                        <input ref="coverInput" type="file" class="sr-only" accept="image/jpeg,image/png,image/webp" :aria-label="t('trial.drawer.cover')" @change="onCover" />
                        <Button type="button" :label="coverUrl ? t('trial.drawer.coverChange') : t('trial.drawer.coverPick')" icon="pi pi-image" size="small" severity="secondary" variant="outlined" :loading="coverUploading" :disabled="!canManage" @click="coverInput?.click()" />
                        <Button v-if="form.imageKey" type="button" :label="t('trial.drawer.coverRemove')" size="small" severity="secondary" variant="text" @click="removeCover" />
                    </div>
                    <small v-if="coverError" class="text-xs text-red-600 dark:text-red-400">{{ coverError }}</small>
                </div>
                <p v-if="!trial" class="m-0 text-xs text-muted-color">{{ t('trial.drawer.draftNote') }}</p>

                <Message v-if="error" severity="error" variant="simple" size="small" role="alert">{{ error }}</Message>
            </div>

            <!-- 미리보기 -->
            <aside class="flex flex-col gap-3 self-start rounded-lg bg-surface-100 p-3 text-[13px] dark:bg-surface-950" :aria-label="t('trial.drawer.preview')">
                <h3 class="m-0 text-xs font-medium text-muted-color">{{ t('trial.drawer.preview') }}</h3>
                <p class="m-0">{{ t('trial.drawer.splitLine', { reviewer: split.reviewer, general: split.general }) }}</p>
                <p v-if="form.applyStartAt" class="m-0 text-xs text-muted-color">
                    {{ t('trial.drawer.earlyLine', { gold: time.full(earlyAt(form.reviewerGoldHours)), silver: time.full(earlyAt(form.reviewerSilverHours)) }) }}
                </p>
                <p class="m-0 border-t border-(--line) pt-2 text-[11px] text-muted-color">{{ t('trial.drawer.amountNote') }}</p>
            </aside>
        </form>
        <div v-else class="h-40 animate-pulse rounded-lg bg-surface-100 dark:bg-surface-800" role="status" :aria-label="t('common.loading')" />

        <template #footer>
            <div class="flex justify-end gap-2">
                <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="saving" @click="emit('update:open', false)" />
                <Button v-if="canManage" type="submit" :form="formId" :label="t('common.save')" :loading="saving" :disabled="!options" />
            </div>
        </template>
    </Drawer>
</template>

<script setup lang="ts">
import type { AdminTrialDetail, AdminTrialFormOptions, AdminTrialRequest } from '~/composables/trial/api/useTrialApi.types';
import UtilTrial from '~/utils/util-trial';
import UtilTrialMessages from '~/utils/util-trial-messages';

interface Props {
    open: boolean;
    /** 고칠 모집 — null 이면 만들기 */
    trial?: AdminTrialDetail | null;
    /** 고를 거리(광고주·사용기 게시판·상한·요금표) — 받기 전 null */
    options?: AdminTrialFormOptions | null;
    canManage?: boolean;
    saving?: boolean;
    /** 대표 그림 올리기(부모가 API 로 채운다) → 올린 키·주소 */
    uploadCover?: ((file: File) => Promise<{ key: string; url: string }>) | null;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { trial: null, options: null, canManage: false, saving: false, uploadCover: null, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:open': [open: boolean]; save: [body: AdminTrialRequest, id: string | null] }>();

const { t, locale } = useI18n();
const time = useDisplayTime();
const titleId = useId();
const formId = useId();

const form = ref<AdminTrialRequest>(UtilTrial.emptyForm(null));
const tried = ref(false);

// 열 때마다 처음 값 — 고치기는 그 모집, 만들기는 처음 값
watch(
    () => [props.open, props.options] as const,
    ([open]) => {
        if (!open) return;
        form.value = props.trial ? UtilTrial.formOf(props.trial) : UtilTrial.emptyForm(props.options);
        coverUrl.value = props.trial?.imageUrl ?? null;
        coverError.value = '';
        tried.value = false;
    },
    { immediate: true },
);

const giveLimit = computed(() => props.options?.givePriceLimit ?? 50000);
/** 광고주 후보 — 목록을 못 받았어도(권한 없음) 고치는 모집의 광고주는 보이게 맨 앞에 둔다 */
const advertisers = computed(() => {
    const list = props.options?.advertisers ?? [];
    const current = props.trial?.advertiser;
    return current && !list.some((a) => a.id === current.id) ? [current, ...list] : list;
});
/** 사용기 게시판 후보 — 「체험단 사용기 받기」를 켠 게시판(`useTrialFormOptions`). 고치는 모집의 게시판이 그새 꺼졌어도 지금 값은 보이게 맨 앞에 둔다 */
const reviewBoards = computed(() => {
    const list = props.options?.reviewBoards ?? [];
    const current = props.trial?.reviewBoard;
    return current && !list.some((b) => b.id === current.id) ? [current, ...list] : list;
});
/** 제품값이 상한을 넘으면 제공형을 막는다(이미 골랐으면 대여형으로 옮긴다) */
const giveBlocked = computed(() => !UtilTrial.giveAllowed(form.value.productPrice, giveLimit.value));
watch(giveBlocked, (blocked) => {
    if (blocked && form.value.form === 'GIVE') form.value = { ...form.value, form: 'LEND', returnDays: form.value.returnDays ?? 21 };
});
watch(
    () => form.value.form,
    (f) => {
        if (f === 'LEND' && form.value.returnDays === null) form.value = { ...form.value, returnDays: 21 };
    },
);

/* ── 대표 그림 ── */
const coverInput = ref<HTMLInputElement | null>(null);
const coverUrl = ref<string | null>(null);
const coverUploading = ref(false);
const coverError = ref('');
async function onCover(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    input.value = '';
    if (!file || !props.uploadCover) return;
    const problem = UtilTrial.photoProblem(file);
    if (problem) {
        coverError.value = problem === 'type' ? t('trial.photoTypeError') : t('trial.photoSizeError');
        return;
    }
    coverError.value = '';
    coverUploading.value = true;
    try {
        const uploaded = await props.uploadCover(file);
        form.value = { ...form.value, imageKey: uploaded.key };
        coverUrl.value = uploaded.url;
    } catch (e) {
        coverError.value = e instanceof Error ? e.message : String(e);
    } finally {
        coverUploading.value = false;
    }
}
function removeCover(): void {
    form.value = { ...form.value, imageKey: null };
    coverUrl.value = null;
}

const problems = computed(() => UtilTrial.problems(form.value, giveLimit.value));
function errorOf(field: keyof AdminTrialRequest): string {
    if (props.fieldErrors[field]) return props.fieldErrors[field]!;
    const p = problems.value[field];
    if (!p) return '';
    // 제공형 상한은 바로 알린다, 그 밖은 [저장]을 한 번 누른 뒤
    if (p === 'giveLimit') return t('trial.problem.giveLimit', { limit: won(giveLimit.value) });
    return tried.value ? t(`trial.problem.${p}`) : '';
}

const split = computed(() => UtilTrial.split(form.value.recruitCount, form.value.reviewerShare));
function earlyAt(hours: number): string {
    return new Date(Date.parse(form.value.applyStartAt) - hours * 3600_000).toISOString();
}
const won = (n: number) => UtilTrialMessages.won(t, n, locale.value);

function submit(): void {
    tried.value = true;
    if (Object.keys(problems.value).length || props.saving) return;
    emit('save', UtilTrial.toRequest(form.value), props.trial?.id ?? null);
}
</script>
