<template>
    <!--
        떠 있는 글쓰기 단추(모양만) — 홈·게시판 목록(셸의 `WriteFab`)·가젯 페이지가 같이 쓴다. 보일지 말지·어디로 갈지는 쓰는 쪽이 정한다(사용자 결정 2026-11-05).
        펼쳐지는 하위 메뉴 없이 누르면 바로 `to` 로 간다. 스크롤해도 창 안에 그대로 보인다:
          · 넓은 화면(md 이상): 단추 가운데가 본문(`#main-content`) 오른쪽 끝 선 위, 창 세로 가운데(아래 구석은 찾아가기 멀다).
          · 좁은 화면: 하단 탭 바로 위 오른쪽 구석(탭 위로 약 10px, 창 오른쪽에서 12px) — 본문이 창 끝까지 닿아 선 위에 걸치면 절반이 창 밖으로 나간다.
        고정(`fixed`)은 바깥 상자가 맡는다 — PrimeVue 단추 자체는 `.p-button { position: relative }` 가 Tailwind 의 `fixed` 를 이긴다.
        본문 쪽 조상에 `transform` 이 생겨도 창 기준이 흔들리지 않게 Nuxt 의 `#teleports`(body 바로 아래)에 그린다.
    -->
    <Teleport to="#teleports">
        <div class="fixed flex bottom-[calc(4rem+env(safe-area-inset-bottom))] z-30 md:top-1/2 md:bottom-auto md:-translate-y-1/2" :style="{ right: `${right}px` }" data-testid="write-float">
            <Button type="button" icon="pi pi-pencil" rounded :aria-label="t('shell.write')" v-tooltip.left="t('shell.write')" class="size-11! shadow-lg" data-testid="write-float-button" @click="go" />
        </div>
    </Teleport>
</template>

<script setup lang="ts">
import type { RouteLocationRaw } from 'vue-router';

const props = defineProps<{ to: RouteLocationRaw }>();

const { t } = useI18n();

/** 단추 지름(px) — `size-11` */
const SIZE = 44;
/** 창 오른쪽 끝에서 최소한 띄울 거리(px) — 본문이 창 끝까지 닿을 때(모바일) */
const INSET = 12;
/** 창 오른쪽에서 단추 오른쪽 끝까지(px) — 단추 가운데가 본문 오른쪽 끝 선에 오도록. 그러면 창 밖으로 나가면 INSET */
const right = ref(INSET);

function place(): void {
    // `fixed` 의 right 는 세로 스크롤바를 뺀 폭(clientWidth) 기준이다 — innerWidth 로 재면 스크롤바만큼 어긋난다
    const width = document.documentElement.clientWidth;
    const main = document.getElementById('main-content');
    const edge = main ? main.getBoundingClientRect().right : width;
    right.value = Math.max(INSET, Math.round(width - edge - SIZE / 2));
}

// 창 폭·본문 폭(메뉴 접기·테마 바뀜)이 바뀌면 다시 맞춘다
let observer: ResizeObserver | null = null;
onMounted(() => {
    place();
    window.addEventListener('resize', place, { passive: true });
    const main = document.getElementById('main-content');
    if (main) {
        observer = new ResizeObserver(place);
        observer.observe(main);
    }
});
onBeforeUnmount(() => {
    window.removeEventListener('resize', place);
    observer?.disconnect();
});

function go(): void {
    void navigateTo(props.to);
}
</script>
