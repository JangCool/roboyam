<template>
    <!--
        팁·강좌·사용기 「태그」 가젯(`TIPS_TAGS` — `/tips` 배치에만, tips-contract 「/tips 레이아웃 편집」). 태그는 묶음 게시판에서 최근 많이 달린 위 10개(서버 실제 값 — post-tags-contract).
        칩을 누르면 `/tips?tag=`(최신 글 목록을 그 태그로 거른다 — 고른 칩은 채운 모양 · `aria-current`). 태그가 없으면 빈 가젯(꾸미기 「비었을 때」를 따른다 — 처음 꾸미기는 영역 유지 + 안내).
        데이터는 화면이 나눠 주는 `useTipsShared`(머리 몫) — `/tips` 밖이면 그리지 않는다.
    -->
    <GadgetFrame v-if="tips" :gadget="gadget" :empty="empty" empty-icon="pi pi-hashtag">
        <div class="min-w-0" :aria-busy="tips.overviewStatus.value === 'loading'" data-testid="tips-tags">
            <div v-if="tips.overviewStatus.value === 'loading'" class="flex flex-wrap gap-2" aria-hidden="true">
                <i v-for="n in 6" :key="n" class="h-7 w-16 animate-pulse rounded-full bg-surface-200 dark:bg-surface-800" />
            </div>
            <ul v-else class="m-0 flex list-none flex-wrap gap-2 p-0">
                <li v-for="tag in tags" :key="tag.name">
                    <NuxtLink
                        :to="tips.to({ board: tips.board.value, tag: tag.name, page: 1 })"
                        class="inline-flex min-h-7 items-center justify-center rounded-full border px-3 text-xs leading-none focus-visible:outline-2 focus-visible:outline-offset-1 focus-visible:outline-primary"
                        :class="active(tag.name) ? 'border-primary bg-primary/10 font-semibold text-primary' : 'border-hairline-strong text-color hover:border-(--p-text-color)'"
                        :aria-label="t('tips.tagFilter', { name: tag.name })"
                        :aria-current="active(tag.name) ? 'true' : undefined"
                        data-testid="tips-tag"
                    >
                        <span class="relative top-[-0.09em]">#{{ tag.name }}</span>
                    </NuxtLink>
                </li>
            </ul>
        </div>
    </GadgetFrame>
</template>

<script setup lang="ts">
import type { Gadget } from '~/composables/gadget/api/useGadgetApi.types';
import UtilPostTag from '~/utils/util-post-tag';

interface Props {
    gadget: Gadget;
}

withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const tips = useTipsShared();

const tags = computed(() => tips?.tags.value ?? []);
/** 지금 거르는 태그인가 */
function active(name: string): boolean {
    const now = tips?.state.value.tag ?? '';
    return now !== '' && UtilPostTag.key(now) === UtilPostTag.key(name);
}
/** 다 받았는데 태그가 없거나 머리 몫을 못 받았으면 빈 가젯 */
const empty = computed(() => Boolean(tips) && tips!.overviewStatus.value !== 'loading' && tags.value.length === 0);
</script>
