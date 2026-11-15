<template>
    <!--
        매거진(`/tips`) 글 줄의 태그(post-tags-contract 「매거진」) — 앞에서 1~3개를 `#태그` 작은 글자로, 누르면 `/tips?tag=`(고른 게시판은 지킨다). 넘치면 한 줄에서 잘린다(말줄임).
        글 줄 전체가 글 링크라 링크 안에 넣을 수 없어 글 링크 바로 아래에 둔다(쓰는 가젯이 여백을 맞춘다). 태그가 없으면 아무것도 그리지 않는다.
    -->
    <ul v-if="shown.length" class="m-0 flex min-w-0 list-none gap-x-2 overflow-hidden whitespace-nowrap p-0 text-xs leading-snug" :aria-label="t('postTag.viewLabel')" data-testid="tips-post-tags">
        <li v-for="tag in shown" :key="tag" class="min-w-0 max-w-full truncate">
            <NuxtLink
                :to="{ path: UtilTips.PATH, query: UtilTips.build({ board, tag, page: 1 }) }"
                class="rounded text-muted-color hover:text-primary hover:underline focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary"
                :aria-label="t('tips.tagFilter', { name: tag })"
                data-testid="tips-post-tag"
            >
                #{{ tag }}
            </NuxtLink>
        </li>
    </ul>
</template>

<script setup lang="ts">
import UtilTips from '~/utils/util-tips';

interface Props {
    /** 글 태그 */
    tags?: string[];
    /** 지금 고른 게시판 slug(전체 = '') — 태그 주소에 그대로 둔다 */
    board?: string;
    /** 보일 최대 수 */
    max?: number;
}

const props = withDefaults(defineProps<Props>(), { tags: () => [], board: '', max: 3 });

const { t } = useI18n();

const shown = computed(() => props.tags.slice(0, Math.max(1, props.max)));
</script>
