<template>
    <!--
        채팅 링크 설정 `/groups/{id}/manage/chat` (모임장 고정) — 카카오 오픈채팅·디스코드 초대 링크 하나씩(주소 + 이름(선택, 30자)). 링크는 멤버에게만 보인다.
        주소를 비우고 저장하면 그 링크가 지워진다(「해제」는 입력만 비우고, 저장 전이면 「되돌리기」). 둘 다 지우는 저장은 한 번 더 묻는다.
        `PUT /api/groups/{id}/chat-links` 는 204 라 보낸 값으로 화면을 맞춘다. 주소 검사는 서버와 같은 규칙으로 미리 알린다(`UtilGroup.isKakaoUrl`·`isDiscordUrl`).
    -->
    <GroupManageShell
        :title="t('group.chatTitle')"
        :state="state"
        :error-message="errorMessage"
        :group-id="id"
        :back-to="UtilGroup.managePath(id)"
        :back-label="t('group.manageTitle')"
        :blocked-reason="group?.blockedReason ?? null"
        @retry="load()"
    >
        <form v-if="group" class="flex flex-col gap-4" novalidate data-testid="group-chat-form" @submit.prevent="save">
            <p class="rounded-lg bg-primary-50 px-3 py-2.5 text-xs leading-relaxed text-primary-800 dark:bg-primary-950/40 dark:text-primary-200" role="note">{{ t('group.chatBanner') }}</p>

            <section
                v-for="p in PROVIDERS"
                :key="p.field"
                class="flex flex-col gap-3 panel-card p-4"
                :aria-labelledby="`${uid}-${p.field}`"
                :data-testid="`group-chat-${p.field}`"
            >
                <div class="flex items-center justify-between gap-2">
                    <div class="min-w-0">
                        <h2 :id="`${uid}-${p.field}`" class="text-sm font-semibold">{{ t(p.labelKey) }}</h2>
                        <p v-if="savedOf(p.field)" class="mt-0.5 break-words text-xs text-green-700 [overflow-wrap:anywhere] dark:text-green-400">
                            {{ savedOf(p.field)?.label ? t('group.chatLinkedLabel', { label: savedOf(p.field)?.label }) : t('group.chatLinked') }}
                        </p>
                    </div>
                    <Button
                        v-if="savedOf(p.field) && !clearing(p.field)"
                        type="button"
                        :label="t('group.chatRelease')"
                        size="small"
                        severity="danger"
                        variant="text"
                        class="shrink-0"
                        :disabled="saving"
                        :aria-label="t('group.chatReleaseAria', { name: t(p.labelKey) })"
                        @click="release(p.field)"
                    />
                    <Button v-else-if="savedOf(p.field)" type="button" :label="t('common.reset')" size="small" severity="secondary" variant="text" class="shrink-0" :disabled="saving" @click="restore(p.field)" />
                </div>

                <LabeledField :label="t('group.chatUrlField')" :error="fieldError(p.field)">
                    <template #default="{ id: fieldId, describedBy, invalid }">
                        <InputText
                            :id="fieldId"
                            v-model="form[p.urlKey]"
                            :name="`${p.field}-url`"
                            type="url"
                            inputmode="url"
                            autocomplete="off"
                            autocapitalize="none"
                            spellcheck="false"
                            fluid
                            :placeholder="p.placeholder"
                            :invalid="invalid"
                            :aria-describedby="describedBy"
                            :disabled="saving"
                            :data-testid="`group-chat-${p.field}-url`"
                        />
                    </template>
                </LabeledField>

                <LabeledField :label="t('group.chatLabelField')" :hint="t('group.chatLabelHint', { max: UtilGroup.LABEL_MAX })">
                    <template #aside>
                        <small class="text-xs tabular-nums" :class="labelLength(p.field) > UtilGroup.LABEL_MAX ? 'font-semibold text-red-600 dark:text-red-400' : 'text-muted-color'">{{ t('group.counter', { n: labelLength(p.field), max: UtilGroup.LABEL_MAX }) }}</small>
                    </template>
                    <template #default="{ id: fieldId, describedBy }">
                        <InputText :id="fieldId" v-model="form[p.labelKeyForm]" :name="`${p.field}-label`" autocomplete="off" fluid :aria-describedby="describedBy" :disabled="saving" :data-testid="`group-chat-${p.field}-label`" />
                    </template>
                </LabeledField>

                <p v-if="clearing(p.field)" class="rounded-lg bg-amber-100 px-3 py-2 text-xs leading-relaxed text-amber-900 dark:bg-amber-900/40 dark:text-amber-200" role="status">
                    {{ t('group.chatWillClear', { name: t(p.labelKey) }) }}
                </p>
            </section>

            <div class="flex flex-col gap-2">
                <Message v-if="saveSummary" severity="error" variant="simple">{{ saveSummary }}</Message>
                <p v-if="changed" role="status" class="text-xs text-amber-700 dark:text-amber-400">{{ t('common.saveBarDirty') }}</p>
                <Button type="submit" :label="t('common.save')" fluid :loading="saving" :disabled="!canSave" data-testid="group-chat-save" />
                <p class="text-xs text-muted-color">{{ t('group.chatSaveHelp') }}</p>
            </div>
        </form>
    </GroupManageShell>
</template>

<script setup lang="ts">
import type { GroupChatLink } from '~/composables/group/api/useGroupApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilGroup, { type GroupChatField, type GroupChatForm } from '~/utils/util-group';
import UtilGroupMessages from '~/utils/util-group-messages';

definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
useSeoMeta({ title: () => t('group.chatTitle'), robots: 'noindex,nofollow' });

const route = useRoute();
const id = String(route.params.id);
const uid = useId();
const api = useGroupApi();
const toast = useAppToast();
const { confirm } = useConfirmDialog();
const { state, errorMessage, group, load, patch, block } = useGroupManage(id, 'LEADER');

const PROVIDERS: readonly { field: GroupChatField; urlKey: 'kakaoUrl' | 'discordUrl'; labelKeyForm: 'kakaoLabel' | 'discordLabel'; labelKey: string; placeholder: string }[] = [
    { field: 'kakao', urlKey: 'kakaoUrl', labelKeyForm: 'kakaoLabel', labelKey: 'group.chatKakao', placeholder: 'https://open.kakao.com/o/…' },
    { field: 'discord', urlKey: 'discordUrl', labelKeyForm: 'discordLabel', labelKey: 'group.chatDiscord', placeholder: 'https://discord.gg/…' },
];

const form = reactive<GroupChatForm>({ kakaoUrl: '', kakaoLabel: '', discordUrl: '', discordLabel: '' });
const saving = ref(false);
const serverErrors = ref<Partial<Record<GroupChatField, string>>>({});
const saveSummary = ref('');

let filled = false;
watch(
    group,
    (g) => {
        if (!g || filled) return;
        Object.assign(form, UtilGroup.chatFormOf(g.chatLinks));
        filled = true;
    },
    { immediate: true },
);

function savedOf(field: GroupChatField): GroupChatLink | null {
    return group.value ? UtilGroup.chatLinkOf(group.value.chatLinks, field) : null;
}

function clearing(field: GroupChatField): boolean {
    return group.value ? UtilGroup.chatClears(form, group.value.chatLinks).includes(field) : false;
}

function labelLength(field: GroupChatField): number {
    return UtilGroup.trimmedLength(field === 'kakao' ? form.kakaoLabel : form.discordLabel);
}

const problems = computed(() => UtilGroup.chatProblems(form));
const changed = computed(() => (group.value ? UtilGroup.isChatChanged(form, group.value.chatLinks) : false));
const canSave = computed(() => group.value !== null && !group.value.blockedReason && changed.value && Object.keys(problems.value).length === 0 && !saving.value);

function fieldError(field: GroupChatField): string {
    const problem = problems.value[field];
    if (problem) return UtilGroupMessages.chatProblem(t, field, problem);
    return serverErrors.value[field] ?? '';
}

for (const p of PROVIDERS) {
    watch(
        () => [form[p.urlKey], form[p.labelKeyForm]],
        () => {
            if (serverErrors.value[p.field] !== undefined) {
                const rest = { ...serverErrors.value };
                delete rest[p.field];
                serverErrors.value = rest;
            }
            saveSummary.value = '';
        },
    );
}

function release(field: GroupChatField) {
    if (field === 'kakao') Object.assign(form, { kakaoUrl: '', kakaoLabel: '' });
    else Object.assign(form, { discordUrl: '', discordLabel: '' });
}

function restore(field: GroupChatField) {
    if (!group.value) return;
    const saved = UtilGroup.chatFormOf(group.value.chatLinks);
    if (field === 'kakao') Object.assign(form, { kakaoUrl: saved.kakaoUrl, kakaoLabel: saved.kakaoLabel });
    else Object.assign(form, { discordUrl: saved.discordUrl, discordLabel: saved.discordLabel });
}

async function save() {
    if (!group.value || !canSave.value) return;
    const request = UtilGroup.toChatRequest(form);
    const hasAny = request.kakao !== null || request.discord !== null;
    if (!hasAny && !(await confirm({ title: t('group.chatClearAllTitle'), message: t('group.chatClearAllMessage'), confirmLabel: t('group.chatClearAllConfirm'), tone: 'danger' }))) return;

    saving.value = true;
    serverErrors.value = {};
    saveSummary.value = '';
    try {
        await api.setChatLinks(id, request);
        patch({ chatLinks: UtilGroup.chatLinksOf(request) });
        if (group.value) Object.assign(form, UtilGroup.chatFormOf(group.value.chatLinks));
        toast.success(hasAny ? t('group.chatSavedAny') : t('group.chatSavedNone'), 5000);
    } catch (e) {
        if (UtilGroup.isSessionLost(e)) return;
        const fields = UtilGroup.serverFields(e, ['kakao', 'discord'] as const);
        const keys = Object.keys(fields) as GroupChatField[];
        if (keys.length) {
            serverErrors.value = fields;
            saveSummary.value = UtilGroupMessages.serverFieldsSummary(
                t,
                keys.map((k) => t(k === 'kakao' ? 'group.chatKakao' : 'group.chatDiscord')),
            );
            return;
        }
        const blocked = UtilGroup.blockedReasonOf(e);
        if (blocked) block(blocked);
        if (UtilGroup.isCode(e, 'GROUP_LEADER_ONLY')) {
            toast.error(UtilBoardMessages.of(t, e), 8000);
            filled = false;
            await load();
            return;
        }
        toast.error(UtilGroupMessages.failed(t, 'group.saveFailed', e), 8000);
    } finally {
        saving.value = false;
    }
}

onBeforeRouteLeave(async () => {
    if (!changed.value || saving.value) return true;
    return await confirm({ title: t('group.leaveUnsavedTitle'), message: t('group.leaveUnsavedMessage'), confirmLabel: t('group.leaveUnsavedConfirm'), tone: 'danger' });
});
</script>
