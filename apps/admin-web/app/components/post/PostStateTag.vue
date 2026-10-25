<template>
    <!--
        글 상태 알약 — 공개(초록)·숨김(주황)·삭제(빨강). `lines` 면 아래에 숨김 까닭·시각(기간 숨김은 풀리는 시각), 지운 시각, 삭제 예정, 원문 파기를 작은 줄로 붙인다.
        게시글 관리의 목록 줄과 글 서랍 머리가 같이 쓴다.
    -->
    <div class="flex min-w-0 flex-col items-start gap-0.5">
        <Tag :value="t(`post.state.${item.state}`)" :icon="UtilPost.stateIcon(item.state)" :severity="UtilPost.stateSeverity(item.state)" class="text-[11px]!" />
        <template v-if="lines">
            <span v-for="(line, i) in stateLines" :key="i" class="text-[11px] leading-snug text-muted-color">{{ line }}</span>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { AdminPostItem } from '~/composables/post/api/usePostApi.types';
import UtilPost from '~/utils/util-post';
import UtilPostMessages from '~/utils/util-post-messages';

interface Props {
    item: AdminPostItem;
    /** 알약 아래 작은 줄들을 붙인다 */
    lines?: boolean;
}

const props = withDefaults(defineProps<Props>(), { lines: false });

const { t } = useI18n();
const time = useDisplayTime();

const stateLines = computed(() => UtilPostMessages.stateLines(t, time.value, props.item));
</script>
