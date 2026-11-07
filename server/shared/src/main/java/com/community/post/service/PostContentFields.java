package com.community.post.service;

import com.community.board.dto.BoardPrefixInfo;
import com.community.board.dto.BoardSecurityValues;
import com.community.board.dto.ContentBody;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.ContentRules;
import com.community.board.service.RichText;
import com.community.post.entity.ContentFormat;
import com.community.zconfiguration.message.MessageRef;
import java.util.Map;
import java.util.Optional;

/**
 * 글 칸 검사 — 제목·본문 형식·본문 길이(HTML 은 보이는 글자), 말머리, 19금, 링크·이미지 수. 공개 사이트 글쓰기({@code PostWriteService}, api)와
 * 관리자 공지 쓰기({@code NoticeAdminService}, admin)가 같은 규칙을 쓴다. 위반은 {@code fields} 에 칸 경로({@code title}·{@code contentFormat}·{@code content}·
 * {@code prefixId}·{@code adult})로 모은다 — 던지는 것은 부르는 쪽이다.
 */
public final class PostContentFields {

    private PostContentFields() {
    }

    /**
     * 검사를 마친 글 칸.
     *
     * @param adultByPrefix 성인 전용 말머리를 골라 19금이 됐다(글쓴이가 고르지 않았어도)
     */
    public record Cleaned(Long prefixId, String title, ContentBody body, boolean adult, boolean adultByPrefix, boolean hasImage) {
    }

    /**
     * 글 칸 검사 — 제목·본문 길이, 말머리(이 게시판의 켜진 말머리, 운영진 전용은 매니저·스텝만 — 이미 그 글에 붙은 말머리는 그대로 둘 수 있다), 19금, 링크·이미지.
     *
     * @param currentPrefixId 고치기면 지금 글의 말머리, 쓰기면 null
     */
    public static Cleaned check(EffectiveBoard b, boolean staff, ContentRules.Writer writer, Long currentPrefixId, String prefixText, String titleText,
                                String formatText, String contentText, Boolean adultFlag, BoardSecurityValues sec, Map<String, MessageRef> fields) {
        String title = ContentRules.normalize(titleText).replace('\n', ' ');
        int titleLength = ContentRules.length(title);
        if (titleLength < ContentRules.TITLE_MIN || titleLength > ContentRules.TITLE_MAX) {
            fields.put("title", new MessageRef("board.content.title.length", ContentRules.TITLE_MIN, ContentRules.TITLE_MAX));
        }
        ContentBody body = body(b, formatText, contentText, fields);
        if (!fields.containsKey("contentFormat") && !fields.containsKey("content")) {
            int contentLength = ContentRules.length(body.plain());
            if (contentLength < ContentRules.CONTENT_MIN || contentLength > ContentRules.CONTENT_MAX) {
                fields.put("content", new MessageRef("board.content.body.length", ContentRules.CONTENT_MIN, String.format("%,d", ContentRules.CONTENT_MAX)));
            } else if (body.isHtml() && body.content().length() > ContentRules.CONTENT_HTML_MAX) {
                fields.put("content", new MessageRef("board.content.html.tooLong"));
            }
        }
        Long prefixId = null;
        boolean adultByPrefix = false;
        if (prefixText != null && !prefixText.isBlank()) {
            prefixId = parseId(prefixText);
            Optional<BoardPrefixInfo> prefix = b.prefix(prefixId);
            boolean keep = prefixId != null && prefixId.equals(currentPrefixId);
            if (prefix.isEmpty() || (!keep && !prefix.get().active())) {
                fields.put("prefixId", new MessageRef("board.content.prefix.unknown"));
            } else if (!keep && prefix.get().staffOnly() && !staff) {
                fields.put("prefixId", new MessageRef("board.content.prefix.staffOnly"));
            } else {
                adultByPrefix = prefix.get().adultOnly() && b.allowAdult(); // 성인 전용 말머리 = 19금으로 저장(adult-content-contract)
            }
        }
        boolean adult = adultByPrefix || Boolean.TRUE.equals(adultFlag);
        if (adult && !b.allowAdult()) {
            fields.put("adult", new MessageRef("board.content.adult"));
        }
        if (!fields.containsKey("content") && !fields.containsKey("contentFormat")) {
            if (!body.isHtml()) {
                // 평문: 이미지 주소·마크다운 이미지를 예전처럼 게시판의 이미지 허용 칸으로 본다
                ContentRules.checkLinksImages(body.links(), body.images(), writer, b.imageSetting(), sec, "content", fields);
            } else if (body.images() > ContentRules.POST_IMAGE_MAX) {
                fields.put("content", new MessageRef("board.content.image.limit", ContentRules.POST_IMAGE_MAX));
            } else if (body.videos() > ContentRules.POST_YOUTUBE_MAX) {
                fields.put("content", new MessageRef("board.content.youtube.limit", ContentRules.POST_YOUTUBE_MAX));
            } else {
                // HTML: 게시판의 이미지 금지는 새 이미지만 422 로 막는다(고치기에서 이미 있던 이미지는 둔다) — 여기서는 비회원·새 회원 규칙만
                ContentRules.checkLinksImages(body.links(), body.images(), writer, true, sec, "content", fields);
            }
        }
        return new Cleaned(prefixId, title, body, adult, adultByPrefix, body.images() > 0);
    }

    /**
     * 본문 형식과 본문 — 형식이 빠지면 TEXT. 모르는 형식 {@code fields.contentFormat}, 평문 게시판(글 에디터 NONE)에 HTML {@code fields.contentFormat}.
     * HTML 은 게시판 에디터 수준(간단·고급)의 허용 목록으로 정화한다. 정화하기 전 길이가 상한의 두 배를 넘으면 정화하지 않고 거절한다.
     */
    private static ContentBody body(EffectiveBoard b, String formatText, String contentText, Map<String, MessageRef> fields) {
        ContentFormat format = ContentRules.format(formatText);
        if (format == null) {
            fields.put("contentFormat", new MessageRef("board.content.format.unknown"));
            return ContentBody.text(contentText);
        }
        if (format == ContentFormat.TEXT) {
            return ContentBody.text(contentText);
        }
        if (!b.postHtml()) {
            fields.put("contentFormat", new MessageRef("board.content.format.notAllowed"));
            return ContentBody.text(contentText);
        }
        if (contentText != null && contentText.length() > ContentRules.CONTENT_HTML_MAX * 2) {
            fields.put("content", new MessageRef("board.content.html.tooLong"));
            return ContentBody.text("");
        }
        return ContentBody.html(contentText, RichText.level(b.postEditor()));
    }

    /** 문자열 ID → long(공개 서버 {@code PostQueryService#parseId} 와 같은 규칙). 형식이 틀리면 null */
    private static Long parseId(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > 19) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
