<template>
    <div
        class="flex min-h-16 w-full items-center justify-center rounded-(--gadget-card-radius) border border-dashed border-hairline-strong px-4 py-3 text-center"
        :style="ratio ? { aspectRatio: ratio } : undefined"
        :aria-hidden="message ? undefined : 'true'"
        data-testid="ad-inquiry"
    >
        <p v-if="message" class="m-0 flex flex-wrap items-center justify-center gap-x-2 gap-y-1 text-sm text-muted-color">
            <PointShopTag tone="neutral">{{ t('ad.mark') }}</PointShopTag>
            <span>{{ text.trim() || t('ad.inquiryDefault') }}</span>
            <NuxtLink :to="UtilBusiness.CENTER_PATH" class="rounded font-semibold text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="ad-inquiry-link">
                {{ t('ad.inquiryLink') }} ›
            </NuxtLink>
        </p>
    </div>
</template>

<script setup lang="ts">
import UtilBusiness from '~/utils/util-business';

interface Props {
    /** 끄면 빈 틀만 */
    message?: boolean;
    /** 비우면 기본 문구 */
    text?: string;
    /** 한 줄 광고는 null */
    ratio?: string | null;
}

withDefaults(defineProps<Props>(), { message: true, text: '', ratio: null });

const { t } = useI18n();
</script>
