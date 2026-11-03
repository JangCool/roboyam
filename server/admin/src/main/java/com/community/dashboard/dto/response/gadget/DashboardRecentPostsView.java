package com.community.dashboard.dto.response.gadget;

import java.time.Instant;
import java.util.List;

/**
 * {@code RECENT_POSTS} — 최근 글(최근 차례, 최대 limit). {@code reported} = 열린 신고 사건(대기·검토 중)이 있다. 게시판 이름은 {@code boardName}·{@code boardNameEn} 둘 다 준다.
 */
public record DashboardRecentPostsView(List<Item> items) implements DashboardGadgetData {

    public record Item(String id, String boardName, String boardNameEn, String title, String authorNickname, Instant createdAt, boolean hidden, boolean reported) {
    }
}
