package com.community.home.dto.response.gadget;

import com.community.post.dto.response.PostFeedRowView;
import java.util.List;

/**
 * {@code HOT_DEALS} 가젯 data(user-web {@code HotDealsData}, 메모 1-6) — 진행 중(ACTIVE — 의심·종료·기간 종료 제외)·공개 글을 설정 차례로. 줄은 게시판이 섞인 글 줄 + {@code deal}.
 *
 * @param more 「핫딜 더 보기」가 갈 곳({@code /boards/{slug}}) — 갈 핫딜 게시판이 없으면 null
 */
public record HotDealsDataView(List<PostFeedRowView> items, More more) {

    /** 「더 보기」 */
    public record More(String to) {
    }
}
