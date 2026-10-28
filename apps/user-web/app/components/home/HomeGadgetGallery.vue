<template>
    <!--
        갤러리 가젯(`GALLERY`) — 사진이 있는 글의 썸네일 격자(칸이 좁으면 2열, 넓으면 3열 — 템플릿이 `galleryColumns: 4` 를 고르면 4열). 서버가 사진이 있는 정상 공개 글만 보낸다.
        한 열(모바일) + 템플릿이 `mobile.galleryScroll` 을 고르면 가로로 미는 썸네일 줄(`gadget-scroll-row` — 한 칸 42%, 약 2.3장이 보여 더 있다는 걸 알 수 있다).
        사진 위에 GIF·영상 표시(글자 「GIF」/「영상」 — 색만으로 알리지 않는다)와 영상 재생 표시를 얹는다. 칸 전체가 글 링크다.
    -->
    <HomeGadgetFrame :gadget="gadget" :empty="gadget.empty || items.length === 0" empty-icon="pi pi-images">
        <ul :class="scroll ? 'gadget-scroll-row' : ['m-0 grid list-none grid-cols-2 gap-x-3 gap-y-3.5 p-0 pt-1', columnsClass]" :style="scroll ? ROW : undefined" :aria-label="label" data-testid="home-gallery">
            <li v-for="item in items" :key="item.postId" class="min-w-0" data-testid="home-gallery-item">
                <NuxtLink :to="`/posts/${item.postId}`" class="block rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
                    <HomeThumb :src="item.thumbnailUrl" ratio="4 / 3" class="w-full">
                        <!-- 사진 위 표시 — 반투명 어두운 바탕 + 밝은 글자(사진 색과 무관하게 읽히게) -->
                        <span v-if="item.kind" class="pointer-events-none absolute left-1.5 top-1.5 inline-flex items-center rounded bg-surface-950/75 px-1.5 py-1 text-[0.6875rem] font-bold leading-none text-surface-0">
                            <span class="relative top-[-0.09em]">{{ item.kind === 'GIF' ? t('home.badgeGif') : t('home.badgeVideo') }}</span>
                        </span>
                        <span v-if="item.kind === 'VIDEO'" class="pointer-events-none absolute bottom-1.5 right-1.5 grid size-[1.375rem] place-items-center rounded-full bg-surface-950/70 text-surface-0" aria-hidden="true">
                            <i class="pi pi-play text-[0.625rem]" />
                        </span>
                    </HomeThumb>
                    <span class="mt-1.5 block truncate text-[0.8125rem] font-medium">{{ item.title }}</span>
                    <span class="mt-px flex items-center gap-1 text-xs text-muted-color">
                        <span v-if="item.board.name" class="min-w-0 truncate font-semibold">{{ localizedName(item.board.name, item.board.nameEn) }}</span>
                        <span v-if="item.board.name" aria-hidden="true">·</span>
                        <span class="shrink-0">{{ t('board.likesShort', { n: item.likeCount }) }}</span>
                    </span>
                </NuxtLink>
            </li>
        </ul>
    </HomeGadgetFrame>
</template>

<script setup lang="ts">
import type { HomeGadget } from '~/composables/home/api/useHomeGadgetApi.types';
import UtilHomeGadget from '~/utils/util-home-gadget';

interface Props {
    gadget: HomeGadget;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const items = computed(() => UtilHomeGadget.galleryItems(props.gadget.data));
/** 넓은 칸의 열 수 — 템플릿이 고른 세부 모양(기본 3: 30rem 이상 3칸 · 4: 36rem 이상 한 줄 4칸). 좁으면 2칸 */
const options = useHomeGadgetLookOptions();
const columnsClass = options.galleryColumns === 4 ? '@min-[36rem]:grid-cols-4' : '@min-[30rem]:grid-cols-3';

/** 가로로 미는 줄 — 한 열(모바일)이고 템플릿이 골랐을 때만(데스크톱·태블릿 격자는 위 열 격자 그대로) */
const stacked = useHomeGadgetStacked();
const scroll = computed(() => stacked.value && options.mobile.galleryScroll);
const ROW = { '--row-item': '42%' };
const label = computed(() => (scroll.value ? t('home.scrollRowLabel', { name: props.gadget.title || t('home.galleryLabel') }) : t('home.galleryLabel')));
</script>
