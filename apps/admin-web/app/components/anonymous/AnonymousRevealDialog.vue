<template>
    <!--
        익명 작성자 열람 창(anonymous-board-contract 정책 10) — 게시글 관리 서랍·신고 처리 서랍의 「작성자 열람」(권한 `anonymous-author.reveal` 이 있는 운영자에게만 단추가 보인다)이 연다.
        안내 「열람은 감사 기록에 남고, 다른 운영자에게 알림이 가요」 · 사유 고르기(필수) · 메모(필수, 500자) · [닫기] [열람].
        열면 창 안에 결과(닉네임 + 회원 상세 링크, 탈퇴면 「탈퇴한 회원」)를 보이고 칸은 잠근다(같은 창에서 두 번 열람하지 않는다). 서버 호출은 `useAnonymousReveal` 이 한다.
        서버 거절: `fields.reason|memo` 는 칸 아래, 그 밖은 창 안 한 줄(서버 문구 그대로). 쓴 메모는 그대로 둔다.
    -->
    <Dialog v-model:visible="open" modal :draggable="false" :closable="!view.busy" :header="t('anonymousAuthor.revealTitle')" :style="{ width: 'min(32rem, calc(100vw - 2rem))' }">
        <form :id="formId" class="flex flex-col gap-4" novalidate @submit.prevent="submit">
            <Message severity="warn" size="small" :closable="false" icon="pi pi-eye">{{ t('anonymousAuthor.revealNotice') }}</Message>

            <!-- 사유(필수) -->
            <fieldset class="m-0 flex flex-col gap-1.5 border-0 p-0" :aria-describedby="reasonError ? `${formId}-reason-error` : undefined">
                <legend class="mb-1 p-0 text-sm font-medium">{{ t('anonymousAuthor.revealReason') }}</legend>
                <div class="grid gap-1.5 sm:grid-cols-2">
                    <label v-for="r in ANONYMOUS_REVEAL_REASONS" :key="r" :for="`${formId}-reason-${r}`" class="flex cursor-pointer items-center gap-2 rounded-lg border px-3 py-2 text-[13px]" :class="reason === r ? 'border-primary bg-primary-50/40 dark:bg-primary-950/30' : 'border-(--line)'">
                        <RadioButton v-model="reason" :input-id="`${formId}-reason-${r}`" :name="`${formId}-reason`" :value="r" :disabled="locked" :invalid="Boolean(reasonError)" />
                        {{ t(`anonymousAuthor.reason.${r}`) }}
                    </label>
                </div>
                <p v-if="reasonError" :id="`${formId}-reason-error`" class="m-0 text-xs text-red-600 dark:text-red-400" role="alert">{{ reasonError }}</p>
            </fieldset>

            <!-- 메모(필수, 500자) -->
            <LabeledField :label="t('anonymousAuthor.revealMemo')" :hint="t('anonymousAuthor.revealMemoCounter', { n: memoLength, max: ANONYMOUS_REVEAL_MEMO_MAX })" :error="memoError">
                <template #default="{ id, describedBy, invalid }">
                    <Textarea :id="id" v-model="memo" rows="3" auto-resize fluid :maxlength="ANONYMOUS_REVEAL_MEMO_MAX" :placeholder="t('anonymousAuthor.revealMemoPlaceholder')" :disabled="locked" :invalid="invalid" :aria-describedby="describedBy" />
                </template>
            </LabeledField>

            <Message v-if="view.error" severity="error" size="small" variant="simple" :closable="false">{{ view.error }}</Message>

            <!-- 결과 — 회원 요약(탈퇴면 「탈퇴한 회원」) -->
            <section v-if="view.result" class="flex flex-col gap-1 rounded-lg border border-(--line) px-3 py-2.5" :aria-labelledby="`${formId}-result`" data-testid="anonymous-reveal-result">
                <h3 :id="`${formId}-result`" class="m-0 text-xs font-medium text-muted-color">{{ t('anonymousAuthor.revealResult') }}</h3>
                <p v-if="view.result.withdrawn || !view.result.memberId" class="m-0 text-sm text-muted-color">{{ t('anonymousAuthor.revealWithdrawn') }}</p>
                <p v-else class="m-0 flex flex-wrap items-center gap-2 text-sm">
                    <b class="min-w-0 truncate">{{ view.result.nickname }}</b>
                    <span class="text-xs text-muted-color tabular-nums">#{{ view.result.memberId }}</span>
                    <Tag v-if="view.result.status && view.result.status !== 'ACTIVE'" :value="t(`member.status.${view.result.status}`)" severity="danger" class="text-[11px]!" />
                    <NuxtLink :to="{ path: '/members', query: { memberNo: view.result.memberId } }" class="inline-flex items-center gap-1 text-xs text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                        {{ t('anonymousAuthor.revealOpenMember') }} <i class="pi pi-arrow-right text-[10px]" aria-hidden="true" />
                    </NuxtLink>
                </p>
            </section>
        </form>
        <template #footer>
            <Button type="button" :label="t('common.close')" severity="secondary" variant="text" :disabled="view.busy" @click="open = false" />
            <Button v-if="!view.result" type="submit" :form="formId" icon="pi pi-eye" :label="t('anonymousAuthor.revealSubmit')" severity="danger" :loading="view.busy" />
        </template>
    </Dialog>
</template>

<script setup lang="ts">
import { ANONYMOUS_REVEAL_MEMO_MAX, ANONYMOUS_REVEAL_REASONS, type AnonymousRevealReason } from '~/composables/anonymous/api/useAnonymousAuthorApi.types';
import type { AnonymousRevealView } from '~/composables/anonymous/useAnonymousReveal.types';

const open = defineModel<boolean>('visible', { required: true });

interface Props {
    view: AnonymousRevealView;
}

const props = withDefaults(defineProps<Props>(), {});
const emit = defineEmits<{ submit: [payload: { reason: AnonymousRevealReason; memo: string }] }>();

const { t } = useI18n();
const formId = useId();

const reason = ref<AnonymousRevealReason | null>(null);
const memo = ref('');
const tried = ref(false);

// 열 때 칸을 비운다(앞 글에 적던 사유·메모가 다음 글에 딸려 가면 안 된다)
watch(open, (isOpen) => {
    if (!isOpen) return;
    reason.value = null;
    memo.value = '';
    tried.value = false;
});

/** 결과가 나왔거나 보내는 중이면 칸을 잠근다 */
const locked = computed(() => props.view.busy || props.view.result !== null);
/** 메모 글자 수(보이는 글자 — 코드 포인트) */
const memoLength = computed(() => [...memo.value.trim()].length);
const reasonError = computed(() => props.view.fieldErrors.reason || (tried.value && !reason.value ? t('anonymousAuthor.reasonRequired') : ''));
const memoError = computed(() => {
    if (props.view.fieldErrors.memo) return props.view.fieldErrors.memo;
    if (!tried.value) return '';
    if (memoLength.value === 0) return t('anonymousAuthor.memoRequired');
    return memoLength.value > ANONYMOUS_REVEAL_MEMO_MAX ? t('anonymousAuthor.memoTooLong', { max: ANONYMOUS_REVEAL_MEMO_MAX }) : '';
});

function submit(): void {
    if (locked.value) return;
    tried.value = true;
    if (!reason.value || memoLength.value === 0 || memoLength.value > ANONYMOUS_REVEAL_MEMO_MAX) return;
    emit('submit', { reason: reason.value, memo: memo.value.trim() });
}
</script>
