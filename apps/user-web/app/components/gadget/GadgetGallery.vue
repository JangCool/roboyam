<template>
    <!--
        갤러리 가젯(`GALLERY`) — 사진이 있는 글의 썸네일 격자(칸이 좁으면 2열, 넓으면 3열 — 템플릿이 `galleryColumns: 4` 를 고르면 4열). 서버가 사진이 있는 정상 공개 글만 보낸다.
        한 열(모바일) + 템플릿이 `mobile.galleryScroll` 을 고르면 가로로 미는 썸네일 줄(`gadget-scroll-row` — 한 칸 42%, 약 2.3장이 보여 더 있다는 걸 알 수 있다).
        사진 위에 GIF·영상 표시(글자 「GIF」/「영상」 — 색만으로 알리지 않는다)와 영상 재생 표시를 얹는다. 칸 전체가 글 링크다. 마우스 올림 효과(설정 `hover`)는 사진 카드라 제목 밑줄(`gadget-hover-photo`, `main.css`).
    -->
    <GadgetFrame :gadget="gadget" :empty="gadget.empty || items.length === 0" empty-icon="pi pi-images">
        <!--
            모바일 목록형(템플릿 `mobile.listRows`) — 최신글·아추! 줄과 같은 틀(사용자 2026-11-19 「영상·짤도 동일한 UI」): 줄마다 선 · 왼쪽 사진 칸(64 × 48, 4:3 — GIF·영상 표시는 사진 위 그대로) +
            제목 한 줄(살짝 굵게 500) + 정보 줄(게시판 · 시간 …… 추천). 갤러리는 사진 있는 글만 오지만 사진을 못 불러오면 옅은 첫 글자
        -->
        <ul v-if="listRows" class="m-0 list-none p-0" :aria-label="t('home.galleryLabel')" data-testid="home-gallery-rows">
            <li v-for="item in items" :key="item.postId" class="border-b border-surface last:border-b-0">
                <NuxtLink :to="`/posts/${item.postId}`" class="gadget-hover flex items-start gap-3 py-2.5 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-primary" data-testid="home-gallery-row">
                    <GadgetThumb :src="item.thumbnailUrl" ratio="4 / 3" :letter="letterOf(item)" class="w-16 shrink-0">
                        <span v-if="item.kind" class="pointer-events-none absolute left-1 top-1 inline-flex items-center rounded bg-surface-950/75 px-1 py-0.5 text-[0.5625rem] font-bold leading-none text-surface-0">
                            {{ item.kind === 'GIF' ? t('home.badgeGif') : t('home.badgeVideo') }}
                        </span>
                    </GadgetThumb>
                    <div class="min-w-0 flex-1">
                        <p class="m-0 line-clamp-1 break-words text-[0.9375rem] font-medium leading-snug">{{ item.title }}</p>
                        <p class="m-0 mt-1.5 flex items-center gap-x-1 text-xs text-muted-color">
                            <span v-if="item.board.name" class="min-w-0 truncate">{{ localizedName(item.board.name, item.board.nameEn) }}</span>
                            <span v-if="item.board.name" aria-hidden="true">·</span>
                            <PostListDate :iso="item.createdAt" :mode="display.dateDisplay" class="shrink-0" />
                            <PostRowStats :row="{ likeCount: item.likeCount, viewCount: 0 }" :views="false" class="ml-auto" />
                        </p>
                    </div>
                </NuxtLink>
            </li>
        </ul>
        <ul v-else :class="scroll ? 'gadget-scroll-row' : ['m-0 grid list-none grid-cols-2 gap-x-3 gap-y-3.5 p-0 pt-1', columnsClass]" :style="scroll ? ROW : undefined" :aria-label="label" data-testid="home-gallery">
            <li v-for="item in items" :key="item.postId" class="min-w-0" data-testid="home-gallery-item">
                <NuxtLink :to="`/posts/${item.postId}`" class="gadget-hover-photo block rounded-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary">
                    <GadgetThumb :src="item.thumbnailUrl" ratio="4 / 3" class="w-full">
                        <!-- 사진 위 표시 — 반투명 어두운 바탕 + 밝은 글자(사진 색과 무관하게 읽히게) -->
                        <span v-if="item.kind" class="pointer-events-none absolute left-1.5 top-1.5 inline-flex items-center rounded bg-surface-950/75 px-1.5 py-1 text-[0.6875rem] font-bold leading-none text-surface-0">
                            <span class="relative top-[-0.09em]">{{ item.kind === 'GIF' ? t('home.badgeGif') : t('home.badgeVideo') }}</span>
                        </span>
                        <span v-if="item.kind === 'VIDEO'" class="pointer-events-none absolute bottom-1.5 right-1.5 grid size-[1.375rem] place-items-center rounded-full bg-surface-950/70 text-surface-0" aria-hidden="true">
                            <i class="pi pi-play text-[0.625rem]" />
                        </span>
                    </GadgetThumb>
                    <span class="gadget-hover-title mt-1.5 block truncate text-[0.8125rem] font-medium">{{ item.title }}</span>
                    <span class="mt-px flex items-center gap-1 text-xs text-muted-color">
                        <span v-if="item.board.name" class="min-w-0 truncate font-semibold">{{ localizedName(item.board.name, item.board.nameEn) }}</span>
                        <span v-if="item.board.name" aria-hidden="true">·</span>
                        <span class="shrink-0">{{ t('board.likesShort', { n: item.likeCount }) }}</span>
                    </span>
                </NuxtLink>
            </li>
        </ul>
    </GadgetFrame>
</template>

<script setup lang="ts">
import type { Gadget } from '~/composables/gadget/api/useGadgetApi.types';
import type { GalleryItem } from '~/composables/gadget/api/useGadgetApi.types';
import UtilGadget from '~/utils/util-gadget';

interface Props {
    gadget: Gadget;
}

const props = withDefaults(defineProps<Props>(), {});

const { t } = useI18n();
const { localizedName } = useLocalizedName();
const items = computed(() => UtilGadget.galleryItems(props.gadget.data));
/** 넓은 칸의 열 수 — 템플릿이 고른 세부 모양(기본 3: 30rem 이상 3칸 · 4: 36rem 이상 한 줄 4칸). 좁으면 2칸 */
const options = useGadgetLookOptions();
const columnsClass = options.galleryColumns === 4 ? '@min-[36rem]:grid-cols-4' : '@min-[30rem]:grid-cols-3';

/** 가로로 미는 줄 — 한 열(모바일)이고 템플릿이 골랐을 때만(데스크톱·태블릿 격자는 위 열 격자 그대로) */
const stacked = useGadgetStacked();
const scroll = computed(() => stacked.value && options.mobile.galleryScroll);
const ROW = { '--row-item': '42%' };
/** 모바일 목록형(템플릿 `mobile.listRows`, 한 열일 때만) — 켜면 가로로 미는 줄 대신 최신글 같은 글 줄 */
const listRows = useGadgetListRows();
const { display } = useBoardDisplay();
/** 사진 없는 줄의 빈 썸네일 글자 — 게시판 이름 첫 글자 하나 */
function letterOf(item: GalleryItem): string {
    return (Array.from(localizedName(item.board.name, item.board.nameEn).trim())[0] ?? '?').toUpperCase();
}
const label = computed(() => (scroll.value ? t('home.scrollRowLabel', { name: props.gadget.title || t('home.galleryLabel') }) : t('home.galleryLabel')));
</script>
