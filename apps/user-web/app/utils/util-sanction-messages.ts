import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { Restriction } from '~/composables/auth/api/useAuthApi.types';
import type { MySanction, SanctionStatus } from '~/composables/sanction/api/useSanctionApi.types';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import type { AppealOutcome, AppealReasonProblem, AppealScreen, SanctionScreen, SanctionTone } from '~/utils/util-sanction';
import UtilSanction from '~/utils/util-sanction';

/** 전역 배너에 쓸 문구 — 굵은 첫 문장 · 이어지는 말 · 해제 예정(영구 정지에는 없다) */
export interface SanctionBannerCopy {
    title: string;
    text: string;
    release: string | null;
}

/** 제목 + 설명 */
export interface SanctionCopy {
    title: string;
    text: string;
}

/** 안내 상자(`SanctionNotice`)의 색·문구 */
export interface SanctionNoticeCopy extends SanctionCopy {
    tone: 'info' | 'warn' | 'red' | 'ok';
}

/** 요약 표 한 줄 — `tone` 이 있으면 값을 색 태그로 그린다 */
export interface SanctionSummaryRow {
    label: string;
    value: string;
    tone?: SanctionTone;
}

/** 이력 한 줄을 그릴 값 */
export interface SanctionHistoryRow {
    id: string;
    title: string;
    kindTone: SanctionTone;
    statusLabel: string;
    statusTone: SanctionTone;
    voided: boolean;
    reason: string;
    dateText: string;
    periodText: string | null;
    needsConfirm: boolean;
    appeal: { no: number; statusLabel: string; tone: SanctionTone; answer: string | null } | null;
    canAppeal: boolean;
}

function messageOf(error: unknown): string {
    const message = typeof error === 'object' && error !== null ? (error as { message?: unknown }).message : undefined;
    return typeof message === 'string' ? message.trim() : '';
}

/**
 * 제재 화면의 계산이 낀 문구 — 전역 배너·제재 안내·이의제기. **문구 본문은 `i18n/locales/{ko,en}/sanction.ts`** 에 있고 여기는 "어느 키를 고를지"만 안다.
 * 상수 문구(「자세히 보기」「확인하고 계속」…)는 컴포넌트가 `t('sanction.…')` 를 바로 부른다. 날짜는 표시 값(`useDisplayTime`)의 시간대·날짜 형식으로 그린다.
 * 원본(community) `UtilSanctionMessages` 에서 게시판 한정 제재·알림 문구를 뺐다.
 */
export default class UtilSanctionMessages {
    /**
     * 전역 배너의 문구 — 정지 중이면 "이용이 정지된 계정이에요. 읽기만 할 수 있어요. 해제 예정 2026.10.02(금)", 영구 정지면 "계정이 영구 정지됐어요. 읽기만 할 수 있어요."(해제 예정 없음). 그 밖에는 null(배너를 안 보인다)
     * @param {Translate} t - 번역 함수(`useI18n().t`).
     * @param {Restriction | null | undefined} restriction - 로그인 정보의 `restriction`.
     * @param {DisplayTime} time - 표시 시간대·형식(`useDisplayTime()`).
     * @returns {SanctionBannerCopy | null} 배너 문구. 보이지 않을 때는 null.
     */
    static banner(t: Translate, restriction: Restriction | null | undefined, time: DisplayTime): SanctionBannerCopy | null {
        if (restriction?.status === 'BANNED') return { title: t('sanction.bannedTitle'), text: t('sanction.readOnly'), release: null };
        if (restriction?.status === 'SUSPENDED') {
            const day = time.dateDay(restriction.until);
            return { title: t('sanction.suspendedTitle'), text: t('sanction.readOnly'), release: day ? t('sanction.release', { date: day }) : null };
        }
        return null;
    }

    /**
     * 화면 머리 제목 — 정지·영구 정지면 「이용 정지 안내」, 아니면 「운영 안내」
     * @param {Translate} t - 번역 함수.
     * @param {SanctionScreen} screen - 갈래.
     * @returns {string} 제목.
     */
    static headerTitle(t: Translate, screen: SanctionScreen): string {
        return screen === 'SUSPENDED' || screen === 'BANNED' ? t('sanction.headerRestricted') : t('sanction.headerNotice');
    }

    /**
     * 이의제기 화면 머리 제목 — 답변이 왔으면 「이의제기 답변」
     * @param {Translate} t - 번역 함수.
     * @param {AppealScreen} screen - 갈래.
     * @returns {string} 제목.
     */
    static appealHeaderTitle(t: Translate, screen: AppealScreen): string {
        return screen === 'ACCEPTED' || screen === 'REJECTED' ? t('sanction.headerAppealAnswer') : t('sanction.headerAppeal');
    }

    /**
     * 종류 이름 — 「경고 2회」「정지 7일」「영구 정지」
     * @param {Translate} t - 번역 함수.
     * @param {string} kind - 제재 종류.
     * @param {number | null} warningNo - 경고 번호.
     * @returns {string} 이름.
     */
    static kindLabel(t: Translate, kind: string, warningNo: number | null = null): string {
        if (kind === 'WARNING') return warningNo === 1 || warningNo === 2 ? t('sanction.kindWarningN', { n: warningNo }) : t('sanction.kindWarning');
        if (kind === 'BAN') return t('sanction.kindBan');
        const days = UtilSanction.daysOf(kind);
        if (days) return t('sanction.kindSuspendDays', { days });
        return kind.startsWith('SUSPEND') ? t('sanction.kindSuspend') : t('sanction.kindOther');
    }

    /**
     * 상태 이름 — 경고는 「유효」, 정지는 「진행 중」, 그 밖 「종료」「해제됨」「무효」
     * @param {Translate} t - 번역 함수.
     * @param {string} kind - 제재 종류.
     * @param {SanctionStatus | string} status - 상태.
     * @returns {string} 이름. 모르는 상태면 빈 글자.
     */
    static statusLabel(t: Translate, kind: string, status: SanctionStatus | string): string {
        switch (status) {
            case 'ACTIVE':
                return kind === 'WARNING' ? t('sanction.statusValid') : t('sanction.statusActive');
            case 'ENDED':
                return t('sanction.statusEnded');
            case 'LIFTED':
                return t('sanction.statusLifted');
            case 'VOIDED':
                return t('sanction.statusVoided');
            default:
                return '';
        }
    }

    /**
     * 이의제기 상태 이름
     * @param {Translate} t - 번역 함수.
     * @param {string} status - `PENDING`·`ACCEPTED`·`REJECTED`.
     * @returns {string} 이름.
     */
    static appealStatusLabel(t: Translate, status: string): string {
        if (status === 'ACCEPTED') return t('sanction.appealAccepted');
        if (status === 'REJECTED') return t('sanction.appealRejected');
        return t('sanction.appealPending');
    }

    /**
     * 기간 글자 — 「2026.10.01 ~ 2026.10.08」
     * @param {string} startsAt - 시작.
     * @param {string | null} endsAt - 끝.
     * @param {DisplayTime} time - 표시 시각.
     * @returns {string} 기간. 시작이 없으면 빈 글자.
     */
    static period(startsAt: string | null | undefined, endsAt: string | null | undefined, time: DisplayTime): string {
        const from = time.date(startsAt);
        const to = time.date(endsAt);
        if (!from) return '';
        return to ? `${from} ~ ${to}` : from;
    }

    /**
     * 해제 예정 — 「2026.10.08(목) 15:00」
     * @param {string | null} endsAt - 끝.
     * @param {DisplayTime} time - 표시 시각.
     * @returns {string} 글자. 없으면 빈 글자.
     */
    static releaseText(endsAt: string | null | undefined, time: DisplayTime): string {
        const day = time.dateDay(endsAt);
        return day ? `${day} ${time.clock(endsAt)}` : '';
    }

    /**
     * 경고 안내의 머리 — 1회째 「경고가 부과됐어요」, 2회째 「경고가 2회 쌓였어요」
     * @param {Translate} t - 번역 함수.
     * @param {number} count - 누적 경고 수.
     * @returns {SanctionCopy} 제목과 설명.
     */
    static warningHeading(t: Translate, count: number): SanctionCopy {
        const n = count >= UtilSanction.WARNING_LIMIT ? UtilSanction.WARNING_LIMIT : 1;
        return { title: n >= UtilSanction.WARNING_LIMIT ? t('sanction.warningTitle2') : t('sanction.warningTitle1'), text: t('sanction.warningApplied', { n }) };
    }

    /**
     * 경고 안내의 다음 단계 안내
     * @param {Translate} t - 번역 함수.
     * @param {number} count - 누적 경고 수.
     * @returns {SanctionNoticeCopy} 안내.
     */
    static warningNotice(t: Translate, count: number): SanctionNoticeCopy {
        if (count >= UtilSanction.WARNING_LIMIT) return { tone: 'red', title: t('sanction.warningNotice2Title'), text: t('sanction.warningNotice2Text') };
        return { tone: 'warn', title: '', text: t('sanction.warningNotice1') };
    }

    /**
     * 누적 경고 글자 — 「1 / 2회」
     * @param {Translate} t - 번역 함수.
     * @param {number} count - 누적 경고 수.
     * @returns {string} 글자.
     */
    static warningCountText(t: Translate, count: number): string {
        return t('sanction.warningCount', { n: count, max: UtilSanction.WARNING_LIMIT });
    }

    /**
     * 정지·영구 정지 안내
     * @param {Translate} t - 번역 함수.
     * @param {SanctionScreen} screen - 갈래.
     * @param {number | null} days - 정지 날 수.
     * @returns {SanctionNoticeCopy} 안내.
     */
    static restrictedNotice(t: Translate, screen: SanctionScreen, days: number | null): SanctionNoticeCopy {
        if (screen === 'BANNED') return { tone: 'red', title: t('sanction.bannedNoticeTitle'), text: t('sanction.bannedNoticeText') };
        return { tone: 'red', title: days ? t('sanction.suspendedNoticeDays', { days }) : t('sanction.suspendedNoticeTitle'), text: t('sanction.suspendedNoticeText') };
    }

    /**
     * 「확인하고 계속」 뒤 단추 글자 — `next` 가 있으면 「계속 둘러보기」, 정지 중이면 「(읽기만 가능)」을 붙인다
     * @param {Translate} t - 번역 함수.
     * @param {SanctionScreen} screen - 갈래.
     * @param {boolean} hasNext - 원래 가려던 곳이 있는가.
     * @returns {string} 글자.
     */
    static continueLabel(t: Translate, screen: SanctionScreen, hasNext: boolean): string {
        const label = hasNext ? t('sanction.keepBrowsing') : t('sanction.home');
        return screen === 'SUSPENDED' || screen === 'BANNED' ? t('sanction.readOnlySuffix', { label }) : label;
    }

    /**
     * 안내 위 요약 표
     * @param {Translate} t - 번역 함수.
     * @param {SanctionScreen} screen - 갈래.
     * @param {MySanction | null} focus - 안내가 말하는 제재.
     * @param {number} warningCount - 누적 경고 수.
     * @param {DisplayTime} time - 표시 시각.
     * @returns {SanctionSummaryRow[]} 줄들(값이 빈 줄은 뺀다).
     */
    static summaryRows(t: Translate, screen: SanctionScreen, focus: MySanction | null, warningCount: number, time: DisplayTime): SanctionSummaryRow[] {
        if (!focus) return [];
        const rows: SanctionSummaryRow[] = [];
        const step = UtilSanctionMessages.kindLabel(t, focus.kind, focus.warningNo);
        if (screen === 'WARNING') {
            rows.push(
                { label: t('sanction.rowReason'), value: focus.reason },
                { label: t('sanction.rowDate'), value: time.date(focus.createdAt) },
                { label: t('sanction.rowWarnings'), value: UtilSanctionMessages.warningCountText(t, warningCount) },
            );
        } else if (screen === 'SUSPENDED') {
            const days = UtilSanction.daysOf(focus.kind);
            const period = UtilSanctionMessages.period(focus.startsAt, focus.endsAt, time);
            rows.push(
                { label: t('sanction.rowPeriod'), value: period && days ? t('sanction.rowPeriodDays', { period, days }) : period },
                { label: t('sanction.rowRelease'), value: UtilSanctionMessages.releaseText(focus.endsAt, time) },
                { label: t('sanction.rowReason'), value: focus.reason },
                { label: t('sanction.rowStep'), value: step },
            );
        } else if (screen === 'BANNED') {
            rows.push({ label: t('sanction.rowDate'), value: time.date(focus.createdAt) }, { label: t('sanction.rowReason'), value: focus.reason }, { label: t('sanction.rowStep'), value: step });
        }
        return rows.filter((row) => row.value !== '');
    }

    /**
     * 이력 한 줄
     * @param {Translate} t - 번역 함수.
     * @param {MySanction} s - 제재.
     * @param {DisplayTime} time - 표시 시각.
     * @returns {SanctionHistoryRow} 그릴 값.
     */
    static historyRow(t: Translate, s: MySanction, time: DisplayTime): SanctionHistoryRow {
        const appeal = s.appeal;
        return {
            id: s.id,
            title: UtilSanctionMessages.kindLabel(t, s.kind, s.warningNo),
            kindTone: UtilSanction.kindTone(s.kind),
            statusLabel: UtilSanctionMessages.statusLabel(t, s.kind, s.status),
            statusTone: UtilSanction.statusTone(s.kind, s.status),
            voided: s.status === 'VOIDED',
            reason: s.reason,
            dateText: time.date(s.createdAt),
            periodText: s.endsAt ? UtilSanctionMessages.period(s.startsAt, s.endsAt, time) : null,
            needsConfirm: s.kind === 'WARNING' && s.status !== 'VOIDED' && !s.acknowledged,
            appeal: appeal
                ? { no: appeal.no, statusLabel: UtilSanctionMessages.appealStatusLabel(t, appeal.status), tone: UtilSanction.appealTone(appeal.status), answer: appeal.answer?.trim() ? appeal.answer : null }
                : null,
            canAppeal: UtilSanction.canAppeal(s),
        };
    }

    /**
     * 이의제기 대상 요약 — 제재 · 기간(또는 처리일) · 사유
     * @param {Translate} t - 번역 함수.
     * @param {MySanction} s - 제재.
     * @param {DisplayTime} time - 표시 시각.
     * @returns {SanctionSummaryRow[]} 줄들.
     */
    static appealTargetRows(t: Translate, s: MySanction, time: DisplayTime): SanctionSummaryRow[] {
        const rows: SanctionSummaryRow[] = [{ label: t('sanction.rowSanction'), value: UtilSanctionMessages.kindLabel(t, s.kind, s.warningNo) }];
        if (s.endsAt) rows.push({ label: t('sanction.rowTerm'), value: UtilSanctionMessages.period(s.startsAt, s.endsAt, time) });
        else rows.push({ label: t('sanction.rowDate'), value: time.date(s.createdAt) });
        rows.push({ label: t('sanction.rowReason'), value: s.reason });
        return rows.filter((row) => row.value !== '');
    }

    /**
     * 이의제기 결과 요약 — 접수번호·접수일·상태 / 처리 결과(·해제 예정)
     * @param {Translate} t - 번역 함수.
     * @param {AppealScreen} screen - 갈래.
     * @param {MySanction | null} s - 제재.
     * @param {DisplayTime} time - 표시 시각.
     * @param {number | null} submittedNo - 방금 접수한 번호.
     * @returns {SanctionSummaryRow[]} 줄들.
     */
    static appealRows(t: Translate, screen: AppealScreen, s: MySanction | null, time: DisplayTime, submittedNo: number | null = null): SanctionSummaryRow[] {
        const appeal = s?.appeal ?? null;
        const no = appeal?.no ?? submittedNo;
        const noRow: SanctionSummaryRow[] = no === null ? [] : [{ label: t('sanction.rowAppealNo'), value: `#${no}` }];
        switch (screen) {
            case 'DONE':
            case 'PENDING': {
                const date = appeal ? time.date(appeal.createdAt) : '';
                const dateRow: SanctionSummaryRow[] = date ? [{ label: t('sanction.rowAppealDate'), value: date }] : [];
                return [...noRow, ...dateRow, { label: t('sanction.rowStatus'), value: t('sanction.appealPending'), tone: UtilSanction.appealTone('PENDING') }];
            }
            case 'ACCEPTED':
                return [{ label: t('sanction.rowResult'), value: t('sanction.resultAccepted'), tone: UtilSanction.appealTone('ACCEPTED') }, ...noRow];
            case 'REJECTED': {
                // 제재가 아직 진행 중인 정지일 때만 해제 예정을 보인다
                const release = s && s.status === 'ACTIVE' && s.endsAt ? UtilSanctionMessages.releaseText(s.endsAt, time) : '';
                const releaseRow: SanctionSummaryRow[] = release ? [{ label: t('sanction.rowRelease'), value: release }] : [];
                return [{ label: t('sanction.rowResult'), value: t('sanction.resultRejected'), tone: UtilSanction.appealTone('REJECTED') }, ...releaseRow, ...noRow];
            }
            default:
                return [];
        }
    }

    /**
     * 이의 사유 칸 아래 안내
     * @param {Translate} t - 번역 함수.
     * @param {AppealReasonProblem} problem - 까닭.
     * @param {number} length - 글자 수.
     * @returns {string} 안내.
     */
    static reasonProblem(t: Translate, problem: AppealReasonProblem, length: number): string {
        if (problem === 'EMPTY') return t('sanction.reasonEmpty');
        if (problem === 'TOO_SHORT') return t('sanction.reasonTooShort', { min: UtilSanction.APPEAL_REASON_MIN });
        return t('sanction.reasonTooLong', { n: length, max: UtilSanction.APPEAL_REASON_MAX });
    }

    /**
     * 접수 실패 안내 — 서버 문구가 있으면 그대로(가공하지 않는다), 쓴 내용은 그대로라는 말을 붙인다
     * @param {Translate} t - 번역 함수.
     * @param {AppealOutcome} outcome - 실패 갈래.
     * @param {unknown} error - 잡은 오류.
     * @returns {SanctionCopy} 제목과 설명.
     */
    static submitFailure(t: Translate, outcome: AppealOutcome, error: unknown): SanctionCopy {
        const title = t('sanction.submitFailTitle');
        if (outcome === 'NETWORK') return { title, text: t('sanction.submitFailNetwork') };
        const message = messageOf(error);
        if (!message) return { title, text: t('sanction.submitFailLater') };
        return { title, text: t('sanction.submitFailServer', { message }) };
    }
}
