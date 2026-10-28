package com.community.home.dto.response.gadget;

import com.community.post.dto.response.PostFeedRowView;
import java.util.List;
import java.util.Map;

/**
 * {@code POST_LIST} 가젯의 data — user-web {@code PostListData}. 게시판·정렬·개수는 서버가 가젯 설정으로 정해 준다.
 *
 * @param periods 인기글 기간 탭(설정 {@code periodTabs})일 때만 REALTIME·YESTERDAY·WEEK 의 순위 — 그 밖에는 null
 */
public record PostListDataView(List<PostFeedRowView> items, Map<String, List<PostFeedRowView>> periods) {
}
