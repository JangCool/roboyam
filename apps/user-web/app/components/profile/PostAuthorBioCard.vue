<template>
    <!-- 글 보기 본문 아래 작성자 카드 — 회원 글이면 늘 그린다(사용자 2026-11-08): 공개 프로필은 자기소개(없으면 「자기소개가 없습니다」), 비공개 프로필은 「비공개 프로필입니다」 -->
    <section class="flex items-start gap-3 rounded-lg border border-surface bg-surface-50 px-3 py-3 dark:bg-surface-900/40" :aria-label="t('profile.authorCardLabel')" data-testid="post-author-bio">
        <ProfileBorderAvatar :initial="UtilPointShop.initial(author.name)" :src="avatarSrc" :still-src="avatarStillSrc" size="sm" :border-style="borderStyle" />
        <div class="min-w-0 flex-1">
            <div class="flex min-w-0 items-center justify-between gap-2">
                <PostAuthorLabel :author="author" strong class="min-w-0 text-sm" />
                <!-- 비공개 프로필은 공개 프로필 화면에서도 거의 보이는 것이 없어 링크를 숨긴다 -->
                <NuxtLink
                    v-if="author.memberId && !privateProfile"
                    :to="`/users/${author.memberId}`"
                    class="inline-flex shrink-0 items-center gap-0.5 rounded text-xs text-muted-color hover:text-color hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                    data-testid="post-author-bio-profile"
                >
                    {{ t('profile.authorCardProfile') }}<i class="pi pi-angle-right text-[0.625rem]" aria-hidden="true" />
                </NuxtLink>
            </div>
            <template v-if="bio && !privateProfile">
                <!-- 자기소개 — 줄바꿈 그대로, 3줄까지 보이고 넘치면 「더보기」로 펼친다 -->
                <p
                    :id="textId"
                    ref="text"
                    class="m-0 mt-1 whitespace-pre-line break-words text-sm leading-snug text-muted-color"
                    :class="expanded ? '' : 'line-clamp-3'"
                    data-testid="post-author-bio-text"
                    >{{ bio }}</p
                >
                <button
                    v-if="clamped || expanded"
                    type="button"
                    class="mt-1 rounded text-xs font-medium text-muted-color hover:text-color hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                    :aria-expanded="expanded"
                    :aria-controls="textId"
                    data-testid="post-author-bio-more"
                    @click="expanded = !expanded"
                >
                    {{ expanded ? t('profile.authorCardLess') : t('profile.authorCardMore') }}
                </button>
            </template>
            <p v-else class="m-0 mt-1 text-sm leading-snug text-muted-color" :data-testid="privateProfile ? 'post-author-bio-private' : 'post-author-bio-empty'">
                <i v-if="privateProfile" class="pi pi-lock mr-1 text-[0.6875rem]" aria-hidden="true" />{{ privateProfile ? t('profile.authorCardPrivate') : t('profile.authorCardBioEmpty') }}
            </p>
        </div>
    </section>
</template>

<script setup lang="ts">
import type { PostAuthor } from '~/composables/board/api/usePostApi.types';
import UtilPointShop from '~/utils/util-point-shop';

/**
 * 글 보기의 작성자 카드 — 본문·추천 줄 아래(차단으로 접힌 글은 함께 접힌다). `profile-privacy-contract.md`.
 * 회원 글이면 늘 그린다(비회원·탈퇴·숨긴 글·사업자 핫딜 글은 부모가 그리지 않는다). 닉네임은 링크 없이, 오른쪽 「프로필 보기」가 `/users/{id}` 로 간다(비공개 프로필은 숨김).
 * 자기소개는 줄바꿈 포함 최대 300자 — 3줄을 넘으면 접고 「더보기」.
 */
const props = defineProps<{
    author: PostAuthor;
    /** 공개 프로필의 자기소개 — 없으면 「자기소개가 없습니다」 */
    bio?: string | null;
    /** 작성자 프로필이 비공개 — 「비공개 프로필입니다」 */
    privateProfile?: boolean;
    /** 아바타 그림(내 것은 방금 고른 것) — 없으면 이니셜 */
    avatarSrc?: string | null;
    /** 움직이는 아바타의 정지 그림(움직임 줄이기) */
    avatarStillSrc?: string | null;
    /** 장착한 프로필 테두리 */
    borderStyle?: string | null;
}>();
const { t } = useI18n();
const textId = useId();
const text = ref<HTMLElement | null>(null);
const expanded = ref(false);
/** 3줄에서 잘렸는가 — 그릴 때·글이 바뀔 때·폭이 바뀔 때 잰다 */
const clamped = ref(false);

function measure() {
    const el = text.value;
    clamped.value = !!el && !expanded.value && el.scrollHeight > el.clientHeight + 1;
}

watch(
    () => props.bio,
    () => {
        expanded.value = false;
        void nextTick(measure);
    },
);
let observer: ResizeObserver | null = null;
onMounted(() => {
    measure();
    if (text.value && typeof ResizeObserver !== 'undefined') {
        observer = new ResizeObserver(measure);
        observer.observe(text.value);
    }
});
onBeforeUnmount(() => observer?.disconnect());
</script>
