<template>
    <!--
        메일 템플릿(`/settings/mail-templates`, 설정 > 시스템 「메일 서버」 다음) — 서버가 보내는 메일의 제목·본문을 고친다. 계약 `docs/features/message-template-contract.md`(화면 목업 단계).
        구획(탭 없이 `AppGadget anchor` + 우측 구역 내비):
          · 공통 레이아웃(`mail-layout`) — 로고·사이트 이름 보이기 + 언어별 틀(간단 에디터, `{{content}}` 한 번 — 지울 수 없음) + 미리보기. 구획 하나 = 저장 하나(저장 줄은 구획 맨 아래).
          · 왼쪽 메일 종류 목록(묶음: 시스템 메일 / 관리자 알림 — 영역별) — 지금 쓰는 템플릿 · 규칙 수 · 보안 메일(자물쇠). 고른 종류는 주소 `?kind=` 에 남는다.
          · 오른쪽(고른 종류): ① 쓰는 템플릿(`mail-selected` — 언어별 기본으로 쓸 템플릿) ② 특수한 상황(`mail-rules` — 조건 규칙, 위에서부터 맞는 첫 규칙. 순서 위/아래 · 추가 · 삭제)
            — ①②는 `PUT kinds/{kind}` 한 번으로 저장하므로 하단 떠 있는 저장 줄(`StickySaveBar`)이다. ③ 템플릿 목록(`mail-templates-list` — 기본 템플릿은 읽기 전용 + 복사해서 만들기, 사용자 템플릿은 편집·복사·삭제 — 바로 반영).
          · 템플릿 편집은 서랍(`MailTemplateEditDrawer`) — 저장·시험 발송·변경 이력(제목 옆 단추 → 오른쪽 이력 서랍 `SettingHistoryDrawer`, 처음 열 때 받는다).
        권한: 화면 `template.read`(메뉴가 거른다) · 바꾸기 `template.manage` — 없으면 입력 잠금, 저장 줄·편집·복사·삭제·시험 단추 없음(감추는 것은 편의이고 막는 것은 서버다). 서버 `Permission` 에는 아직 없다(목업 후보).
        ⚠ MOCK: API 는 메모리 목업이다(`useMailTemplateApi` · `?mock=…` 스위치는 `composables/template/api/mock/mail-template-mock.ts` 머리말). 새로고침하면 처음 상태다.
    -->
    <div class="flex flex-col gap-4">
        <PageTitle :title="t('mailTemplate.title')" :subtitle="t('mailTemplate.subtitle')" />

        <Message v-if="denied" severity="info" :closable="false" size="small">{{ t('mailTemplate.denied') }}</Message>
        <Message v-else-if="!canManage" severity="info" :closable="false" size="small">{{ t('mailTemplate.readOnly') }}</Message>

        <template v-if="!denied">
            <!-- 공통 레이아웃 -->
            <AppGadget :title="t('mailTemplate.layoutTitle')" anchor="mail-layout" :changes="layoutChanges" :invalid="layoutInvalid">
                <div v-if="layoutLoading && !layout" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
                    <Skeleton height="2rem" width="min(24rem, 100%)" />
                    <Skeleton height="8rem" />
                </div>
                <ErrorState v-else-if="!layout" :title="t('mailTemplate.layoutLoadFailed')" :description="layoutError?.message" retry :retrying="layoutLoading" @retry="loadLayout" />

                <div v-else class="flex flex-col gap-4">
                    <p class="m-0 text-xs leading-relaxed text-muted-color">{{ t('mailTemplate.layoutHint', { token: contentToken }) }}</p>

                    <div class="grid gap-4 xl:grid-cols-2">
                        <div class="flex min-w-0 flex-col gap-4">
                            <div class="flex flex-wrap gap-x-6 gap-y-2">
                                <label class="inline-flex items-center gap-2 text-sm">
                                    <ToggleSwitch v-model="layoutForm.showLogo" :disabled="!canManage || layoutSaving" />
                                    {{ t('mailTemplate.showLogo') }}
                                </label>
                                <label class="inline-flex items-center gap-2 text-sm">
                                    <ToggleSwitch v-model="layoutForm.showSiteName" :disabled="!canManage || layoutSaving" />
                                    {{ t('mailTemplate.showSiteName', { name: layout.siteName }) }}
                                </label>
                            </div>
                            <p v-if="layoutForm.showLogo && !layout.logoUrl" class="-mt-2 m-0 text-xs text-muted-color">
                                {{ t('mailTemplate.noLogo') }}
                                <NuxtLink v-if="can('site.read')" to="/settings/general" class="ml-1 font-medium underline underline-offset-2">{{ t('mailTemplate.noLogoLink') }}</NuxtLink>
                            </p>

                            <div class="flex flex-col gap-1.5">
                                <div class="flex flex-wrap items-center justify-between gap-2">
                                    <span :id="frameLabelId" class="text-sm font-medium">{{ t('mailTemplate.frameLabel') }}</span>
                                    <div class="flex items-center gap-2">
                                        <SelectButton v-model="layoutLocale" :options="localeOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('mailTemplate.localeLabel')" />
                                        <MailTemplateVariableMenu v-if="canManage" :variables="layout.variables" :disabled="layoutSaving" @pick="insertFrame" />
                                    </div>
                                </div>
                                <RichEditor
                                    :key="`${layoutKey}-${layoutLocale}`"
                                    ref="frameEditor"
                                    v-model="layoutForm.frames[layoutLocale]"
                                    level="SIMPLE"
                                    compact
                                    :disabled="!canManage || layoutSaving"
                                    :invalid="Boolean(frameError(layoutLocale))"
                                    :labelledby="frameLabelId"
                                    :describedby="frameMessageId"
                                    @ready="(html: string) => onFrameReady(layoutLocale, html)"
                                />
                                <div :id="frameMessageId" aria-live="polite" class="text-xs leading-relaxed">
                                    <p v-if="frameError(layoutLocale)" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ frameError(layoutLocale) }}</p>
                                    <p v-else-if="frameError(otherLocale)" role="alert" class="m-0 text-red-600 dark:text-red-400">{{ t('mailTemplate.frameOtherError', { locale: UtilMailTemplateMessages.localeLabel(t, otherLocale) }) }}</p>
                                    <p v-else class="m-0 text-muted-color">{{ t('mailTemplate.frameHelp', { token: contentToken }) }}</p>
                                </div>
                            </div>
                        </div>

                        <MailTemplatePreviewFrame :subject="layoutPreview.subject" :html="layoutPreview.html" :loading="layoutPreview.loading" :error="layoutPreview.error" height="22rem" />
                    </div>

                    <Message v-if="layoutFormError" severity="error" variant="simple" size="small">{{ layoutFormError }}</Message>

                    <div class="flex flex-wrap items-center gap-2 border-t border-(--line) pt-4">
                        <p v-if="layout.updatedAt" class="m-0 text-xs text-muted-color">
                            {{ layout.updatedByName ? t('mailTemplate.updatedBy', { at: time.full(layout.updatedAt), name: layout.updatedByName }) : time.full(layout.updatedAt) }}
                        </p>
                        <div v-if="canManage" class="ml-auto flex flex-wrap items-center gap-2">
                            <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" size="small" :disabled="layoutSaving || layoutChanges === 0" @click="revertLayout" />
                            <Button type="button" :label="t('common.save')" icon="pi pi-check" size="small" :loading="layoutSaving" :disabled="layoutChanges === 0 || layoutInvalid > 0" @click="saveLayout" />
                        </div>
                    </div>
                </div>
            </AppGadget>

            <!-- 메일 종류 -->
            <div v-if="kindsLoading && !kinds" class="grid gap-4 lg:grid-cols-[17rem_minmax(0,1fr)]" aria-busy="true">
                <span class="sr-only">{{ t('common.loading') }}</span>
                <Skeleton height="20rem" />
                <Skeleton height="20rem" />
            </div>
            <ErrorState v-else-if="kindsError" :title="t('mailTemplate.kindsLoadFailed')" :description="kindsError.message" retry :retrying="kindsLoading" @retry="loadKinds" />
            <EmptyState v-else-if="!kinds?.length" icon="pi pi-envelope" :title="t('mailTemplate.kindsEmptyTitle')" :description="t('mailTemplate.kindsEmptyBody')" />

            <div v-else class="grid items-start gap-4 lg:grid-cols-[17rem_minmax(0,1fr)]">
                <!-- 왼쪽 — 메일 종류 목록 -->
                <AppGadget :title="t('mailTemplate.kindsTitle')">
                    <nav :aria-label="t('mailTemplate.kindsTitle')" class="flex flex-col gap-3">
                        <section v-for="group in kindGroups" :key="group.id" :aria-labelledby="`${idBase}-${group.id}`">
                            <h3 :id="`${idBase}-${group.id}`" class="m-0 mb-1 px-1 text-[11px] font-normal text-muted-color">{{ group.label }}</h3>
                            <ul class="m-0 flex list-none flex-col gap-0.5 p-0">
                                <li v-for="item in group.items" :key="item.kind">
                                    <button
                                        type="button"
                                        class="flex w-full cursor-pointer items-center gap-2 rounded-lg border-0 bg-transparent px-2.5 py-2 text-left focus-visible:outline-2 focus-visible:outline-primary"
                                        :class="item.kind === selectedKind ? 'bg-primary-50! dark:bg-primary-950!' : 'hover:bg-(--p-content-hover-background)'"
                                        :aria-current="item.kind === selectedKind ? 'true' : undefined"
                                        @click="pickKind(item.kind)"
                                    >
                                        <span class="min-w-0 flex-1">
                                            <span class="flex min-w-0 items-center gap-1.5">
                                                <span class="truncate text-[13px]" :class="item.kind === selectedKind ? 'text-primary-700 dark:text-primary-300' : ''">{{ item.name }}</span>
                                                <span v-if="item.security" v-tooltip.top="t('mailTemplate.securityTip')" class="inline-flex shrink-0" role="img" :aria-label="t('mailTemplate.securityTag')">
                                                    <i class="pi pi-lock text-[11px] text-amber-600 dark:text-amber-400" aria-hidden="true" />
                                                </span>
                                            </span>
                                            <span class="block truncate text-[11px] text-muted-color">{{ t('mailTemplate.kindUsing', { ko: item.selected.ko.name, en: item.selected.en.name }) }}</span>
                                        </span>
                                        <span v-if="item.ruleCount > 0" class="inline-flex shrink-0 items-center justify-center rounded-full bg-surface-100 px-1.5 py-0.5 text-[10px] leading-none text-muted-color dark:bg-surface-800" :aria-label="t('mailTemplate.ruleCount', { count: item.ruleCount })">
                                            <span class="mail-template-pill">{{ t('mailTemplate.ruleCount', { count: item.ruleCount }) }}</span>
                                        </span>
                                    </button>
                                </li>
                            </ul>
                        </section>
                    </nav>
                </AppGadget>

                <!-- 오른쪽 — 고른 종류 -->
                <div class="flex min-w-0 flex-col gap-4">
                    <div v-if="currentKind" class="flex flex-col gap-1">
                        <div class="flex flex-wrap items-center gap-2">
                            <h2 class="m-0 text-base font-semibold">{{ currentKind.name }}</h2>
                            <Tag v-if="currentKind.security" :value="t('mailTemplate.securityTag')" icon="pi pi-shield" severity="warn" class="mail-template-tag text-[11px]!" />
                            <code class="text-[11px] text-muted-color">{{ currentKind.kind }}</code>
                        </div>
                        <p class="m-0 text-xs text-muted-color">{{ currentKind.description }}</p>
                        <p v-if="currentKind.security" class="m-0 text-xs text-amber-700 dark:text-amber-400">{{ t('mailTemplate.securityHint', { names: requiredNames }) }}</p>
                    </div>

                    <div v-if="detailLoading && !detail" class="flex flex-col gap-4" aria-busy="true">
                        <Skeleton v-for="n in 3" :key="n" height="8rem" />
                    </div>
                    <ErrorState v-else-if="detailError" :title="t('mailTemplate.detailLoadFailed')" :description="detailError.message" retry :retrying="detailLoading" @retry="loadDetail" />

                    <template v-else-if="detail && currentKind">
                        <!-- ① 쓰는 템플릿 -->
                        <AppGadget :title="t('mailTemplate.selectedTitle')" anchor="mail-selected" :changes="selectedChanges" :invalid="selectedInvalid">
                            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('mailTemplate.selectedHint') }}</p>
                            <div class="grid gap-4 sm:grid-cols-2">
                                <LabeledField v-for="locale in LOCALES" :key="locale" :label="t('mailTemplate.selectedFor', { locale: UtilMailTemplateMessages.localeLabel(t, locale) })" :error="kindFieldErrors[`selected.${locale}`]">
                                    <template #default="{ id, describedBy, invalid }">
                                        <Select v-model="kindForm.selected[locale]" :input-id="id" :options="templateOptionsOf(locale)" option-label="label" option-value="value" :disabled="!canManage || kindSaving" :invalid="invalid" :aria-describedby="describedBy" fluid />
                                    </template>
                                </LabeledField>
                            </div>
                        </AppGadget>

                        <!-- ② 특수한 상황 -->
                        <AppGadget :title="t('mailTemplate.rulesTitle')" anchor="mail-rules" :changes="rulesChanges" :invalid="rulesInvalid">
                            <template v-if="canManage && currentKind.conditions.length" #actions>
                                <Button type="button" :label="t('mailTemplate.ruleAdd')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" :disabled="kindSaving || kindForm.rules.length >= RULE_MAX" @click="addRule" />
                            </template>
                            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('mailTemplate.rulesHint') }}</p>

                            <EmptyState v-if="!currentKind.conditions.length" icon="pi pi-filter-slash" :title="t('mailTemplate.noConditionsTitle')" :description="t('mailTemplate.noConditionsBody')" />
                            <p v-else-if="!kindForm.rules.length" class="m-0 rounded-lg border border-dashed border-(--line) p-4 text-center text-xs text-muted-color">{{ t('mailTemplate.rulesEmpty') }}</p>

                            <ol v-else class="m-0 flex list-none flex-col gap-2 p-0">
                                <li v-for="(rule, at) in kindForm.rules" :key="rule.uid" class="flex flex-col gap-1.5 rounded-lg border border-(--line) p-2.5">
                                    <div class="flex flex-wrap items-center gap-2">
                                        <span class="inline-flex size-6 shrink-0 items-center justify-center rounded-full bg-surface-100 text-[11px] leading-none tabular-nums dark:bg-surface-800" :aria-label="t('mailTemplate.ruleOrder', { n: at + 1 })">{{ at + 1 }}</span>
                                        <span class="text-xs text-muted-color">{{ t('mailTemplate.ruleWhen') }}</span>
                                        <Select
                                            :model-value="conditionValue(rule)"
                                            :options="conditionOptions"
                                            option-label="label"
                                            option-value="value"
                                            option-group-label="label"
                                            option-group-children="items"
                                            :disabled="!canManage || kindSaving"
                                            :invalid="Boolean(ruleError(at, 'condition'))"
                                            :aria-label="t('mailTemplate.ruleConditionLabel', { n: at + 1 })"
                                            class="min-w-44 flex-1"
                                            @update:model-value="(value: string) => setCondition(rule, value)"
                                        />
                                        <span class="text-xs text-muted-color">{{ t('mailTemplate.ruleUse') }}</span>
                                        <Select
                                            v-model="rule.templateId"
                                            :options="allTemplateOptions"
                                            option-label="label"
                                            option-value="value"
                                            :placeholder="t('mailTemplate.ruleTemplatePlaceholder')"
                                            :disabled="!canManage || kindSaving"
                                            :invalid="Boolean(ruleError(at, 'templateId'))"
                                            :aria-label="t('mailTemplate.ruleTemplateLabel', { n: at + 1 })"
                                            class="min-w-44 flex-1"
                                        />
                                        <div v-if="canManage" class="flex items-center">
                                            <Button type="button" icon="pi pi-arrow-up" size="small" severity="secondary" variant="text" rounded :disabled="at === 0 || kindSaving" :aria-label="t('mailTemplate.ruleUp', { n: at + 1 })" v-tooltip.top="t('mailTemplate.ruleUpTip')" @click="moveRule(at, -1)" />
                                            <Button type="button" icon="pi pi-arrow-down" size="small" severity="secondary" variant="text" rounded :disabled="at === kindForm.rules.length - 1 || kindSaving" :aria-label="t('mailTemplate.ruleDown', { n: at + 1 })" v-tooltip.top="t('mailTemplate.ruleDownTip')" @click="moveRule(at, 1)" />
                                            <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" rounded :disabled="kindSaving" :aria-label="t('mailTemplate.ruleRemove', { n: at + 1 })" v-tooltip.top="t('common.delete')" @click="removeRule(at)" />
                                        </div>
                                    </div>
                                    <p v-if="ruleError(at, 'condition') || ruleError(at, 'templateId')" role="alert" class="m-0 pl-8 text-xs text-red-600 dark:text-red-400">{{ ruleError(at, 'condition') || ruleError(at, 'templateId') }}</p>
                                    <p v-else-if="shadowedBy(at) !== null" class="m-0 pl-8 text-xs text-amber-700 dark:text-amber-400">{{ t('mailTemplate.ruleShadowed', { n: (shadowedBy(at) ?? 0) + 1 }) }}</p>
                                </li>
                            </ol>
                            <p v-if="kindFieldErrors.rules" role="alert" class="m-0 mt-2 text-xs text-red-600 dark:text-red-400">{{ kindFieldErrors.rules }}</p>
                        </AppGadget>

                        <!-- ③ 템플릿 목록 -->
                        <AppGadget :title="t('mailTemplate.listTitle')" anchor="mail-templates-list">
                            <template #actions>
                                <span class="text-xs tabular-nums text-muted-color">{{ t('mailTemplate.listCount', { count: userTemplateCount, max: MAIL_TEMPLATE_LIMIT }) }}</span>
                            </template>
                            <p class="m-0 mb-3 text-xs leading-relaxed text-muted-color">{{ t('mailTemplate.listHint') }}</p>
                            <DataTable :value="detail.templates" data-key="id" :size="prefs.density === 'compact' ? 'small' : undefined" scrollable>
                                <Column :header="t('mailTemplate.colName')" style="min-width: 15rem">
                                    <template #body="{ data }">
                                        <div class="flex min-w-0 flex-col gap-1">
                                            <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                                                <span class="text-[13px] font-medium">{{ data.name }}</span>
                                                <Tag v-if="data.builtin" :value="t('mailTemplate.builtinTag')" icon="pi pi-lock" severity="secondary" class="mail-template-tag text-[10px]!" />
                                            </div>
                                            <div v-if="usageOf(data).length" class="flex flex-wrap items-center gap-1">
                                                <Tag v-for="label in usageOf(data)" :key="label" :value="label" severity="info" class="mail-template-tag text-[10px]!" />
                                            </div>
                                        </div>
                                    </template>
                                </Column>
                                <Column :header="t('mailTemplate.colLocale')" style="width: 7rem">
                                    <template #body="{ data }">
                                        <span class="text-[12px]">{{ UtilMailTemplateMessages.localeLabel(t, data.locale) }}</span>
                                    </template>
                                </Column>
                                <Column :header="t('mailTemplate.colUpdated')" style="width: 12rem">
                                    <template #body="{ data }">
                                        <span v-if="data.updatedAt" class="text-[12px]">
                                            <span class="tabular-nums">{{ time.listLabel(data.updatedAt) }}</span>
                                            <span v-if="data.updatedByName" class="block text-[11px] text-muted-color">{{ data.updatedByName }}</span>
                                        </span>
                                        <span v-else class="text-[12px] text-muted-color">{{ t('mailTemplate.builtinUpdated') }}</span>
                                    </template>
                                </Column>
                                <Column :header="t('mailTemplate.colActions')" style="width: 15rem">
                                    <template #body="{ data }">
                                        <div class="flex flex-wrap items-center gap-1">
                                            <Button type="button" :label="canManage && !data.builtin ? t('common.edit') : t('common.show')" size="small" severity="secondary" variant="outlined" @click="openEdit(data)" />
                                            <span v-if="canManage" v-tooltip.top="copyBlockedReason || undefined" class="inline-flex">
                                                <Button type="button" :label="data.builtin ? t('mailTemplate.copyCreate') : t('mailTemplate.copy')" icon="pi pi-copy" size="small" severity="secondary" variant="text" :disabled="Boolean(copyBlockedReason)" :aria-description="copyBlockedReason || undefined" @click="openCopy(data)" />
                                            </span>
                                            <span v-if="canManage && !data.builtin" v-tooltip.top="deleteBlockedReason(data) || undefined" class="inline-flex">
                                                <Button type="button" icon="pi pi-trash" size="small" severity="danger" variant="text" :disabled="Boolean(deleteBlockedReason(data)) || deletingId === data.id" :loading="deletingId === data.id" :aria-label="t('mailTemplate.deleteLabel', { name: data.name })" :aria-description="deleteBlockedReason(data) || undefined" @click="confirmDelete(data)" />
                                            </span>
                                        </div>
                                    </template>
                                </Column>
                            </DataTable>
                        </AppGadget>

                        <StickySaveBar v-if="canManage" revertable :dirty="kindDirty" :saving="kindSaving" :disabled="selectedInvalid + rulesInvalid > 0" :count="selectedChanges + rulesChanges" :error-count="selectedInvalid + rulesInvalid" :error="kindFormError" @save="saveKind" @revert="revertKind" />
                    </template>
                </div>
            </div>
        </template>

        <MailTemplateEditDrawer
            :visible="editOpen"
            :kind="currentKind"
            :template="editTemplate"
            :mode="editMode"
            :defaults="defaultTemplates"
            :can-manage="canManage"
            :locale-locked="editLocaleLocked"
            :history-available="editMode === 'edit' && Boolean(editTemplate && !editTemplate.builtin)"
            :saving="editSaving"
            :field-errors="editFieldErrors"
            :form-error="editFormError"
            :previewer="api.preview"
            @update:visible="onEditVisible"
            @update:dirty="(v: boolean) => (editDirty = v)"
            @save="saveTemplate"
            @test="openTest"
            @copy="openCopy"
            @history="openHistory"
        />

        <!-- 템플릿 변경 이력 — 편집 서랍 제목 옆 단추가 오른쪽 서랍으로 연다(처음 열 때 받는다) -->
        <SettingHistoryDrawer v-model:visible="historyOpen" :hint="t('mailTemplate.historyHint')">
            <SettingHistoryTable
                :items="history.rows.value"
                :total="history.total.value"
                :first="history.first.value"
                :page-size="history.size.value"
                :loading="history.loading.value"
                :loaded="history.loaded.value"
                :error="history.error.value"
                :compact="prefs.density === 'compact'"
                @page="history.onPage"
                @retry="history.load"
            />
        </SettingHistoryDrawer>

        <MailTestDialog v-model:visible="testOpen" v-model:to="testTo" :help="t('mailTemplate.testHelp')" :to-error="testToError" :error="testError" :testing="testing" :result="testResult" @send="runTest" />
    </div>
</template>

<script setup lang="ts">
import type { MailTestDialogResult } from '~/components/mail/MailTestDialog.vue';
import type { MailTemplateTestDraft } from '~/components/template/MailTemplateEditDrawer.vue';
import type { ApiError } from '~/composables/common/useApiClient';
import { mockTemplateCan } from '~/composables/template/api/mock/mail-template-mock';
import type { MailKindDetailView, MailKindView, MailLayoutView, MailLocale, MailTemplateSaveRequest, MailTemplateView } from '~/composables/template/api/useMailTemplateApi.types';
import { MAIL_TEMPLATE_LIMIT } from '~/composables/template/api/useMailTemplateApi.types';
import UtilMailSettingsMessages from '~/utils/util-mail-settings-messages';
import UtilMailTemplate from '~/utils/util-mail-template';
import UtilMailTemplateMessages from '~/utils/util-mail-template-messages';
import UtilNotification from '~/utils/util-notification';
import UtilNotificationMessages from '~/utils/util-notification-messages';
import UtilValidation from '~/utils/util-validation';

const { t } = useI18n();
useSeoMeta({ title: () => t('mailTemplate.title') });

const api = useMailTemplateApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const route = useRoute();
const router = useRouter();
const { user } = useAdminAuth();
const { confirm } = useConfirmDialog();
// ⚠ 통째로 받아 `prefs.density` 를 쓰지 않으면 조밀 모드가 이 표에만 안 먹는다
const { prefs } = useLayoutPrefs();
// MOCK: 목업 스위치(`?mock=template-readonly|template-denied`)로 권한을 흉내 낸다 — 서버가 붙으면 아래 REAL 줄로 바꾼다
const can = mockTemplateCan(useAdminPermission().can);
// REAL: const { can } = useAdminPermission();

/** 바꾸는 데 필요한 권한(정의: util-permission-catalog.ts — 목업 후보) */
const canManage = computed(() => can('template.manage'));

const LOCALES: readonly MailLocale[] = ['ko', 'en'];
/** 규칙 수 상한(목업 가정) */
const RULE_MAX = 10;
const contentToken = UtilMailTemplate.token(UtilMailTemplate.CONTENT);
const idBase = useId();
const frameLabelId = useId();
const frameMessageId = useId();

const localeOptions = computed(() => LOCALES.map((value) => ({ value, label: UtilMailTemplateMessages.localeLabel(t, value) })));

/** 읽기 권한이 없다(403) — 오류가 아니라 안내다(`forms.md`) */
const denied = ref(false);
function isDenied(error: ApiError): boolean {
    return error.status === 403;
}

/* ── 공통 레이아웃 ── */

const layout = ref<MailLayoutView | null>(null);
const layoutLoading = ref(false);
const layoutError = ref<ApiError | null>(null);
const layoutSaving = ref(false);
const layoutFormError = ref('');
const layoutServerErrors = ref<Record<string, string>>({});
const layoutKey = ref(0);
const layoutLocale = ref<MailLocale>('ko');
const otherLocale = computed<MailLocale>(() => (layoutLocale.value === 'ko' ? 'en' : 'ko'));
const layoutForm = reactive<{ showLogo: boolean; showSiteName: boolean; frames: Record<MailLocale, string> }>({ showLogo: true, showSiteName: true, frames: { ko: '', en: '' } });
/** 기준 틀 — 에디터가 정규화한 값으로 바꿔 둔다(`onFrameReady`) */
const frameBaseline = reactive<Record<MailLocale, string>>({ ko: '', en: '' });
const frameEditor = ref<{ insertText: (text: string) => void } | null>(null);

function fillLayout(view: MailLayoutView): void {
    layout.value = view;
    layoutForm.showLogo = view.showLogo;
    layoutForm.showSiteName = view.showSiteName;
    layoutForm.frames = { ...view.frames };
    frameBaseline.ko = view.frames.ko;
    frameBaseline.en = view.frames.en;
    layoutServerErrors.value = {};
    layoutFormError.value = '';
    layoutKey.value += 1;
}

function onFrameReady(locale: MailLocale, html: string): void {
    if (layoutForm.frames[locale] === frameBaseline[locale]) {
        frameBaseline[locale] = html;
        layoutForm.frames[locale] = html;
    }
}

const layoutChanges = computed(() => {
    if (!layout.value) return 0;
    let n = 0;
    if (layoutForm.showLogo !== layout.value.showLogo) n += 1;
    if (layoutForm.showSiteName !== layout.value.showSiteName) n += 1;
    for (const locale of LOCALES) if (layoutForm.frames[locale] !== frameBaseline[locale]) n += 1;
    return n;
});

function frameError(locale: MailLocale): string {
    const server = layoutServerErrors.value[`frames.${locale}`];
    if (server) return server;
    if (!layout.value || !canManage.value) return '';
    const problem = UtilMailTemplate.validateFrame(layoutForm.frames[locale], layout.value.variables.map((v) => v.name));
    return problem ? UtilMailTemplateMessages.problem(t, 'bodyHtml', problem) : '';
}
const layoutInvalid = computed(() => LOCALES.filter((locale) => Boolean(frameError(locale))).length);

for (const locale of LOCALES) {
    watch(
        () => layoutForm.frames[locale],
        () => {
            if (layoutServerErrors.value[`frames.${locale}`]) layoutServerErrors.value = { ...layoutServerErrors.value, [`frames.${locale}`]: '' };
        },
    );
}

function insertFrame(name: string): void {
    frameEditor.value?.insertText(UtilMailTemplate.token(name));
}

async function loadLayout(): Promise<void> {
    layoutLoading.value = true;
    layoutError.value = null;
    try {
        fillLayout(await api.layout());
    } catch (e) {
        const error = toApiError(e);
        if (isDenied(error)) denied.value = true;
        else layoutError.value = error;
    } finally {
        layoutLoading.value = false;
    }
}

function revertLayout(): void {
    if (layout.value) fillLayout(layout.value);
}

async function saveLayout(): Promise<void> {
    if (layoutSaving.value || !canManage.value || layoutChanges.value === 0 || layoutInvalid.value > 0) return;
    layoutSaving.value = true;
    layoutFormError.value = '';
    try {
        fillLayout(await api.saveLayout({ showLogo: layoutForm.showLogo, showSiteName: layoutForm.showSiteName, frames: { ...layoutForm.frames } }));
        toast.success(t('mailTemplate.layoutSaved'));
    } catch (e) {
        const error = toApiError(e);
        const found: Record<string, string> = {};
        for (const locale of LOCALES) {
            const message = error.field(`frames.${locale}`);
            if (message) found[`frames.${locale}`] = message;
        }
        layoutServerErrors.value = found;
        if (Object.keys(found).length === 0) layoutFormError.value = error.message;
    } finally {
        layoutSaving.value = false;
    }
}

// 레이아웃 미리보기 — 저장 전 값으로(견본 본문 한 줄)
const layoutPreview = reactive({ subject: '', html: '', loading: false, error: '' });
let layoutPreviewTimer: ReturnType<typeof setTimeout> | null = null;
let layoutPreviewSeq = 0;
watch(
    () => [layout.value, layoutLocale.value, layoutForm.showLogo, layoutForm.showSiteName, layoutForm.frames[layoutLocale.value]] as const,
    () => {
        if (layoutPreviewTimer) clearTimeout(layoutPreviewTimer);
        layoutPreviewTimer = setTimeout(() => void runLayoutPreview(), 400);
    },
);
async function runLayoutPreview(): Promise<void> {
    if (!layout.value) return;
    const mine = ++layoutPreviewSeq;
    layoutPreview.loading = true;
    try {
        // 종류 없이 — 공통 변수만 견본 값으로 채운다
        const result = await api.preview({
            locale: layoutLocale.value,
            subject: t('mailTemplate.layoutSampleSubject'),
            bodyHtml: `<p>${UtilMailTemplate.escape(t('mailTemplate.layoutSampleBody'))}</p>`,
            layout: { showLogo: layoutForm.showLogo, showSiteName: layoutForm.showSiteName, frames: { ...layoutForm.frames } },
        });
        if (mine !== layoutPreviewSeq) return;
        layoutPreview.subject = result.subject;
        layoutPreview.html = result.html;
        layoutPreview.error = '';
    } catch (e) {
        if (mine === layoutPreviewSeq) layoutPreview.error = toApiError(e).message;
    } finally {
        if (mine === layoutPreviewSeq) layoutPreview.loading = false;
    }
}

/* ── 메일 종류 ── */

const kinds = ref<MailKindView[] | null>(null);
const kindsLoading = ref(false);
const kindsError = ref<ApiError | null>(null);
const selectedKind = ref<string | null>(null);
const currentKind = computed(() => kinds.value?.find((item) => item.kind === selectedKind.value) ?? null);
const requiredNames = computed(() => (currentKind.value?.variables ?? []).filter((v) => v.required).map((v) => UtilMailTemplate.token(v.name)).join(', '));

/** 묶음 — 시스템 메일, 그다음 관리자 알림(영역 차례는 알림 화면과 같다) */
const kindGroups = computed(() => {
    const list = kinds.value ?? [];
    const groups: { id: string; label: string; items: MailKindView[] }[] = [];
    const system = list.filter((item) => item.group === 'SYSTEM');
    if (system.length) groups.push({ id: 'system', label: t('mailTemplate.groupSystem'), items: system });
    const areas = [...new Set(list.filter((item) => item.group === 'NOTIFICATION').map((item) => item.area ?? ''))];
    areas.sort((a, b) => order(a) - order(b));
    for (const area of areas) {
        groups.push({ id: `notification-${area}`, label: t('mailTemplate.groupNotification', { area: UtilNotificationMessages.areaLabel(t, area) }), items: list.filter((item) => item.group === 'NOTIFICATION' && (item.area ?? '') === area) });
    }
    return groups;
});
function order(area: string): number {
    const at = UtilNotification.AREA_ORDER.indexOf(area);
    return at < 0 ? Number.MAX_SAFE_INTEGER : at;
}

async function loadKinds(): Promise<void> {
    kindsLoading.value = true;
    kindsError.value = null;
    try {
        kinds.value = await api.kinds();
        const wanted = typeof route.query.kind === 'string' ? route.query.kind : null;
        const first = kinds.value.find((item) => item.kind === wanted) ?? kinds.value[0];
        selectedKind.value = first?.kind ?? null;
        if (first) void loadDetail();
    } catch (e) {
        const error = toApiError(e);
        if (isDenied(error)) denied.value = true;
        else kindsError.value = error;
    } finally {
        kindsLoading.value = false;
    }
}

async function pickKind(kind: string): Promise<void> {
    if (kind === selectedKind.value) return;
    if (kindDirty.value && !(await confirm({ title: t('mailTemplate.leaveKindTitle'), message: t('mailTemplate.leaveKindMessage'), confirmLabel: t('mailTemplate.leaveConfirm'), tone: 'danger' }))) return;
    selectedKind.value = kind;
    void router.replace({ query: { ...route.query, kind } });
    void loadDetail();
}

/* ── 고른 종류 — ① 쓰는 템플릿 ② 특수한 상황 ── */

interface RuleRow {
    /** 화면 안 줄 열쇠(새 규칙도 갖는다) */
    uid: string;
    id?: string;
    conditionKey: string;
    conditionValue: string;
    templateId: string;
}

const detail = ref<MailKindDetailView | null>(null);
const detailLoading = ref(false);
const detailError = ref<ApiError | null>(null);
const kindSaving = ref(false);
const kindFormError = ref('');
const kindFieldErrors = ref<Record<string, string>>({});
const kindForm = reactive<{ selected: Record<MailLocale, string>; rules: RuleRow[] }>({ selected: { ko: '', en: '' }, rules: [] });
let uidSeq = 0;
let detailSeq = 0;

function fillDetail(view: MailKindDetailView): void {
    detail.value = view;
    kindForm.selected = { ...view.selected };
    kindForm.rules = view.rules.map((rule) => ({ uid: `r${(uidSeq += 1)}`, id: rule.id, conditionKey: rule.condition.key, conditionValue: rule.condition.value, templateId: rule.templateId }));
    kindFieldErrors.value = {};
    kindFormError.value = '';
    syncKindRow(view);
}

/** 왼쪽 목록의 「쓰는 템플릿 · 규칙 수」를 받은 상세에 맞춘다 */
function syncKindRow(view: MailKindDetailView): void {
    const row = kinds.value?.find((item) => item.kind === view.kind);
    if (!row) return;
    for (const locale of LOCALES) {
        const template = view.templates.find((item) => item.id === view.selected[locale]);
        if (template) row.selected[locale] = { id: template.id, name: template.name, builtin: template.builtin };
    }
    row.ruleCount = view.rules.length;
    row.templateCount = view.templates.filter((item) => !item.builtin).length;
}

async function loadDetail(): Promise<void> {
    const kind = selectedKind.value;
    if (!kind) return;
    const mine = ++detailSeq;
    detailLoading.value = true;
    detailError.value = null;
    detail.value = null;
    try {
        const view = await api.kind(kind);
        if (mine === detailSeq) fillDetail(view);
    } catch (e) {
        if (mine === detailSeq) detailError.value = toApiError(e);
    } finally {
        if (mine === detailSeq) detailLoading.value = false;
    }
}

/** 고르기 목록의 이름 — 기본 템플릿 이름은 서버가 「기본 템플릿」처럼 준다 */
function templateLabel(template: MailTemplateView): string {
    return template.name;
}

function templateOptionsOf(locale: MailLocale) {
    return (detail.value?.templates ?? []).filter((item) => item.locale === locale).map((item) => ({ value: item.id, label: templateLabel(item) }));
}
const allTemplateOptions = computed(() => (detail.value?.templates ?? []).map((item) => ({ value: item.id, label: `${templateLabel(item)} · ${UtilMailTemplateMessages.localeLabel(t, item.locale)}` })));

/** 조건 고르기 — 조건 이름별 묶음, 값은 `이름:값` */
const conditionOptions = computed(() => (currentKind.value?.conditions ?? []).map((condition) => ({ label: condition.label, items: condition.options.map((option) => ({ value: `${condition.key}:${option.value}`, label: `${condition.label}: ${option.label}` })) })));
const conditionValue = (rule: RuleRow) => `${rule.conditionKey}:${rule.conditionValue}`;
function setCondition(rule: RuleRow, value: string): void {
    const at = value.indexOf(':');
    rule.conditionKey = value.slice(0, at);
    rule.conditionValue = value.slice(at + 1);
}

function addRule(): void {
    const condition = currentKind.value?.conditions[0];
    const option = condition?.options[0];
    if (!condition || !option || kindForm.rules.length >= RULE_MAX) return;
    kindForm.rules.push({ uid: `r${(uidSeq += 1)}`, conditionKey: condition.key, conditionValue: option.value, templateId: '' });
}

function moveRule(at: number, delta: number): void {
    const to = at + delta;
    if (to < 0 || to >= kindForm.rules.length) return;
    const next = kindForm.rules.slice();
    const [row] = next.splice(at, 1);
    next.splice(to, 0, row as RuleRow);
    kindForm.rules = next;
    kindFieldErrors.value = {};
}

function removeRule(at: number): void {
    kindForm.rules = kindForm.rules.filter((_, index) => index !== at);
    kindFieldErrors.value = {};
}

/** 같은 조건이 위에 있으면 이 규칙은 쓰이지 않는다 — 위 규칙의 순번(없으면 null) */
function shadowedBy(at: number): number | null {
    const rule = kindForm.rules[at];
    if (!rule) return null;
    const found = kindForm.rules.findIndex((other, index) => index < at && other.conditionKey === rule.conditionKey && other.conditionValue === rule.conditionValue);
    return found < 0 ? null : found;
}

function ruleError(at: number, field: 'condition' | 'templateId'): string {
    const server = kindFieldErrors.value[`rules[${at}].${field}`];
    if (server) return server;
    const rule = kindForm.rules[at];
    if (field === 'templateId' && rule && !rule.templateId) return t('mailTemplate.ruleTemplateRequired');
    return '';
}

const selectedChanges = computed(() => (detail.value ? LOCALES.filter((locale) => kindForm.selected[locale] !== detail.value?.selected[locale]).length : 0));
const selectedInvalid = computed(() => LOCALES.filter((locale) => !kindForm.selected[locale] || Boolean(kindFieldErrors.value[`selected.${locale}`])).length);
const rulesChanges = computed(() => {
    const saved = detail.value?.rules ?? [];
    let n = Math.abs(saved.length - kindForm.rules.length);
    kindForm.rules.forEach((rule, at) => {
        const before = saved[at];
        if (before && (before.id !== rule.id || before.condition.key !== rule.conditionKey || before.condition.value !== rule.conditionValue || before.templateId !== rule.templateId)) n += 1;
    });
    return n;
});
const rulesInvalid = computed(() => kindForm.rules.filter((_, at) => Boolean(ruleError(at, 'condition') || ruleError(at, 'templateId'))).length + (kindFieldErrors.value.rules ? 1 : 0));
const kindDirty = computed(() => selectedChanges.value + rulesChanges.value > 0);

function revertKind(): void {
    if (detail.value) fillDetail(detail.value);
}

async function saveKind(): Promise<void> {
    const kind = selectedKind.value;
    if (!kind || kindSaving.value || !canManage.value || !kindDirty.value || selectedInvalid.value + rulesInvalid.value > 0) return;
    kindSaving.value = true;
    kindFormError.value = '';
    kindFieldErrors.value = {};
    try {
        fillDetail(
            await api.saveKind(kind, {
                selected: { ...kindForm.selected },
                rules: kindForm.rules.map((rule) => ({ ...(rule.id ? { id: rule.id } : {}), condition: { key: rule.conditionKey, value: rule.conditionValue }, templateId: rule.templateId })),
            }),
        );
        toast.success(t('mailTemplate.kindSaved'));
    } catch (e) {
        const error = toApiError(e);
        const fields = error.fields ?? {};
        kindFieldErrors.value = { ...fields };
        // 칸에 붙지 않는 오류만 저장 줄 안 한 줄 — 서버 문구 그대로
        if (Object.keys(fields).length === 0) kindFormError.value = error.message;
    } finally {
        kindSaving.value = false;
    }
}

watch(
    () => kindForm.selected,
    () => {
        for (const locale of LOCALES) if (kindFieldErrors.value[`selected.${locale}`]) kindFieldErrors.value = { ...kindFieldErrors.value, [`selected.${locale}`]: '' };
    },
    { deep: true },
);

/* ── ③ 템플릿 목록 ── */

const userTemplateCount = computed(() => (detail.value?.templates ?? []).filter((item) => !item.builtin).length);
const copyBlockedReason = computed(() => (userTemplateCount.value >= MAIL_TEMPLATE_LIMIT ? t('mailTemplate.limitReached', { max: MAIL_TEMPLATE_LIMIT }) : ''));
const defaultTemplates = computed<Partial<Record<MailLocale, MailTemplateView>>>(() => {
    const out: Partial<Record<MailLocale, MailTemplateView>> = {};
    for (const locale of LOCALES) {
        const found = detail.value?.templates.find((item) => item.id === detail.value?.defaults[locale]);
        if (found) out[locale] = found;
    }
    return out;
});

/** 저장된 상태에서 이 템플릿을 쓰는 곳(「한국어 기본」 · 「규칙 2」) */
function usageOf(template: MailTemplateView): string[] {
    const view = detail.value;
    if (!view) return [];
    const out: string[] = [];
    for (const locale of LOCALES) if (view.selected[locale] === template.id) out.push(t('mailTemplate.usedAsDefault', { locale: UtilMailTemplateMessages.localeLabel(t, locale) }));
    const rules = view.rules.filter((rule) => rule.templateId === template.id).length;
    if (rules > 0) out.push(t('mailTemplate.usedByRules', { count: rules }));
    return out;
}

function deleteBlockedReason(template: MailTemplateView): string {
    return usageOf(template).length ? t('mailTemplate.deleteInUse') : '';
}

const deletingId = ref<string | null>(null);

async function confirmDelete(template: MailTemplateView): Promise<void> {
    if (deletingId.value) return;
    const ok = await confirm({ title: t('mailTemplate.deleteTitle', { name: template.name }), message: t('mailTemplate.deleteMessage'), confirmLabel: t('common.delete'), tone: 'danger' });
    if (!ok) return;
    deletingId.value = template.id;
    try {
        await api.remove(template.id);
        toast.success(t('mailTemplate.deleted'));
        if (detail.value) {
            const next = { ...detail.value, templates: detail.value.templates.filter((item) => item.id !== template.id) };
            detail.value = next;
            syncKindRow(next);
        }
    } catch (e) {
        // 목록 한 줄 조치의 실패는 토스트(사유 포함 8초 — forms.md 2-3), 서버 문구 그대로
        toast.error(toApiError(e).message, 8000);
    } finally {
        deletingId.value = null;
    }
}

/* ── 템플릿 편집 서랍 ── */

const editOpen = ref(false);
const editMode = ref<'edit' | 'create'>('edit');
const editTemplate = ref<MailTemplateView | null>(null);
const editDirty = ref(false);
const editSaving = ref(false);
const editFieldErrors = ref<Record<string, string>>({});
const editFormError = ref('');
/** 그 언어의 기본으로 쓰는 중이면 언어를 바꿀 수 없다(서버가 거절한다 — 목업 가정) */
const editLocaleLocked = computed(() => {
    const template = editTemplate.value;
    return editMode.value === 'edit' && Boolean(template && detail.value?.selected[template.locale] === template.id);
});

async function guardEdit(): Promise<boolean> {
    if (!editOpen.value || !editDirty.value) return true;
    return confirm({ title: t('mailTemplate.leaveEditTitle'), message: t('mailTemplate.leaveEditMessage'), confirmLabel: t('mailTemplate.leaveConfirm'), tone: 'danger' });
}

function resetEditErrors(): void {
    editFieldErrors.value = {};
    editFormError.value = '';
}

async function openEdit(template: MailTemplateView): Promise<void> {
    if (!(await guardEdit())) return;
    editMode.value = 'edit';
    editTemplate.value = template;
    resetEditErrors();
    editOpen.value = true;
}

async function openCopy(template: MailTemplateView): Promise<void> {
    if (copyBlockedReason.value) {
        toast.error(copyBlockedReason.value, 8000);
        return;
    }
    if (!(await guardEdit())) return;
    editMode.value = 'create';
    editTemplate.value = { ...template };
    resetEditErrors();
    editOpen.value = true;
}

async function onEditVisible(visible: boolean): Promise<void> {
    if (visible) {
        editOpen.value = true;
        return;
    }
    if (!(await guardEdit())) return;
    editOpen.value = false;
    historyOpen.value = false;
}

async function saveTemplate(body: MailTemplateSaveRequest): Promise<void> {
    if (editSaving.value) return;
    editSaving.value = true;
    resetEditErrors();
    try {
        const saved = editMode.value === 'create' || !editTemplate.value ? await api.create(body) : await api.update(editTemplate.value.id, body);
        toast.success(editMode.value === 'create' ? t('mailTemplate.created') : t('mailTemplate.saved'));
        // 만든 뒤에는 같은 서랍에서 그 템플릿을 이어서 고친다
        editMode.value = 'edit';
        editTemplate.value = saved;
        if (detail.value) {
            const templates = detail.value.templates.some((item) => item.id === saved.id) ? detail.value.templates.map((item) => (item.id === saved.id ? saved : item)) : [...detail.value.templates, saved];
            const next = { ...detail.value, templates };
            detail.value = next;
            syncKindRow(next);
        }
        if (historyLoadedFor === saved.id) void history.reloadFirst();
    } catch (e) {
        const error = toApiError(e);
        const found: Record<string, string> = {};
        for (const field of ['name', 'locale', 'subject', 'bodyHtml']) {
            const message = error.field(field);
            if (message) found[field] = message;
        }
        editFieldErrors.value = found;
        if (Object.keys(found).length === 0) editFormError.value = error.message;
    } finally {
        editSaving.value = false;
    }
}

/* ── 변경 이력(오른쪽 서랍) ── */

const historyOpen = ref(false);
let historyLoadedFor: string | null = null;
const history = useSettingHistory((page, size) => api.history(editTemplate.value?.id ?? '', page, size));

function openHistory(): void {
    const id = editTemplate.value?.id;
    if (!id) return;
    historyOpen.value = true;
    // 처음 열 때(또는 다른 템플릿일 때) 받는다
    if (historyLoadedFor !== id) {
        historyLoadedFor = id;
        void history.reloadFirst();
    }
}

/* ── 나에게 시험 발송 ── */

const testOpen = ref(false);
const testTo = ref('');
const testing = ref(false);
const testToError = ref('');
const testError = ref('');
const testResult = ref<MailTestDialogResult | null>(null);
const testDraft = ref<MailTemplateTestDraft | null>(null);

watch(testTo, () => (testToError.value = ''));

function openTest(draft: MailTemplateTestDraft): void {
    testDraft.value = draft;
    testTo.value = user.value?.email ?? '';
    testToError.value = '';
    testError.value = '';
    testResult.value = null;
    testOpen.value = true;
}

async function runTest(): Promise<void> {
    const draft = testDraft.value;
    const kind = selectedKind.value;
    if (testing.value || !draft || !kind) return;
    const to = testTo.value.trim();
    const problem = UtilValidation.email(to);
    if (problem) {
        testToError.value = t(problem === 'REQUIRED' ? 'mail.testToRequired' : 'mail.testToFormat');
        return;
    }
    testing.value = true;
    testError.value = '';
    testResult.value = null;
    try {
        const result = await api.test({ kind, locale: draft.locale, subject: draft.subject, bodyHtml: draft.bodyHtml, to });
        testResult.value = { succeeded: result.succeeded, text: result.succeeded ? '' : UtilMailSettingsMessages.testFailure(t, result), to };
    } catch (e) {
        const error = toApiError(e);
        const field = error.field('to');
        if (field) testToError.value = field;
        else if (error.code === 'MAIL_TEST_RATE_LIMITED') testError.value = t('mail.testRateLimited', { seconds: error.retryAfterSeconds ?? 60 });
        else testError.value = error.fields?.subject || error.fields?.bodyHtml || error.message;
    } finally {
        testing.value = false;
    }
}

// 저장하지 않은 변경(레이아웃 · 쓰는 템플릿 · 규칙 · 편집 서랍)이 있는 채 다른 화면으로 가려 하면 묻는다
onBeforeRouteLeave(async (to) => {
    if (to.path === route.path) return true;
    if (layoutChanges.value === 0 && !kindDirty.value && !(editOpen.value && editDirty.value)) return true;
    return confirm({ title: t('mailTemplate.leaveTitle'), message: t('mailTemplate.leaveMessage'), confirmLabel: t('mailTemplate.leaveConfirm'), tone: 'danger' });
});

onMounted(() => {
    void loadLayout();
    void loadKinds();
});
</script>

<style scoped>
/* 한글 글꼴 보정 — 알약 안에서 글자가 아래로 붙어 보이지 않게 글자만 올린다(components.md 2-4) */
.mail-template-tag :deep(.p-tag-label) {
    position: relative;
    top: -0.09em;
    white-space: nowrap;
}
.mail-template-pill {
    position: relative;
    top: -0.09em;
}
</style>
