package com.community.notice.dto.response;

import com.community.post.dto.response.AdminPostPrefixRefView;
import java.util.List;

/**
 * 공지 게시판(slug {@code notice}, 소모임 게시판 아님).
 *
 * @param pinnedCount 지금 고정 글 수(지우지 않은 고정 글 — 게시글 관리와 같은 셈)
 * @param prefixes    켜진 말머리
 * @param publicUrl   공개 사이트의 이 게시판 주소(사이트 공개 주소 + {@code /boards/{slug}}), 공개 주소가 없으면 null
 */
public record NoticeBoardView(String id, String slug, String name, String nameEn, boolean active, int pinnedMax, long pinnedCount,
                              List<AdminPostPrefixRefView> prefixes, String publicUrl) {
}
