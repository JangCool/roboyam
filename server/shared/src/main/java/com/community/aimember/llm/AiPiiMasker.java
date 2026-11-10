package com.community.aimember.llm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 제공사(OpenAI·Google·Anthropic)에 보내기 전에 인용할 글·댓글에서 개인정보 모양을 {@value #MASK} 로 가린다(ai-members-contract 법무 메모 — 국외 이전 최소화).
 * 닉네임은 원래 보내지 않는다({@link AiPrompts}). 여기서는 회원이 본문에 직접 적은 연락처·식별 번호를 지운다.
 *
 * <p>가리는 것(차례대로 — 긴 모양을 먼저): 이메일 · 주민등록번호·외국인등록번호 모양 · 카드번호(4자리 넷) · 휴대전화·일반전화 · 계좌번호 모양(숫자 묶음을 하이픈으로 이어 숫자 10~16자) ·
 * 하이픈 없는 긴 숫자(10~16자리). 날짜(2026-11-10)·가격·짧은 숫자는 그대로 둔다. 모양만 보므로 조금 넓게 가려도 괜찮다(답글 품질보다 개인정보가 먼저).
 */
public final class AiPiiMasker {

    /** 가린 자리에 넣는 글자 */
    public static final String MASK = "***";

    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern RESIDENT_ID = Pattern.compile("(?<!\\d)\\d{6}\\s*-\\s*[1-8]\\d{6}(?!\\d)");
    private static final Pattern CARD = Pattern.compile("(?<!\\d)\\d{4}[- ]\\d{4}[- ]\\d{4}[- ]\\d{4}(?!\\d)");
    private static final Pattern MOBILE = Pattern.compile("(?<!\\d)(?:\\+?82[- ]?)?0?1[016789][- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)");
    private static final Pattern LANDLINE = Pattern.compile("(?<!\\d)(?:\\+?82[- ]?)?0\\d{1,2}[- .)]\\s?\\d{3,4}[- .]\\d{4}(?!\\d)");
    private static final Pattern GROUPED_DIGITS = Pattern.compile("(?<!\\d)\\d{2,6}(?:-\\d{2,7}){1,4}(?!\\d)");
    private static final Pattern LONG_DIGITS = Pattern.compile("(?<!\\d)\\d{10,16}(?!\\d)");

    private AiPiiMasker() {
    }

    /** 개인정보 모양을 가린 글. null 이면 빈 글 */
    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String s = EMAIL.matcher(text).replaceAll(MASK);
        s = RESIDENT_ID.matcher(s).replaceAll(MASK);
        s = CARD.matcher(s).replaceAll(MASK);
        s = MOBILE.matcher(s).replaceAll(MASK);
        s = LANDLINE.matcher(s).replaceAll(MASK);
        s = maskGroupedAccounts(s);
        return LONG_DIGITS.matcher(s).replaceAll(MASK);
    }

    /** 하이픈으로 이은 숫자 묶음 중 숫자가 10~16자인 것만(계좌번호 모양) — 날짜·버전 번호 같은 짧은 묶음은 둔다 */
    private static String maskGroupedAccounts(String s) {
        Matcher m = GROUPED_DIGITS.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            int digits = m.group().replace("-", "").length();
            m.appendReplacement(out, digits >= 10 && digits <= 16 ? MASK : Matcher.quoteReplacement(m.group()));
        }
        m.appendTail(out);
        return out.toString();
    }
}
