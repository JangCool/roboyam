/*
 * 쪽지의 링크·외부 연락처 감지 — community 서버 `ContactDetector` 의 규칙을 그대로 옮긴 것이다(community `docs/api-dm.md` 「링크·외부 연락처」).
 * 상대가 이 대화에서 아직 쪽지를 보내지 않았을 때(`contactAllowed` false) 입력 중에 경고하고 보내기를 막는 데 쓴다. **서버가 최종 판정한다.**
 * 정리 순서(NFKC → 보이지 않는 글자 지우기 → 소문자 → 판정, 전화번호는 숫자 사이의 공백·점·하이픈 지우기)를 서버와 같게 둔다 — 로보얌 서버를 만들 때 같은 규칙을 쓴다.
 * 자바의 `\s` 는 ASCII 공백뿐이라 같은 글자 모음으로 적었고, 글자를 코드 포인트로 세려고 `u` 플래그를 쓴다.
 */

const WS = ' \\t\\n\\x0B\\f\\r';
const NOT_WS = `[^${WS}]`;
const URL = new RegExp(`(?:https?|ftp)://${NOT_WS}+|(?<![a-z0-9])www\\.[a-z0-9-]+`, 'u');
const DOMAIN = new RegExp(
    '(?<![a-z0-9@._-])[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*' +
        '\\.(?:com|net|org|kr|io|ly|gl|link|app|xyz|dev|ai|tv|cc|info|biz|gg|jp|cn)(?![a-z0-9])',
    'u',
);
const SHORT_DOMAIN = new RegExp(`(?<![a-z0-9])[a-z0-9-]+\\.(?:me|to|so|be|us|ee|gd|ms)/${NOT_WS}*`, 'u');
const EMAIL_PATTERN = /[a-z0-9._%+-]+@[a-z0-9-]+(?:\.[a-z0-9-]+)+/u;
const HANDLE_PATTERN = /(?<![a-z0-9._%+-])@[a-z0-9._]{3,}/u;
const PHONE_PATTERN = /(?<!\d)0\d{8,10}(?!\d)|(?<!\d)\+?82\d{9,10}(?!\d)|(?<!\d)1[5-9]\d{6}(?!\d)/u;
const DIGIT_GAPS = new RegExp(`(?<=\\d)[${WS}.·_()-]+(?=\\d)`, 'gu');
const SPELLED_PHONE = new RegExp(`(?:공|영)[${WS}]*일[${WS}]*(?:공|영)`, 'u');
const MESSENGER_NAMES =
    '카카오톡|카톡|카카오|kakaotalk|kakao|텔레그램|텔레|telegram|디스코드|discord|인스타그램|인스타|instagram|' + '위챗|wechat|왓츠앱|whatsapp|스카이프|skype|라인|페메';
const MESSENGER_ID = new RegExp(`(?:${MESSENGER_NAMES})[^\\n]{0,8}?(?:아이디|id)[^a-z0-9\\n]{0,5}[a-z0-9][a-z0-9._-]{2,}`, 'u');
const MESSENGER_TOKEN = new RegExp(`(?:${MESSENGER_NAMES})[^a-z0-9\\n]{0,6}@?(?=[a-z0-9._-]*[a-z])(?=[a-z0-9._-]*\\d)[a-z0-9][a-z0-9._-]{3,}`, 'u');
const OPEN_CHAT = new RegExp(`오픈[${WS}]*(?:채팅|카톡|톡)|open\\.?kakao`, 'u');
const INVISIBLE = /[­​-‏‪-‮⁠-⁩﻿]/gu;

/** 무엇이 걸렸는지 */
export type DmContactKind = 'LINK' | 'EMAIL' | 'HANDLE' | 'PHONE' | 'MESSENGER';

export default class UtilDmContact {
    /**
     * 쪽지 본문에서 링크·외부 연락처를 찾는다(링크 → 이메일 → @아이디 → 전화번호 → 메신저 차례).
     * @param {string | null | undefined} body - 입력한 내용.
     * @returns {DmContactKind | null} 걸린 종류. 없으면 null.
     */
    static detect(body: string | null | undefined): DmContactKind | null {
        if (!body) return null;
        const text = body.normalize('NFKC').replace(INVISIBLE, '').toLowerCase();
        if (URL.test(text) || DOMAIN.test(text) || SHORT_DOMAIN.test(text)) return 'LINK';
        if (EMAIL_PATTERN.test(text)) return 'EMAIL';
        if (HANDLE_PATTERN.test(text)) return 'HANDLE';
        if (PHONE_PATTERN.test(text.replace(DIGIT_GAPS, '')) || SPELLED_PHONE.test(text)) return 'PHONE';
        if (MESSENGER_ID.test(text) || MESSENGER_TOKEN.test(text) || OPEN_CHAT.test(text)) return 'MESSENGER';
        return null;
    }

    /**
     * 링크·외부 연락처가 들어 있는가.
     * @param {string | null | undefined} body - 입력한 내용.
     * @returns {boolean} 하나라도 걸리면 true.
     */
    static has(body: string | null | undefined): boolean {
        return UtilDmContact.detect(body) !== null;
    }
}
