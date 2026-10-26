<template>
    <!--
        기능 제한 안내 — 막힌 기능(충전·공구 참여·포인트 사용)을 쓰려 할 때 그 자리에 보인다(commerce.md 12-3). 계정 정지 배너(`RestrictionBanner`)와 다르다 — 계정 전체가 아니라 기능 하나다.
        첫 줄 「공동구매 참여가 제한돼 있어요 · 10월 25일 14:00까지」(무기한이면 「해제될 때까지」), 둘째 줄 공개 사유, 셋째 줄 그래도 할 수 있는 것.
        값은 서버 오류(403 `MEMBER_RESTRICTED`)에서 꺼낸 것(`UtilFeatureRestriction.fromError`)을 받는다 — 운영 메모는 오지 않는다. 종류는 아이콘과 글자로 함께 구분한다.
    -->
    <div role="alert" class="flex items-start gap-3 rounded-xl border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-950 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-100" :data-restriction="restriction.type" data-testid="feature-restriction-notice">
        <i :class="UtilFeatureRestriction.icon(restriction.type)" class="mt-0.5 shrink-0" aria-hidden="true" />
        <div class="min-w-0 flex-1 leading-relaxed">
            <p class="m-0 font-semibold" data-testid="feature-restriction-title">{{ title }}</p>
            <p v-if="restriction.reason" class="m-0 mt-0.5 break-words">{{ t('restriction.reason', { reason: restriction.reason }) }}</p>
            <p class="m-0 mt-1 text-xs opacity-80">{{ t(`restriction.allowed_${restriction.type}`) }}</p>
        </div>
    </div>
</template>

<script setup lang="ts">
import type { FeatureRestriction } from '~/utils/util-feature-restriction';
import UtilFeatureRestriction from '~/utils/util-feature-restriction';
import UtilFeatureRestrictionMessages from '~/utils/util-feature-restriction-messages';

interface Props {
    restriction: FeatureRestriction;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const time = useDisplayTime();

const title = computed(() => UtilFeatureRestrictionMessages.title(t, props.restriction, time.value));
</script>
