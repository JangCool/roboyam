package com.community.notificationtemplate.engine;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;

/**
 * 이메일 본문·메일 템플릿 틀의 HTML 정화 — 허용 목록(OWASP Java HTML Sanitizer). 게시판 「간단」 에디터({@code RichText} SIMPLE)와 같은 태그에 「버튼」 블록만 더했다:
 * {@code p br strong em u s a ul ol li blockquote} + {@code <p data-mail-button="">}. 저장 전에 <b>서버에서 반드시</b> 거친다 — 목록에 없는 태그·속성(스크립트, 이벤트 속성, style, 그림 …)은
 * 사라진다(글자는 남는다).
 *
 * <ul>
 *   <li>링크 주소는 http·https, 또는 <b>변수 하나</b>({@code href="{{verifyLink}}"})만 남는다. {@code javascript:}·{@code mailto:} 같은 다른 주소는 {@code href} 를 걷는다.</li>
 *   <li>변수 주소는 정화기가 모르는 모양이라 정화하는 동안만 표식({@value #VAR_PROTOCOL}{@code :이름})으로 바꿔 두었다가 되돌린다.</li>
 * </ul>
 */
public final class MailHtml {

    private static final String VAR_PROTOCOL = "roboyam-var";
    private static final Pattern VAR_HREF = Pattern.compile("href=\"\\{\\{\\s*([A-Za-z][A-Za-z0-9_]*)\\s*\\}\\}\"");
    private static final Pattern MARKED_HREF = Pattern.compile("href=\"" + VAR_PROTOCOL + ":([A-Za-z][A-Za-z0-9_]*)\"");

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements("p", "br", "strong", "em", "u", "s", "blockquote", "ul", "ol", "li", "a")
            .allowAttributes("data-mail-button").onElements("p")
            .allowUrlProtocols("http", "https", VAR_PROTOCOL)
            .allowAttributes("href").onElements("a")
            .toFactory();

    private MailHtml() {
    }

    /** 허용 목록으로 정화한 HTML */
    public static String sanitize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String marked = VAR_HREF.matcher(raw).replaceAll("href=\"" + VAR_PROTOCOL + ":$1\"");
        // 정화기는 글자 속 { } 를 문자 참조로 바꾼다(클라이언트 템플릿 주입 방어) — 변수 자리는 글자 표식으로 감쌌다가 되돌린다
        marked = TemplateText.VARIABLE.matcher(marked).replaceAll(TEXT_BEGIN + "$1" + TEXT_END);
        String safe = POLICY.sanitize(marked);
        safe = MARKED_HREF.matcher(safe).replaceAll("href=\"{{$1}}\"");
        Matcher m = MARKED_TEXT.matcher(safe);
        return m.replaceAll("{{$1}}");
    }

    private static final String TEXT_BEGIN = "RBYVARBEGIN";
    private static final String TEXT_END = "RBYVAREND";
    private static final Pattern MARKED_TEXT = Pattern.compile(TEXT_BEGIN + "([A-Za-z][A-Za-z0-9_]*?)" + TEXT_END);
}
