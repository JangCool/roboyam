package com.community.post.service;

import java.util.Collection;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 목록 줄의 썸네일({@code thumbnailUrl}) — 목록 썸네일의 유일한 자리(게시판 목록·최신글·피드·가젯·팁이 부른다). 규칙은 shared {@link PostThumbnailChoices#listUrls}:
 * 대표 썸네일 선택이 있으면 그것(NONE 이면 없음), 없으면 글에 붙은 첫 이미지(먼저 올린 것 — 목록용 작은 그림, 없으면 원본), 그것도 없으면 본문의 첫 YouTube 영상 썸네일.
 * 그 밖의 영상 플랫폼({@code data-embed})은 썸네일이 없다. docs/features/list-thumbnail-contract.md.
 */
@Component
public class PostThumbnails {

    private final PostThumbnailChoices choices;

    public PostThumbnails(PostThumbnailChoices choices) {
        this.choices = choices;
    }

    /** 글 ID → 썸네일 주소(없는 글은 빠진다) */
    public Map<Long, String> of(Collection<Long> postIds) {
        return choices.listUrls(postIds);
    }
}
