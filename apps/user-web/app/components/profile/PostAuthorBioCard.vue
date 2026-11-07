<template>
    <!-- 글 보기 본문 아래 작성자 한 줄 소개 — 프로필이 공개인 회원이 소개를 썼을 때만 그린다(부모가 `bio` 로 가른다) -->
    <section class="flex items-start gap-3 rounded-lg border border-surface bg-surface-50 px-3 py-3 dark:bg-surface-900/40" :aria-label="t('profile.authorCardLabel')" data-testid="post-author-bio">
        <ProfileBorderAvatar :initial="UtilPointShop.initial(author.name)" :src="avatarSrc" :still-src="avatarStillSrc" size="sm" :border-style="borderStyle" />
        <div class="min-w-0 flex-1">
            <div class="flex min-w-0 items-center justify-between gap-2">
                <PostAuthorLabel :author="author" strong class="min-w-0 text-sm" />
                <NuxtLink
                    v-if="author.memberId"
                    :to="`/users/${author.memberId}`"
                    class="inline-flex shrink-0 items-center gap-0.5 rounded text-xs text-muted-color hover:text-color hover:underline focus-visible:outline-2 focus-visible:outline-primary"
                    data-testid="post-author-bio-profile"
                >
                    {{ t('profile.authorCardProfile') }}<i class="pi pi-angle-right text-[0.625rem]" aria-hidden="true" />
                </NuxtLink>
            </div>
            <p class="m-0 mt-1 break-words text-sm leading-snug text-muted-color" data-testid="post-author-bio-text">{{ bio }}</p>
        </div>
    </section>
</template>

<script setup lang="ts">
import type { PostAuthor } from '~/composables/board/api/usePostApi.types';
import UtilPointShop from '~/utils/util-point-shop';

/**
 * 글 보기의 작성자 한 줄 소개 카드 — 본문·추천 줄 아래(차단으로 접힌 글은 함께 접힌다). `profile-privacy-contract.md`.
 * 프로필을 공개한 회원의 소개만 온다(비공개·소개 없음·비회원·탈퇴는 부모가 그리지 않는다). 닉네임은 링크 없이, 오른쪽 「프로필 보기」가 `/users/{id}` 로 간다.
 */
defineProps<{
    author: PostAuthor;
    bio: string;
    /** 아바타 그림(내 것은 방금 고른 것) — 없으면 이니셜 */
    avatarSrc?: string | null;
    /** 움직이는 아바타의 정지 그림(움직임 줄이기) */
    avatarStillSrc?: string | null;
    /** 장착한 프로필 테두리 */
    borderStyle?: string | null;
}>();
const { t } = useI18n();
</script>
