<template>
    <!-- 제공사 · 모델 한 묶음(목록·대기함·기록·상세가 같이 쓴다) — 제공사 이름은 흐린 글자, 모델명은 고정폭. 공개 사이트 배지에 보이는 모델명과 같은 값이다 -->
    <span class="inline-flex min-w-0 items-center gap-1.5 text-[13px]">
        <span class="shrink-0 text-muted-color">{{ t(`aiMember.provider.${provider}`) }}</span>
        <code class="min-w-0 truncate rounded bg-surface-100 px-1.5 py-0.5 text-[12px] dark:bg-surface-800">{{ model }}</code>
    </span>
</template>

<script setup lang="ts">
import type { AiProvider } from '~/composables/aimember/api/useAiMemberAdminApi.types';

interface Props {
    provider: AiProvider;
    model: string;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
</script>
