<template>
    <!--
        공구 대표 이미지 편집 — 최대 8장, 첫 장이 대표(목록 썸네일), 순서 바꾸기(앞으로·뒤로), 빼기, 더하기(여러 장 고르기). 계약 결정 2.
        올리기는 부르는 쪽이 준 `upload`(API 컴포저블로 만든 함수)로 한다 — 이 부품은 서버를 모른다(`RichEditor` 의 `upload` 와 같은 방식).
        JPEG·PNG·WebP 5MB 는 서버가 판정하고(다시 인코딩·EXIF 제거·긴 변 1600px), 실패 문구는 서버 문구 그대로 아래에 한 줄씩 보인다.
    -->
    <div class="flex flex-col gap-2">
        <ul class="m-0 grid list-none grid-cols-[repeat(auto-fill,minmax(7.5rem,1fr))] gap-2 p-0" :aria-label="label">
            <li v-for="(image, index) in model" :key="image.mediaId" class="group relative flex flex-col overflow-hidden rounded-lg border border-(--line)">
                <img :src="image.thumbUrl" :alt="t('groupBuy.imageAlt', { n: index + 1 })" class="aspect-square w-full object-cover" />
                <span v-if="index === 0" class="absolute top-1 left-1 inline-flex items-center rounded bg-primary px-1.5 py-0.5 text-[10px] leading-none font-medium text-primary-contrast">{{ t('groupBuy.imageMain') }}</span>
                <div class="flex items-center justify-between gap-1 border-t border-(--line) px-1 py-0.5">
                    <span class="text-[11px] tabular-nums text-muted-color">{{ index + 1 }}</span>
                    <span class="flex items-center">
                        <Button type="button" icon="pi pi-angle-left" size="small" severity="secondary" variant="text" class="size-7!" :aria-label="t('groupBuy.imageMoveUp')" :disabled="disabled || index === 0" @click="move(index, -1)" />
                        <Button type="button" icon="pi pi-angle-right" size="small" severity="secondary" variant="text" class="size-7!" :aria-label="t('groupBuy.imageMoveDown')" :disabled="disabled || index === model.length - 1" @click="move(index, 1)" />
                        <Button type="button" icon="pi pi-times" size="small" severity="danger" variant="text" class="size-7!" :aria-label="t('groupBuy.imageRemove')" :disabled="disabled" @click="remove(index)" />
                    </span>
                </div>
            </li>
            <li v-if="model.length < UtilGroupBuy.MAX_IMAGES && !disabled">
                <label class="flex aspect-square cursor-pointer flex-col items-center justify-center gap-1 rounded-lg border border-dashed border-(--line) text-muted-color hover:border-primary hover:text-primary">
                    <i :class="uploading > 0 ? 'pi pi-spin pi-spinner' : 'pi pi-plus'" aria-hidden="true" />
                    <span class="text-xs">{{ uploading > 0 ? t('groupBuy.imageUploading', { n: uploading }) : t('groupBuy.imageAdd') }}</span>
                    <input type="file" class="sr-only" accept="image/jpeg,image/png,image/webp" multiple :disabled="uploading > 0" @change="onPick" />
                </label>
            </li>
        </ul>
        <p class="m-0 text-xs text-muted-color">{{ t('groupBuy.imageHelp', { max: UtilGroupBuy.MAX_IMAGES, count: model.length }) }}</p>
        <p v-for="(message, i) in errors" :key="i" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ message }}</p>
    </div>
</template>

<script setup lang="ts">
import type { GroupBuyImage } from '~/composables/groupbuy/api/useGroupBuyAdminApi.types';
import UtilGroupBuy from '~/utils/util-groupbuy';

const model = defineModel<GroupBuyImage[]>({ required: true });

interface Props {
    /** 목록 이름(스크린리더) */
    label: string;
    /** 한 장을 올리는 함수 — 실패는 `ApiError` 를 던진다 */
    upload: (file: File) => Promise<GroupBuyImage>;
    disabled?: boolean;
}

const props = withDefaults(defineProps<Props>(), { disabled: false });

const { t } = useI18n();
const toApiError = useApiErrorOf();
const uploading = ref(0);
const errors = ref<string[]>([]);

function move(index: number, delta: number): void {
    const next = [...model.value];
    const [item] = next.splice(index, 1);
    if (!item) return;
    next.splice(index + delta, 0, item);
    model.value = next;
}

function remove(index: number): void {
    model.value = model.value.filter((_, i) => i !== index);
}

async function onPick(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []);
    input.value = '';
    errors.value = [];
    const room = UtilGroupBuy.MAX_IMAGES - model.value.length;
    if (files.length > room) errors.value.push(t('groupBuy.imageTooMany', { max: UtilGroupBuy.MAX_IMAGES }));
    for (const file of files.slice(0, room)) {
        uploading.value += 1;
        try {
            const image = await props.upload(file);
            if (model.value.length < UtilGroupBuy.MAX_IMAGES) model.value = [...model.value, image];
        } catch (e) {
            const error = toApiError(e);
            errors.value.push(`${file.name}: ${error.field('file') ?? error.message}`);
        } finally {
            uploading.value -= 1;
        }
    }
}
</script>
