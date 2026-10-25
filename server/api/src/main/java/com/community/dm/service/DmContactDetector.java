package com.community.dm.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 쪽지의 링크·외부 연락처 감지 — 상대가 이 대화에서 아직 쪽지를 보내지 않았으면(contactAllowed = false) 이런 내용을 막는다({@code 400 DM_CONTACT_NOT_ALLOWED}).
 * user-web {@code utils/util-dm-contact.ts} 와 <b>같은 규칙</b>이다(한쪽을 고치면 다른 쪽도 고친다): NFKC → 보이지 않는 글자 지우기 → 소문자 → 판정, 전화번호는 숫자 사이의
 * 공백·점·하이픈 등을 지운 뒤. 공백은 ASCII 공백 모음만(자바·화면 같게). 1차 방어선이라 완벽하지 않다(글자 쪼개기 등은 못 잡는다).
 */
public final class DmContactDetector {

    private static final String WS = " \\t\\n\\x0B\\f\\r";
    private static final String NOT_WS = "[^" + WS + "]";
    private static final Pattern URL = Pattern.compile("(?:https?|ftp)://" + NOT_WS + "+|(?<![a-z0-9])www\\.[a-z0-9-]+");
    private static final Pattern DOMAIN = Pattern.compile(
            "(?<![a-z0-9@._-])[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)*"
                    + "\\.(?:com|net|org|kr|io|ly|gl|link|app|xyz|dev|ai|tv|cc|info|biz|gg|jp|cn)(?![a-z0-9])");
    private static final Pattern SHORT_DOMAIN = Pattern.compile("(?<![a-z0-9])[a-z0-9-]+\\.(?:me|to|so|be|us|ee|gd|ms)/" + NOT_WS + "*");
    private static final Pattern EMAIL = Pattern.compile("[a-z0-9._%+-]+@[a-z0-9-]+(?:\\.[a-z0-9-]+)+");
    private static final Pattern HANDLE = Pattern.compile("(?<![a-z0-9._%+-])@[a-z0-9._]{3,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)0\\d{8,10}(?!\\d)|(?<!\\d)\\+?82\\d{9,10}(?!\\d)|(?<!\\d)1[5-9]\\d{6}(?!\\d)");
    private static final Pattern DIGIT_GAPS = Pattern.compile("(?<=\\d)[" + WS + ".·_()-]+(?=\\d)");
    private static final Pattern SPELLED_PHONE = Pattern.compile("(?:공|영)[" + WS + "]*일[" + WS + "]*(?:공|영)");
    private static final String MESSENGER_NAMES = "카카오톡|카톡|카카오|kakaotalk|kakao|텔레그램|텔레|telegram|디스코드|discord|인스타그램|인스타|instagram|"
            + "위챗|wechat|왓츠앱|whatsapp|스카이프|skype|라인|페메";
    private static final Pattern MESSENGER_ID = Pattern.compile("(?:" + MESSENGER_NAMES + ")[^\\n]{0,8}?(?:아이디|id)[^a-z0-9\\n]{0,5}[a-z0-9][a-z0-9._-]{2,}");
    private static final Pattern MESSENGER_TOKEN = Pattern.compile(
            "(?:" + MESSENGER_NAMES + ")[^a-z0-9\\n]{0,6}@?(?=[a-z0-9._-]*[a-z])(?=[a-z0-9._-]*\\d)[a-z0-9][a-z0-9._-]{3,}");
    private static final Pattern OPEN_CHAT = Pattern.compile("오픈[" + WS + "]*(?:채팅|카톡|톡)|open\\.?kakao");
    private static final Pattern INVISIBLE = Pattern.compile("[\\u00AD\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2069\\uFEFF]");

    private DmContactDetector() {
    }

    /** 걸린 종류 — 링크 → 이메일 → @아이디 → 전화번호 → 메신저 차례. 없으면 null */
    public static String detect(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        String text = INVISIBLE.matcher(Normalizer.normalize(body, Normalizer.Form.NFKC)).replaceAll("").toLowerCase(Locale.ROOT);
        if (URL.matcher(text).find() || DOMAIN.matcher(text).find() || SHORT_DOMAIN.matcher(text).find()) {
            return "LINK";
        }
        if (EMAIL.matcher(text).find()) {
            return "EMAIL";
        }
        if (HANDLE.matcher(text).find()) {
            return "HANDLE";
        }
        if (PHONE.matcher(DIGIT_GAPS.matcher(text).replaceAll("")).find() || SPELLED_PHONE.matcher(text).find()) {
            return "PHONE";
        }
        if (MESSENGER_ID.matcher(text).find() || MESSENGER_TOKEN.matcher(text).find() || OPEN_CHAT.matcher(text).find()) {
            return "MESSENGER";
        }
        return null;
    }

    public static boolean has(String body) {
        return detect(body) != null;
    }
}
