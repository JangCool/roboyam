import UtilDisplayChoices from '#layers/display-time/utils/util-display-choices';
import UtilTime, { type DisplayValues } from '#layers/display-time/utils/util-time';
import type { Translate } from '#layers/i18n/utils/util-i18n';
import type { MemberDetailRow, MemberFilter } from '~/utils/util-member';

/** 요약 글자를 만들 때 화면이 빌려 주는 도구 */
export interface MemberSummaryContext {
    /** 역할 id → 이름 */
    roleName: (roleId: string) => string;
    /** 등급 키 → 이름(등급 목록에서 읽는다 — 목록에 없으면 키 글자) */
    gradeName: (gradeKey: string) => string;
    /** 달력 날짜 글자(`YYYY-MM-DD`) → 표시 날짜 형식 */
    date: (ymd: string) => string;
    /** 숫자 → 표시 글자(자리 구분) */
    number: (n: number) => string;
}

/**
 * 회원 조회 화면의 계산이 낀 문구. 상수 문구는 컴포넌트가 `t('member.…')` 로 쓴다(`i18n.md`).
 */
export default class UtilMemberMessages {
    /**
     * 회원의 시간대 글자 — 공유 레이어의 시간대 표기(`(UTC+09:00) Asia/Seoul · 서울`)를 운영자 화면 언어로.
     * @param {Translate} t - 번역 함수.
     * @param {string} locale - 운영자 화면 언어.
     * @param {DisplayValues} display - 회원의 표시 값.
     * @param {number} now - 시차를 잴 순간(밀리초).
     * @returns {string} 시간대 글자.
     */
    static zoneText(t: Translate, locale: string, display: DisplayValues, now: number): string {
        return UtilDisplayChoices.zoneText(display.zoneId, now, { t, language: locale });
    }

    /**
     * 회원의 시간 형식 글자 — `24시간 (14:32)` 꼴(고른 시간대의 지금 견본이 붙는다).
     * @param {Translate} t - 번역 함수.
     * @param {string} locale - 운영자 화면 언어.
     * @param {DisplayValues} display - 회원의 표시 값.
     * @param {number} now - 견본으로 그릴 순간(밀리초).
     * @returns {string} 시간 형식 글자.
     */
    static timeFormatText(t: Translate, locale: string, display: DisplayValues, now: number): string {
        return UtilDisplayChoices.timeFormatChoices(display.zoneId, now, { t, language: locale }).find((c) => c.value === display.timeFormat)?.label ?? display.timeFormat;
    }

    /**
     * 회원의 날짜 형식 글자 — `연.월.일 (2026.10.14)` 꼴.
     * @param {Translate} t - 번역 함수.
     * @param {string} locale - 운영자 화면 언어.
     * @param {DisplayValues} display - 회원의 표시 값.
     * @param {number} now - 견본으로 그릴 순간(밀리초).
     * @returns {string} 날짜 형식 글자.
     */
    static dateFormatText(t: Translate, locale: string, display: DisplayValues, now: number): string {
        return UtilDisplayChoices.dateFormatChoices(display.zoneId, now, { t, language: locale }).find((c) => c.value === display.dateFormat)?.label ?? display.dateFormat;
    }

    /**
     * 회원의 언어 이름 — 그 언어로 적는다(`한국어`·`English`).
     * @param {DisplayValues} display - 회원의 표시 값.
     * @returns {string} 언어 이름.
     */
    static languageName(display: DisplayValues): string {
        return UtilDisplayChoices.LANGUAGE_CHOICES.find((c) => c.value === display.language)?.label ?? display.language;
    }

    /**
     * 이 회원에게 시각이 어떻게 보이는지 견본 — 회원의 표시 값으로 그린 정확한 시각.
     * @param {DisplayValues} display - 회원의 표시 값.
     * @param {number} now - 견본으로 그릴 순간(밀리초).
     * @returns {string} 시각 글자.
     */
    static displayPreview(display: DisplayValues, now: number): string {
        return UtilTime.of(display).full(new Date(now).toISOString());
    }

    /**
     * 기간 글자 — 둘 다 있으면 `시작 ~ 끝`, 하나만 있으면 `시작 ~` / `~ 끝`. 둘 다 없으면 빈 글자.
     * @param {string} from - 시작(`YYYY-MM-DD`, 비면 '').
     * @param {string} to - 끝(`YYYY-MM-DD`, 비면 '').
     * @param {(ymd: string) => string} date - 날짜 표시 함수.
     * @returns {string} 기간 글자.
     */
    static range(from: string, to: string, date: (ymd: string) => string): string {
        if (!from && !to) return '';
        return `${from ? date(from) : ''} ~ ${to ? date(to) : ''}`.trim();
    }

    /**
     * 마지막으로 검색한 조건을 사람이 읽는 조각들로 — 조회 카드 안의 "적용 중인 조건" 칩이다. 화면에서 꺼 둔 상세 줄의 조건은 넣지 않는다(검색에도 안 간다).
     * @param {Translate} t - 번역 함수.
     * @param {MemberFilter} filter - 마지막으로 검색한 조건.
     * @param {readonly MemberDetailRow[]} rows - 그때 화면에 보였던 상세 줄.
     * @param {MemberSummaryContext} ctx - 화면이 빌려 주는 도구.
     * @returns {string[]} 조건 조각들. 걸린 조건이 없으면 빈 배열.
     */
    static summary(t: Translate, filter: MemberFilter, rows: readonly MemberDetailRow[], ctx: MemberSummaryContext): string[] {
        const shown = new Set(rows);
        const out: string[] = [];
        const add = (nameKey: string, value: string) => {
            if (value) out.push(t('member.summary.of', { name: t(nameKey), value }));
        };
        const list = (prefix: string, values: readonly string[]) => values.map((v) => t(`${prefix}.${v}`)).join(', ');

        const keyword = filter.keyword.trim();
        if (keyword) out.push(t('member.summary.of', { name: t(`member.searchField.${filter.searchField}`), value: keyword }));
        add('member.filter.status', list('member.status', filter.statuses));
        add('member.filter.role', filter.roleIds.map(ctx.roleName).join(', '));
        add('member.filter.joined', UtilMemberMessages.range(filter.joinedFrom, filter.joinedTo, ctx.date));
        if (shown.has('lastLogin')) add('member.filter.lastLogin', filter.noLoginHistory ? t('member.filter.noLoginHistory') : UtilMemberMessages.range(filter.lastLoginFrom, filter.lastLoginTo, ctx.date));
        if (shown.has('grade')) add('member.filter.grade', filter.grades.map(ctx.gradeName).join(', '));
        if (shown.has('memberType') && filter.memberType) add('member.filter.memberType', t(`member.type.${filter.memberType}`));
        if (shown.has('emailVerified') && filter.emailVerified) add('member.filter.emailVerified', t(`member.emailVerified.${filter.emailVerified}`));
        if (shown.has('authMethod')) add('member.filter.authMethod', list('member.authMethod', filter.authMethods));
        if (shown.has('dormant')) add('member.filter.dormant', UtilMemberMessages.range(filter.dormantFrom, filter.dormantTo, ctx.date));
        if (shown.has('statusChanged')) {
            const kind = filter.statusChangedKind ? t(`member.statusChange.${filter.statusChangedKind}`) : '';
            const period = UtilMemberMessages.range(filter.statusChangedFrom, filter.statusChangedTo, ctx.date);
            add('member.filter.statusChanged', [kind, period].filter(Boolean).join(' · '));
        }
        if (shown.has('point') && (filter.pointMin !== null || filter.pointMax !== null)) {
            add('member.filter.point', `${filter.pointMin !== null ? ctx.number(filter.pointMin) : ''} ~ ${filter.pointMax !== null ? ctx.number(filter.pointMax) : ''}`.trim());
        }
        if (shown.has('boardRole') && filter.boardRole) add('member.filter.boardRole', t(`member.boardRole.${filter.boardRole}`));
        if (shown.has('sanction') && filter.hasSanction) add('member.filter.sanction', t(`member.sanction.${filter.hasSanction}`));
        return out;
    }
}
