package com.community.site.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.site.dto.PostLinkPreview;
import com.community.site.dto.PublicSiteSnapshot;
import com.community.site.dto.RenderedIndex;
import com.community.site.dto.SiteLogoUrls;
import com.community.site.dto.response.SiteInfoResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 글 공유 미리보기 머리({@link IndexHtmlRenderer#withPost}) — 사이트 머리를 채운 사본에서 제목·설명·og·twitter 칸만 글의 것으로 바뀌는가(관리자 QA 2026-11-17).
 * 틀은 손으로 만든 작은 HTML 에 실제 {@link IndexHtmlRenderer#fill} 로 머리를 채운다.
 */
class IndexHtmlRendererPostTest {

    private static final String TEMPLATE = "<!doctype html><html><head><meta charset=\"utf-8\"><title>틀</title></head><body><div id=\"__nuxt\"></div></body></html>";
    private static final String SITE_OG = "https://irobit.test/media/site/og.png";

    private final IndexHtmlRenderer renderer = new IndexHtmlRenderer();

    private static PublicSiteSnapshot snapshot() {
        SiteInfoResponse info = new SiteInfoResponse("아이로빗", "로봇 커뮤니티", null, null, SITE_OG, null, null, null, null, null, List.of(), false,
                SiteLogoUrls.EMPTY, false);
        return new PublicSiteSnapshot(info, null, null, true, null, "https://irobit.test", "e1");
    }

    private static RenderedIndex base() {
        byte[] body = IndexHtmlRenderer.fill(TEMPLATE, snapshot()).getBytes(StandardCharsets.UTF_8);
        return new RenderedIndex(body, "base");
    }

    private static String html(RenderedIndex index) {
        return new String(index.body(), StandardCharsets.UTF_8);
    }

    @Test
    void 글_제목_설명_그림으로_바꾸고_운영자_그림_크기_칸은_뺀다() {
        RenderedIndex out = renderer.withPost(base(), new PostLinkPreview("새 로봇 청소기 후기", "흡입력이 좋아요.", "https://irobit.test/media/t/1.webp",
                "https://irobit.test/posts/1"), snapshot());
        String h = html(out);
        assertThat(h).contains("<title>새 로봇 청소기 후기 — 아이로빗</title>")
                .contains("<meta property=\"og:type\" content=\"article\">")
                .contains("<meta property=\"og:title\" content=\"새 로봇 청소기 후기 — 아이로빗\">")
                .contains("<meta name=\"twitter:title\" content=\"새 로봇 청소기 후기 — 아이로빗\">")
                .contains("<meta name=\"description\" content=\"흡입력이 좋아요.\">")
                .contains("<meta property=\"og:description\" content=\"흡입력이 좋아요.\">")
                .contains("<meta name=\"twitter:description\" content=\"흡입력이 좋아요.\">")
                .contains("<meta property=\"og:image\" content=\"https://irobit.test/media/t/1.webp\">")
                .contains("<meta name=\"twitter:image\" content=\"https://irobit.test/media/t/1.webp\">")
                .contains("<meta property=\"og:site_name\" content=\"아이로빗\">")
                .doesNotContain("og:image:width").doesNotContain("og:image:height").doesNotContain(SITE_OG).doesNotContain("<title>틀</title>");
        assertThat(out.etag()).isNotEqualTo("base");
    }

    @Test
    void 설명이_비고_그림이_없으면_사이트_소개와_그림을_그대로_둔다() {
        String h = html(renderer.withPost(base(), new PostLinkPreview("그림 없는 글", "", null, null), snapshot()));
        assertThat(h).contains("<meta property=\"og:title\" content=\"그림 없는 글 — 아이로빗\">")
                .contains("<meta property=\"og:description\" content=\"로봇 커뮤니티\">")
                .contains("<meta property=\"og:image\" content=\"" + SITE_OG + "\">")
                .contains("og:image:width");
    }

    @Test
    void 제목의_특수문자는_이스케이프한다() {
        String h = html(renderer.withPost(base(), new PostLinkPreview("<script>\"따옴표\" & 꺾쇠", "a < b", null, null), snapshot()));
        assertThat(h).contains("<title>&lt;script&gt;&quot;따옴표&quot; &amp; 꺾쇠 — 아이로빗</title>")
                .contains("content=\"&lt;script&gt;&quot;따옴표&quot; &amp; 꺾쇠 — 아이로빗\"")
                .contains("<meta name=\"description\" content=\"a &lt; b\">")
                .doesNotContain("<script>\"");
    }
}
