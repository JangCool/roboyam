<template>
    <!--
        설정 변경 이력 서랍 — 설정 화면 제목 옆 「변경 이력」 단추가 연다. 화면 본문 아래에 이력 구획을 두지 않고 오른쪽에서 나오는 서랍에 이력 표(`SettingHistoryTable`)를 담는다.
        서랍은 늘 오른쪽이다(화면 설정의 서랍 위치를 따르지 않는다 — 설정 화면의 보조 보기). 거르기(공급자 등)는 `filters` 칸, 표는 기본 칸에 넣는다.
    -->
    <Drawer v-model:visible="open" position="right" :header="title || t('settingHistory.title')" :style="{ width: '46rem', maxWidth: '100vw' }">
        <div class="flex flex-col gap-3">
            <div v-if="$slots.filters" class="flex flex-wrap items-center gap-2">
                <slot name="filters" />
            </div>
            <p v-if="hint" class="m-0 text-xs leading-relaxed text-muted-color">{{ hint }}</p>
            <slot />
        </div>
    </Drawer>
</template>

<script setup lang="ts">
/** `title` — 서랍 제목(없으면 「변경 이력」. 주소 DB 「적재 이력」처럼 다른 이력이면 넘긴다) */
defineProps<{ hint?: string; title?: string }>();

const open = defineModel<boolean>('visible', { required: true });
const { t } = useI18n();
</script>
