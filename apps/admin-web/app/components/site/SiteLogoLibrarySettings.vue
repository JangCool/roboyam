<template>
    <!--
        「로고」 모달의 본문(사용자 최종 결정 2026-11-18 — 일반 화면 「로고」 줄 `SiteLogoSummaryRow` 의 「로고 관리」 단추가 여는 큰 Dialog 안에 놓인다)의 두 구획 — 「로고 보관함」 + 「보이기 규칙」.
        모달 안이라 구획에 주소(`anchor`)를 주지 않는다(일반 화면의 우측 구역 내비에 올리지 않는다). 모달이 열릴 때마다 새로 만들어져 다시 읽으므로, 저장하지 않은 규칙 변경은 모달을 닫으면 버려진다.
        이 위에 겹쳐 뜨는 창(묶음 고치기·기념일 규칙 줄)이 열려 있는지는 `overlay` 로 알린다 — PrimeVue Dialog 는 창마다 문서에 Esc 를 걸어 두어, 바깥 모달이 그동안 Esc 를 끄지 않으면 Esc 한 번에 둘 다 닫힌다.
        묶음 목록은 줄 형식이다(한 줄에 묶음 하나 — 밝은·어두운 바탕 미리보기, 이름·배지, 올린 날·칸 수, 오른쪽 끝 [보기]/[고치기], 줄 사이 구분선).
        기념일 로고가 기본 제공으로 들어와(기본 2 + 기념일 28일 × 두 벌 = 58개, 기념일 규칙 28줄) 목록을 세 묶음으로 나눈다 — 기본 로고 · 기념일 로고(처음엔 접힘, 펼치면 캐릭터 로고 | 글자 로고 전환으로 한 유형씩 줄 형식) · 올린 로고.
          기념일 규칙이 기념일 묶음을 가리키면 실제로는 「기념일 로고 유형」(`rules.holidayStyle` — AUTO 면 그 회원이 평소에 보는 로고(성별 로고 → 기본 로고)의 벌, 아니면 고른 벌. 화면 계산은 방문자 = 기본 로고 기준)에 맞는 짝이 보인다 —
          규칙 줄 미리보기·「오늘 보이는 로고」가 그 계산(`UtilSiteLogoRule.matchStyle`, 서버 `SiteLogoResolver.matchStyle` 과 같다)을 쓴다. 기념일 규칙 목록은 최대 높이 안에서 넘겨 본다.
        예전 일반 화면 「기본 정보」 안 로고 4칸을 넓힌 것이다(`docs/features/site-logo-variants-contract.md` 의 4칸이 묶음 한 벌이 된다).
          · 로고 보관함: 묶음(한 벌 = 4칸)을 카드로 나열한다 — 밝은 바탕 한글 칸 미리보기 · 이름 · 올린 날 · 채워진 칸 수 · 지금 쓰이는 곳(「기본 로고」/「규칙 n개」/「보관 중」).
            기본 제공 묶음 2개(「캐릭터 로고」·「글자 로고」)는 늘 있고 고치지 못한다(보기만). 「새 로고 올리기」로 더하고, 올린 묶음은 이름·칸·「이름 포함」을 고친다(`SiteLogoBundleDialog` — 창의 [저장] 한 번).
            **삭제 단추는 없다** — 올린 로고는 지우지 않고 보관해 나중에도 고른다(사용자 결정).
          · 보이기 규칙: 우선순위 기념일 → 성별 → 기본. 기본 로고(묶음 하나) · 기념일 규칙(줄 더하기·고치기·빼기·사용 켬/끔·순서 — 위 줄이 먼저, `SiteLogoRuleDialog`) · 성별 규칙(남성·여성 각각, 로그인한 회원만).
            구획의 [저장] 한 번으로 저장한다. 「오늘 보이는 로고」는 **저장된 규칙** 기준 요약이다(계산 `UtilSiteLogoRule` — 실제 표시는 서버·공개 사이트 몫, API 단계).
        서버에 연결돼 있다(`useSiteLogoLibraryApi` — `/api/admin/site/logo-library/**`, `docs/features/site-logo-library-contract.md`). 응답이 늘 묶음 목록 + 규칙 전체라 그것으로 다시 그린다.
          서버 칸 오류는 가공하지 않고 그 칸·그 줄 아래에 보인다(묶음 창: `name`·`cells`·`cells.<칸>` / 규칙: `defaultBundleId`·`anniversaries[i].…`·`gender.male|female`), 칸이 없는 오류(404·409 등)는 아래 한 줄.
          규칙을 저장하면 기념일 줄 id 가 새로 매겨져 온다. 기본 로고 묶음의 한글·밝은 칸은 관리자 화면 상단 로고이기도 해서 저장 뒤 바로 맞춘다(`setSiteLogoUrl`).
          쉬는 날 목록은 휴일 관리 표(`useHolidayApi().year`)를 쓰고, 못 받거나 비면 고를 것이 없다는 안내를 보인다.
        권한: 조회 `site.read`, 변경 `site.manage` — 없으면 올리기·고치기·저장이 사라지고 보기만 한다. 다른 구획과 따로 읽고 저장한다.
    -->
    <AppGadget :title="t('siteLogo.libraryTitle')">
        <template v-if="canManage && saved" #actions>
            <Button type="button" :label="t('siteLogo.createButton')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" @click="openBundle(null)" />
        </template>

        <div v-if="loading && !saved" class="flex flex-col gap-2" role="status" :aria-label="t('common.loading')">
            <Skeleton v-for="n in 3" :key="n" height="3.5rem" />
        </div>

        <ErrorState v-else-if="!saved" :title="t('siteLogo.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />

        <div v-else class="flex flex-col gap-3">
            <p class="text-xs leading-relaxed text-muted-color">{{ t('siteLogo.libraryIntro') }}</p>

            <!--
                세 묶음 — 기본 로고(기본 제공 2) · 기념일 로고(처음엔 접혀 있다 — 펼치면 고른 유형의 벌만) · 올린 로고. 모두 같은 줄 형식.
                줄 형식: 한 줄에 묶음 하나, 줄 사이 구분선. 좁으면 한 줄 안에서 줄바꿈된다(미리보기·이름 위, 정보·단추 아래).
            -->
            <section v-for="section in sections" :key="section.key" class="flex flex-col gap-2" :aria-labelledby="`${uid}-group-${section.key}`">
                <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
                    <h3 :id="`${uid}-group-${section.key}`" class="text-sm font-medium">{{ section.title }}</h3>
                    <span class="text-xs tabular-nums text-muted-color">{{ t('siteLogo.groupCount', { count: section.count }) }}</span>
                    <Button
                        v-if="section.key === 'days' && section.count > 0"
                        type="button"
                        :label="daysOpen ? t('siteLogo.daysHide') : t('siteLogo.daysShow', { count: section.count })"
                        :icon="daysOpen ? 'pi pi-angle-up' : 'pi pi-angle-down'"
                        size="small"
                        severity="secondary"
                        variant="text"
                        class="ml-auto"
                        :aria-expanded="daysOpen"
                        :aria-controls="`${uid}-days`"
                        @click="daysOpen = !daysOpen"
                    />
                </div>

                <!-- 기념일 로고 — 펼치면 유형 전환(캐릭터 로고 | 글자 로고)이 나오고, 고른 유형의 묶음만 아래 줄 목록(기본 로고와 같은 형식)으로 그린다(그림은 펼쳤을 때만) -->
                <template v-if="section.key === 'days'">
                    <p class="text-xs leading-relaxed text-muted-color">{{ t('siteLogo.daysHelp') }}</p>
                    <SelectButton v-if="daysOpen" :model-value="daysStyle" :options="styleOptions" option-label="label" option-value="value" :allow-empty="false" size="small" :aria-label="t('siteLogo.daysStyleAria')" class="self-start" @update:model-value="onDaysStyleChange">
                        <template #option="{ option }">
                            <span class="flex items-center gap-1.5">
                                <span>{{ option.label }}</span>
                                <Tag v-if="option.value === usedStyle" class="site-logo-tag" :value="t('siteLogo.badgeInUse')" severity="success" />
                            </span>
                        </template>
                    </SelectButton>
                </template>

                <p v-if="section.key !== 'days' && !section.bundles.length" class="rounded-lg border border-dashed border-(--line) px-3 py-4 text-center text-xs text-muted-color">{{ t('siteLogo.uploadedEmpty') }}</p>

                <ul v-else-if="section.bundles.length" :id="section.key === 'days' ? `${uid}-days` : undefined" class="m-0 flex list-none flex-col divide-y divide-(--line) rounded-xl border border-(--line) p-0">
                <li v-for="bundle in section.bundles" :key="bundle.id" class="flex min-w-0 flex-wrap items-center gap-x-3 gap-y-2 px-3 py-2.5">
                    <!-- 밝은 바탕 한글 칸 미리보기(비었으면 채워진 첫 칸) + 어두운 바탕 한글 칸(올려 둔 것이 있을 때만) — 그림은 `<img src>` 하나로만 -->
                    <span class="flex shrink-0 items-center gap-1.5">
                        <span class="flex h-9 w-28 items-center justify-center overflow-hidden rounded-md border border-(--line) bg-surface-0 px-2 text-surface-900">
                            <img v-if="UtilSiteLogoRule.cardPreview(bundle.logos)" :src="UtilSiteLogoRule.cardPreview(bundle.logos) ?? undefined" alt="" loading="lazy" class="block h-5 w-auto max-w-full object-contain" />
                            <i v-else class="pi pi-image opacity-60" aria-hidden="true" />
                        </span>
                        <span v-if="bundle.logos.koDark" class="flex h-9 w-28 items-center justify-center overflow-hidden rounded-md border border-(--line) bg-surface-900 px-2 max-sm:hidden">
                            <img :src="bundle.logos.koDark" alt="" loading="lazy" class="block h-5 w-auto max-w-full object-contain" />
                        </span>
                    </span>

                    <div class="flex min-w-40 flex-1 flex-col gap-0.5">
                        <div class="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
                            <h4 class="min-w-0 truncate text-sm font-semibold">{{ bundle.name }}</h4>
                            <Tag v-if="rowUsage(bundle).isDefault" class="site-logo-tag" :value="t('siteLogo.badgeDefault')" severity="success" />
                            <Tag v-if="rowUsage(bundle).ruleCount > 0" class="site-logo-tag" :value="t('siteLogo.badgeRules', { count: rowUsage(bundle).ruleCount })" severity="info" />
                            <Tag v-if="!rowUsage(bundle).isDefault && rowUsage(bundle).ruleCount === 0" class="site-logo-tag" :value="t('siteLogo.badgeStored')" severity="secondary" />
                            <!-- 올린 로고의 유형(운영자가 고른 값) — 기본·성별 로고로 쓰일 때 기념일 벌을 정한다 -->
                            <Tag v-if="section.key === 'uploaded'" class="site-logo-tag" :value="bundle.style === 'WORDMARK' ? t('siteLogo.styleWordmark') : t('siteLogo.styleCharacter')" severity="secondary" />
                        </div>
                        <p class="text-xs leading-relaxed text-muted-color">
                            {{ bundle.builtIn ? t('siteLogo.builtIn') : t('siteLogo.uploadedAt', { date: time.date(bundle.createdAt) }) }}
                            <span aria-hidden="true"> · </span>
                            {{ t('siteLogo.filledCount', { count: UtilSiteLogoRule.filledCount(bundle.logos), total: UtilSiteLogo.VARIANTS.length }) }}
                        </p>
                    </div>

                    <Button
                        type="button"
                        :label="bundle.builtIn || !canManage ? t('siteLogo.viewButton') : t('siteLogo.editButton')"
                        :icon="bundle.builtIn || !canManage ? 'pi pi-eye' : 'pi pi-pencil'"
                        size="small"
                        severity="secondary"
                        variant="outlined"
                        class="ml-auto shrink-0"
                        :aria-label="t(bundle.builtIn || !canManage ? 'siteLogo.viewAria' : 'siteLogo.editAria', { name: bundle.name })"
                        @click="openBundle(bundle)"
                    />
                </li>
                </ul>
            </section>

            <p class="text-xs leading-relaxed text-muted-color">{{ t('siteLogo.keepNote') }}</p>
        </div>
    </AppGadget>

    <AppGadget :title="t('siteLogo.rulesTitle')">
        <div v-if="loading && !saved" class="flex flex-col gap-3" role="status" :aria-label="t('common.loading')">
            <Skeleton height="2.25rem" width="min(24rem, 100%)" />
            <Skeleton height="4rem" width="min(36rem, 100%)" />
        </div>

        <ErrorState v-else-if="!saved || !draft" :title="t('siteLogo.loadFailed')" :description="loadError?.message" retry :retrying="loading" @retry="load" />

        <form v-else class="flex flex-col gap-5" novalidate @submit.prevent="saveRules">
            <!-- 우선순위 한 줄 -->
            <Message severity="info" :closable="false" size="small">{{ t('siteLogo.orderNote') }}</Message>

            <!-- 오늘 보이는 로고 — 저장된 규칙 기준 -->
            <div class="flex flex-col gap-1.5 rounded-lg bg-(--p-content-hover-background) p-3" data-testid="site-logo-today">
                <span class="text-xs text-muted-color">{{ t('siteLogo.todayLabel', { date: time.serviceDate(today) }) }}</span>
                <p class="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                    <span v-if="todayPreview" class="flex h-7 w-24 shrink-0 items-center justify-center rounded border border-(--line) bg-surface-0 px-1.5">
                        <img :src="todayPreview" alt="" class="block h-5 w-auto max-w-full object-contain" />
                    </span>
                    <span>{{ todayLine }}</span>
                </p>
                <p v-for="line in todayGenderLines" :key="line" class="text-xs text-muted-color">{{ line }}</p>
                <p v-if="changeCount > 0" class="text-xs text-muted-color">{{ t('siteLogo.todaySavedOnly') }}</p>
            </div>

            <!-- 기본 로고 -->
            <LabeledField :label="t('siteLogo.defaultLabel')" :help="t('siteLogo.defaultHelp')" :error="ruleFields.defaultBundleId ?? ''">
                <template #default="{ id, describedBy, invalid }">
                    <SiteLogoBundleSelect :model-value="draft.defaultBundleId" :bundles="saved.bundles" :input-id="id" :disabled="!canManage || saving" :invalid="invalid" :described-by="describedBy" @update:model-value="onDefaultChange" />
                </template>
            </LabeledField>

            <!-- 성별 규칙 — 기본 로고 바로 아래(평소에 보이는 로고: 성별 → 기본) -->
            <div role="group" :aria-labelledby="`${uid}-gender`" class="flex flex-col gap-2">
                <h3 :id="`${uid}-gender`" class="text-sm font-medium">{{ t('siteLogo.genderTitle') }}</h3>
                <p class="text-xs leading-relaxed text-muted-color">{{ t('siteLogo.genderHelp') }}</p>
                <div class="grid gap-4 sm:grid-cols-2">
                    <LabeledField :label="t('siteLogo.genderMale')" :error="ruleFields['gender.male'] ?? ''">
                        <template #default="{ id, describedBy, invalid }">
                            <SiteLogoBundleSelect :model-value="draft.gender.male" :bundles="saved.bundles" allow-empty :input-id="id" :disabled="!canManage || saving" :invalid="invalid" :described-by="describedBy" @update:model-value="(value) => setGender('male', value)" />
                        </template>
                    </LabeledField>
                    <LabeledField :label="t('siteLogo.genderFemale')" :error="ruleFields['gender.female'] ?? ''">
                        <template #default="{ id, describedBy, invalid }">
                            <SiteLogoBundleSelect :model-value="draft.gender.female" :bundles="saved.bundles" allow-empty :input-id="id" :disabled="!canManage || saving" :invalid="invalid" :described-by="describedBy" @update:model-value="(value) => setGender('female', value)" />
                        </template>
                    </LabeledField>
                </div>
            </div>

            <!-- 기념일 규칙 -->
            <div role="group" :aria-labelledby="`${uid}-anniversary`" class="flex flex-col gap-2">
                <div class="flex flex-wrap items-center gap-2">
                    <h3 :id="`${uid}-anniversary`" class="text-sm font-medium">{{ t('siteLogo.anniversaryTitle') }}</h3>
                    <Button v-if="canManage" type="button" :label="t('siteLogo.ruleAddButton')" icon="pi pi-plus" size="small" severity="secondary" variant="outlined" class="ml-auto" :disabled="saving" @click="openRule(null)" />
                </div>
                <p class="text-xs leading-relaxed text-muted-color">{{ t('siteLogo.anniversaryHelp') }}</p>

                <!-- 기념일 로고 유형 — 기본 제공 기념일 로고 전체가 어느 벌로 보일지(초안 값 — 바꾸면 아래 줄 미리보기가 바로 따라온다) -->
                <div class="flex flex-col gap-1.5">
                    <span :id="`${uid}-holiday-style`" class="text-sm font-medium">{{ t('siteLogo.holidayStyleLabel') }}</span>
                    <SelectButton
                        :model-value="draft.holidayStyle"
                        :options="holidayStyleOptions"
                        option-label="label"
                        option-value="value"
                        :allow-empty="false"
                        size="small"
                        :disabled="!canManage || saving"
                        :invalid="Boolean(ruleFields.holidayStyle)"
                        :aria-labelledby="`${uid}-holiday-style`"
                        :aria-describedby="`${uid}-holiday-style-help`"
                        class="self-start"
                        @update:model-value="onHolidayStyleChange"
                    />
                    <p :id="`${uid}-holiday-style-help`" class="text-xs leading-relaxed text-muted-color">
                        {{ t('siteLogo.holidayStyleHelp', { count: parts.days.length }) }}
                        <template v-if="draft.holidayStyle === 'AUTO'">{{ t('siteLogo.holidayStyleAutoNow', { style: styleLabel(draftStyle) }) }}</template>
                    </p>
                    <p v-if="ruleFields.holidayStyle" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ ruleFields.holidayStyle }}</p>
                </div>

                <p v-if="!draft.anniversaries.length" class="rounded-lg border border-dashed border-(--line) px-3 py-4 text-center text-xs text-muted-color">{{ t('siteLogo.anniversaryEmpty') }}</p>

                <!-- 줄이 많다(기본 28줄) — 목록 영역에 최대 높이를 두고 그 안에서 넘겨 본다 -->
                <ol v-else class="m-0 flex max-h-96 list-none flex-col gap-2 overflow-y-auto p-0 pr-1" tabindex="0" :aria-label="t('siteLogo.anniversaryTitle')">
                    <li v-for="(rule, index) in draft.anniversaries" :key="rule.id" class="flex flex-wrap items-center gap-x-3 gap-y-2 rounded-lg border border-(--line) px-3 py-2" :class="rule.enabled ? '' : 'opacity-70'">
                        <span class="w-5 shrink-0 text-right text-xs tabular-nums text-muted-color" aria-hidden="true">{{ index + 1 }}</span>
                        <ToggleSwitch :model-value="rule.enabled" :disabled="!canManage || saving" :aria-label="t('siteLogo.ruleEnabledAria', { name: rule.name })" @update:model-value="(value: boolean) => setRuleEnabled(index, value)" />
                        <div class="min-w-40 flex-1">
                            <p class="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm font-medium">
                                <span class="min-w-0 truncate">{{ rule.name }}</span>
                                <Tag v-if="rule.enabled && UtilSiteLogoRule.matches(rule, today, holidays)" class="site-logo-tag" :value="t('siteLogo.ruleToday')" severity="success" />
                                <Tag v-if="!rule.enabled" class="site-logo-tag" :value="t('common.off')" severity="secondary" />
                            </p>
                            <p class="text-xs text-muted-color">{{ UtilSiteLogoRuleMessages.when(t, rule, formatDate) }}</p>
                        </div>
                        <span class="flex min-w-0 items-center gap-2 text-xs">
                            <span v-if="previewOf(shownOf(rule.bundleId)?.id ?? null)" class="flex h-6 w-20 shrink-0 items-center justify-center rounded border border-(--line) bg-surface-0 px-1">
                                <img :src="previewOf(shownOf(rule.bundleId)?.id ?? null) ?? undefined" alt="" loading="lazy" class="block h-4 w-auto max-w-full object-contain" />
                            </span>
                            <span class="min-w-0 truncate">{{ shownOf(rule.bundleId)?.name ?? t('siteLogo.bundleMissing') }}</span>
                        </span>
                        <span v-if="canManage" class="ml-auto flex shrink-0 items-center gap-0.5">
                            <Button v-tooltip.top="t('siteLogo.ruleUp')" type="button" icon="pi pi-arrow-up" text size="small" severity="secondary" :aria-label="t('siteLogo.ruleUpAria', { name: rule.name })" :disabled="saving || index === 0" @click="moveRule(index, -1)" />
                            <Button v-tooltip.top="t('siteLogo.ruleDown')" type="button" icon="pi pi-arrow-down" text size="small" severity="secondary" :aria-label="t('siteLogo.ruleDownAria', { name: rule.name })" :disabled="saving || index === draft.anniversaries.length - 1" @click="moveRule(index, 1)" />
                            <Button v-tooltip.top="t('common.edit')" type="button" icon="pi pi-pencil" text size="small" severity="secondary" :aria-label="t('siteLogo.ruleEditAria', { name: rule.name })" :disabled="saving" @click="openRule(rule)" />
                            <Button v-tooltip.top="t('siteLogo.ruleRemove')" type="button" icon="pi pi-times" text size="small" severity="danger" :aria-label="t('siteLogo.ruleRemoveAria', { name: rule.name })" :disabled="saving" @click="removeRule(index)" />
                        </span>
                        <!-- 서버가 이 줄에 붙여 준 오류(`anniversaries[i].…`) — 문구 그대로 -->
                        <p v-if="ruleRowError(index)" role="alert" class="w-full text-xs text-red-600 dark:text-red-400">{{ ruleRowError(index) }}</p>
                    </li>
                </ol>

                <p v-if="ruleFields.anniversaries" role="alert" class="text-xs text-red-600 dark:text-red-400">{{ ruleFields.anniversaries }}</p>
            </div>

            <Message v-if="formError" severity="error" variant="simple" size="small">{{ formError }}</Message>

            <div v-if="canManage" class="flex flex-wrap items-center justify-end gap-2">
                <Button type="button" :label="t('common.reset')" severity="secondary" variant="outlined" :disabled="saving || changeCount === 0" @click="resetRules" />
                <Button type="submit" :label="t('common.save')" icon="pi pi-check" :loading="saving" :disabled="changeCount === 0" />
            </div>
        </form>
    </AppGadget>

    <SiteLogoBundleDialog
        v-model:visible="bundleOpen"
        :bundle="bundleTarget"
        :readonly="!canManage || Boolean(bundleTarget?.builtIn)"
        :busy="bundleBusy"
        :error="bundleError"
        :field-errors="bundleFieldErrors"
        :site-name="siteLabel"
        @submit="saveBundle"
    />

    <SiteLogoRuleDialog
        v-if="saved"
        v-model:visible="ruleOpen"
        :rule="ruleTarget"
        :bundles="saved.bundles"
        :holidays="holidays"
        :holiday-fallback="holidayFallback"
        :default-bundle-id="draft?.defaultBundleId ?? ''"
        :today="today"
        @submit="applyRule"
    />
</template>

<script setup lang="ts">
import type { ApiError } from '~/composables/common/useApiClient';
import type {
    SiteLogoAnniversaryRule,
    SiteLogoBundle,
    SiteLogoBundleInput,
    SiteLogoHolidayStyle,
    SiteLogoLibraryResponse,
    SiteLogoRules,
    SiteLogoStyle,
} from '~/composables/site/api/useSiteLogoLibraryApi.types';
import UtilSiteLogo from '~/utils/util-site-logo';
import UtilSiteLogoRule, { type SiteLogoBundleUsage } from '~/utils/util-site-logo-rule';
import UtilSiteLogoRuleMessages from '~/utils/util-site-logo-rule-messages';

const emit = defineEmits<{
    /** 이 위에 겹쳐 뜨는 창(묶음 고치기·기념일 규칙 줄)이 열렸다·닫혔다 */
    overlay: [open: boolean];
}>();

const { t } = useI18n();
const api = useSiteLogoLibraryApi();
const toast = useAppToast();
const toApiError = useApiErrorOf();
const time = useDisplayTime();
const { can } = useAdminPermission();
const { siteLabel, setSiteLogoUrl, setSiteLogos } = useAdminAuth();
const canManage = computed(() => can('site.manage'));
const uid = useId();

/** 서버가 준 값(묶음 목록 + 저장된 규칙). `null` = 아직 못 받았다 */
const saved = ref<SiteLogoLibraryResponse | null>(null);
/** 보이기 규칙의 초안 — [저장]을 눌러야 반영된다 */
const draft = ref<SiteLogoRules | null>(null);
const loading = ref(false);
const loadError = ref<ApiError | null>(null);
const saving = ref(false);
const formError = ref('');
/** 규칙 저장 때 서버가 칸에 붙여 준 오류 — 열쇠는 서버 경로 그대로(`defaultBundleId` · `anniversaries[0].name` · `gender.male` …) */
const ruleFields = ref<Record<string, string>>({});

/** 오늘(표시 시간대의 달력 날짜)과 쉬는 날 표(휴일 관리 — 못 받거나 비면 빈 목록) */
const { today, holidays, holidayFallback, loadHolidays } = useSiteLogoHolidays();

/** 달력 날짜(`YYYY-MM-DD`)를 표시 형식으로 */
function formatDate(ymd: string): string {
    return time.value.serviceDate(ymd);
}

function fill(res: SiteLogoLibraryResponse): void {
    saved.value = res;
    draft.value = UtilSiteLogoRule.copy(res.rules);
    formError.value = '';
    ruleFields.value = {};
}

async function load(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
        fill(await api.library());
    } catch (e) {
        loadError.value = toApiError(e);
    } finally {
        loading.value = false;
    }
}

/* ── 묶음 찾기·쓰이는 곳 ── */

function bundleOf(id: string | null): SiteLogoBundle | null {
    return saved.value?.bundles.find((bundle) => bundle.id === id) ?? null;
}

function nameOf(id: string | null): string {
    return bundleOf(id)?.name ?? t('siteLogo.bundleMissing');
}

function previewOf(id: string | null): string | null {
    const bundle = bundleOf(id);
    return bundle ? UtilSiteLogoRule.cardPreview(bundle.logos) : null;
}

/** 카드 배지 — 저장된 규칙 기준(초안은 아직 쓰이는 것이 아니다) */
const usages = computed(() => {
    const map = new Map<string, SiteLogoBundleUsage>();
    if (saved.value) for (const bundle of saved.value.bundles) map.set(bundle.id, UtilSiteLogoRule.usage(saved.value.rules, bundle.id));
    return map;
});

function usageOf(id: string): SiteLogoBundleUsage {
    return usages.value.get(id) ?? { isDefault: false, ruleCount: 0 };
}

/* ── 보관함의 세 묶음(기본 로고 · 기념일 로고 · 올린 로고) ── */

const parts = computed(() => UtilSiteLogoRule.groups(saved.value?.bundles ?? []));
/** 기념일 로고 구획을 펼쳤는가 — 처음엔 접혀 있다(모달이 길어지지 않게, 그림 56장을 미리 그리지 않게) */
const daysOpen = ref(false);

interface LibrarySection {
    key: 'usual' | 'days' | 'uploaded';
    title: string;
    /** 기본·올린 로고는 묶음 수, 기념일 로고는 날 수 */
    count: number;
    /** 줄 형식으로 그릴 묶음 — 기념일 구획은 펼쳤을 때만, 고른 유형의 벌만 */
    bundles: SiteLogoBundle[];
}

/* ── 기념일 로고 유형(캐릭터 로고 | 글자 로고) ── */

function styleLabel(style: SiteLogoStyle): string {
    return style === 'WORDMARK' ? t('siteLogo.holidayStyleWordmark') : t('siteLogo.holidayStyleCharacter');
}

const styleOptions = computed<{ label: string; value: SiteLogoStyle }[]>(() => [
    { label: styleLabel('CHARACTER'), value: 'CHARACTER' },
    { label: styleLabel('WORDMARK'), value: 'WORDMARK' },
]);
/** 보이기 규칙의 「기념일 로고 유형」 고르개 */
const holidayStyleOptions = computed<{ label: string; value: SiteLogoHolidayStyle }[]>(() => [{ label: t('siteLogo.holidayStyleAuto'), value: 'AUTO' }, ...styleOptions.value]);

/** 지금 실제로 쓰이는 벌 — 저장된 규칙 기준(보관함의 다른 배지와 같다), 로그인하지 않은 방문자(기본 로고) 기준. 「사용 중」 표시와 보관함 전환의 처음 값 */
const usedStyle = computed<SiteLogoStyle>(() => (saved.value ? UtilSiteLogoRule.holidayStyle(saved.value.rules, saved.value.bundles) : 'CHARACTER'));
/** 초안 기준으로 쓰일 벌(방문자 기준) — 「기본·성별 로고에 맞춤」일 때 어느 쪽이 되는지 안내한다 */
const draftStyle = computed<SiteLogoStyle>(() => (saved.value && draft.value ? UtilSiteLogoRule.holidayStyle(draft.value, saved.value.bundles) : usedStyle.value));

/** 보관함에서 직접 고른 유형 — 고르기 전(null)에는 지금 쓰이는 유형을 본다 */
const daysStylePicked = ref<SiteLogoStyle | null>(null);
const daysStyle = computed<SiteLogoStyle>(() => daysStylePicked.value ?? usedStyle.value);

function onDaysStyleChange(value: SiteLogoStyle | null): void {
    if (value) daysStylePicked.value = value;
}

/** 기념일 구획에 그릴 묶음 — 날마다 고른 유형의 벌 하나(없는 날은 뺀다) */
const dayBundles = computed(() => parts.value.days.map((pair) => (daysStyle.value === 'WORDMARK' ? pair.wordmark : pair.character)).filter((bundle): bundle is SiteLogoBundle => bundle !== null));

const sections = computed<LibrarySection[]>(() => [
    { key: 'usual', title: t('siteLogo.groupUsual'), count: parts.value.usual.length, bundles: parts.value.usual },
    { key: 'days', title: t('siteLogo.groupDays'), count: parts.value.days.length, bundles: daysOpen.value ? dayBundles.value : [] },
    { key: 'uploaded', title: t('siteLogo.groupUploaded'), count: parts.value.uploaded.length, bundles: parts.value.uploaded },
]);

/** 줄의 배지 — 기념일 묶음은 그날 두 벌의 것을 더한다(규칙은 어느 벌을 가리켜도 같은 날이다) */
function rowUsage(bundle: SiteLogoBundle): SiteLogoBundleUsage {
    const pair = bundle.builtIn && bundle.dayKey ? parts.value.days.find((item) => item.dayKey === bundle.dayKey) : null;
    if (!pair) return usageOf(bundle.id);
    const a = pair.character ? usageOf(pair.character.id) : { isDefault: false, ruleCount: 0 };
    const b = pair.wordmark ? usageOf(pair.wordmark.id) : { isDefault: false, ruleCount: 0 };
    return { isDefault: a.isDefault || b.isDefault, ruleCount: a.ruleCount + b.ruleCount };
}

/** 기념일 규칙 줄이 실제로 보일 묶음 — 초안의 기념일 로고 유형·기본 로고를 따라 저장 전 미리보기도 바뀐다 */
function shownOf(bundleId: string): SiteLogoBundle | null {
    return saved.value && draft.value ? UtilSiteLogoRule.shownBundle(draft.value, bundleId, saved.value.bundles) : null;
}

/* ── 오늘 보이는 로고(저장된 규칙 기준) ── */

const todayRule = computed(() => (saved.value ? UtilSiteLogoRule.todayRule(saved.value.rules, today.value, holidays.value) : null));
/** 오늘 보일 묶음 — 기념일 묶음을 가리키는 줄이면 저장된 「기념일 로고 유형」에 맞는 짝(`matchStyle`) */
const todayBundleId = computed(() => (saved.value ? (UtilSiteLogoRule.todayBundle(saved.value.rules, saved.value.bundles, today.value, holidays.value).bundle?.id ?? null) : null));
const todayPreview = computed(() => previewOf(todayBundleId.value));
const todayLine = computed(() => {
    const bundle = nameOf(todayBundleId.value);
    return todayRule.value ? t('siteLogo.todayAnniversary', { rule: todayRule.value.name, bundle }) : t('siteLogo.todayDefault', { bundle });
});
/**
 * 성별 로고가 정해진 회원에게 오늘 보일 묶음 — 기념일이 없는 날은 그 성별 로고,
 * 기념일에는 그 줄의 묶음을 그 성별 로고의 벌에 맞춘 짝(유형이 「맞춤」일 때만 방문자와 달라진다). 방문자와 같으면 null(줄을 더하지 않는다)
 */
function todayGenderBundleId(genderBundleId: string | null): string | null {
    const lib = saved.value;
    if (!lib || !genderBundleId) return null;
    if (!todayRule.value) return genderBundleId;
    const id = UtilSiteLogoRule.shownBundle(lib.rules, todayRule.value.bundleId, lib.bundles, genderBundleId)?.id ?? null;
    return id && id !== todayBundleId.value ? id : null;
}

const todayGenderLines = computed(() => {
    const rules = saved.value?.rules;
    if (!rules) return [];
    const lines: string[] = [];
    const male = todayGenderBundleId(rules.gender.male);
    const female = todayGenderBundleId(rules.gender.female);
    if (male) lines.push(t('siteLogo.todayGenderMale', { bundle: nameOf(male) }));
    if (female) lines.push(t('siteLogo.todayGenderFemale', { bundle: nameOf(female) }));
    return lines;
});

/* ── 보이기 규칙 초안 ── */

const changeCount = computed(() => (saved.value && draft.value && !UtilSiteLogoRule.isSame(saved.value.rules, draft.value) ? 1 : 0));

/** 서버 칸 오류를 지운다 — 그 칸을 고치면 그 칸 것만, 기념일 줄은 차례가 바뀔 수 있어 줄 오류 전부 */
function clearRuleFields(prefix: string): void {
    ruleFields.value = Object.fromEntries(Object.entries(ruleFields.value).filter(([key]) => !key.startsWith(prefix)));
}

/** 기념일 줄 하나의 서버 오류(`anniversaries[i].…`)를 한 줄로 */
function ruleRowError(index: number): string {
    const prefix = `anniversaries[${index}].`;
    return Object.entries(ruleFields.value)
        .filter(([key]) => key.startsWith(prefix))
        .map(([, message]) => message)
        .join(' ');
}

/** 화면에 자리(칸·줄)가 있어 보이는 서버 칸 오류 수 — 0 이면 폼 아래 한 줄로 보인다 */
const shownRuleFieldCount = computed(
    () => Object.keys(ruleFields.value).filter((key) => key === 'defaultBundleId' || key === 'holidayStyle' || key === 'gender.male' || key === 'gender.female' || key === 'anniversaries' || /^anniversaries\[\d+\]\./.test(key)).length,
);

function onDefaultChange(value: string | null): void {
    if (!draft.value || !value) return;
    draft.value.defaultBundleId = value;
    clearRuleFields('defaultBundleId');
}

function onHolidayStyleChange(value: SiteLogoHolidayStyle | null): void {
    if (!draft.value || !value) return;
    draft.value.holidayStyle = value;
    clearRuleFields('holidayStyle');
}

function setGender(key: 'male' | 'female', value: string | null): void {
    if (!draft.value) return;
    draft.value.gender[key] = value;
    clearRuleFields(`gender.${key}`);
}

function setRuleEnabled(index: number, enabled: boolean): void {
    const rule = draft.value?.anniversaries[index];
    if (rule) rule.enabled = enabled;
}

/** 관리자 화면 상단 로고(`me` 의 `siteLogoUrl`)는 기본 로고 묶음의 한글·밝은 칸(비었으면 채워진 첫 칸)이다 · 상단 로고 자리는 그 묶음 4칸(`siteLogos`)에서 놓인 면에 맞는 칸 — 규칙·묶음을 저장하면 바로 맞춘다 */
function syncAdminLogo(): void {
    const bundle = bundleOf(saved.value?.rules.defaultBundleId ?? null);
    setSiteLogoUrl(bundle ? UtilSiteLogoRule.cardPreview(bundle.logos) : null);
    setSiteLogos(bundle ? bundle.logos : null, bundle ? bundle.nameInLogo : false);
}

/** 위 줄이 먼저다 — 한 칸 위·아래로 옮긴다 */
function moveRule(index: number, delta: -1 | 1): void {
    if (!draft.value) return;
    const list = [...draft.value.anniversaries];
    const target = index + delta;
    const moving = list[index];
    const other = list[target];
    if (!moving || !other) return;
    list[index] = other;
    list[target] = moving;
    draft.value.anniversaries = list;
    clearRuleFields('anniversaries');
}

/** 줄을 뺀다 — 초안이라 묻지 않는다(저장해야 반영되고 [되돌리기]로 돌아온다) */
function removeRule(index: number): void {
    if (!draft.value) return;
    draft.value.anniversaries = draft.value.anniversaries.filter((_, i) => i !== index);
    clearRuleFields('anniversaries');
}

function resetRules(): void {
    if (saved.value) fill(saved.value);
}

const ruleOpen = ref(false);
const ruleTarget = ref<SiteLogoAnniversaryRule | null>(null);

function openRule(rule: SiteLogoAnniversaryRule | null): void {
    ruleTarget.value = rule ? { ...rule } : null;
    ruleOpen.value = true;
}

/** 창에서 고친 줄을 초안에 넣는다 — 새 줄은 맨 아래 */
function applyRule(rule: SiteLogoAnniversaryRule): void {
    if (!draft.value) return;
    const index = draft.value.anniversaries.findIndex((item) => item.id === rule.id);
    if (index >= 0) draft.value.anniversaries = draft.value.anniversaries.map((item, i) => (i === index ? rule : item));
    else draft.value.anniversaries = [...draft.value.anniversaries, rule];
    clearRuleFields('anniversaries');
    ruleOpen.value = false;
}

async function saveRules(): Promise<void> {
    if (saving.value || !canManage.value || !draft.value || changeCount.value === 0) return;
    saving.value = true;
    formError.value = '';
    try {
        // 반응형 껍데기 없이 평범한 사본으로 보낸다
        // 응답으로 다시 채운다 — 기념일 줄 id 가 새로 매겨져 온다
        fill(await api.saveRules(UtilSiteLogoRule.copy(toRaw(draft.value))));
        syncAdminLogo();
        toast.success(t('siteLogo.rulesSaved'));
    } catch (e) {
        const error = toApiError(e);
        // 서버 칸 오류(`defaultBundleId` · `anniversaries[i].…` · `anniversaries` · `gender.male|female`)는 그 칸·그 줄 아래에 그대로, 칸이 없는 것만 아래 한 줄
        ruleFields.value = { ...(error.fields ?? {}) };
        if (!shownRuleFieldCount.value) formError.value = error.message;
    } finally {
        saving.value = false;
    }
}

/* ── 묶음 올리기·고치기 — 창의 [저장] 한 번(규칙 초안은 건드리지 않는다) ── */

const bundleOpen = ref(false);
const bundleTarget = ref<SiteLogoBundle | null>(null);
const bundleBusy = ref(false);
const bundleError = ref('');
const bundleFieldErrors = ref<Record<string, string>>({});

function openBundle(bundle: SiteLogoBundle | null): void {
    bundleTarget.value = bundle;
    bundleError.value = '';
    bundleFieldErrors.value = {};
    bundleOpen.value = true;
}

async function saveBundle(input: SiteLogoBundleInput): Promise<void> {
    if (bundleBusy.value || !canManage.value) return;
    const target = bundleTarget.value;
    bundleBusy.value = true;
    bundleError.value = '';
    bundleFieldErrors.value = {};
    try {
        const res = target ? await api.updateBundle(target.id, input) : await api.createBundle(input);
        // 묶음 목록만 바꾼다 — 저장하지 않은 규칙 초안은 그대로 둔다
        saved.value = res;
        syncAdminLogo();
        bundleOpen.value = false;
        toast.success(target ? t('siteLogo.bundleSaved') : t('siteLogo.bundleCreated'));
    } catch (e) {
        const error = toApiError(e);
        // 서버 칸 오류(`name` · `cells` · `cells.koLight`…)는 창의 그 칸 아래에 그대로. 그 밖(404·409·칸 없는 400)은 창 아래 한 줄
        const fields = { ...(error.fields ?? {}) };
        bundleFieldErrors.value = fields;
        const shown = Object.keys(fields).some((key) => key === 'name' || key === 'style' || key === 'cells' || key.startsWith('cells.'));
        if (!shown) bundleError.value = error.message;
    } finally {
        bundleBusy.value = false;
    }
}

// 겹쳐 뜨는 창(묶음 고치기·기념일 규칙 줄)이 열려 있는 동안 바깥 모달이 Esc 로 같이 닫히지 않게 알린다(부수 효과 — 바깥으로 알림)
watch(
    () => bundleOpen.value || ruleOpen.value,
    (open) => emit('overlay', open),
);

void load();
void loadHolidays();
</script>

<style scoped>
/* 한글 글자가 알약 안에서 아래로 앉는 것만 올린다(`components.md` 2-4) */
.site-logo-tag :deep(.p-tag-label) {
    text-box: trim-both cap alphabetic;
    padding-block: var(--chip-label-pad);
    white-space: nowrap;
}
</style>
