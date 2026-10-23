<template>
    <!--
        Slack · Mattermost 메시지 카드 미리보기 — 채널에 올라가는 첨부(attachment) 모양: 봇 이름 줄 · 왼쪽 색 막대 · (심각도 그림 +) 굵은 제목 · 본문 · 꼬리말 · 「열기」 버튼.
        두 앱의 모양을 가른다 — Slack: 네모난 봇 그림 + 「앱」 딱지, 둥근 막대 위 첨부, 흰 버튼(테두리). Mattermost: 둥근 봇 그림 + 「BOT」 딱지, 테두리 있는 첨부 상자 + 왼쪽 막대, 파란 글자 버튼.
        템플릿 편집(`/settings/notification-templates/{slack|mattermost}/[id]` — 견본 문구)과 문구 편집(`/settings/notification-messages/[id]` — 고른 템플릿에 그 문구를 씌운 것)이 같이 쓴다.
        값은 `UtilChatTemplate.card` 가 만든다(`bodyHtml` 은 값을 가린 뒤 표기법만 살린 태그뿐이라 `v-html` 로 보인다). 서버 호출은 없다.
    -->
    <div class="flex flex-col gap-2">
        <div class="chat-card rounded-lg border border-(--line) bg-white p-3 text-[#1d1c1d] dark:bg-[#1a1d21] dark:text-[#d1d2d3]" :class="card.tool === 'SLACK' ? 'chat-card--slack' : 'chat-card--mattermost'">
            <div class="flex gap-2.5">
                <span class="grid size-9 shrink-0 place-items-center text-sm font-bold text-white" :class="card.tool === 'SLACK' ? 'rounded-md bg-[#4a154b]' : 'rounded-full bg-[#1e325c]'" aria-hidden="true">{{ botInitial }}</span>
                <div class="min-w-0 flex-1">
                    <p class="m-0 flex flex-wrap items-center gap-1.5 text-[13px]">
                        <span class="font-bold">{{ botName }}</span>
                        <span class="chat-card__badge inline-flex items-center justify-center rounded px-1 py-0.5 text-[9px] font-semibold leading-none" :class="card.tool === 'SLACK' ? 'bg-black/10 dark:bg-white/15' : 'bg-[#1c58d9]/15 text-[#1c58d9] dark:text-[#5d89ea]'">
                            <span class="chat-card__badge-label">{{ card.tool === 'SLACK' ? t('notificationTemplate.chatAppBadge') : 'BOT' }}</span>
                        </span>
                        <span class="text-[11px] text-[#616061] dark:text-[#ababad]">{{ timeLabel }}</span>
                    </p>
                    <!-- 첨부 — 왼쪽 색 막대 -->
                    <div class="mt-1.5 flex" :class="card.tool === 'MATTERMOST' ? 'overflow-hidden rounded border border-black/15 dark:border-white/15' : ''">
                        <span class="w-1 shrink-0" :class="card.tool === 'SLACK' ? 'rounded-full' : ''" :style="{ background: card.color }" aria-hidden="true" />
                        <div class="min-w-0 flex-1" :class="card.tool === 'SLACK' ? 'pl-3' : 'px-3 py-2.5'">
                            <p v-if="card.title || card.icon" class="m-0 break-words text-[14px]" :class="card.boldTitle ? 'font-bold' : ''">
                                <span v-if="card.icon" class="mr-1" aria-hidden="true">{{ card.icon }}</span>{{ card.title }}
                            </p>
                            <!-- eslint-disable-next-line vue/no-v-html -- 화면이 만든 태그(strong·em·del·code·a·ul·li·br)만 들어 있다(값은 가렸다 — UtilChatTemplate.renderMarkup) -->
                            <div v-if="card.bodyHtml" class="chat-card__body mt-0.5 break-words text-[14px] leading-relaxed" v-html="card.bodyHtml" />
                            <p v-if="card.footer" class="m-0 mt-1.5 text-[11px] text-[#616061] dark:text-[#ababad]">{{ card.footer }}</p>
                            <div v-if="card.button" class="mt-2">
                                <span
                                    class="inline-flex items-center justify-center text-[13px] font-semibold leading-none"
                                    :class="card.tool === 'SLACK' ? 'rounded border border-black/25 px-3 py-1.5 dark:border-white/25' : 'rounded bg-[#1c58d9]/10 px-3 py-2 text-[#1c58d9] dark:text-[#5d89ea]'"
                                >
                                    <span class="chat-card__badge-label">{{ card.button }}</span>
                                </span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        <p v-if="note" class="m-0 text-xs text-muted-color">{{ note }}</p>
    </div>
</template>

<script setup lang="ts">
import type { ChatCardModel } from '~/utils/util-chat-template';

interface Props {
    /** 카드 값(`UtilChatTemplate.card`) */
    card: ChatCardModel;
    /** 봇(앱) 이름 — 없으면 「로보얌 알림」 */
    botName?: string;
    /** 이름 줄의 시각 글자 — 없으면 「방금」 */
    timeLabel?: string;
    /** 카드 아래 안내 한 줄 */
    note?: string;
}

const props = withDefaults(defineProps<Props>(), { botName: '', timeLabel: '', note: '' });

const { t } = useI18n();
const botName = computed(() => props.botName || t('notificationTemplate.chatBotName'));
const timeLabel = computed(() => props.timeLabel || t('notificationTemplate.chatNow'));
const botInitial = computed(() => botName.value.slice(0, 1));
</script>

<style scoped>
/* 딱지·버튼 글자 상하 중앙 + 한글 글꼴 보정(components.md 2-4) */
.chat-card__badge-label {
    position: relative;
    top: -0.09em;
}
.chat-card__body :deep(ul) {
    margin: 0.25rem 0;
    padding-left: 1.25rem;
    list-style: disc;
}
.chat-card__body :deep(code) {
    padding: 0 0.25rem;
    border-radius: 3px;
    background: rgb(0 0 0 / 0.06);
    font-size: 12px;
}
.chat-card--slack .chat-card__body :deep(a) {
    color: #1264a3;
}
.chat-card--mattermost .chat-card__body :deep(a) {
    color: #1c58d9;
}
.chat-card__body :deep(a) {
    text-decoration: underline;
}
</style>
