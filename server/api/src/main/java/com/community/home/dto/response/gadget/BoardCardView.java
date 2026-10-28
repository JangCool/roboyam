package com.community.home.dto.response.gadget;

import com.community.post.dto.response.PostFeedRowView;
import java.util.List;

/**
 * {@code BOARD_CARDS} 가젯의 카드 한 장(게시판 하나) — user-web {@code BoardCard}. 글이 없는 게시판도 카드는 온다.
 *
 * @param lead 대표 글(사진 있는 가장 새 글, 없으면 가장 새 글) — 글이 없으면 null
 */
public record BoardCardView(Board board, PostFeedRowView lead, List<PostFeedRowView> items) {

    /** @param color 운영자가 고른 표시색 또는 null(자동) */
    public record Board(String slug, String name, String nameEn, String color, boolean investmentNotice) {
    }
}
