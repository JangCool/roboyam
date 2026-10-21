<template>
    <!-- 확인 대화상자 자리 — 레이아웃에 한 번만 놓는다. 띄우는 것은 `useConfirmDialog().confirm` · `choose` -->
    <Dialog
        :visible="state.open"
        modal
        :closable="false"
        :draggable="false"
        dismissable-mask
        role="alertdialog"
        :aria-labelledby="titleId"
        :aria-describedby="state.message ? messageId : undefined"
        :style="{ width: 'min(22rem, calc(100vw - 2rem))' }"
        @update:visible="onVisible"
    >
        <template #container>
            <div class="p-5">
                <h3 :id="titleId" class="text-base font-semibold">{{ state.title }}</h3>
                <p v-if="state.message" :id="messageId" class="mt-1.5 whitespace-pre-line text-sm leading-relaxed text-muted-color">{{ state.message }}</p>

                <!-- 선택지가 셋 이상: 위에서 아래로 쌓는다(작성 중 이탈: 임시저장하고 나가기 · 버리고 나가기 · 계속 쓰기). 마지막 선택지가 안전한 쪽이라 거기에 먼저 초점을 둔다 -->
                <div v-if="state.choices" class="mt-5 flex flex-col gap-2">
                    <Button
                        v-for="(choice, index) in state.choices"
                        :key="choice.key"
                        type="button"
                        :label="choice.label"
                        :severity="choice.tone === 'danger' ? 'danger' : choice.tone === 'neutral' ? 'secondary' : undefined"
                        :variant="choice.tone === 'neutral' ? 'outlined' : undefined"
                        :autofocus="index === state.choices.length - 1"
                        fluid
                        @click="pick(choice.key)"
                    />
                </div>

                <div v-else class="mt-5 flex gap-2">
                    <!-- 되돌릴 수 없는 동작이라 처음에는 취소에 초점을 둔다 -->
                    <Button type="button" :label="state.cancelLabel" severity="secondary" variant="outlined" class="flex-1" autofocus @click="cancel()" />
                    <Button
                        type="button"
                        :label="state.confirmLabel"
                        :severity="state.tone === 'danger' ? 'danger' : undefined"
                        class="flex-1"
                        @click="accept()"
                    />
                </div>
            </div>
        </template>
    </Dialog>
</template>

<script setup lang="ts">
const { state, accept, cancel, pick } = useConfirmDialog();
const titleId = useId();
const messageId = `${titleId}-msg`;

/** 바깥을 누르거나 Esc 를 눌러 닫히면 취소다 */
function onVisible(visible: boolean) {
    if (!visible) cancel();
}
</script>
