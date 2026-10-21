<template>
    <!-- 데스크톱 헤더의 알림 종 — 안 읽은 숫자 배지 + 누르면 알림 패널(드롭다운). 모바일은 하단 탭의 "알림"이 같은 일을 하므로 여기서는 숨긴다.
         버튼의 이름에 안 읽은 수가 들어가고("알림, 안 읽은 알림 3개"), 점·숫자는 눈으로만 보는 것이라 aria-hidden 이다 -->
    <span class="max-md:hidden">
        <button
            type="button"
            class="relative flex size-10 shrink-0 items-center justify-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary dark:hover:bg-surface-800"
            aria-haspopup="dialog"
            :aria-expanded="open"
            :aria-label="label"
            data-testid="notification-bell"
            @click="toggle"
        >
            <!-- 배지는 40px 버튼 모서리가 아니라 아이콘(18px) 모서리에 붙인다 — 버튼 모서리에 두면 아이콘에서 멀어져 떠 보인다 -->
            <span class="relative inline-flex">
                <i class="pi pi-bell text-lg" aria-hidden="true" />
                <span
                    v-if="text"
                    class="absolute left-full top-0 grid min-w-4 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-red-600 px-1 text-[0.625rem] font-bold leading-4 text-white"
                    aria-hidden="true"
                    data-testid="notification-bell-badge"
                >
                    {{ text }}
                </span>
            </span>
        </button>

        <!-- 바깥 누르기·Esc 로 닫히고, 열릴 때 안쪽 패널이 만들어지며 목록을 새로 받는다 -->
        <Popover ref="popover" :aria-label="t('notification.title')"class="w-[24rem] max-w-[calc(100vw-1rem)]" :pt="{ content: { class: 'p-0!' } }" @show="open = true" @hide="open = false">
            <NotificationPanel @close="close" />
        </Popover>
    </span>
</template>

<script setup lang="ts">
/** 팝오버의 열고 닫기 — PrimeVue `Popover` 가 바깥 클릭·Esc·초점 가두기를 맡는다 */
interface PopoverHandle {
    toggle: (event: Event) => void;
    hide: () => void;
}

const { t } = useI18n();
const route = useRoute();
const { text, label } = useNotificationBadge();

const popover = ref<PopoverHandle | null>(null);
const open = ref(false);

function toggle(event: Event) {
    popover.value?.toggle(event);
}

function close() {
    popover.value?.hide();
}

// 다른 화면으로 가면 닫는다(알림을 눌러 글로 갈 때 포함)
watch(
    () => route.fullPath,
    () => close(),
);
</script>
