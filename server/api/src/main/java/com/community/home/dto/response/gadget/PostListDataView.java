package com.community.home.dto.response.gadget;

import com.community.post.dto.response.PostFeedRowView;
import java.util.List;
import java.util.Map;

/**
 * {@code POST_LIST} 가젯의 data — user-web {@code PostListData}. 게시판·정렬·개수는 서버가 가젯 설정으로 정해 준다.
 *
 * @param periods 인기글 기간 탭(설정 {@code periodTabs})일 때만 REALTIME·YESTERDAY·WEEK 의 순위 — 그 밖에는 null
 * @param tagged  태그 탭(설정 {@code tagTabs})이 있을 때만 태그(설정의 글자, 탭 차례) → 그 태그가 달린 글 줄 — 탭마다 {@code items} 와 같은 정렬·{@code count}·대상 범위·뺄 게시판,
 *                첫 글(모바일 쪽 넘기기면 쪽마다 첫 글) 요약 규칙도 같다(post-tags-contract). 인기 정렬은 실시간 순위에서 고르고, 기간 탭({@code periods})과는 함께 쓰지 않아 null.
 *                탭이 없으면 null
 */
public record PostListDataView(List<PostFeedRowView> items, Map<String, List<PostFeedRowView>> periods, Map<String, List<PostFeedRowView>> tagged) {

    /** 태그 탭 없이 */
    public PostListDataView(List<PostFeedRowView> items, Map<String, List<PostFeedRowView>> periods) {
        this(items, periods, null);
    }
}
