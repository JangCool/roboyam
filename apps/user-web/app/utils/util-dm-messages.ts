import type { Translate } from '#layers/i18n/utils/util-i18n';
import UtilTime from '#layers/display-time/utils/util-time';
import type { DisplayTime } from '#layers/display-time/utils/util-time';
import { ApiError } from '~/composables/common/useApiClient';
import type { DmLastMessage, DmPartner, DmQuota, DmReason } from '~/composables/dm/api/useDmApi.types';
import UtilBoardMessages from '~/utils/util-board-messages';
import UtilDm from '~/utils/util-dm';
import UtilDmRules from '~/utils/util-dm-rules';
import type { DmBodyProblem, DmSearchProblem } from '~/utils/util-dm-rules';

/*
 * 쪽지 화면의 **계산이 낀** 문구 — 못 보내는 이유(배너·입력창 잠금)·전송 거절·전송 실패·토스트·시간 글자(community `UtilDmMessages`·`UtilDmProfile` 의 문구).
 * 상수 문구는 컴포넌트가 `t('dm.*')` 로 쓴다. **서버 `message` 가 있으면 그것을 그대로 쓴다** — 한도·수신 거부 같은 거절 문구는 서버가 정한다. 분기는 `code` 로 한다.
 */

/** 안내 상자의 색 — `info` 회색 · `warn` 노랑(한도류) · `red` 정지·실패 · `muted` 수신 거부·차단 · `ok` 완료 */
export type DmNoticeTone = 'info' | 'warn' | 'red' | 'muted' | 'ok';

/** 안내 상자에서 이어지는 링크 */
export interface DmNoticeLink {
    label: string;
    to: string;
}

/** 안내 상자 한 개 — 굵은 첫 문장 + 설명 */
export interface DmNoticeCopy {
    tone: DmNoticeTone;
    title: string;
    text: string;
    link: DmNoticeLink | null;
}

/** 못 보내는 이유 하나의 문구 — 배너와 입력창 잠금 글자(`lock`), 배너 밑 도움말(`help`) */
export interface DmReasonCopy extends DmNoticeCopy {
    lock: string;
    help: string | null;
}

/** 이유 문구에 필요한 값 */
export interface DmReasonContext {
    quota?: DmQuota | null;
    availableFrom?: string | null;
    minDays?: number;
    now?: number;
    time: DisplayTime;
}

/** 보내기가 거절됐을 때의 안내 — `RATE_LIMITED` 면 기다릴 초 */
export interface DmRejection extends DmNoticeCopy {
    retryAfterSeconds: number | null;
}

/** 거절 뒤 대화를 다시 읽어야 하는 오류 — 보낸 사람·받는 사람의 상태가 바뀌었다 */
const STATE_CHANGED = new Set([
    'DM_UNAVAILABLE',
    'DM_RECIPIENT_REFUSES',
    'DM_SENDER_TOO_NEW',
    'DM_DAILY_LIMIT',
    'DM_NEW_PARTNER_LIMIT',
    'ACCOUNT_RESTRICTED',
    'EMAIL_NOT_VERIFIED',
    'BUSINESS_ACCOUNT_RESTRICTED',
    'PROFILE_NOT_FOUND',
    'DM_NOT_PERMITTED',
]);

export default class UtilDmMessages {
    /** 이메일 인증 안내로 가는 링크 */
    static verifyLink(t: Translate): DmNoticeLink {
        return { label: t('dm.verifyLink'), to: '/verify-email' };
    }

    /**
     * 날짜 칩·오래된 쪽지함 줄의 날짜 — "9월 29일", 올해가 아니면 "2025년 9월 29일"(표시 시간대).
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} iso - 시각.
     * @param {DisplayTime} time - 표시 시각 함수.
     * @param {number} now - 지금(밀리초).
     * @returns {string} 날짜 글자.
     */
    static dayLabel(t: Translate, iso: string | null | undefined, time: DisplayTime, now: number = Date.now()): string {
        const then = time.parts(iso);
        if (!then) return '';
        const thisYear = time.parts(new Date(now).toISOString())?.y;
        return then.y === thisYear ? t('dm.dayLabel', { m: then.mo, d: then.d }) : t('dm.dayLabelYear', { y: then.y, m: then.mo, d: then.d });
    }

    /**
     * 쪽지함 한 줄의 시간 — 24시간 안은 "방금"·"5분 전"·"1시간 전", 그다음 "어제", 그보다 오래되면 날짜.
     * @param {Translate} t - 번역 함수.
     * @param {string | null | undefined} iso - 시각.
     * @param {DisplayTime} time - 표시 시각 함수.
     * @param {number} now - 지금(밀리초).
     * @returns {string} 시간 글자.
     */
    static threadTime(t: Translate, iso: string | null | undefined, time: DisplayTime, now: number = Date.now()): string {
        const ms = iso ? Date.parse(iso) : Number.NaN;
        if (Number.isNaN(ms)) return '';
        if (now - ms < 86_400_000) return time.ago(iso, now);
        if (UtilDm.daysAgo(iso, time, now) === 1) return t('dm.yesterday');
        return UtilDmMessages.dayLabel(t, iso, time, now);
    }

    /**
     * 쪽지함 한 줄의 마지막 쪽지 — 내가 보낸 것이면 "나: ".
     * @param {Translate} t - 번역 함수.
     * @param {Pick<DmLastMessage, 'mine' | 'preview'>} last - 마지막 쪽지.
     * @returns {string} 한 줄.
     */
    static lastLine(t: Translate, last: Pick<DmLastMessage, 'mine' | 'preview'>): string {
        return last.mine ? t('dm.lastMine', { text: last.preview }) : last.preview;
    }

    /**
     * 못 보내는 이유 하나의 문구 — 색·굵은 첫 문장·설명·잠금 글자.
     * @param {Translate} t - 번역 함수.
     * @param {DmReason} reason - 서버가 알려 준 이유.
     * @param {DmReasonContext} ctx - 한도·보낼 수 있게 되는 시각.
     * @returns {DmReasonCopy} 문구.
     */
    static reason(t: Translate, reason: DmReason, ctx: DmReasonContext): DmReasonCopy {
        const minDays = ctx.minDays ?? UtilDmRules.MIN_ACCOUNT_AGE_DAYS;
        const plain = (tone: DmNoticeTone, key: string, lockKey: string): DmReasonCopy => ({ tone, title: t(key), text: '', link: null, lock: t(lockKey), help: null });
        switch (reason) {
            case 'SELF':
                return plain('muted', 'dm.reasonSelf', 'dm.lockSelf');
            case 'EMAIL_NOT_VERIFIED':
                return { tone: 'warn', title: t('dm.reasonUnverified'), text: t('dm.reasonUnverifiedText'), link: UtilDmMessages.verifyLink(t), lock: t('dm.lockUnverified'), help: null };
            case 'ACCOUNT_RESTRICTED':
                return { tone: 'red', title: t('dm.restrictedTitle'), text: t('dm.restrictedText'), link: null, lock: t('dm.lockRestricted'), help: null };
            case 'BUSINESS_RESTRICTED':
                return { tone: 'muted', title: t('dm.reasonBusiness'), text: t('dm.readStillText'), link: null, lock: t('dm.lockBusiness'), help: null };
            case 'NOT_PERMITTED':
                return { tone: 'muted', title: t('dm.reasonNotPermitted'), text: t('dm.readStillText'), link: null, lock: t('dm.lockNotPermitted'), help: null };
            case 'TOO_NEW': {
                const from = UtilDmMessages.dayLabel(t, ctx.availableFrom, ctx.time, ctx.now);
                return {
                    tone: 'warn',
                    title: t('dm.reasonTooNew', { days: minDays }),
                    text: from ? t('dm.reasonTooNewFrom', { date: from }) : t('dm.readStillText'),
                    link: null,
                    lock: from ? t('dm.lockTooNewFrom', { days: minDays, date: from }) : t('dm.lockTooNew', { days: minDays }),
                    help: null,
                };
            }
            case 'RECIPIENT_WITHDRAWN':
                return plain('muted', 'dm.reasonWithdrawn', 'dm.lockWithdrawn');
            case 'UNAVAILABLE':
                return { ...plain('muted', 'dm.reasonUnavailable', 'dm.lockUnavailable'), help: t('dm.reasonUnavailableHelp') };
            case 'RECIPIENT_REFUSES':
                return plain('muted', 'dm.reasonRefuses', 'dm.lockRefuses');
            case 'DAILY_LIMIT':
                return {
                    tone: 'warn',
                    title: ctx.quota ? t('dm.reasonDailyLimit', { n: ctx.quota.dailyLimit }) : t('dm.reasonDailyLimitPlain'),
                    text: t('dm.reasonDailyLimitText'),
                    link: null,
                    lock: t('dm.lockDailyLimit'),
                    help: null,
                };
            case 'NEW_PARTNER_LIMIT':
                return {
                    tone: 'warn',
                    title: ctx.quota ? t('dm.reasonPartnerLimit', { n: ctx.quota.newPartnerLimit }) : t('dm.reasonPartnerLimitPlain'),
                    text: t('dm.reasonPartnerLimitText'),
                    link: null,
                    lock: t('dm.lockPartnerLimit'),
                    help: null,
                };
            default:
                return plain('muted', 'dm.reasonUnknown', 'dm.lockUnknown');
        }
    }

    /**
     * 프로필의 비활성 쪽지 단추 아래 한 줄.
     * @param {Translate} t - 번역 함수.
     * @param {DmReason | null} reason - 서버가 준 이유.
     * @param {DisplayTime} time - 표시 시각 함수.
     * @param {{ availableFrom?: string | null; newPartnerLimit?: number | null }} options - 끼울 값.
     * @returns {string} 한 줄.
     */
    static profileReason(t: Translate, reason: DmReason | null, time: DisplayTime, options: { availableFrom?: string | null; newPartnerLimit?: number | null } = {}): string {
        switch (reason) {
            case 'TOO_NEW': {
                const date = UtilDmMessages.dayLabel(t, options.availableFrom, time);
                return date ? t('dm.profileTooNewFrom', { date }) : t('dm.profileTooNew');
            }
            case 'DAILY_LIMIT':
                return t('dm.profileDailyLimit');
            case 'NEW_PARTNER_LIMIT': {
                const n = options.newPartnerLimit;
                return typeof n === 'number' && n > 0 ? t('dm.profilePartnerLimit', { n }) : t('dm.profilePartnerLimitPlain');
            }
            case 'RECIPIENT_REFUSES':
                return t('dm.lockRefuses');
            case 'UNAVAILABLE':
                return t('dm.lockUnavailable');
            case 'ACCOUNT_RESTRICTED':
                return t('dm.lockRestricted');
            case 'BUSINESS_RESTRICTED':
                return t('dm.lockBusiness');
            case 'NOT_PERMITTED':
                return t('dm.lockNotPermitted');
            case 'EMAIL_NOT_VERIFIED':
                return t('dm.profileUnverified');
            case 'RECIPIENT_WITHDRAWN':
                return t('dm.lockWithdrawn');
            default:
                return t('dm.profileUnknown');
        }
    }

    /**
     * 받는 사람 검색어 문제.
     * @param {Translate} t - 번역 함수.
     * @param {DmSearchProblem} problem - 문제.
     * @returns {string} 한 줄.
     */
    static searchProblem(t: Translate, problem: DmSearchProblem): string {
        return problem === 'TOO_SHORT' ? t('dm.searchTooShort', { n: UtilDmRules.SEARCH_MIN }) : t('dm.searchTooLong', { n: UtilDmRules.SEARCH_MAX });
    }

    /**
     * 본문 문제(서버 `fields.body` 와 같은 뜻).
     * @param {Translate} t - 번역 함수.
     * @param {DmBodyProblem} problem - 문제.
     * @param {number} max - 최대 글자 수.
     * @returns {string} 한 줄.
     */
    static bodyProblem(t: Translate, problem: DmBodyProblem, max: number = UtilDmRules.BODY_MAX): string {
        if (problem === 'EMPTY') return t('dm.bodyEmpty');
        if (problem === 'TOO_LONG') return t('dm.bodyTooLong', { max });
        return t('dm.bodyInvalid');
    }

    /**
     * 받는 사람을 고른 뒤 도움말.
     * @param {Translate} t - 번역 함수.
     * @param {boolean} newPartner - 처음 대화하는 상대인가.
     * @returns {string} 한 줄.
     */
    static recipientHelp(t: Translate, newPartner: boolean): string {
        return newPartner ? t('dm.recipientHelpNew') : t('dm.recipientHelpExisting');
    }

    /**
     * 보냈다는 토스트 — "쪽지를 보냈어요 · 오늘 남은 14통 · 새 상대 2명".
     * @param {Translate} t - 번역 함수.
     * @param {DmQuota} quota - 보낸 뒤의 한도.
     * @param {boolean} created - 새 상대였는가.
     * @returns {string} 글자.
     */
    static sentToast(t: Translate, quota: DmQuota, created: boolean): string {
        return created ? t('dm.sentToastNew', { n: quota.remainingToday, m: quota.remainingNewPartners }) : t('dm.sentToast', { n: quota.remainingToday });
    }

    /**
     * 가입 후 일수 진행 막대 옆 글자.
     * @param {Translate} t - 번역 함수.
     * @param {number} day - 지난 일수.
     * @param {number} left - 남은 일수.
     * @param {number} minDays - 일수.
     * @returns {{ title: string; text: string }} 글자.
     */
    static joinProgress(t: Translate, day: number, left: number, minDays: number): { title: string; text: string } {
        return { title: day <= 0 ? t('dm.joinedToday') : t('dm.joinedDay', { n: day }), text: t('dm.joinLeft', { days: minDays, left }) };
    }

    /**
     * ⋯ 시트 제목 — "회원D님과의 대화", 탈퇴한 회원은 "탈퇴한 회원과의 대화".
     * @param {Translate} t - 번역 함수.
     * @param {Pick<DmPartner, 'nickname' | 'withdrawn'>} partner - 상대.
     * @returns {string} 제목.
     */
    static sheetTitle(t: Translate, partner: Pick<DmPartner, 'nickname' | 'withdrawn'>): string {
        return partner.withdrawn ? t('dm.sheetTitleWithdrawn') : t('dm.sheetTitle', { nickname: partner.nickname });
    }

    /**
     * 대화방 쪽 제목 — "회원D님과의 쪽지" · 모르면 "쪽지".
     * @param {Translate} t - 번역 함수.
     * @param {Pick<DmPartner, 'nickname' | 'withdrawn'> | null | undefined} partner - 상대.
     * @returns {string} 제목.
     */
    static roomTitle(t: Translate, partner: Pick<DmPartner, 'nickname' | 'withdrawn'> | null | undefined): string {
        if (!partner) return t('dm.roomTitleUnknown');
        return partner.withdrawn ? t('dm.roomTitleWithdrawn') : t('dm.roomTitle', { nickname: partner.nickname });
    }

    /**
     * 서버 문구가 있으면 그대로, 아니면(네트워크 등) 대신 쓸 문구.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류.
     * @returns {string} 한 줄.
     */
    static errorOf(t: Translate, e: unknown): string {
        return UtilBoardMessages.of(t, e);
    }

    /** 없는·남의 대화(`404 DM_THREAD_NOT_FOUND`)인가 */
    static isThreadMissing(e: unknown): boolean {
        return e instanceof ApiError && e.code === 'DM_THREAD_NOT_FOUND';
    }

    /** 없는·탈퇴한 회원(`404 PROFILE_NOT_FOUND`)인가 */
    static isProfileMissing(e: unknown): boolean {
        return e instanceof ApiError && e.code === 'PROFILE_NOT_FOUND';
    }

    /** 서버가 판정하지 못한 전송 실패인가 — 연결 끊김·시간 초과·5xx(말풍선이 남아 다시 보낼 수 있다) */
    static isLocalFailure(e: unknown): boolean {
        return e instanceof ApiError && (e.status === 0 || e.status >= 500);
    }

    /** 거절 뒤 대화를 다시 읽어야 하는가 — 차단·수신 거부·한도·정지 */
    static changesState(e: unknown): boolean {
        return e instanceof ApiError && STATE_CHANGED.has(e.code);
    }

    /**
     * 전송 실패(연결 끊김·5xx) 뒤 붉은 안내 — 한도는 실패한 쪽지만큼 줄지 않는다.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류.
     * @returns {DmNoticeCopy} 안내.
     */
    static localFailure(t: Translate, e: unknown): DmNoticeCopy {
        const lead = e instanceof ApiError && e.status !== 0 ? e.message : t('dm.networkLead');
        return { tone: 'red', title: '', text: t('dm.localFailure', { lead }), link: null };
    }

    /**
     * 서버가 보내기를 거절했을 때(4xx)의 안내 — 서버 `message` 를 그대로 쓴다.
     * @param {Translate} t - 번역 함수.
     * @param {unknown} e - 잡은 오류.
     * @returns {DmRejection} 안내.
     */
    static rejection(t: Translate, e: unknown): DmRejection {
        if (!(e instanceof ApiError)) return { tone: 'red', title: '', text: UtilDmMessages.errorOf(t, e), link: null, retryAfterSeconds: null };
        const notice = (tone: DmNoticeTone, title = '', link: DmNoticeLink | null = null, text: string = e.message): DmRejection => ({ tone, title, text, link, retryAfterSeconds: null });
        switch (e.code) {
            case 'DM_CONTACT_NOT_ALLOWED':
                return notice('warn', t('dm.contactWarnTitle'));
            case 'DM_DAILY_LIMIT':
            case 'DM_NEW_PARTNER_LIMIT':
            case 'DM_SENDER_TOO_NEW':
                return notice('warn');
            case 'RATE_LIMITED':
                return { ...notice('warn'), retryAfterSeconds: e.retryAfterSeconds ?? null };
            case 'EMAIL_NOT_VERIFIED':
                return notice('warn', '', UtilDmMessages.verifyLink(t));
            case 'ACCOUNT_RESTRICTED':
                return notice('red');
            case 'DM_UNAVAILABLE':
            case 'DM_RECIPIENT_REFUSES':
            case 'DM_SELF':
            case 'PROFILE_NOT_FOUND':
            case 'BUSINESS_ACCOUNT_RESTRICTED':
            case 'DM_NOT_PERMITTED':
                return notice('muted');
            case 'VALIDATION':
                return notice('red', '', null, e.field('body') ?? e.message);
            default:
                return notice('red');
        }
    }

    /**
     * 요청 제한 안내에 붙는 기다림 — "3초 뒤에 다시 보낼 수 있어요".
     * @param {Translate} t - 번역 함수.
     * @param {number} seconds - 남은 초.
     * @param {string} language - 표시 언어(`ko`·`en`).
     * @returns {string} 글자.
     */
    static retryIn(t: Translate, seconds: number, language: string): string {
        return t('dm.retryIn', { time: UtilTime.duration(seconds, language) });
    }
}
