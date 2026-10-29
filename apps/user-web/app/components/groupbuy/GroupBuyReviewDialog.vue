<template>
    <!--
        리뷰 쓰기·고치기 대화상자 — 산 단품 이름(자동) · 별점(필수) · 글 10~1,000자 · 사진 5장까지(먼저 올리고 ID 로 붙인다 — 반품 요청 사진과 같은 방식).
        서버는 부르지 않는다: 사진 올리기는 `upload` 함수로 받고, 저장은 `submit` 으로 올린다. 부르는 쪽이 열 때마다 `:key` 를 바꿔 처음 값으로 그린다.
    -->
    <Dialog :visible="visible" modal :draggable="false" :closable="!busy" :header="review ? t('groupBuyReview.editTitle') : t('groupBuyReview.writeTitle')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }" @update:visible="(v: boolean) => emit('update:visible', v)">
        <form :id="formId" class="flex flex-col gap-4" novalidate data-testid="group-buy-review-form" @submit.prevent="submit">
            <p class="m-0 rounded-lg border border-surface bg-surface-50 px-3 py-2 text-sm dark:bg-surface-800/60">
                <span class="font-medium">{{ skuName }}</span><span v-if="roundNo > 1" class="ml-1.5 text-xs text-muted-color">{{ t('groupBuyReview.roundBought', { n: roundNo }) }}</span>
            </p>

            <fieldset class="m-0 flex flex-col gap-1.5 border-0 p-0">
                <legend class="mb-1 text-sm font-medium">{{ t('groupBuyReview.ratingLabel') }}</legend>
                <Rating v-model="rating" :disabled="busy" :aria-invalid="!!ratingError" />
                <p v-if="ratingError" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ ratingError }}</p>
            </fieldset>

            <LabeledField :label="t('groupBuyReview.bodyLabel')" :error="bodyError">
                <template #aside>
                    <small class="text-xs tabular-nums" :class="bodyLength > MAX ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ bodyLength }} / {{ MAX }}</small>
                </template>
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="body" rows="5" auto-resize :maxlength="MAX" :placeholder="t('groupBuyReview.bodyPlaceholder')" :disabled="busy" :invalid="invalid" :aria-describedby="describedBy" fluid />
                </template>
            </LabeledField>
            <p class="-mt-2 m-0 flex items-start gap-1.5 text-xs text-orange-700 dark:text-orange-400"><i class="pi pi-shield mt-0.5 text-xs" aria-hidden="true" />{{ t('groupBuyReview.privacyHint') }}</p>

            <div class="flex flex-col gap-2">
                <span class="text-sm font-medium">{{ t('groupBuyReview.photosLabel', { max: PHOTO_MAX }) }}</span>
                <div class="flex flex-wrap gap-2">
                    <div v-for="(photo, i) in photos" :key="photo.id" class="relative size-20 overflow-hidden rounded-lg border border-surface">
                        <img :src="photo.url" :alt="t('groupBuyReview.photoAlt', { n: i + 1 })" class="size-full object-cover" />
                        <Button type="button" icon="pi pi-times" size="small" rounded severity="secondary" class="absolute! right-0.5 top-0.5 size-6!" :aria-label="t('groupBuyReview.photoRemove')" :disabled="busy" @click="removePhoto(photo.id)" />
                    </div>
                    <label v-if="photos.length < PHOTO_MAX" class="grid size-20 cursor-pointer place-items-center rounded-lg border border-dashed border-surface text-muted-color hover:border-primary focus-within:outline-2 focus-within:outline-primary">
                        <i v-if="!photoBusy" class="pi pi-camera" aria-hidden="true" />
                        <i v-else class="pi pi-spin pi-spinner" aria-hidden="true" />
                        <span class="sr-only">{{ t('groupBuyReview.photoAdd') }}</span>
                        <input type="file" accept="image/jpeg,image/png,image/webp" class="sr-only" :disabled="photoBusy || busy" @change="addPhoto" />
                    </label>
                </div>
                <p v-if="photoError || fieldErrors.photoIds" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ photoError || fieldErrors.photoIds }}</p>
            </div>

            <p class="m-0 flex items-start gap-1.5 text-xs text-muted-color"><i class="pi pi-history mt-0.5 text-xs" aria-hidden="true" />{{ t('groupBuyReview.editNote') }}</p>
            <Message v-if="error" severity="error" size="small" variant="simple" :closable="false" role="alert">{{ error }}</Message>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.cancel')" severity="secondary" variant="text" :disabled="busy" @click="emit('update:visible', false)" />
            <Button type="submit" :form="formId" :label="review ? t('groupBuyReview.saveEdit') : t('groupBuyReview.submit')" :loading="busy" :disabled="photoBusy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import type { GroupBuyReview, GroupBuyReviewPhoto, GroupBuyReviewRequest } from '~/composables/groupbuy/api/useGroupBuyReviewApi.types';
import UtilGroupBuyMessages from '~/utils/util-group-buy-messages';

interface Props {
    visible: boolean;
    /** 산 단품 이름(주문 줄 사본) */
    skuName: string;
    roundNo?: number;
    /** 고치기면 그 리뷰 */
    review?: GroupBuyReview | null;
    upload: (file: File) => Promise<GroupBuyReviewPhoto>;
    busy?: boolean;
    error?: string;
    fieldErrors?: Record<string, string>;
}

const props = withDefaults(defineProps<Props>(), { roundNo: 1, review: null, busy: false, error: '', fieldErrors: () => ({}) });
const emit = defineEmits<{ 'update:visible': [visible: boolean]; submit: [value: GroupBuyReviewRequest] }>();

const MAX = 1000;
const MIN = 10;
const PHOTO_MAX = 5;
const { t } = useI18n();
const formId = useId();

const rating = ref<number | null>(props.review?.rating ?? null);
const body = ref(props.review?.body ?? '');
const photos = ref<GroupBuyReviewPhoto[]>(props.review?.photos.map((p) => ({ ...p })) ?? []);
const tried = ref(false);
const bodyLength = computed(() => body.value.trim().length);

const ratingError = computed(() => props.fieldErrors.rating || (tried.value && !rating.value ? t('groupBuyReview.ratingRequired') : ''));
const bodyError = computed(() => props.fieldErrors.body || (tried.value && (bodyLength.value < MIN || bodyLength.value > MAX) ? t('groupBuyReview.bodyLength') : ''));

const photoBusy = ref(false);
const photoError = ref('');

async function addPhoto(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file || photos.value.length >= PHOTO_MAX) return;
    photoBusy.value = true;
    photoError.value = '';
    try {
        photos.value = [...photos.value, await props.upload(file)];
    } catch (e) {
        photoError.value = UtilGroupBuyMessages.error(t, e);
    } finally {
        photoBusy.value = false;
    }
}

function removePhoto(id: string): void {
    photos.value = photos.value.filter((p) => p.id !== id);
}

function submit(): void {
    tried.value = true;
    if (ratingError.value || bodyError.value || !rating.value) return;
    emit('submit', { rating: rating.value, body: body.value.trim(), photoIds: photos.value.map((p) => p.id) });
}
</script>
