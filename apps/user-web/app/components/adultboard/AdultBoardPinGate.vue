<template>
    <!--
        성인 전용 게시판의 핀 입력 화면 — 게시판 목록·글 보기·글쓰기가 서버의 「핀 필요」(`403 ADULT_BOARD_PIN_REQUIRED`)를 받으면 내용 자리에 그린다(adult-board-pin-contract).
        6칸을 다 채우면 바로 확인한다. 틀리면 「n번 더 틀리면 10분 동안 잠겨요」, 5번 틀리면 잠김(남은 시간이 초 단위로 줄고 입력을 막는다).
        맞으면 `unlocked` 를 알리고 부모가 보던 화면을 다시 읽는다. [핀번호를 잊었어요] → 설정 화면의 다시 정하기.
        핀번호는 이 화면 메모리에만 있고 확인할 때마다 비운다.
        제목 단계는 놓이는 자리가 정한다(`headingLevel`) — 화면 제목(`h1`)이 따로 있는 글쓰기·대화상자 안에서는 2.
        접근이 그새 꺼졌으면(`409 ADULT_BOARD_DISABLED` — 다른 기기에서 끔) 메뉴·게시판 목록을 다시 받고 `unlocked` 로 부모가 다시 읽게 한다(없는 게시판으로 바뀐다).
    -->
    <section class="flex flex-col items-center gap-3 px-5 py-10 text-center" :aria-labelledby="`${uid}-title`" data-testid="adult-board-pin-gate" :data-state="locked ? 'locked' : wrong ? 'wrong' : 'idle'">
        <span class="inline-flex size-12 items-center justify-center rounded-full bg-surface-100 text-muted-color dark:bg-surface-800" aria-hidden="true">
            <i class="pi pi-lock text-lg" />
        </span>
        <component :is="`h${headingLevel}`" :id="`${uid}-title`" class="m-0 text-lg font-semibold">{{ t('adultBoard.gateTitle') }}</component>
        <p :id="`${uid}-text`" class="m-0 max-w-md text-sm leading-relaxed text-muted-color">{{ t('adultBoard.gateText') }}</p>

        <div ref="field" role="group" :aria-labelledby="`${uid}-title`" :aria-describedby="`${uid}-text ${uid}-status`" class="mt-1">
            <InputOtp
                :model-value="pin"
                :length="UtilAdultBoard.PIN_LENGTH"
                mask
                integer-only
                :invalid="wrong && !locked"
                :disabled="busy || locked"
                data-testid="adult-board-pin-input"
                @update:model-value="onInput"
            />
        </div>

        <!-- 결과 한 줄 — 잠김(남은 시간) · 틀림(남은 횟수) · 그 밖의 오류(서버 문구) -->
        <p :id="`${uid}-status`" class="m-0 min-h-5 text-sm" :class="locked || wrong || error ? 'font-medium text-red-600 dark:text-red-400' : 'text-muted-color'" aria-live="polite" data-testid="adult-board-pin-status">
            <template v-if="locked">{{ t('adultBoard.gateLocked', { time: UtilAdultBoard.clock(lock.remaining.value) }) }}</template>
            <template v-else-if="busy">{{ t('adultBoard.gateChecking') }}</template>
            <template v-else-if="wrong">{{ attempts !== null ? t('adultBoard.gateWrong', { n: attempts }) : t('adultBoard.gateWrongPlain') }}</template>
            <template v-else-if="error">{{ error }}</template>
        </p>

        <NuxtLink :to="UtilAdultBoard.RESET_PATH" class="text-sm text-primary underline-offset-2 hover:underline focus-visible:outline-2 focus-visible:outline-primary" data-testid="adult-board-pin-forgot">
            {{ t('adultBoard.gateForgot') }}
        </NuxtLink>
    </section>
</template>

<script setup lang="ts">
import UtilAdultBoard from '~/utils/util-adult-board';
import UtilBoardMessages from '~/utils/util-board-messages';

interface Props {
    /** 처음부터 잠겨 있으면 그 끝 시각(ISO) — 「핀 필요」 오류의 `lockedUntil` */
    lockedUntil?: string | null;
    /** 제목 단계 — 내용 자리를 통째로 차지하면 1(기본), 화면 제목이 따로 있는 곳(글쓰기·대화상자)은 2 */
    headingLevel?: 1 | 2;
}

const props = withDefaults(defineProps<Props>(), { lockedUntil: null, headingLevel: 1 });
const emit = defineEmits<{ unlocked: [] }>();

const { t } = useI18n();
const adultBoard = useAdultBoard();
const uid = useId();

const pin = ref('');
const busy = ref(false);
/** 남은 횟수 — 틀린 적이 없으면 null */
const attempts = ref<number | null>(null);
const wrong = ref(false);
/** 틀림·잠김이 아닌 실패(네트워크 등) — 서버 문구 그대로 */
const error = ref('');
const field = ref<HTMLElement | null>(null);

/** 틀림 잠금 — 끝나면 처음 모양으로 돌아가 다시 넣을 수 있다 */
const lockEndsAt = ref(UtilAdultBoard.millis(props.lockedUntil));
const lock = useCountdown(lockEndsAt, () => {
    wrong.value = false;
    attempts.value = null;
    focusFirst();
});
const locked = computed(() => lock.active.value);

function focusFirst(): void {
    void nextTick(() => field.value?.querySelector<HTMLInputElement>('input:not([disabled])')?.focus());
}

function onInput(value: string | undefined): void {
    pin.value = value ?? '';
    if (pin.value) error.value = '';
    if (UtilAdultBoard.isPin(pin.value)) void submit();
}

async function submit(): Promise<void> {
    if (busy.value || locked.value) return;
    busy.value = true;
    error.value = '';
    const entered = pin.value;
    try {
        await adultBoard.unlock(entered);
        pin.value = '';
        emit('unlocked');
    } catch (e) {
        pin.value = '';
        if (e instanceof ApiError && e.code === UtilAdultBoard.CODE_PIN_LOCKED) {
            wrong.value = false;
            lockEndsAt.value = UtilAdultBoard.lockEndsAt(e);
        } else if (e instanceof ApiError && e.code === UtilAdultBoard.CODE_PIN_WRONG) {
            const left = Number(e.extraText('attemptsLeft'));
            attempts.value = Number.isFinite(left) ? left : null;
            wrong.value = true;
        } else if (e instanceof ApiError && e.code === UtilAdultBoard.CODE_DISABLED) {
            // 접근이 꺼져 있다(다른 기기에서 끔) — 메뉴·게시판 목록을 맞추고 부모가 다시 읽게 한다(성인 전용 게시판은 없는 게시판이 된다)
            wrong.value = false;
            error.value = e.message;
            adultBoard.refreshListings();
            emit('unlocked');
        } else {
            wrong.value = false;
            error.value = UtilBoardMessages.of(t, e);
        }
    } finally {
        busy.value = false;
        if (!locked.value) focusFirst();
    }
}

onMounted(() => {
    if (!locked.value) focusFirst();
});
</script>
