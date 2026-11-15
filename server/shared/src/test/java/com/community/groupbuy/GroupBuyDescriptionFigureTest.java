package com.community.groupbuy;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.groupbuy.service.GroupBuyDescriptionHtml;
import org.junit.jupiter.api.Test;

/**
 * 상품 설명 정화 — 에디터 이미지 도구의 이미지 묶음({@code figure}·{@code figcaption}, 정렬·크기). 계약 docs/features/editor-image-tools-contract.md.
 */
class GroupBuyDescriptionFigureTest {

    private static final String IMG = "<img src=\"https://cdn.example.com/a.jpg\" alt=\"사진\" width=\"800\" height=\"600\" />";

    @Test
    void 정렬_크기_캡션이_있는_묶음은_정해진_모양으로_남는다() {
        String raw = "<figure data-align=\"center\" data-width=\"50\" class=\"x\" style=\"float:left\">"
                + "<img src=\"https://cdn.example.com/a.jpg\" data-image-id=\"7\" alt=\"사진\" width=\"800\" height=\"600\">"
                + "<figcaption>정면 <b>모습</b></figcaption></figure>";

        assertThat(GroupBuyDescriptionHtml.sanitize(raw)).isEqualTo("<figure data-align=\"center\" data-width=\"50\">"
                + "<img src=\"https://cdn.example.com/a.jpg\" data-image-id=\"7\" alt=\"사진\" width=\"800\" height=\"600\" />"
                + "<figcaption>정면 모습</figcaption></figure>");
    }

    @Test
    void 이미지_표식은_숫자일_때만_남는다_고치기_화면이_다시_읽는다() {
        assertThat(GroupBuyDescriptionHtml.sanitize("<img src=\"/media/p/1.webp\" data-image-id=\"42\">"))
                .isEqualTo("<img src=\"/media/p/1.webp\" data-image-id=\"42\" />");
        assertThat(GroupBuyDescriptionHtml.sanitize("<img src=\"/media/p/1.webp\" data-image-id=\"x1\">")).isEqualTo("<img src=\"/media/p/1.webp\" />");
        assertThat(GroupBuyDescriptionHtml.sanitize("<img src=\"/media/p/1.webp\" data-image-id=\"0\">")).isEqualTo("<img src=\"/media/p/1.webp\" />");
    }

    @Test
    void 정화한_결과를_다시_정화해도_같다() {
        String once = GroupBuyDescriptionHtml.sanitize("<figure data-align=\"right\" data-width=\"75\"><img src=\"/media/p/1.webp\" data-image-id=\"42\" alt=\"옆모습\">"
                + "<figcaption>옆 &amp; 뒤</figcaption></figure>");

        assertThat(GroupBuyDescriptionHtml.sanitize(once)).isEqualTo(once);
    }

    @Test
    void 정해진_값이_아닌_정렬_크기는_버리고_빈_캡션은_뺀다() {
        String raw = "<figure data-align=\"justify\" data-width=\"33\"><img src=\"https://cdn.example.com/a.jpg\" alt=\"사진\" width=\"800\" height=\"600\">"
                + "<figcaption>  </figcaption></figure>";

        assertThat(GroupBuyDescriptionHtml.sanitize(raw)).isEqualTo("<figure>" + IMG + "</figure>");
    }

    @Test
    void 캡션은_200자까지만_남고_꺾쇠는_이스케이프된다() {
        String raw = "<figure data-align=\"right\"><img src=\"https://cdn.example.com/a.jpg\" alt=\"사진\" width=\"800\" height=\"600\">"
                + "<figcaption>&lt;script&gt;" + "가".repeat(300) + "</figcaption></figure>";

        String safe = GroupBuyDescriptionHtml.sanitize(raw);
        String caption = safe.substring(safe.indexOf("<figcaption>") + "<figcaption>".length(), safe.indexOf("</figcaption>"));
        assertThat(safe).startsWith("<figure data-align=\"right\">" + IMG).doesNotContain("<script");
        assertThat(caption).startsWith("&lt;script&gt;");
        assertThat(caption.replace("&lt;", "<").replace("&gt;", ">").codePointCount(0, caption.replace("&lt;", "<").replace("&gt;", ">").length())).isEqualTo(200);
    }

    @Test
    void 이미지가_없는_묶음은_태그만_벗기고_글자는_남긴다() {
        String raw = "<figure data-align=\"center\"><img src=\"javascript:alert(1)\"><figcaption>설명 글</figcaption></figure><p>본문</p>";

        assertThat(GroupBuyDescriptionHtml.sanitize(raw)).doesNotContain("figure").doesNotContain("figcaption").doesNotContain("javascript")
                .contains("설명 글").contains("<p>본문</p>");
    }

    @Test
    void 묶음이_없는_이미지는_지금처럼_그대로다() {
        assertThat(GroupBuyDescriptionHtml.sanitize("<p>설명</p><img src=\"https://cdn.example.com/a.jpg\" alt=\"사진\" width=\"800\" height=\"600\">"))
                .isEqualTo("<p>설명</p>" + IMG);
    }
}
