<template>
    <!--
        게시판 기본 설정 › 보안 — 조회수 중복 방지 · 금지어 · 새 회원 스팸 방지 · 외부 링크. **자기 요청·자기 저장**(`GET·PUT /api/admin/boards/defaults/security`, 하단 StickySaveBar).
        그누보드7 원본(보안 탭 + `config/board.php` limits) → roboyam 권장안:
          · post/comment_cooldown_seconds 0 → 이 화면에 두지 않는다. 이미 「일반 › 작성 속도」(60초·10초)에 있어 중복하지 않고 링크로 안내한다.
          · report_cooldown_seconds 60 → 「신고 정책 › 신고 제한」으로 옮겼다.
          · view_count_cache_ttl 86400초(60~604800) → 시간 단위 viewCountDedupHours 24(1~168)로 바꿨다(운영자가 읽기 쉬운 단위, 최대 7일 그대로).
          · blocked_keywords(원본은 게시판 설정 탭) → 전역 금지어로 여기 둔다. 걸렸을 때 BLOCK(저장 거부, 단어 알려 줌) / MASK(***) 를 고른다. 최대 500개·단어당 1~30자·중복 제거.
            처음 값은 서버(설치) 기본값이다. 적용 범위: 제목·본문·댓글(닉네임은 회원 쪽). 운영자 작성물은 검사하지 않는다.
          · 새 회원 스팸 방지(그누보드엔 없음, 추가 — Discourse 의 새 사용자 링크 제한 같은 업계 관행): 첫 등급 회원의 링크 2 · 이미지 3 · 같은 내용 재작성 10분.
          · 외부 링크 nofollow(기본 켬, 검색엔진 스팸 유입 방지) · 외부 링크 경고 페이지(기본 끔) — 추가.
          · 비회원 글쓰기(그누보드의 비회원 작성 규칙을 한 구획으로): 자동입력 방지 켬(끄면 경고 줄) · 이름 2~20자 · 비밀번호 4자 이상(서버는 해시만 저장) · 링크 0(금지 권장) · 이미지 올리기 끔.
            비회원은 IP 로 식별해 작성 속도·같은 글 반복 거절을 IP 기준으로, 금지어 검사를 그대로 적용한다. 비회원 글쓰기 허용 자체는 게시판별 「글쓰기·댓글 최소 자격 = 비회원 포함 모두」다.
        금지어는 줄마다 하나씩 쓰는 textarea 다(PrimeVue InputChips 는 500개 목록에서 고치기 어렵다). 저장할 때 앞뒤 공백·빈 줄·중복을 뺀다.
        권한: 조회 `board.read`, 변경 `board.settings`. 서버 연결됨(`useBoardApi`) — 금지어는 저장만 하고 글쓰기 검사는 후속이다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('board.tabTitle.security')" :subtitle="t('board.tabSubtitle.security')" />

        <AppGadget v-if="tab.loading.value && !tab.response.value" :title="t('board.section.securityView')">
            <div class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                <Skeleton v-for="n in 4" :key="n" height="3rem" />
            </div>
        </AppGadget>

        <AppGadget v-else-if="!tab.response.value || !form" :title="t('board.tabTitle.security')">
            <ErrorState :title="t('board.tabLoadFailed')" :description="tab.loadError.value" retry :retrying="tab.loading.value" @retry="tab.load" />
        </AppGadget>

        <form v-else class="flex flex-col gap-4" novalidate @submit.prevent="tab.save">
            <p v-if="tab.response.value.updatedAt" class="-mt-2 text-xs text-muted-color">{{ t('board.lastSaved', { at: time.full(tab.response.value.updatedAt), by: tab.response.value.updatedBy ?? '—' }) }}</p>

            <!-- 조회수 + 작성 속도 안내 -->
            <AppGadget :title="t('board.section.securityView')" anchor="security-view" :changes="tab.changesIn(['viewCountDedupHours'])" :invalid="tab.invalidIn(['viewCountDedupHours'])">
                <BoardSettingField field="viewCountDedupHours" :model-value="form.viewCountDedupHours" :editable="editable" :error="tab.errorOf('viewCountDedupHours')" @update:model-value="(v: BoardFieldValue) => tab.set('viewCountDedupHours', v)" />
                <p class="mt-4 flex items-start gap-2 border-t border-(--line) pt-3 text-xs leading-relaxed text-muted-color">
                    <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                    <span>
                        {{ t('board.note.rateElsewhere') }}
                        <NuxtLink to="/boards/defaults/general#defaults-rate" class="underline">{{ t('board.openRate') }}</NuxtLink>
                        · {{ t('board.note.reportCooldownMoved') }}
                        <NuxtLink to="/boards/defaults/report#report-limit" class="underline">{{ t('board.openReportLimit') }}</NuxtLink>
                    </span>
                </p>
            </AppGadget>

            <!-- 금지어 -->
            <AppGadget :title="t('board.section.securityWords')" anchor="security-words" :changes="tab.changesIn(['blockedWordsEnabled', 'blockedWordsAction', 'blockedWords'])" :invalid="tab.invalidIn(['blockedWords'])">
                <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                    <BoardSettingField field="blockedWordsEnabled" :model-value="form.blockedWordsEnabled" :editable="editable" @update:model-value="(v: BoardFieldValue) => tab.set('blockedWordsEnabled', v)" />
                    <BoardSettingField
                        field="blockedWordsAction"
                        :model-value="form.blockedWordsAction"
                        :editable="editable"
                        :inactive="!form.blockedWordsEnabled"
                        :note="form.blockedWordsEnabled ? '' : t('board.inactiveNote.wordsOff')"
                        @update:model-value="(v: BoardFieldValue) => tab.set('blockedWordsAction', v)"
                    />
                </div>
                <LabeledField class="mt-5" :label="t('board.field.blockedWords')" :hint="t('board.wordsHint', { max: UtilBoard.BLOCKED_WORDS_MAX, len: UtilBoard.BLOCKED_WORD_LENGTH_MAX })" :help="t('board.fieldHelp.blockedWords')" :error="tab.errorOf('blockedWords')">
                    <template #aside>
                        <span class="text-xs tabular-nums" :class="words.length > UtilBoard.BLOCKED_WORDS_MAX ? 'text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('board.wordsCount', { n: words.length, max: UtilBoard.BLOCKED_WORDS_MAX }) }}</span>
                    </template>
                    <template #default="{ id, describedBy, invalid }">
                        <Textarea :id="id" v-model="wordsText" rows="8" :readonly="!editable" :disabled="!form.blockedWordsEnabled" :invalid="invalid" :aria-describedby="describedBy" spellcheck="false" fluid class="font-mono" />
                    </template>
                </LabeledField>
                <ul class="m-0 mt-4 flex list-none flex-col gap-1.5 border-t border-(--line) p-0 pt-3">
                    <li v-for="key in ['board.note.wordsScope', 'board.note.wordsStaff', 'board.note.wordsDedupe']" :key="key" class="flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                        <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                        <span>{{ t(key) }}</span>
                    </li>
                </ul>
            </AppGadget>

            <!-- 새 회원 스팸 방지 -->
            <AppGadget :title="t('board.section.securityNewMember')" anchor="security-new-member" :changes="tab.changesIn(NEW_MEMBER)" :invalid="tab.invalidIn(NEW_MEMBER)">
                <p class="mb-4 text-xs leading-relaxed text-muted-color">{{ t('board.securityNewMemberLead') }}</p>
                <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                    <BoardSettingField v-for="field in NEW_MEMBER" :key="field" :field="field" :model-value="form[field]" :editable="editable" :error="tab.errorOf(field)" @update:model-value="(v: BoardFieldValue) => tab.set(field, v)" />
                </div>
            </AppGadget>

            <!-- 비회원 글쓰기 -->
            <AppGadget :title="t('board.section.securityGuest')" anchor="security-guest" :changes="tab.changesIn(GUEST)" :invalid="tab.invalidIn(GUEST)">
                <p class="mb-4 text-xs leading-relaxed text-muted-color">{{ t('board.securityGuestLead') }}</p>
                <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                    <BoardSettingField
                        v-for="field in GUEST"
                        :key="field"
                        :field="field"
                        :model-value="form[field]"
                        :editable="editable"
                        :note="field === 'guestCaptchaRequired' && !form.guestCaptchaRequired ? t('board.guestCaptchaOffWarn') : ''"
                        :error="tab.errorOf(field)"
                        @update:model-value="(v: BoardFieldValue) => tab.set(field, v)"
                    />
                </div>
                <ul class="m-0 mt-4 flex list-none flex-col gap-1.5 border-t border-(--line) p-0 pt-3">
                    <li v-for="key in ['board.note.guestIp', 'board.note.guestWords', 'board.note.guestImpersonate']" :key="key" class="flex items-start gap-2 text-xs leading-relaxed text-muted-color">
                        <i class="pi pi-info-circle mt-0.5 text-[11px]" aria-hidden="true" />
                        <span>{{ t(key) }}</span>
                    </li>
                </ul>
            </AppGadget>

            <!-- 외부 링크 -->
            <AppGadget :title="t('board.section.securityLink')" anchor="security-link" :changes="tab.changesIn(LINK)">
                <div class="grid gap-x-8 gap-y-5 md:grid-cols-2">
                    <BoardSettingField v-for="field in LINK" :key="field" :field="field" :model-value="form[field]" :editable="editable" @update:model-value="(v: BoardFieldValue) => tab.set(field, v)" />
                </div>
            </AppGadget>

            <StickySaveBar v-if="tab.canEdit.value" submit revertable :dirty="tab.dirty.value" :saving="tab.saving.value" :disabled="!tab.canSave.value" :count="tab.changed.value.length" :error-count="tab.errorCount.value" :error="tab.formError.value" @revert="tab.revert" />
            <p v-else class="text-xs text-muted-color">{{ t('board.readOnlyNote') }}</p>
        </form>
    </div>
</template>

<script setup lang="ts">
import type { BoardSecuritySettings } from '~/composables/board/api/useBoardApi.types';
import type { BoardFieldValue, BoardTabForm } from '~/utils/util-board';
import UtilBoard from '~/utils/util-board';

const { t } = useI18n();
useSeoMeta({ title: () => t('board.tabTitle.security') });

const api = useBoardApi();
const time = useDisplayTime();

const NEW_MEMBER = ['newMemberLinkLimit', 'newMemberImageLimit', 'duplicatePostWindowMinutes'] as const satisfies readonly (keyof BoardSecuritySettings)[];
const LINK = ['externalLinkNofollow', 'externalLinkWarn'] as const satisfies readonly (keyof BoardSecuritySettings)[];
const GUEST = ['guestCaptchaRequired', 'guestImageUploadAllowed', 'guestNameMin', 'guestNameMax', 'guestPasswordMin', 'guestLinkLimit'] as const satisfies readonly (keyof BoardSecuritySettings)[];

/** 금지어 목록 검사 — 개수·길이(정리한 뒤 기준) */
function validate(f: BoardTabForm<BoardSecuritySettings>): Record<string, string> {
    // 이름 최소 ≤ 최대(둘 다 정수일 때만 — 범위 문제는 숫자 칸 검사가 따로 말한다)
    const nameMin = UtilBoard.parse(f.guestNameMin);
    const nameMax = UtilBoard.parse(f.guestNameMax);
    const out: Record<string, string> = nameMin !== null && nameMax !== null && nameMin > nameMax ? { guestNameMax: t('board.problemNameMinMax') } : {};
    return { ...out, ...validateWords(f) };
}

function validateWords(f: BoardTabForm<BoardSecuritySettings>): Record<string, string> {
    const problem = UtilBoard.wordsProblem(UtilBoard.wordsOf(f.blockedWords.join('\n')));
    if (problem === 'WORDS_COUNT') return { blockedWords: t('board.problemWordsCount', { max: UtilBoard.BLOCKED_WORDS_MAX }) };
    if (problem === 'WORD_LENGTH') return { blockedWords: t('board.problemWordLength', { max: UtilBoard.BLOCKED_WORD_LENGTH_MAX }) };
    return {};
}

const tab = useBoardDefaultsTab({
    load: api.security,
    save: api.saveSecurity,
    validate,
    // 줄 글자 → 정리된 목록(앞뒤 공백·빈 줄·중복 제거)
    prepare: (body) => ({ ...body, blockedWords: UtilBoard.wordsOf(body.blockedWords.join('\n')) }),
    savedKey: 'board.tabSaved',
});

const form = computed(() => tab.form.value);
const editable = computed(() => tab.canEdit.value && !tab.saving.value);
/** 금지어 입력 — 줄마다 하나. 입력 중에는 줄을 그대로 두고(빈 줄 포함) 저장할 때 정리한다 */
const wordsText = computed<string>({
    get: () => form.value?.blockedWords.join('\n') ?? '',
    set: (text) => tab.set('blockedWords', text.split(/\r?\n/)),
});
const words = computed(() => UtilBoard.wordsOf(wordsText.value));

onMounted(() => void tab.load());
</script>
