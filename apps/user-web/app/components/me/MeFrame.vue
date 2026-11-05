<template>
    <!--
        내 정보 묶음의 틀 — 넓은 화면(lg 이상)은 왼쪽 화면 + 오른쪽 위 메뉴 열(`MeMenu`). 셸이 이 틀을 화면 사이에 그대로 두어 메뉴는 남고 왼쪽 화면만 바뀐다.
        왼쪽 화면은 폭을 다 쓴다 — 각 화면이 혼자 열릴 때 쓰는 가운데 정렬·최대 폭(`mx-auto max-w-*`)을 이 틀 안에서는 푼다.
        좁은 화면은 화면만 그린다(메뉴는 `/me` 목록 · 하단 탭이 맡는다).
    -->
    <div class="lg:grid lg:grid-cols-[minmax(0,1fr)_17.5rem] lg:items-start lg:gap-6" data-testid="me-frame">
        <div class="min-w-0 lg:*:mx-0 lg:*:max-w-none">
            <slot />
        </div>
        <aside class="max-lg:hidden lg:sticky lg:top-16">
            <MeMenu />
        </aside>
    </div>
</template>
