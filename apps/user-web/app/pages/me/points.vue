<template>
    <div class="mx-auto flex max-w-2xl flex-col gap-4">
        <h1 class="text-lg font-bold">{{ t('point.title') }}</h1>

        <!-- 사업자 전용 계정(H-2) — 포인트가 없다(서버 `403 BUSINESS_ACCOUNT_RESTRICTED`) -->
        <EmptyState v-if="businessAccount" :title="t('point.businessTitle')" :description="t('point.businessText')" icon="pi pi-wallet" data-testid="points-business" />

        <template v-else-if="user">
            <!-- 보유 포인트 · 오늘 적립 — 실제 `GET /api/me/points` -->
            <section class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-labelledby="`${uid}-balance`" data-testid="points-summary">
                <h2 :id="`${uid}-balance`" class="text-sm font-semibold text-muted-color">{{ t('point.balance') }}</h2>

                <div v-if="!summary && (summaryStatus === 'loading' || summaryStatus === 'idle')" class="mt-2 flex flex-col gap-3" role="status" :aria-label="t('point.loading')" data-testid="points-loading">
                    <div class="h-9 w-40 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                    <div v-for="n in 3" :key="n" class="h-8 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                </div>
                <ErrorState v-else-if="!summary" :title="t('point.loadFailed')" retry class="py-6!" data-testid="points-error" @retry="points.load" />
                <template v-else>
                    <p class="mt-1 text-3xl font-bold tabular-nums" data-testid="points-balance">{{ UtilPoint.points(summary.balance) }}</p>
                    <!-- 충전 기능이 열리기 전(`chargeAvailable=false`, 지금은 늘)에는 「포인트」 하나만 — 적립/충전으로 나누지 않는다(commerce D13) -->
                    <p v-if="summary.chargeAvailable" class="mt-1 text-sm tabular-nums text-muted-color" data-testid="points-split">
                        {{ t('point.splitLine', { paid: UtilPoint.points(summary.paidBalance), free: UtilPoint.points(summary.freeBalance) }) }}
                    </p>
                    <p v-if="summary.expiring" class="mt-1 flex items-center gap-1.5 text-sm tabular-nums" data-testid="points-expiring">
                        <i class="pi pi-hourglass text-xs text-muted-color" aria-hidden="true" />
                        {{ t('point.expiringLine', { date: time.serviceDate(summary.expiring.date), points: UtilPoint.points(summary.expiring.amount) }) }}
                    </p>
                    <p class="mt-1 text-xs leading-relaxed text-muted-color">{{ t(summary.chargeAvailable ? 'point.expiryNoteSplit' : 'point.expiryNote') }}</p>

                    <!-- 미회수 — 지운 글·댓글의 포인트를 다 돌려받지 못한 양. 다음 적립에서 먼저 차감된다(commerce 5-4) -->
                    <div v-if="summary.revokeDebt > 0" class="mt-3 flex items-start gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2.5 text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200" role="note" data-testid="points-debt">
                        <i class="pi pi-exclamation-triangle mt-0.5 text-sm" aria-hidden="true" />
                        <div class="min-w-0 text-sm">
                            <p class="font-semibold tabular-nums">{{ t('point.debtTitle', { points: UtilPoint.points(summary.revokeDebt) }) }}</p>
                            <p class="mt-0.5 text-xs leading-relaxed">{{ t('point.debtText') }}</p>
                        </div>
                    </div>

                    <h3 class="mt-4 text-xs font-semibold text-muted-color">{{ t('point.todayTitle') }}</h3>
                    <ul class="m-0 mt-2 flex list-none flex-col gap-3 p-0" data-testid="points-today">
                        <!-- 오늘 총 적립(커머스 1-3) — 하루 총 한도가 있을 때만(cap 0 = 한도 없음이면 그리지 않는다). 단위는 횟수가 아니라 포인트 -->
                        <li v-if="todayTotal && todayTotal.cap > 0" class="text-sm" data-testid="points-today-total">
                            <div class="flex items-baseline justify-between gap-2">
                                <span class="font-medium">{{ t('point.todayTotal') }}</span>
                                <span class="tabular-nums text-muted-color">
                                    <b class="text-color">{{ t('point.totalOfCap', { amount: UtilPoint.number(todayTotal.amount), cap: UtilPoint.points(todayTotal.cap) }) }}</b>
                                    <template v-if="UtilPoint.remaining({ count: todayTotal.amount, cap: todayTotal.cap }) === 0"> · {{ t('point.totalCapReached') }}</template>
                                </span>
                            </div>
                            <div
                                class="mt-1 h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-800"
                                role="progressbar"
                                :aria-label="t('point.todayTotal')"
                                :aria-valuenow="todayTotal.amount"
                                aria-valuemin="0"
                                :aria-valuemax="todayTotal.cap"
                            >
                                <div class="h-full rounded-full bg-primary" :style="{ width: `${UtilPoint.progress({ count: todayTotal.amount, cap: todayTotal.cap })}%` }" />
                            </div>
                        </li>
                        <!-- 오늘 출석(커머스 1-3) — 출석 기능이 있을 때만. 출석 전이면 바로 출석하는 단추(달력 구획의 단추와 같은 동작) -->
                        <li v-if="summary.attendanceAvailable" class="flex min-h-8 items-center justify-between gap-2 text-sm" data-testid="points-today-attendance">
                            <span class="flex items-center gap-1.5">
                                {{ t('point.todayAttendance') }}
                                <span v-if="summary.today.attended" class="inline-flex shrink-0 items-center justify-center rounded bg-green-100 px-1.5 py-1 text-[0.6875rem] font-semibold leading-none text-green-800 dark:bg-green-950 dark:text-green-300">
                                    <span class="relative top-[-0.09em]">{{ t('point.attendDone') }}</span>
                                </span>
                            </span>
                            <span v-if="summary.today.attended" class="tabular-nums text-muted-color">{{ summary.rules.attendance > 0 ? `+${UtilPoint.points(summary.rules.attendance)}` : t('point.ruleOff') }}</span>
                            <Button
                                v-else
                                type="button"
                                size="small"
                                :label="attendLabel"
                                :loading="attending"
                                :disabled="attendBlocked !== null"
                                data-testid="points-today-attend"
                                @click="attendance.attend"
                            />
                        </li>
                        <li v-for="row in todayRows" :key="row.key" class="text-sm">
                            <div class="flex items-baseline justify-between gap-2">
                                <span>{{ t(row.labelKey) }}</span>
                                <span v-if="row.value.cap > 0" class="tabular-nums text-muted-color">
                                    <b class="text-color">{{ t('point.countOfCap', { count: row.value.count, cap: row.value.cap }) }}</b>
                                    <template v-if="UtilPoint.remaining(row.value) === 0"> · {{ t('point.capReached') }}</template>
                                </span>
                                <span v-else class="text-xs text-muted-color">{{ t('point.ruleOff') }}</span>
                            </div>
                            <div
                                v-if="row.value.cap > 0"
                                class="mt-1 h-1.5 overflow-hidden rounded-full bg-surface-200 dark:bg-surface-800"
                                role="progressbar"
                                :aria-label="t(row.labelKey)"
                                :aria-valuenow="row.value.count"
                                aria-valuemin="0"
                                :aria-valuemax="row.value.cap"
                            >
                                <div class="h-full rounded-full bg-primary" :style="{ width: `${UtilPoint.progress(row.value)}%` }" />
                            </div>
                        </li>
                    </ul>
                    <p class="mt-3 text-xs text-muted-color">{{ t('point.dayBasis') }}</p>
                </template>
            </section>

            <!-- 출석체크(커머스 1-3, community AttendancePanel) — 출석 기능이 있을 때만. 월간 달력(출석한 날·오늘) · 연속 N일 · 다음 연속 보너스까지 · [오늘 출석하기] -->
            <section v-if="summary?.attendanceAvailable" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-labelledby="`${uid}-attendance`" data-testid="attendance-panel">
                <div class="flex flex-wrap items-center justify-between gap-2">
                    <h2 :id="`${uid}-attendance`" class="text-sm font-semibold">{{ t('point.attendanceTitle') }}</h2>
                    <span v-if="attendanceStreak !== null && attendanceStreak > 0" class="inline-flex items-center gap-1 text-sm font-semibold text-primary" data-testid="attendance-streak">
                        <i class="pi pi-bolt text-xs" aria-hidden="true" />{{ t('point.streak', { count: UtilPoint.number(attendanceStreak) }) }}
                    </span>
                </div>
                <p v-if="streakNextLine" class="mt-1 text-xs text-muted-color" data-testid="attendance-streak-next">{{ streakNextLine }}</p>

                <div class="mt-3 flex items-center justify-between">
                    <button
                        type="button"
                        class="grid size-10 place-items-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-40 dark:hover:bg-surface-800"
                        :aria-label="t('point.calPrev')"
                        :disabled="!canPrev"
                        data-testid="cal-prev"
                        @click="attendance.go(-1)"
                    >
                        <i class="pi pi-chevron-left" aria-hidden="true" />
                    </button>
                    <p class="text-sm font-semibold" aria-live="polite" data-testid="cal-month">{{ UtilPointMessages.monthLabel(t, calMonth) }}</p>
                    <button
                        type="button"
                        class="grid size-10 place-items-center rounded-full hover:bg-surface-100 focus-visible:outline-2 focus-visible:outline-primary disabled:opacity-40 dark:hover:bg-surface-800"
                        :aria-label="t('point.calNext')"
                        :disabled="!canNext"
                        data-testid="cal-next"
                        @click="attendance.go(1)"
                    >
                        <i class="pi pi-chevron-right" aria-hidden="true" />
                    </button>
                </div>

                <!-- 불러오는 중 -->
                <div v-if="attendanceStatus === 'loading' || attendanceStatus === 'idle'" class="mt-2" role="status" :aria-label="t('point.calLoading')" data-testid="cal-loading">
                    <div class="h-44 animate-pulse rounded-lg bg-surface-200 dark:bg-surface-800" />
                </div>
                <!-- 달력을 못 받았다 — 출석 단추는 달력과 상관없이 쓸 수 있다 -->
                <ErrorState v-else-if="attendanceStatus === 'error'" :title="t('point.calLoadFailed')" :description="attendanceError" retry class="py-6!" data-testid="cal-error" @retry="attendance.load()" />
                <template v-else>
                    <div class="mt-2 grid grid-cols-7 text-center text-xs text-muted-color" aria-hidden="true">
                        <span v-for="weekday in weekdays" :key="weekday" class="py-1">{{ weekday }}</span>
                    </div>
                    <ol class="m-0 grid list-none grid-cols-7 gap-y-1 p-0 text-center text-sm" :aria-label="t('point.calLabel')">
                        <li
                            v-for="(cell, index) in calCells"
                            :key="index"
                            class="mx-auto grid size-9 place-items-center rounded-full border-2"
                            :class="[
                                cell.attended ? 'bg-primary-100 font-semibold text-primary-700 dark:bg-primary-950 dark:text-primary-300' : '',
                                cell.today ? 'border-primary font-bold' : 'border-transparent',
                            ]"
                            :aria-label="cell.day === null ? undefined : UtilPointMessages.cellLabel(t, calMonth, cell)"
                            :aria-hidden="cell.day === null ? 'true' : undefined"
                            :data-testid="cell.day === null ? undefined : `cal-day-${cell.day}`"
                            :data-attended="cell.day === null ? undefined : String(cell.attended)"
                            :data-today="cell.today ? 'true' : undefined"
                        >
                            {{ cell.day ?? '' }}
                        </li>
                    </ol>
                    <p v-if="calEmpty" class="mt-2 text-center text-xs text-muted-color" data-testid="cal-empty">{{ t('point.calEmptyMonth') }}</p>
                    <p v-else class="mt-2 flex flex-wrap justify-between gap-x-3 gap-y-1 text-xs text-muted-color">
                        <span>{{ t('point.calLegend') }}</span>
                        <span v-if="attendanceData" class="tabular-nums" data-testid="cal-month-count">{{ t('point.monthCount', { count: attendanceData.monthCount }) }}</span>
                    </p>
                </template>

                <!-- 오늘 출석 — 정지 중·이메일 인증 전에는 단추를 끄고 이유를 말한다(서버가 403 으로 막았으면 그 까닭이 먼저) -->
                <div class="mt-3">
                    <Button
                        type="button"
                        :label="attendedToday ? t('point.attended') : attending ? t('point.attending') : attendLabel"
                        :icon="attendedToday ? 'pi pi-check' : undefined"
                        :loading="attending"
                        :disabled="attendedToday || attendBlocked !== null"
                        fluid
                        data-testid="attend-button"
                        @click="attendance.attend"
                    />
                    <div
                        v-if="attendBlocked && !attendedToday"
                        class="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-xs leading-relaxed text-amber-900 dark:bg-amber-950/40 dark:text-amber-200"
                        role="status"
                        data-testid="attend-blocked"
                    >
                        <b>{{ UtilPointMessages.blocked(t, attendBlocked).title }}</b>
                        <span class="mt-0.5 block">{{ UtilPointMessages.blocked(t, attendBlocked).text }}</span>
                        <NuxtLink v-if="attendBlocked === 'EMAIL_NOT_VERIFIED'" to="/verify-email" class="mt-1 inline-block font-semibold underline">{{ t('point.blockedEmailLink') }}</NuxtLink>
                        <NuxtLink v-else to="/sanction" class="mt-1 inline-block font-semibold underline">{{ t('point.blockedSanctionLink') }}</NuxtLink>
                    </div>
                    <p v-else-if="attendedToday" class="mt-2 text-center text-xs text-muted-color" data-testid="attend-done-help">{{ t('point.attendedHelp') }}</p>
                    <p v-else class="mt-2 text-center text-xs text-muted-color">{{ t('point.attendHelp') }}</p>

                    <!-- 출석 요청이 실패했다 -->
                    <Message v-if="attendNotice" severity="error" :closable="false" size="small" class="mt-2" data-testid="attend-notice">
                        <b>{{ attendNotice.title }}</b> {{ attendNotice.text }}
                    </Message>
                </div>
            </section>

            <!-- 적립 규칙 요약 — 운영자가 정한 값(최대 15초 뒤 반영). 지급 지점이 없는 규칙(출석·인기글·교환권)은 보이지 않는다 -->
            <section v-if="summary" class="rounded-xl border border-surface bg-surface-0 p-4 dark:bg-surface-900" :aria-labelledby="`${uid}-rules`" data-testid="points-rules">
                <h2 :id="`${uid}-rules`" class="text-sm font-semibold">{{ t('point.rulesTitle') }}</h2>
                <ul class="m-0 mt-2 flex list-none flex-col gap-1.5 p-0 text-sm">
                    <li v-for="rule in ruleLines" :key="rule.key" class="flex items-baseline justify-between gap-3">
                        <span>{{ rule.label }}</span>
                        <span class="shrink-0 text-right text-muted-color">{{ rule.value }}</span>
                    </li>
                    <!-- 본인인증 보너스(가입 포인트, 커머스 1-3) — 0 이면 그리지 않는다. 받았으면 「받았어요」, 아니면 본인인증 화면으로 -->
                    <li v-if="summary.rules.signup && summary.rules.signup.points > 0" class="flex items-baseline justify-between gap-3" data-testid="points-signup">
                        <span>{{ t('point.ruleSignup', { amount: UtilPoint.number(summary.rules.signup.points) }) }}</span>
                        <span v-if="summary.rules.signup.received" class="inline-flex shrink-0 items-center gap-1 text-right text-muted-color">
                            <i class="pi pi-check text-xs" aria-hidden="true" />
                            {{ t('point.signupReceived') }}
                        </span>
                        <NuxtLink v-else to="/settings/identity" class="shrink-0 text-right font-medium text-primary hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ t('point.signupVerify') }} ›</NuxtLink>
                    </li>
                </ul>

                <!-- 적립 조건(커머스 1-3) — 0·false 인 조건은 빼고, 남는 것이 없으면 그리지 않는다 -->
                <template v-if="earnConditions.length">
                    <h3 class="mt-4 text-xs font-semibold text-muted-color">{{ t('point.conditionsTitle') }}</h3>
                    <ul class="m-0 mt-2 flex list-none flex-col gap-1.5 p-0 text-sm" data-testid="points-conditions">
                        <li v-for="line in earnConditions" :key="line" class="flex items-start gap-2">
                            <i class="pi pi-info-circle mt-1 text-xs text-muted-color" aria-hidden="true" />
                            <span>{{ line }}</span>
                        </li>
                    </ul>
                </template>
                <p class="mt-3 text-xs leading-relaxed text-muted-color">{{ t('point.rulesRevokeNote') }}</p>
            </section>

            <!-- 내역 — `GET /api/me/points/ledger`. 거르기는 전체·적립·차감·소멸(사용·사용 취소 거르기는 포인트 상점(1-2)이 더한다 — 그 줄은 「전체」에서만 보인다) -->
            <section class="rounded-xl border border-surface bg-surface-0 dark:bg-surface-900" :aria-labelledby="`${uid}-ledger`" data-testid="points-ledger">
                <div class="flex flex-wrap items-center justify-between gap-2 px-4 pt-4">
                    <h2 :id="`${uid}-ledger`" class="text-sm font-semibold">{{ t('point.history') }}</h2>
                    <SelectButton
                        v-model="kind"
                        :options="kindOptions"
                        option-label="label"
                        option-value="value"
                        :allow-empty="false"
                        size="small"
                        :aria-label="t('point.kindFilter')"
                        data-testid="points-kind"
                    />
                </div>

                <div class="mt-2" :aria-busy="ledgerStatus === 'loading'">
                    <div v-if="ledgerStatus === 'loading'" aria-hidden="true" data-testid="ledger-loading">
                        <div v-for="n in 5" :key="n" class="flex items-center gap-3 border-t border-surface px-4 py-3">
                            <div class="flex-1">
                                <div class="h-3.5 w-1/3 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                                <div class="mt-2 h-3 w-1/2 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                            </div>
                            <div class="h-4 w-14 animate-pulse rounded bg-surface-200 dark:bg-surface-800" />
                        </div>
                    </div>
                    <ErrorState v-else-if="ledgerStatus === 'error'" :title="t('point.historyLoadFailed')" :description="ledgerError" retry data-testid="ledger-error" @retry="loadLedger(true)" />
                    <EmptyState v-else-if="ledger.length === 0" :title="t(kind === 'ALL' ? 'point.historyEmpty' : 'point.historyEmptyFiltered')" icon="pi pi-wallet" data-testid="ledger-empty" />
                    <template v-else>
                        <ul class="m-0 list-none p-0" data-testid="ledger-list">
                            <li v-for="row in ledger" :key="row.entryId" class="flex items-start gap-3 border-t border-surface px-4 py-3" data-testid="ledger-row">
                                <div class="min-w-0 flex-1 text-sm">
                                    <p class="flex flex-wrap items-center gap-1.5 font-medium">
                                        <!-- 소멸·사용 취소는 사유 이름 앞에 작은 딱지 -->
                                        <span v-if="kindTagKey(row.kind)" class="inline-flex shrink-0 items-center justify-center rounded bg-surface-200 px-1.5 py-1 text-[0.6875rem] font-semibold leading-none text-muted-color dark:bg-surface-700" data-testid="ledger-kind-tag">
                                            <span class="relative top-[-0.09em]">{{ t(kindTagKey(row.kind)) }}</span>
                                        </span>
                                        <span>{{ row.reason }}</span>
                                    </p>
                                    <p v-if="row.restoredUntil" class="mt-0.5 text-xs text-muted-color" data-testid="ledger-restored">{{ t('point.restoredUntil', { date: time.date(row.restoredUntil) }) }}</p>
                                    <p v-if="row.note" class="mt-0.5 break-words text-xs text-muted-color">{{ row.note }}</p>
                                    <p v-if="row.target" class="mt-0.5 truncate text-xs text-muted-color">
                                        <NuxtLink v-if="row.target.available && row.target.title" :to="`/posts/${row.target.postId}`" class="hover:underline focus-visible:outline-2 focus-visible:outline-primary">{{ row.target.title }}</NuxtLink>
                                        <template v-else>{{ t('point.targetUnavailable') }}</template>
                                    </p>
                                    <time class="mt-0.5 block text-xs text-muted-color" :datetime="row.createdAt" :title="time.full(row.createdAt)">{{ time.listLabel(row.createdAt) }}</time>
                                </div>
                                <div class="shrink-0 text-right">
                                    <p class="text-sm font-semibold tabular-nums" :class="deltaClass(row)">{{ UtilPoint.signed(row.delta) }}</p>
                                    <p class="mt-0.5 text-xs tabular-nums text-muted-color">{{ t('point.balanceAfter', { points: UtilPoint.points(row.balanceAfter) }) }}</p>
                                </div>
                            </li>
                        </ul>
                        <div v-if="hasNext" class="border-t border-surface p-3 text-center">
                            <Button type="button" :label="t('common.more')" size="small" severity="secondary" variant="outlined" :loading="loadingMore" data-testid="ledger-more" @click="loadLedger(false)" />
                        </div>
                    </template>
                </div>
            </section>
        </template>
    </div>
</template>

<script setup lang="ts">
import type { MyPointLedgerItem, MyPointLedgerKind, PointCount } from '~/composables/point/api/usePointApi.types';
import UtilAuthMessages from '~/utils/util-auth-messages';
import UtilBusiness from '~/utils/util-business';
import UtilPoint from '~/utils/util-point';
import UtilPointMessages from '~/utils/util-point-messages';

/**
 * 포인트 `/me/points` — 로그인 필요(`auth` 미들웨어). 헤더 프로필 메뉴의 「포인트」·햄버거의 포인트 줄·내 정보의 「포인트 내역」이 온다.
 * 보유 포인트·오늘 적립(횟수/상한)·적립 규칙은 `GET /api/me/points`, 내역 목록은 `GET /api/me/points/ledger`(커서 더 보기) — 둘 다 서버 연결됨.
 * 출석 기능이 없으면(`attendanceAvailable=false`) 출석 달력·출석 줄·출석 규칙을 그리지 않는다. 인기글 보상·닉네임 변경권도 지급 지점이 없어 그리지 않는다.
 * 커머스 1-1: 합계 아래에 적립/충전 나눔(충전 기능이 열렸을 때만)·가장 가까운 소멸 예정·소멸 안내·미회수 경고, 내역에 소멸·사용 취소 딱지와 30일 복원 안내.
 * 커머스 1-3: 오늘 적립 맨 위에 「오늘 총 적립 N / 한도」(총 한도 0 이면 없음)와 출석 줄, 적립 규칙에 출석·연속 출석 보너스·본인인증 보너스 줄과 적립 조건 목록(0·false 인 조건은 뺀다).
 *   출석체크 구획(community `AttendancePanel` 을 옮겼다 — `usePointAttendance`, `GET·POST /api/me/attendance`): 달 넘기기(이번 달까지·24달 전까지)·출석한 날·오늘·연속 N일·다음 연속 보너스까지·[오늘 출석하기].
 *   409 `ATTENDANCE_ALREADY_DONE` 는 다시 읽어 출석 완료로, 403(정지·이메일 인증 전)은 단추를 끄고 까닭과 안내 링크를 보인다. 출석으로 포인트가 바뀌면 내역을 다시 받는다.
 */
definePageMeta({ middleware: 'auth' });
const { t } = useI18n();
const { siteLabel } = useSiteName();
useSeoMeta({ title: () => t('point.title'), description: () => t('point.seoDescription', { site: siteLabel.value }), robots: 'noindex' });

const uid = useId();
const time = useDisplayTime();
const { user, emailVerified, restriction } = useAuth();
const api = usePointApi();
const toast = useAppToast();
const points = usePointSummary();
const { summary, status: summaryStatus } = points;

const businessAccount = computed(() => UtilBusiness.isBusinessAccount(user.value?.role));

// ── 출석체크(커머스 1-3) ─────────────────────────────
const attendance = usePointAttendance({ onChanged: () => void loadLedger(true) });
const {
    month: calMonth,
    data: attendanceData,
    status: attendanceStatus,
    errorMessage: attendanceError,
    attending,
    notice: attendNotice,
    serverBlocked: attendServerBlocked,
    streak: attendanceStreak,
    attendedToday,
    canPrev,
    canNext,
    cells: calCells,
    emptyMonth: calEmpty,
} = attendance;

/** 정지 중·이메일 인증 전 — 화면이 미리 아는 까닭. 서버가 막았다고 하면(`403`) 그 까닭이 먼저 */
const attendBlocked = computed(() => attendServerBlocked.value ?? UtilPoint.blockedReason({ emailVerified: emailVerified.value, restriction: restriction.value }));
/** 「오늘 출석하기 +10P」 — 받는 포인트는 서버 설정값 */
const attendLabel = computed(() => UtilPointMessages.attendLabel(t, summary.value?.rules.attendance));
const weekdays = computed(() => UtilPointMessages.weekdays(t));
/** 다음 연속 출석 보너스까지 — 보너스가 꺼졌거나 연속 일수를 아직 모르면 없다 */
const streakNextLine = computed(() =>
    attendanceStreak.value === null ? '' : UtilPointMessages.streakNext(t, attendanceStreak.value, summary.value?.rules.attendanceStreak, attendedToday.value),
);

/** 오늘 적립 줄 — 출석 줄은 템플릿이 따로 그린다(횟수 막대가 아니다) */
const todayRows = computed(() => {
    const today = summary.value?.today;
    if (!today) return [];
    const rows: { key: string; labelKey: string; value: PointCount }[] = [
        { key: 'post', labelKey: 'point.todayPost', value: today.post },
        { key: 'comment', labelKey: 'point.todayComment', value: today.comment },
        { key: 'likeBonus', labelKey: 'point.todayLikeBonus', value: today.likeBonus },
    ];
    return rows;
});

/** 적립 규칙 한 줄씩 — 값이 0 이거나 하루 상한이 0 이면 「지급하지 않아요」 */
const ruleLines = computed(() => {
    const s = summary.value;
    if (!s) return [];
    const r = s.rules;
    const perDay = (amount: number, cap: number) => (amount > 0 && cap > 0 ? t('point.rulePerDay', { amount: UtilPoint.number(amount), cap }) : t('point.ruleOff'));
    const lines = [
        { key: 'post', label: t('point.rulePost'), value: perDay(r.post, s.today.post.cap) },
        { key: 'comment', label: t('point.ruleComment'), value: perDay(r.comment, s.today.comment.cap) },
        {
            key: 'likeBonus',
            label: t('point.ruleLikeBonus', { threshold: r.likeBonus.threshold }),
            value: perDay(r.likeBonus.points, r.likeBonus.dailyCap),
        },
    ];
    if (s.attendanceAvailable) {
        const head = [{ key: 'attendance', label: t('point.ruleAttendance'), value: r.attendance > 0 ? `+${UtilPoint.points(r.attendance)}` : t('point.ruleOff') }];
        // 연속 출석 보너스(커머스 1-3) — 꺼져 있으면(며칠·보너스 중 0) 줄이 없다
        if (UtilPoint.streakBonusOn(r.attendanceStreak)) {
            head.push({ key: 'attendanceStreak', label: t('point.ruleAttendanceStreak', { days: UtilPoint.number(r.attendanceStreak.days) }), value: `+${UtilPoint.points(r.attendanceStreak.bonus)}` });
        }
        lines.unshift(...head);
    }
    return lines;
});

/** 오늘 활동 적립 합 / 하루 총 한도(커머스 1-3 — 서버가 아직 주지 않으면 없다) */
const todayTotal = computed(() => summary.value?.today.total ?? null);

/** 적립 조건 목록(커머스 1-3) — 0·false 인 조건은 뺀다 */
const earnConditions = computed(() => (summary.value ? UtilPointMessages.earnConditions(t, summary.value.rules) : []));

// ── 내역 ─────────────────────────────────────────────
type KindFilter = 'ALL' | Exclude<MyPointLedgerKind, 'USE' | 'CANCEL_USE'>;
const kind = ref<KindFilter>('ALL');
const kindOptions = computed(() => [
    { value: 'ALL', label: t('common.all') },
    { value: 'EARN', label: t('point.kindEarn') },
    { value: 'REVOKE', label: t('point.kindRevoke') },
    { value: 'EXPIRE', label: t('point.kindExpire') },
]);

/** 사유 이름 앞 작은 딱지의 문구 키 — 소멸·사용 취소만(없으면 빈 글자) */
function kindTagKey(value: MyPointLedgerKind): string {
    if (value === 'EXPIRE') return 'point.kindExpire';
    if (value === 'CANCEL_USE') return 'point.kindCancelUse';
    return '';
}

/** 증감 색 — 소멸은 회색(빨강 회수와 구분), 그 밖에는 −빨강 · +초록 */
function deltaClass(row: MyPointLedgerItem): string {
    if (row.kind === 'EXPIRE') return 'text-muted-color';
    return row.delta < 0 ? 'text-red-600 dark:text-red-400' : 'text-green-700 dark:text-green-400';
}

const ledger = ref<MyPointLedgerItem[]>([]);
const ledgerStatus = ref<'loading' | 'ready' | 'error'>('loading');
const ledgerError = ref('');
const hasNext = ref(false);
const nextCursor = ref<string | null>(null);
const loadingMore = ref(false);
let ledgerSeq = 0;

/** 첫 쪽부터(`reset`) 또는 다음 쪽을 받는다. 거르기를 바꾸면 앞 요청의 늦은 응답은 버린다. 다음 쪽 실패는 토스트로 알리고 받은 줄은 그대로 둔다 */
async function loadLedger(reset: boolean) {
    const mine = ++ledgerSeq;
    if (reset) {
        ledgerStatus.value = 'loading';
        ledgerError.value = '';
        nextCursor.value = null;
    } else {
        loadingMore.value = true;
    }
    try {
        const page = await api.ledger({ kind: kind.value === 'ALL' ? undefined : kind.value, cursor: reset ? undefined : (nextCursor.value ?? undefined) });
        if (mine !== ledgerSeq) return;
        ledger.value = reset ? page.items : [...ledger.value, ...page.items];
        hasNext.value = page.hasNext;
        nextCursor.value = page.nextCursor;
        ledgerStatus.value = 'ready';
    } catch (e) {
        if (mine !== ledgerSeq) return;
        const message = e instanceof ApiError ? e.message : UtilAuthMessages.fallback(t, 0, 'NETWORK');
        if (reset) {
            ledgerError.value = message;
            ledgerStatus.value = 'error';
        } else {
            toast.error(message, 8000);
        }
    } finally {
        if (mine === ledgerSeq) loadingMore.value = false;
    }
}

watch(kind, () => void loadLedger(true));
onBeforeUnmount(() => ledgerSeq++);

// 로그인한 사람이 정해지면 요약·내역을 받는다(요약은 받아 둔 값이 있으면 그대로 보이면서 바꾼다). 사람이 바뀌면 출석 달력도 처음으로
watch(
    () => user.value?.id ?? null,
    (id) => {
        attendance.reset();
        if (!id || businessAccount.value) return;
        void points.load();
        void loadLedger(true);
    },
    { immediate: true },
);

// 출석 기능이 있다고 확인되면(요약의 `attendanceAvailable`) 달력을 한 번 받는다
watch(
    () => Boolean(summary.value?.attendanceAvailable) && !businessAccount.value,
    (on) => {
        if (on) attendance.ensureLoaded();
    },
    { immediate: true },
);
</script>
