<template>
    <!--
        광고 자리가 비었는데 「빈 자리 유지」(가젯 꾸미기 `style.empty.keep`)일 때 — 자리를 그대로 두고 「광고 문의」 안내(문구 + 사업자 센터 링크)를 보인다.
        안내를 끄면(`message` 거짓) 같은 크기의 빈 점선 틀만. 배너는 같은 비율 틀(`ratio`)로 자리를 지킨다(좁은 띠 배너도 안내가 들어가게 최소 높이를 둔다).
        글 중간 광고는 늘 접으므로 쓰지 않는다.
    -->
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
    /** 안내를 보이는가(끄면 빈 틀만) */
    message?: boolean;
    /** 운영자가 적은 안내 문구(비우면 기본 문구) */
    text?: string;
    /** 배너 비율(`aspect-ratio`) — 한 줄 광고는 null */
    ratio?: string | null;
}

withDefaults(defineProps<Props>(), { message: true, text: '', ratio: null });

const { t } = useI18n();
</script>
