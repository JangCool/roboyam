package com.community.notificationtemplate.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.web.util.HtmlUtils;

/**
 * 문구·템플릿 글의 변수({@code {{이름}}}) 다루기 — 찾기·채우기·가리기. 문법은 {@code {{이름}}} 만(조건·반복 없음, 이름은 영문자로 시작하는 영문자·숫자·밑줄).
 * admin-web {@code UtilMailTemplate.variablesIn} · {@code UtilNotificationMessage.fillSamples} 와 같은 규칙이다.
 */
public final class TemplateText {

    /** {@code {{ 이름 }}} — 안쪽 공백은 허용한다 */
    public static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\}\\}");
    private static final Pattern CONTENT = Pattern.compile("\\{\\{\\s*content\\s*\\}\\}");
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private TemplateText() {
    }

    /** 글 안의 변수 이름들(나온 차례, 겹침 없음) */
    public static List<String> variables(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        Matcher m = VARIABLE.matcher(text);
        while (m.find()) {
            String name = m.group(1);
            if (!out.contains(name)) {
                out.add(name);
            }
        }
        return out;
    }

    /**
     * 변수를 채운다. {@code value} 가 null 을 돌려주면 그 자리를 {@code keepUnknown} 이면 그대로 두고, 아니면 빈 글자로 지운다.
     *
     * @param value 이름 → 넣을 글자(이미 가린 값). null 이면 모르는 변수
     */
    public static String fill(String text, Function<String, String> value, boolean keepUnknown) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }
        Matcher m = VARIABLE.matcher(text);
        StringBuilder out = new StringBuilder(text.length() + 32);
        while (m.find()) {
            String replaced = value.apply(m.group(1));
            m.appendReplacement(out, Matcher.quoteReplacement(replaced != null ? replaced : keepUnknown ? m.group() : ""));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** {@code {{content}}} 의 수 */
    public static int contentCount(String frame) {
        if (frame == null) {
            return 0;
        }
        Matcher m = CONTENT.matcher(frame);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    /** 틀의 첫 {@code {{content}}} 자리에 본문을 넣는다(다른 변수는 그대로) */
    public static String replaceContent(String frame, String content) {
        return CONTENT.matcher(frame == null ? "" : frame).replaceFirst(Matcher.quoteReplacement(content == null ? "" : content));
    }

    /** HTML 로 가린다 */
    public static String escape(String text) {
        return text == null ? "" : HtmlUtils.htmlEscape(text);
    }

    /** 태그를 뗀 글자 — 이력의 본문 요약 · 「비었는가」 판단(admin-web {@code UtilMailTemplate.plainText} 와 같다) */
    public static String plainText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String s = html.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("(?i)</p>", " ");
        s = TAG.matcher(s).replaceAll("");
        s = HtmlUtils.htmlUnescape(s);
        return WHITESPACE.matcher(s).replaceAll(" ").strip();
    }

    /** 이력의 본문 값 — 글자 요약 80자 */
    public static String excerpt(String html) {
        String plain = plainText(html);
        return plain.length() > 80 ? plain.substring(0, 80) + "…" : plain;
    }

    /** 한 줄로 — 줄바꿈·이어진 공백을 빈칸 하나로 */
    public static String oneLine(String text) {
        return text == null ? "" : WHITESPACE.matcher(text).replaceAll(" ").strip();
    }

    /** http · https 주소인가(링크 변수 값을 주소로 쓸지) */
    public static boolean isWebUrl(String value) {
        if (value == null) {
            return false;
        }
        String v = value.strip().toLowerCase(java.util.Locale.ROOT);
        return (v.startsWith("http://") || v.startsWith("https://")) && v.length() > 8 && v.chars().noneMatch(c -> c <= 0x20);
    }
}
