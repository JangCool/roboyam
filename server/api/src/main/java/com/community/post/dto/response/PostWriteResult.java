package com.community.post.dto.response;

import com.community.hotdeal.dto.response.DealWarningView;
import java.util.List;

/**
 * {@code POST /api/posts}(201) — 새 글 ID.
 *
 * @param warnings 저장은 됐지만 알릴 것(핫딜 — 없으면 빈 목록)
 */
public record PostWriteResult(String id, List<DealWarningView> warnings) {

    public PostWriteResult(String id) {
        this(id, List.of());
    }
}