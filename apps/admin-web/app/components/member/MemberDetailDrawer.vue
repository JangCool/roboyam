<template>
    <!--
        회원 상세 서랍 — 목록 행을 누르면(또는 Enter·Space) 연다. 열리는 쪽은 설정의 "서랍 위치"를 따른다(`prefs.drawerPosition`). 폭 56rem(좁은 화면은 화면 폭 — 오른쪽 구역 내비 열을 두려고 32rem 에서 넓혔다), Esc·바깥 누름으로 닫힌다(PrimeVue Drawer 의 초점·Esc 처리).
        머리(닉네임 · 상태 딱지 · 회원번호)는 목록 행(`member`)으로 바로 그리고(목록 행 없이 열면 — 닉네임 이미지 심사 등 — 상세가 온 뒤 상세로), 내용(`detail`)은 부모가 받아 내려 준다 — 받는 동안 뼈대, 못 받으면 서랍 안 오류 상태(다시 시도). 다른 행을 누르면 열린 채 내용만 바뀐다.
        **읽기 전용**이다 — 상태 변경·제재 부여 같은 조치는 이번 범위 밖(후속). 예외(커머스 0-4): 기능 제한 걸기·해제와 탈퇴 회원 연락처 열람 — 구획이 이벤트로 올리면 부모가 대화상자를 띄운다. 탭은 없다 — 본문(`MemberDetailContent`)이 구획을 한 스크롤 영역에 모두 쌓고 오른쪽 구역 내비(`SectionNav`)로 이동한다. 그 구역 내비는 서랍 범위(`SECTION_NAV_SCOPE`)로 셸 것과 따로 쓴다.
        서버 호출은 하지 않는다(`components.md`) — 부모가 `useMemberDetail` 로 받는다.
    -->
    <Drawer v-model:visible="open" class="member-detail-drawer" :modal="false" :position="prefs.drawerPosition" :aria-labelledby="titleId" :style="{ width: '56rem', maxWidth: '100vw' }" :pt="{ content: { class: 'p-0!' } }">
        <template #header>
            <div v-if="head" class="flex min-w-0 flex-1 flex-col gap-1">
                <div class="flex min-w-0 flex-wrap items-center gap-2">
                    <h2 :id="titleId" class="min-w-0 truncate text-base font-semibold">{{ head.nickname }}</h2>
                    <Tag :value="t(`member.status.${head.status}`)" :icon="UtilMember.statusIcon(head.status)" :severity="UtilMember.statusSeverity(head.status)" />
                </div>
                <p class="text-[11px] text-muted-color">
                    {{ t('member.col.memberNo') }}
                    <code class="tabular-nums">{{ head.memberNo }}</code>
                </p>
            </div>
            <!-- 목록 행 없이 연 경우(닉네임 이미지 심사 등) — 상세가 올 때까지 머리 뼈대 -->
            <div v-else class="flex min-w-0 flex-1 flex-col gap-1.5" aria-hidden="true">
                <h2 :id="titleId" class="sr-only">{{ t('common.loading') }}</h2>
                <Skeleton width="10rem" height="1.25rem" />
                <Skeleton width="7rem" height="0.75rem" />
            </div>
        </template>

        <!-- 없는 회원(404 `MEMBER_NOT_FOUND`)은 다시 시도해도 같다 — 안내만 보이고 다시 시도 단추는 두지 않는다 -->
        <ErrorState v-if="error" :title="t('member.detail.loadFailed')" :description="error.message" :code="error.status === 404 ? '404' : '!'" :retry="error.status !== 404" :retrying="loading" @retry="emit('retry')" />

        <!-- 받는 동안 뼈대 -->
        <div v-else-if="!detail" class="flex flex-col gap-3 px-4 py-4" role="status" :aria-label="t('common.loading')" aria-busy="true">
            <Skeleton width="60%" height="1.25rem" />
            <Skeleton v-for="n in 7" :key="n" height="1rem" />
        </div>

        <!-- 기능 제한·탈퇴 회원 연락처(커머스 0-4)는 부모가 받아 내려 주고, 걸기·해제·열람은 이벤트로 그대로 올린다 -->
        <MemberDetailContent
            v-else
            :detail="detail"
            :restrictions="restrictions"
            :withdrawn-contact="withdrawnContact"
            :point-lots="pointLots"
            @restrict="emit('restrict')"
            @point-lots-query="(q) => emit('point-lots-query', q)"
            @retry-point-lots="emit('retry-point-lots')"
            @lift="(r) => emit('lift', r)"
            @retry-restrictions="emit('retry-restrictions')"
            @reveal-contact="emit('reveal-contact')"
            @mask-contact="emit('mask-contact')"
            @retry-contact="emit('retry-contact')"
        />
    </Drawer>
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type { MemberDetail, MemberRow } from '~/composables/member/api/useMemberApi.types';
import type { MemberDetailEmits, MemberPointLotsView, MemberRestrictionsView, WithdrawnContactView } from '~/components/member/MemberDetailContent.types';
import UtilMember from '~/utils/util-member';

/** 서랍이 열려 있는가 */
const open = defineModel<boolean>('visible', { required: true });

interface Props {
    /** 연 회원의 목록 행 — 머리를 바로 그린다. 목록 행이 없는 화면은 null(상세가 오면 상세로 그린다) */
    member: MemberRow | null;
    /** 받은 상세. 아직 없으면 null */
    detail: MemberDetail | null;
    loading?: boolean;
    error?: ApiError | null;
    /** 이 선택자에 맞는 곳의 누름은 바깥 누름으로 치지 않는다 — 서랍을 여는 행(`tbody tr`) */
    ignoreSelector?: string;
    /** 「기능 제한」 구획의 목록(커머스 0-4) */
    restrictions?: MemberRestrictionsView | null;
    /** 탈퇴 회원의 연락용 이메일(커머스 0-4) */
    withdrawnContact?: WithdrawnContactView | null;
    /** 「등급·포인트」 구획의 포인트 묶음(커머스 1-1) — 없으면 묶음 부분을 그리지 않는다 */
    pointLots?: MemberPointLotsView | null;
}

const props = withDefaults(defineProps<Props>(), { loading: false, error: null, ignoreSelector: '', restrictions: null, withdrawnContact: null, pointLots: null });
const emit = defineEmits<{ retry: [] } & MemberDetailEmits>();

const { t } = useI18n();
const { prefs } = useLayoutPrefs();
/** 머리에 그릴 회원 — 목록 행이 먼저, 없으면 받은 상세 */
const head = computed(() => props.member ?? props.detail);
const titleId = useId();
// 구역 내비의 범위 — 서랍 본문의 `AppGadget`·`SectionNav` 가 셸과 따로 된 목록을 쓰게 한다(`useSectionNav` · 본문은 `MemberDetailContent`)
provide(SECTION_NAV_SCOPE, `member-detail-${titleId}`);

/**
 * 바깥 누름으로 닫기. 서랍은 가림막이 없는(`modal=false`) 서랍이라 PrimeVue 가 바깥 누름을 듣지 않는다 — 열린 채 다른 행을 눌러 내용을 바꿀 수 있어야 해서다.
 * 그래서 직접 듣는다: 서랍 안·`ignore`(표 행) 안·떠 있는 고르개 패널(달력·선택 목록 등) 안의 누름은 무시하고, 그 밖을 누르면 닫는다. Esc 는 PrimeVue Drawer 가 맡는다.
 */
const OVERLAY = '.p-popover, .p-select-overlay, .p-multiselect-overlay, .p-datepicker-panel, .p-toast, .p-dialog';
function onPointerDown(e: PointerEvent): void {
    const target = e.target as Element | null;
    if (!target || target.closest(`.member-detail-drawer, ${OVERLAY}`)) return;
    if (props.ignoreSelector && target.closest(props.ignoreSelector)) return;
    open.value = false;
}
watch(open, (isOpen) => {
    if (isOpen) document.addEventListener('pointerdown', onPointerDown, true);
    else document.removeEventListener('pointerdown', onPointerDown, true);
});
onBeforeUnmount(() => document.removeEventListener('pointerdown', onPointerDown, true));
</script>
