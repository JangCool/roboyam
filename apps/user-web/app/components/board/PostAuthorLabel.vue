<template>
    <!-- 글·댓글 작성자 — 회원: 닉네임 + 등급 뱃지 · 비회원: 이름 + 「비회원」 (+ 글 보기·댓글에서는 작성 IP 앞 두 묶음).
         운영진 공식 뱃지는 그리지 않는다 — 서버가 작성자 `role` 을 회원이면 늘 `USER` 로 준다(운영자는 공개 사이트에 로그인하지 않고, 매니저·스텝 표시는 응답에 아직 없다) -->
    <span class="inline-flex min-w-0 items-center gap-1">
        <span class="truncate" :class="strong ? 'font-semibold text-color' : ''">{{ author.name }}</span>
        <GradeBadge v-if="author.kind === 'MEMBER' && author.grade" :grade="author.grade as Grade" :label="author.gradeLabel ?? ''" class="shrink-0" />
        <span v-if="guest" class="inline-flex shrink-0 items-center justify-center rounded border border-surface px-1.5 py-1 text-[0.6875rem] font-medium leading-none text-muted-color">
            <span class="relative top-[-0.09em]">{{ t('board.guestBadge') }}</span>
        </span>
        <!-- 비회원 작성 IP 앞 두 묶음 — 서버가 가려서 준다(IPv4 `123.45.*.*` · IPv6 `2001:db8:*:*`) -->
        <span v-if="guest && showIp && author.ipMasked" class="shrink-0 text-xs text-muted-color" :aria-label="t('board.guestIp', { ip: author.ipMasked })">({{ author.ipMasked }})</span>
    </span>
</template>

<script setup lang="ts">
import type { Grade } from '~/composables/auth/api/useAuthApi.types';
import type { PostAuthor } from '~/composables/board/api/usePostApi.types';

interface Props {
    author: PostAuthor;
    /** 비회원 IP 앞자리를 보인다(글 보기·댓글) */
    showIp?: boolean;
    /** 이름을 굵게(글 보기 머리) */
    strong?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
    showIp: false,
    strong: false,
});

const { t } = useI18n();
const guest = computed(() => props.author.kind === 'GUEST');
</script>
