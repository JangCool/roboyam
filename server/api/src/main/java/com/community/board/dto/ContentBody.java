package com.community.board.dto;

import com.community.board.service.ContentRules;
import com.community.board.service.RichText;
import com.community.post.entity.ContentFormat;
import java.util.List;

/**
 * 검사·저장할 글·댓글 본문 한 벌 — 평문(TEXT)은 다듬은 글 그대로, HTML 은 정화한 HTML 과 거기서 뽑은 값.
 *
 * @param content  저장할 본문(TEXT: 앞뒤 공백·줄바꿈을 맞춘 평문, HTML: 정화한 HTML — 이미지 표식은 아직 채우기 전)
 * @param plain    보이는 글자(TEXT 는 {@code content} 와 같다) — 길이 규칙·금지어·검색·같은 글 판정·알림 미리보기
 * @param links    링크 수(HTML 은 링크 요소 + 글자 속 주소)
 * @param images   이미지 수(TEXT 는 이미지 주소·마크다운 이미지, HTML 은 이미지 표식)
 * @param imageIds HTML 이미지 표식의 ID(본문 순서, 겹친 것 포함) — TEXT 는 비어 있다
 * @param hrefs    HTML 링크 주소(금지어 검사) — TEXT 는 비어 있다
 * @param youtube  YouTube 표식 수
 */
public record ContentBody(ContentFormat format, String content, String plain, int links, int images, List<Long> imageIds, List<String> hrefs,
                          int youtube) {

    /** 평문 본문 */
    public static ContentBody text(String raw) {
        String content = ContentRules.normalize(raw);
        return new ContentBody(ContentFormat.TEXT, content, content, ContentRules.linkCount(content), ContentRules.imageCount(content), List.of(), List.of(), 0);
    }

    /** HTML 본문 — 허용 목록으로 정화하고 값을 뽑는다 */
    public static ContentBody html(String raw, RichText.Level level) {
        String safe = RichText.sanitize(raw, level);
        List<Long> ids = RichText.imageIds(safe);
        return new ContentBody(ContentFormat.HTML, safe, RichText.plainText(safe), RichText.linkCount(safe), ids.size(), List.copyOf(ids),
                RichText.hrefs(safe), RichText.youtubeCount(safe));
    }

    public boolean isHtml() {
        return format == ContentFormat.HTML;
    }

    /** 금지어 검사에 넘길 글자 — 보이는 글자 + 링크 주소 */
    public String wordsHaystack() {
        return hrefs.isEmpty() ? plain : plain + "\n" + String.join("\n", hrefs);
    }

    /** 금지어 가림 방식이면 가린 본문(HTML 은 태그 밖 글자만 가리고 보이는 글자를 다시 뽑는다). 거부 방식·꺼짐이면 그대로 */
    public ContentBody masked(BoardSecurityValues sec) {
        if (!isHtml()) {
            String content = ContentRules.mask(sec, this.content);
            return content.equals(this.content) ? this : new ContentBody(format, content, content, links, images, imageIds, hrefs, youtube);
        }
        String html = RichText.maskText(content, text -> ContentRules.mask(sec, text));
        return html.equals(content) ? this : new ContentBody(format, html, RichText.plainText(html), links, images, imageIds, hrefs, youtube);
    }
}
