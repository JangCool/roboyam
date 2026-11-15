package com.community.post.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.post.dto.response.TagCountView;
import com.community.post.repository.master.PostTagQueries;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 태그 추천({@code GET /api/admin/tags/popular} — post-tags-contract 「관리자」): 글 목록 가젯 ⚙ 「태그 탭」 고르기 칸의 추천 — 최근 {@code days} 일 글에 많이 달린 태그와 달린 글 수.
 * 셈은 {@link PostTagQueries#popular}(지운·가린·비밀·비멤버에게 안 보이는 소모임 글 빼고, 이름은 가장 많이 쓴 모양).
 *
 * <ul>
 *   <li>{@code boards}: 게시판 slug 를 쉼표로 이은 값 — 비면 전체. 없는 slug 는 건너뛰고, 하나도 남지 않으면 빈 목록(엉뚱한 범위의 추천을 내지 않는다).
 *       꺼진 게시판도 센다(운영자 화면 — 가젯 대상 범위와 같은 slug 를 그대로 보낸다).</li>
 *   <li>{@code days} 1~{@value #DAYS_MAX}(기본 {@value #DAYS_DEFAULT}) · {@code limit} 1~{@value #LIMIT_MAX}(기본 {@value #LIMIT_DEFAULT}) — 범위 밖은 끝 값으로 자른다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class AdminPostTagService {

    static final int DAYS_DEFAULT = 30;
    static final int DAYS_MAX = 365;
    static final int LIMIT_DEFAULT = 30;
    static final int LIMIT_MAX = 100;

    private final BoardCatalog catalog;
    private final PostTagQueries tags;

    public AdminPostTagService(BoardCatalog catalog, PostTagQueries tags) {
        this.catalog = catalog;
        this.tags = tags;
    }

    public List<TagCountView> popular(String boards, Integer days, Integer limit) {
        Instant now = Instant.now();
        int d = Math.clamp(days == null ? DAYS_DEFAULT : days, 1, DAYS_MAX);
        int n = Math.clamp(limit == null ? LIMIT_DEFAULT : limit, 1, LIMIT_MAX);
        Set<Long> scope = null;
        if (boards != null && !boards.isBlank()) {
            BoardCatalogSnapshot s = catalog.snapshot();
            scope = new LinkedHashSet<>();
            for (String slug : boards.split(",")) {
                s.board(slug.strip()).map(EffectiveBoard::id).ifPresent(scope::add);
            }
            if (scope.isEmpty()) {
                return List.of();
            }
        }
        return tags.popular(scope, now.minus(Duration.ofDays(d)), n, now);
    }
}
