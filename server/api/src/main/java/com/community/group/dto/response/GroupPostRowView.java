package com.community.group.dto.response;

import com.community.post.dto.response.PostGroupView;
import com.community.post.dto.response.PostRowView;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * 소모임 글 탭의 한 줄 — 게시판 글 줄({@code PostRow}) 칸을 펼친 것 + {@code group}({@code id}·{@code name}·{@code visibility} PRIVATE|PUBLIC·{@code dissolved}).
 */
public record GroupPostRowView(@JsonUnwrapped PostRowView post, PostGroupView group) {
}
