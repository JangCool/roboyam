<template>
    <!--
        게시판 제재 이력 — 최근 것이 위. 줄마다 번호 · 게시판(`showBoard`) · 단계 · 상태 · 이의제기 상태, 사유 코드 + 덧붙인 글(글자로만), 기간 · 처리자(닉네임 + 매니저/스텝) · 계기 글, 해제·무효 기록.
        게시판 제재 서랍·반복 검토 서랍·이의제기 서랍·회원 상세 「게시판 제재」 구획이 같은 모양으로 쓴다.
        - `currentId` 줄(지금 보는 제재)은 테두리를 강조한다. `linkSanction` 이면 번호가 게시판 제재 서랍(`/board-sanctions?boardSanction=`)으로 가는 링크다.
        - `markedIds` 줄에는 「검토 대상」 표시, `selectableIds` 줄에는 고르기 칸(반복 검토의 「게시판 제재 무효」 — `v-model:selected`).
        - `canManage` 면 진행 중 쓰기 제한 [해제] · 무효 아닌 제재 [무효] — 누르면 `lift`·`void` 로 알린다.
    -->
    <p v-if="!lines.length" class="m-0 text-[13px] text-muted-color">{{ t('boardSanction.historyEmpty') }}</p>
    <ol v-else class="m-0 flex list-none flex-col gap-2 p-0">
        <li v-for="line in lines" :key="line.id" class="flex flex-col gap-1.5 rounded-lg border px-3 py-2.5" :class="line.id === currentId ? 'border-primary' : 'border-(--line)'" :aria-current="line.id === currentId ? 'true' : undefined">
            <div class="flex flex-wrap items-center gap-1.5">
                <Checkbox v-if="selectableIds.includes(line.id)" v-model="selected" :value="line.id" :input-id="`${uid}-${line.id}`" :disabled="busy" />
                <label v-if="selectableIds.includes(line.id)" :for="`${uid}-${line.id}`" class="sr-only">{{ t('boardSanction.review.voidPick') }} {{ UtilSanctionMessages.no(t, line) }}</label>
                <NuxtLink v-if="linkSanction" :to="{ path: '/board-sanctions', query: { boardSanction: line.id } }" class="text-xs tabular-nums text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ UtilSanctionMessages.no(t, line) }}</NuxtLink>
                <span v-else class="text-xs tabular-nums text-muted-color">{{ UtilSanctionMessages.no(t, line) }}</span>
                <span v-if="showBoard" class="max-w-40 truncate text-xs font-medium">{{ localizedName(line.board.name, line.board.nameEn) }}</span>
                <BoardSanctionTag type="kind" :kind="line.kind" />
                <BoardSanctionTag type="status" :kind="line.kind" :status="line.status" />
                <span v-if="markedIds.includes(line.id)" class="inline-flex items-center gap-1 rounded-full bg-amber-100 px-1.5 py-0.5 text-[11px] text-amber-800 dark:bg-amber-950 dark:text-amber-300">
                    <i class="pi pi-eye text-[9px]" aria-hidden="true" />{{ t('boardSanction.review.inReview') }}
                </span>
                <SanctionTag v-if="line.appeal" type="appeal" :status="line.appeal.status" class="ml-auto" />
            </div>
            <p class="m-0 text-[13px] break-words whitespace-pre-line [overflow-wrap:anywhere]" :class="line.status === 'VOIDED' ? 'text-muted-color line-through' : ''">{{ UtilBoardSanctionMessages.reasonLine(t, line) }}</p>
            <p class="m-0 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-muted-color tabular-nums">
                <span>{{ UtilBoardSanctionMessages.period(t, time, line) }}</span>
                <span>{{ UtilBoardSanctionMessages.issuer(t, line.issuedBy) }}</span>
                <NuxtLink v-if="line.trigger" :to="{ path: '/posts', query: { post: line.trigger.postId } }" class="inline-flex max-w-full min-w-0 items-center gap-1 text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">
                    <i :class="line.trigger.type === 'COMMENT' ? 'pi pi-comment' : 'pi pi-file'" class="text-[10px]" aria-hidden="true" /><span class="truncate">{{ UtilBoardSanctionMessages.trigger(t, line.trigger) }}</span>
                </NuxtLink>
            </p>
            <p v-if="line.status === 'LIFTED'" class="m-0 text-xs text-muted-color">
                {{ t('boardSanction.liftedLine', { name: UtilBoardSanctionMessages.liftedBy(t, line.liftedBy), at: time.full(line.liftedAt) }) }}<template v-if="line.liftReason"> — {{ line.liftReason }}</template>
            </p>
            <p v-else-if="line.status === 'VOIDED'" class="m-0 text-xs text-muted-color">
                {{ UtilBoardSanctionMessages.voidedLine(t, time, line) }}<template v-if="line.voidReason"> — {{ line.voidReason }}</template>
            </p>
            <div v-if="canManage && (UtilBoardSanction.canLift(line, true) || UtilBoardSanction.canVoid(line, true))" class="flex justify-end gap-2">
                <Button v-if="UtilBoardSanction.canLift(line, true)" type="button" :label="t('boardSanction.lift')" icon="pi pi-lock-open" size="small" severity="secondary" variant="outlined" :disabled="busy" @click="emit('lift', line)" />
                <Button v-if="UtilBoardSanction.canVoid(line, true)" type="button" :label="t('boardSanction.void')" icon="pi pi-undo" size="small" severity="secondary" variant="text" :disabled="busy" @click="emit('void', line)" />
            </div>
        </li>
    </ol>
</template>

<script setup lang="ts">
import type { BoardSanctionLine } from '~/composables/sanction/api/useBoardSanctionApi.types';
import UtilBoardSanction from '~/utils/util-board-sanction';
import UtilBoardSanctionMessages from '~/utils/util-board-sanction-messages';
import UtilSanctionMessages from '~/utils/util-sanction-messages';

/** 고른 제재 ID — `selectableIds` 가 있을 때만 뜻이 있다 */
const selected = defineModel<string[]>('selected', { default: () => [] });

interface Props {
    lines: BoardSanctionLine[];
    /** 강조할 줄 */
    currentId?: string | null;
    /** 번호를 게시판 제재 서랍 링크로 */
    linkSanction?: boolean;
    /** 게시판 이름을 보인다(한 게시판만 보이는 자리는 끈다) */
    showBoard?: boolean;
    /** 「검토 대상」 표시를 붙일 줄 */
    markedIds?: string[];
    /** 고르기 칸을 둘 줄 */
    selectableIds?: string[];
    /** `sanction.manage` — [해제]·[무효]를 그린다 */
    canManage?: boolean;
    busy?: boolean;
}

withDefaults(defineProps<Props>(), { currentId: null, linkSanction: false, showBoard: true, markedIds: () => [], selectableIds: () => [], canManage: false, busy: false });
const emit = defineEmits<{ lift: [line: BoardSanctionLine]; void: [line: BoardSanctionLine] }>();

const { t } = useI18n();
const time = useDisplayTime();
const { localizedName } = useLocalizedName();
const uid = useId();
</script>
