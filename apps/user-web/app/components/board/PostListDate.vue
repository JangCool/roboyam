<template>
    <!-- 목록의 날짜 — 관리자 기본 설정의 날짜 표시 방식(표준형·유동형)을 따른다. 마우스를 올리면 정확한 시각 -->
    <time :datetime="iso" :title="time.full(iso)">{{ label }}</time>
</template>

<script setup lang="ts">
import type { BoardDateDisplay } from '~/composables/board/api/useBoardApi.types';
import UtilPostMessages from '~/utils/util-post-messages';

interface Props {
    iso: string;
    mode: BoardDateDisplay;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const time = useDisplayTime();
const label = computed(() => UtilPostMessages.listDate(t, time.value, props.iso, props.mode, Date.now()));
</script>
