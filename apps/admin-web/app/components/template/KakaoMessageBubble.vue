<template>
    <!--
        카카오톡 알림톡 말풍선 미리보기 — 채널(발신 프로필) 이름 · 노란 머리 띠(「알림톡 도착」) · 강조 제목 · 본문(줄바꿈 그대로) · 버튼(회색 칸, 위에서 아래로).
        카카오톡 템플릿 편집(`/settings/notification-templates/kakao/[id]` — 대응표의 견본 값)과 문구 편집(`/settings/notification-messages/[id]` — 그 문구 대상의 견본 값)이 같이 쓴다.
        본문은 `UtilKakaoTemplate.fill` 이 채운 평문이다(글자로만 보인다). 서버 호출은 없다. 준비 중 기능이라 흐리게 보이게 하는 것은 부르는 쪽이 정한다.
    -->
    <div class="flex flex-col gap-2">
        <div class="rounded-xl bg-[#b2c7da] p-3 dark:bg-[#2b3440]">
            <div class="flex gap-2">
                <span class="grid size-9 shrink-0 place-items-center rounded-[14px] bg-[#fee500] text-sm font-bold text-[#3c1e1e]" aria-hidden="true">{{ channelName.slice(0, 1) }}</span>
                <div class="min-w-0 max-w-[17rem] flex-1">
                    <p class="m-0 text-[12px] text-[#1f2933] dark:text-[#d5dbe1]">{{ channelName }}</p>
                    <div class="mt-1 overflow-hidden rounded-lg bg-white text-[#191919] shadow-sm">
                        <div class="bg-[#fee500] px-3 py-2 text-[12px] font-semibold">{{ t('notificationTemplate.kakaoArrived') }}</div>
                        <div class="flex flex-col gap-2 px-3 py-2.5">
                            <p v-if="emphasis" class="m-0 text-[15px] font-bold">{{ emphasis }}</p>
                            <p class="m-0 whitespace-pre-line break-words text-[13px] leading-relaxed">{{ body }}</p>
                            <div v-if="buttons.length" class="flex flex-col gap-1.5">
                                <span v-for="name in buttons" :key="name" class="inline-flex items-center justify-center rounded bg-[#f2f2f2] px-3 py-2 text-[13px] leading-none">
                                    <span class="kakao-bubble__label">{{ name }}</span>
                                </span>
                            </div>
                        </div>
                    </div>
                    <p class="m-0 mt-1 text-[10px] text-[#3d4b59] dark:text-[#aab4be]">{{ t('notificationTemplate.chatNow') }}</p>
                </div>
            </div>
        </div>
        <p v-if="note" class="m-0 text-xs text-muted-color">{{ note }}</p>
    </div>
</template>

<script setup lang="ts">
interface Props {
    /** 발신 프로필(카카오톡 채널) 이름 */
    channelName: string;
    /** 강조 제목(없으면 빈 글자) */
    emphasis?: string;
    /** 채운 본문(평문) */
    body: string;
    /** 버튼 이름들 */
    buttons?: string[];
    /** 말풍선 아래 안내 한 줄 */
    note?: string;
}

withDefaults(defineProps<Props>(), { emphasis: '', buttons: () => [], note: '' });

const { t } = useI18n();
</script>

<style scoped>
/* 버튼 글자 상하 중앙 + 한글 글꼴 보정(components.md 2-4) */
.kakao-bubble__label {
    position: relative;
    top: -0.09em;
}
</style>
