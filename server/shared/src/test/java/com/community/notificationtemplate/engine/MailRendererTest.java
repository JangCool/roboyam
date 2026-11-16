package com.community.notificationtemplate.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.notificationtemplate.catalog.MailTemplateAudience;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * 메일 그리기({@link MailRenderer})의 빈 값 줄 걷기 — 실제 발송에서 값이 빈 변수 때문에 라벨만 남은 줄이 보이지 않게 한다(관리자 QA 2026-11-17 F-2:
 * 관리자 앱 주소 {@code ADMIN_URL} 이 없을 때 운영자 계정 안내 메일에 「관리자 앱 주소:」, 꼬리말에 「관리 사이트:」 빈 줄이 남았다).
 */
class MailRendererTest {

    private static final String BODY = "<p>안내 문장</p><p>관리자 앱 주소: {{adminUrl}}</p><p>{{note}}</p><p>임시 비밀번호: <strong>{{pw}}</strong></p>";
    private static final MailTemplateValues TEMPLATE = new MailTemplateValues("t", "틀", true, MailTemplateAudience.OPERATOR, false, false, null,
            Map.of("ko", "운영자에게 보내는 메일이에요.\n관리 사이트: {{adminUrl}}\n\n{{siteUrl}}"), Map.of("ko", "<p>{{content}}</p>"), null, null);

    private static MailRenderer.Result render(Map<String, String> values, boolean keepUnknown) {
        Function<String, String> value = values::get;
        return MailRenderer.render("ko", "제목", BODY, TEMPLATE, value, keepUnknown, "IROBIT", null);
    }

    @Test
    void 값이_빈_라벨_줄은_본문과_꼬리말_모두_걷는다() {
        String html = render(Map.of("pw", "Abc12345"), false).html();
        assertThat(html).doesNotContain("관리자 앱 주소").doesNotContain("관리 사이트").contains("안내 문장").contains("Abc12345");
        // 변수만 든 꼬리말 줄({{siteUrl}})도 빈 값이면 걷고, 운영자가 일부러 비운 줄은 그대로(&nbsp;)
        assertThat(html).contains("운영자에게 보내는 메일이에요.").contains("&nbsp;");
        assertThat(html.split("&nbsp;", -1)).hasSize(2);
    }

    @Test
    void 값이_있으면_라벨_줄을_그대로_둔다() {
        String html = render(Map.of("pw", "Abc12345", "adminUrl", "https://admin.example.com", "siteUrl", "https://example.com"), false).html();
        assertThat(html).contains("관리자 앱 주소: https://admin.example.com").contains("관리 사이트: https://admin.example.com").contains("https://example.com");
    }

    @Test
    void 미리보기는_빈_값_줄을_걷지_않는다() {
        // 관리자 문구 편집 화면의 미리보기(keepUnknown)는 운영자가 어디에 무엇이 들어가는지 보도록 줄을 남긴다
        String html = render(Map.of(), true).html();
        assertThat(html).contains("관리자 앱 주소").contains("관리 사이트");
    }

    @Test
    void 라벨_뒤에_다른_글이_있거나_변수가_둘이면_걷지_않는다() {
        String body = "<p>주소: {{adminUrl}} 에서 확인</p><p>{{a}}: {{b}}</p>";
        String html = MailRenderer.render("ko", "제목", body, TEMPLATE, name -> null, false, "IROBIT", null).html();
        assertThat(html).contains("에서 확인");
        assertThat(html).contains(": ");
    }
}
