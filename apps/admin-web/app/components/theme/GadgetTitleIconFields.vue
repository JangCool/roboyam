<template>
    <!--
        가젯 ⚙ 설정 「모양」의 「제목 아이콘」 구획(사용자 시안 확정 2026-11-10 — gadget-page-contract 「가젯 제목 아이콘」). 고치는 값은 꾸미기(`look` — 설정 창의 편집 중 값을 그대로 고친다):
          · 아이콘 보이기/숨기기(`iconShow` — 테마 기본: 모든 테마 보이기)
          · 고르는 곳(`iconSource`): PrimeIcons(`icon`) · Tabler Icons(`iconTabler` — 이름 + path 조각, 공개 화면은 고른 것만 싣는다) · 직접 올리기(`iconUpload` — SVG·PNG·WEBP 200KB, 정사각 권장)
          · 아이콘 색(`iconColor`): 글자색 그대로 / 색 정하기(프리셋 12색 · #hex). 올린 PNG·WEBP 는 칠할 수 없어 숨긴다(SVG 는 모양만 따서 칠한다)
          · 아이콘 바탕(`iconBg`): 없음 / 옅은 네모
        위 오른쪽 작은 미리보기는 지금 값의 아이콘 + 제목(색·바탕은 대략 — 실제 모양은 실제 화면에서).
    -->
    <section class="flex flex-col gap-3 border-t border-(--line) pt-4" :aria-labelledby="`${uid}-head`" data-testid="gadget-title-icon">
        <div class="flex flex-wrap items-center justify-between gap-2">
            <h3 :id="`${uid}-head`" class="m-0 text-sm font-semibold">{{ t('homeLayout.titleIcon') }}</h3>
            <span class="inline-flex max-w-64 items-center gap-1.5 rounded-md border border-(--line) px-2.5 py-1 text-sm font-bold" data-testid="gadget-title-icon-preview">
                <span class="sr-only">{{ t('homeLayout.titleIconPreview') }}</span>
                <span v-if="previewShown" class="inline-grid shrink-0 place-items-center" :class="look.iconBg === 'SOFT' ? 'size-7 rounded-lg text-[0.8125rem]' : 'text-sm'" :style="previewStyle">
                    <GadgetIconGlyph :prime="look.iconSource === 'PRIME' ? look.icon : null" :paths="look.iconSource === 'TABLER' ? (look.iconTabler?.paths ?? null) : null" :upload="look.iconSource === 'UPLOAD' ? look.iconUpload : null" />
                </span>
                <span class="min-w-0 truncate">{{ title.trim() || t('homeLayout.gadgetTitle') }}</span>
            </span>
        </div>

        <div class="flex flex-col gap-1">
            <div class="flex items-center gap-2">
                <ToggleSwitch v-model="look.iconShow" :input-id="`${uid}-show`" data-testid="gadget-icon-show" />
                <label :for="`${uid}-show`" class="cursor-pointer text-sm">{{ t('homeLayout.titleIconShow') }}</label>
            </div>
            <small class="text-xs text-muted-color">{{ t('homeLayout.titleIconShowHint') }}</small>
        </div>

        <template v-if="look.iconShow">
            <div class="flex flex-col gap-1">
                <span :id="`${uid}-src`" class="text-sm font-medium">{{ t('homeLayout.iconSource') }}</span>
                <SelectButton v-model="look.iconSource" :options="sourceOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-src`" data-testid="gadget-icon-source" />
            </div>

            <GadgetIconPicker v-if="look.iconSource === 'PRIME'" source="PRIME" :selected="look.icon" @pick-prime="(name) => (look.icon = name)" />
            <template v-else-if="look.iconSource === 'TABLER'">
                <GadgetIconPicker source="TABLER" :selected="look.iconTabler?.name ?? null" @pick-tabler="(icon) => (look.iconTabler = icon)" />
                <small class="text-xs text-muted-color">{{ t('homeLayout.tablerNote') }}</small>
            </template>
            <div v-else class="flex items-start gap-3" data-testid="gadget-icon-upload">
                <div class="grid size-12 shrink-0 place-items-center rounded-lg border border-(--line) text-2xl">
                    <GadgetIconGlyph v-if="look.iconUpload" :upload="look.iconUpload" />
                    <i v-else class="pi pi-image text-lg text-muted-color" aria-hidden="true" />
                </div>
                <div class="flex min-w-0 flex-col gap-1.5">
                    <div class="flex flex-wrap gap-2">
                        <Button type="button" :label="look.iconUpload ? t('homeLayout.iconUploadChange') : t('homeLayout.iconUpload')" icon="pi pi-upload" size="small" variant="outlined" :loading="uploading" @click="fileInput?.click()" />
                        <Button v-if="look.iconUpload" type="button" :label="t('homeLayout.iconUploadRemove')" icon="pi pi-times" size="small" severity="danger" variant="text" :disabled="uploading" @click="look.iconUpload = null" />
                    </div>
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('homeLayout.iconUploadHint', { max: UtilGadgetStyle.ICON_UPLOAD_MAX_KB }) }}</p>
                    <p v-if="uploadError" role="alert" class="m-0 text-xs text-red-600 dark:text-red-400">{{ uploadError }}</p>
                    <input ref="fileInput" type="file" accept=".svg,.png,.webp,image/svg+xml,image/png,image/webp" class="hidden" tabindex="-1" :aria-label="t('homeLayout.iconUpload')" @change="onFile" />
                </div>
            </div>

            <div v-if="colorable" class="flex flex-col gap-1">
                <span :id="`${uid}-color`" class="text-sm font-medium">{{ t('homeLayout.iconColor') }}</span>
                <GadgetColorField v-model="look.iconColor" :auto-label="t('homeLayout.iconColorText')" :aria-labelledby="`${uid}-color`" />
            </div>
            <small v-else class="text-xs text-muted-color">{{ t('homeLayout.iconColorRaster') }}</small>

            <div class="flex flex-col gap-1">
                <span :id="`${uid}-bg`" class="text-sm font-medium">{{ t('homeLayout.iconBg') }}</span>
                <SelectButton v-model="look.iconBg" :options="bgOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-labelledby="`${uid}-bg`" data-testid="gadget-icon-bg" />
            </div>
        </template>
    </section>
</template>

<script setup lang="ts">
import type { BoardColor } from '~/composables/board/api/useBoardApi.types';
import type { GadgetStyle } from '~/composables/home/api/useHomeLayoutApi.types';
import { ApiError } from '~/composables/common/useApiClient';
import UtilBoard from '~/utils/util-board';
import UtilGadgetStyle from '~/utils/util-gadget-style';

interface Props {
    /** 편집 중인 꾸미기 — 이 구획이 아이콘 칸을 바로 고친다(설정 창의 [적용]이 저장할 값으로 넘긴다) */
    look: GadgetStyle;
    /** 미리보기에 보일 제목 */
    title: string;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const uid = useId();
const api = useHomeLayoutApi();

/** 편집 중 값(설정 창의 반응형 객체를 그대로 고친다) */
const look = computed(() => props.look);

const sourceOptions = computed(() => UtilGadgetStyle.ICON_SOURCES.map((value) => ({ value, label: t(`homeLayout.iconSource_${value}`) })));
const bgOptions = computed(() => UtilGadgetStyle.ICON_BGS.map((value) => ({ value, label: t(`homeLayout.iconBg_${value}`) })));

/** 색을 칠할 수 있는가 — 올린 PNG·WEBP 만 못 칠한다 */
const colorable = computed(() => !(look.value.iconSource === 'UPLOAD' && look.value.iconUpload && !look.value.iconUpload.svg));

/** 미리보기에 아이콘이 있는가 — 고른 곳의 값이 있을 때 */
const previewShown = computed(() => {
    const s = look.value;
    if (!s.iconShow) return false;
    if (s.iconSource === 'TABLER') return s.iconTabler !== null;
    if (s.iconSource === 'UPLOAD') return s.iconUpload !== null;
    return s.icon !== null;
});

/** 미리보기 색 — 고른 아이콘 색 → 제목 색 → 주 색상(옅은 네모는 그 색 12% 바탕). 프리셋 색은 게시판 표시색과 같은 팔레트 값 */
const previewStyle = computed(() => {
    const s = look.value;
    const pick = s.iconColor ?? s.titleColor;
    const color = (pick ? (UtilGadgetStyle.isHex(pick) ? pick : UtilBoard.colorVar(pick as BoardColor)) : null) ?? 'var(--p-primary-color)';
    return s.iconBg === 'SOFT' ? { color, backgroundColor: `color-mix(in srgb, ${color} 12%, transparent)` } : { color };
});

/* ── 직접 올리기 ── */

const fileInput = ref<HTMLInputElement | null>(null);
const uploading = ref(false);
const uploadError = ref('');

async function onFile(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    // 같은 파일을 다시 고를 수 있게 비운다
    input.value = '';
    if (!file) return;
    uploadError.value = '';
    if (file.size > UtilGadgetStyle.ICON_UPLOAD_MAX_KB * 1024) {
        uploadError.value = t('homeLayout.iconUploadTooBig', { max: UtilGadgetStyle.ICON_UPLOAD_MAX_KB });
        return;
    }
    uploading.value = true;
    try {
        look.value.iconUpload = await api.uploadIcon(file);
    } catch (e) {
        uploadError.value = e instanceof ApiError ? (e.fields?.file ?? e.message) : t('homeLayout.iconUploadFailed');
    } finally {
        uploading.value = false;
    }
}
</script>
