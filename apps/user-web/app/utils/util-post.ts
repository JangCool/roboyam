import type { BoardGuestRules, BoardNewMemberRules } from '~/composables/board/api/useBoardApi.types';
import type { ContentFormat } from '~/composables/board/api/usePostApi.types';
import UtilRichText from '#layers/rich-editor/utils/util-rich-text';

/**
 * 글·댓글을 그리고 미리 검사하는 순수 규칙. 판정은 서버가 한다 — 여기 검사는 미리 알려 주는 용도다.
 * 서버 규칙 이름(후보): 글 길이 `PostPolicy.TITLE_*`·`CONTENT_*`, 비회원 `GuestPolicy`(관리자 보안 설정 `guest*`), 새 회원 `NewMemberPolicy`(`newMember*`).
 *
 * 본문 형식(`ContentFormat`)이 `TEXT` 면 평문이다 — HTML 로 해석하지 않고 빈 줄로 문단을, 줄바꿈으로 줄을 나누고 `http(s)://` 주소만 링크로 만든다.
 * `HTML`(게시판 에디터)이면 길이는 보이는 글자 수(태그를 뗀 글자 — 서버 기준)로 재고, 그리기는 글 보기·댓글 보기가 서버가 정화한 HTML 을 `v-html` 로 한다.
 */

/** 본문 한 조각 — 글자 또는 링크 */
export interface TextSegment {
    kind: 'text' | 'link';
    text: string;
    /** 링크 주소(`http`·`https` 만) */
    href: string;
    /** 이 사이트 밖으로 가는 링크 */
    external: boolean;
}

/** 상대 시간의 단위와 수 */
export interface RelativeAge {
    unit: 'minute' | 'hour' | 'day' | 'month' | 'year';
    value: number;
}

/** 입력칸 문제 — 비었음 · 길이 */
export type LengthProblem = 'REQUIRED' | 'LENGTH';

const URL_PATTERN = /https?:\/\/[^\s<>"']+/g;
/** 링크로 세는 것 — `http(s)://` 와 `www.` 로 시작하는 주소 */
const LINK_COUNT_PATTERN = /(https?:\/\/[^\s<>"']+|\bwww\.[^\s<>"']+)/gi;
/** 이미지로 세는 것(목업 — 에디터가 생기면 첨부 수로 바뀐다): 이미지 확장자 주소, 마크다운 이미지 */
const IMAGE_PATTERN = /(!\[[^\]]*\]\([^)]*\)|https?:\/\/[^\s<>"']+\.(?:png|jpe?g|gif|webp)\b)/gi;

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

export default class UtilPost {
    static readonly TITLE_MIN = 1;
    static readonly TITLE_MAX = 100;
    static readonly CONTENT_MIN = 10;
    static readonly CONTENT_MAX = 200_000;
    /** 댓글 길이 — 관리자 설정에 없는 값이라 가정(1~2,000자) */
    static readonly COMMENT_MAX = 2000;
    /** 기타 신고 사유 설명 최대 길이(가정) */
    static readonly REPORT_DETAIL_MAX = 300;
    /** 자리 표시 체크("로봇이 아닙니다")가 내는 토큰 — Turnstile 위젯이 붙으면 그 토큰으로 바뀐다. 비밀키가 설정된 서버는 이 값을 `fields['guest.captchaToken']` 으로 거절한다 */
    static readonly PLACEHOLDER_CAPTCHA_TOKEN = 'placeholder-captcha';

    /**
     * 본문에 든 링크 수
     * @param {string} text - 본문.
     * @returns {number} `http(s)://`·`www.` 주소 개수.
     */
    static linkCount(text: string): number {
        return text.match(LINK_COUNT_PATTERN)?.length ?? 0;
    }

    /**
     * 본문에 든 이미지 수(목업 판정)
     * @param {string} text - 본문.
     * @returns {number} 이미지 주소·마크다운 이미지 개수.
     */
    static imageCount(text: string): number {
        return text.match(IMAGE_PATTERN)?.length ?? 0;
    }

    /**
     * 본문을 문단 → 줄로 나눈다. 빈 문단은 뺀다
     * @param {string} text - 평문 본문.
     * @returns {string[][]} 문단마다 줄 목록.
     */
    static paragraphs(text: string): string[][] {
        return text
            .replace(/\r\n?/g, '\n')
            .split(/\n{2,}/)
            .map((block) => block.split('\n'))
            .filter((lines) => lines.some((line) => line.trim() !== ''));
    }

    /**
     * 한 줄을 글자·링크 조각으로 나눈다. `http(s)` 주소만 링크다(다른 스킴은 글자로 남는다)
     * @param {string} line - 한 줄.
     * @param {string} siteOrigin - 이 사이트의 출처(`location.origin`) — 다르면 바깥 링크.
     * @returns {TextSegment[]} 조각 목록.
     */
    static segments(line: string, siteOrigin: string): TextSegment[] {
        const out: TextSegment[] = [];
        let last = 0;
        for (const match of line.matchAll(URL_PATTERN)) {
            const start = match.index ?? 0;
            // 문장 끝 문장부호는 주소에서 뗀다
            const raw = match[0];
            const href = raw.replace(/[.,!?)\]]+$/, '');
            if (start > last) out.push({ kind: 'text', text: line.slice(last, start), href: '', external: false });
            out.push({ kind: 'link', text: href, href, external: UtilPost.isExternal(href, siteOrigin) });
            last = start + href.length;
        }
        if (last < line.length) out.push({ kind: 'text', text: line.slice(last), href: '', external: false });
        return out;
    }

    /**
     * 바깥 링크인가
     * @param {string} href - 주소.
     * @param {string} siteOrigin - 이 사이트의 출처.
     * @returns {boolean} 출처가 다르거나 읽을 수 없으면 true.
     */
    static isExternal(href: string, siteOrigin: string): boolean {
        try {
            return new URL(href).origin !== siteOrigin;
        } catch {
            return true;
        }
    }

    /**
     * 링크의 `rel` — 바깥 링크는 `noopener`, 관리자 설정 `externalLinkNofollow` 가 켜져 있으면 `nofollow ugc noopener`
     * @param {boolean} external - 바깥 링크인가.
     * @param {boolean} nofollow - 관리자 보안 설정 `externalLinkNofollow`.
     * @returns {string | undefined} rel 값. 사이트 안 링크는 undefined.
     */
    static linkRel(external: boolean, nofollow: boolean): string | undefined {
        if (!external) return undefined;
        return nofollow ? 'nofollow ugc noopener' : 'noopener';
    }

    /**
     * 「새 글」 표시를 붙일까
     * @param {string} iso - 작성 시각.
     * @param {number} hours - 기준(시간).
     * @param {number} now - 지금(밀리초).
     * @returns {boolean} 기준 안에 쓴 글이면 true.
     */
    static isNew(iso: string, hours: number, now: number): boolean {
        const at = Date.parse(iso);
        return Number.isFinite(at) && now - at >= 0 && now - at < hours * HOUR;
    }

    /**
     * 지금으로부터 얼마나 지났는가 — 분·시간·일·달(30일)·해(365일)
     * @param {string} iso - 시각.
     * @param {number} now - 지금(밀리초).
     * @returns {RelativeAge | null} 단위와 수. 시각을 읽을 수 없으면 null.
     */
    static age(iso: string, now: number): RelativeAge | null {
        const at = Date.parse(iso);
        if (!Number.isFinite(at)) return null;
        const diff = Math.max(0, now - at);
        if (diff < HOUR) return { unit: 'minute', value: Math.floor(diff / MINUTE) };
        if (diff < DAY) return { unit: 'hour', value: Math.floor(diff / HOUR) };
        if (diff < 30 * DAY) return { unit: 'day', value: Math.floor(diff / DAY) };
        if (diff < 365 * DAY) return { unit: 'month', value: Math.floor(diff / (30 * DAY)) };
        return { unit: 'year', value: Math.floor(diff / (365 * DAY)) };
    }

    /**
     * 같은 해인가(표시 시간대가 아니라 사용자 기기 기준 — 표준형 날짜의 연 생략 판단용)
     * @param {string} iso - 시각.
     * @param {number} now - 지금(밀리초).
     * @returns {boolean} 같은 해면 true.
     */
    static sameYear(iso: string, now: number): boolean {
        const at = Date.parse(iso);
        return Number.isFinite(at) && new Date(at).getFullYear() === new Date(now).getFullYear();
    }

    /**
     * 제목 문제 — 1~100자(앞뒤 공백 제외)
     * @param {string} title - 제목.
     * @returns {LengthProblem | null} 문제가 없으면 null.
     */
    static titleProblem(title: string): LengthProblem | null {
        const length = title.trim().length;
        if (length === 0) return 'REQUIRED';
        return length > UtilPost.TITLE_MAX ? 'LENGTH' : null;
    }

    /**
     * 본문 글자 수 — 평문은 앞뒤 공백을 뺀 길이, 에디터 HTML 은 **보이는 글자 수**(태그를 뗀 글자 — 서버가 재는 기준, `UtilRichText.visibleLength`)
     * @param {string} content - 본문.
     * @param {ContentFormat} format - 본문 형식.
     * @returns {number} 글자 수.
     */
    static contentLength(content: string, format: ContentFormat = 'TEXT'): number {
        return format === 'HTML' ? UtilRichText.visibleLength(content) : content.trim().length;
    }

    /**
     * 내용이 있는가(나가기 확인용) — 에디터 HTML 은 보이는 글자나 이미지·유튜브가 있으면 true
     * @param {string} content - 본문.
     * @param {ContentFormat} format - 본문 형식.
     * @returns {boolean} 내용이 있으면 true.
     */
    static hasContent(content: string, format: ContentFormat = 'TEXT'): boolean {
        return format === 'HTML' ? UtilRichText.hasContent(content) : content.trim() !== '';
    }

    /**
     * 본문을 다른 형식으로 바꾼다 — 평문 글을 에디터 게시판에서 고치거나(평문 → HTML), 에디터 글을 평문 게시판에서 고칠 때(HTML → 평문, 서식·이미지는 빠진다).
     * 글쓰기에서 에디터 설정이 다른 게시판으로 바꿀 때도 쓴다.
     * @param {string} content - 본문.
     * @param {ContentFormat} from - 지금 형식.
     * @param {ContentFormat} to - 바꿀 형식.
     * @returns {string} 바꾼 본문(형식이 같으면 그대로).
     */
    static convertContent(content: string, from: ContentFormat, to: ContentFormat): string {
        if (from === to) return content;
        return to === 'HTML' ? UtilRichText.htmlFromText(content) : UtilRichText.textFromHtml(content);
    }

    /**
     * 본문 문제 — 10~200,000자(평문은 앞뒤 공백 제외, 에디터 HTML 은 보이는 글자 수)
     * @param {string} content - 본문.
     * @param {ContentFormat} format - 본문 형식.
     * @returns {LengthProblem | null} 문제가 없으면 null.
     */
    static contentProblem(content: string, format: ContentFormat = 'TEXT'): LengthProblem | null {
        const length = UtilPost.contentLength(content, format);
        if (length === 0) return 'REQUIRED';
        return length < UtilPost.CONTENT_MIN || length > UtilPost.CONTENT_MAX ? 'LENGTH' : null;
    }

    /**
     * 댓글 문제 — 1~2,000자(평문은 앞뒤 공백 제외, 에디터 HTML 은 보이는 글자 수)
     * @param {string} content - 댓글.
     * @param {ContentFormat} format - 본문 형식.
     * @returns {LengthProblem | null} 문제가 없으면 null.
     */
    static commentProblem(content: string, format: ContentFormat = 'TEXT'): LengthProblem | null {
        const length = UtilPost.contentLength(content, format);
        if (length === 0) return 'REQUIRED';
        return length > UtilPost.COMMENT_MAX ? 'LENGTH' : null;
    }

    /**
     * 비회원 이름 문제 — 관리자 보안 설정의 길이
     * @param {string} name - 이름.
     * @param {BoardGuestRules} rules - 비회원 규칙.
     * @returns {LengthProblem | null} 문제가 없으면 null.
     */
    static guestNameProblem(name: string, rules: BoardGuestRules): LengthProblem | null {
        const length = name.trim().length;
        if (length === 0) return 'REQUIRED';
        return length < rules.nameMin || length > rules.nameMax ? 'LENGTH' : null;
    }

    /**
     * 비회원 비밀번호 문제 — 관리자 보안 설정의 최소 길이 ~ 최대 길이
     * @param {string} password - 비밀번호.
     * @param {BoardGuestRules} rules - 비회원 규칙.
     * @returns {LengthProblem | null} 문제가 없으면 null.
     */
    static guestPasswordProblem(password: string, rules: BoardGuestRules): LengthProblem | null {
        if (password.length === 0) return 'REQUIRED';
        return password.length < rules.passwordMin || password.length > rules.passwordMax ? 'LENGTH' : null;
    }

    /**
     * 링크·이미지 수가 규칙을 넘었는가 — 비회원 규칙이 먼저, 회원은 새 회원(첫 등급)일 때만 본다
     * @param {string} content - 본문.
     * @param {{ guest: boolean; guestRules: BoardGuestRules; newMember: BoardNewMemberRules; allowImages: boolean; format?: ContentFormat }} rule - 적용할 규칙(`format` 이 `HTML` 이면 링크는 `a[href]`+글자 속 주소, 이미지는 `img[data-image-id]` 로 센다).
     * @returns {'GUEST_LINK' | 'GUEST_IMAGE' | 'NEW_LINK' | 'NEW_IMAGE' | 'NO_IMAGE' | null} 넘은 규칙. 없으면 null.
     */
    static linkProblem(
        content: string,
        rule: { guest: boolean; guestRules: BoardGuestRules; newMember: BoardNewMemberRules; allowImages: boolean; format?: ContentFormat },
    ): 'GUEST_LINK' | 'GUEST_IMAGE' | 'NEW_LINK' | 'NEW_IMAGE' | 'NO_IMAGE' | null {
        const html = rule.format === 'HTML';
        const links = html ? UtilRichText.linkCount(content) : UtilPost.linkCount(content);
        const images = html ? UtilRichText.imageCount(content) : UtilPost.imageCount(content);
        if (rule.guest) {
            if (links > rule.guestRules.linkLimit) return 'GUEST_LINK';
            if (images > 0 && (!rule.guestRules.imageUploadAllowed || !rule.allowImages)) return 'GUEST_IMAGE';
            return null;
        }
        if (images > 0 && !rule.allowImages) return 'NO_IMAGE';
        if (rule.newMember.applies && links > rule.newMember.linkLimit) return 'NEW_LINK';
        if (rule.newMember.applies && images > rule.newMember.imageLimit) return 'NEW_IMAGE';
        return null;
    }
}
