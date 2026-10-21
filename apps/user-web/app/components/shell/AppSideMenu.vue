<template>
    <!-- 데스크톱 왼쪽 메뉴 — 항상 펼쳐져 있고 길면 안에서 스크롤한다 -->
    <aside class="sticky top-12 hidden max-h-[calc(100dvh-3rem)] w-44 shrink-0 self-start overflow-y-auto pb-6 md:block">
        <AppMenuList />
    </aside>
</template>
