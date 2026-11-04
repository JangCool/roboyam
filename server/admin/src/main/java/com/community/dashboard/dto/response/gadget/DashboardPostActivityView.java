package com.community.dashboard.dto.response.gadget;

import java.time.LocalDate;
import java.util.List;

/** {@code POST_ACTIVITY} — 오늘·어제 글·댓글 + 7일 일별(오래된 차례, 오늘 포함 — 게시판 활동 일별 집계, 없는 날은 0). 글 기준은 R__batch_38 머리말 */
public record DashboardPostActivityView(long postsToday, long postsYesterday, long commentsToday, long commentsYesterday, List<Day> days)
        implements DashboardGadgetData {

    public record Day(LocalDate date, long posts, long comments) {
    }
}
