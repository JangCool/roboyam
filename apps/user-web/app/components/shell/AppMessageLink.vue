<template>
    <!-- 데스크톱 헤더의 쪽지 아이콘 — 안 읽은 대화 수 배지 + 누르면 쪽지함(`/messages`). 모바일은 "내 정보"의 "쪽지함" 줄로 들어가므로 여기서는 숨긴다 (design 01-shell 6).
         링크의 이름에 안 읽은 대화 수가 들어가고("쪽지함, 안 읽은 대화 3개"), 숫자는 눈으로만 보는 것이라 aria-hidden 이다 -->
    <span class="max-md:hidden">
        <NuxtLink
            to="/messages"
            class="relative flex size-10 shrink-0 items-center justify-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
            :aria-label="label"
            data-testid="header-messages"
        >
            <!-- 배지는 40px 링크 모서리가 아니라 아이콘(18px) 모서리에 붙인다 (알림 종과 같은 모양) -->
            <span class="relative inline-flex">
                <i class="pi pi-envelope text-lg" aria-hidden="true" />
                <span
                    v-if="text"
                    class="absolute left-full top-0 grid min-w-4 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-red-600 px-1 text-[0.625rem] font-bold leading-4 text-white"
                    aria-hidden="true"
                    data-testid="header-messages-badge"
                >
                    {{ text }}
                </span>
            </span>
        </NuxtLink>
    </span>
</template>

<script setup lang="ts">
/** 안 읽은 대화 수는 `useDmBadge` 가 셸에서 주기로 조회해 둔다(`layouts/default.vue`) — 여기서는 보기만 한다 */
const { text, label } = useDmBadge();
</script>
