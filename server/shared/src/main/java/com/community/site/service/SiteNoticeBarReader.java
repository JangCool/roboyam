package com.community.site.service;

import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.board.service.BoardFields;
import com.community.post.repository.master.PostRepositoryQDSL;
import com.community.site.dto.SiteNoticeLine;
import com.community.site.entity.SiteSettingEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 공지 띠에 보일 줄을 만든다 — 공개 띠({@code GET /api/site/notice-bar}, api)와 관리자 미리보기({@code GET·PUT /api/admin/site/notice-bar} 의 {@code preview}, admin)가 같은 판정을 쓴다.
 * 켜짐 여부는 보지 않는다(부르는 쪽이 정한다 — 관리자 미리보기는 꺼져 있어도 출처 기준으로 채운다).
 *
 * <ul>
 *   <li>{@code BOARD}: 게시판 사본({@link BoardCatalog}, 15초)의 slug {@value SiteNoticeBars#NOTICE_BOARD_SLUG} 게시판이 있고 켜져 있고 소모임 게시판이 아니면(= 손님이 읽는 공개 게시판 —
 *       공개 게시판은 읽기 제한이 없다) 손님에게 보이는 최근 글 {@code boardCount} 개({@link PostRepositoryQDSL#guestLatest}), 링크 {@code /posts/{id}}. 아니면 빈 목록.</li>
 *   <li>{@code CUSTOM}: 문구가 있으면 한 줄(링크는 저장값 그대로), 없으면 빈 목록.</li>
 * </ul>
 */
@Component
public class SiteNoticeBarReader {

    private final BoardCatalog catalog;
    private final PostRepositoryQDSL posts;

    public SiteNoticeBarReader(BoardCatalog catalog, PostRepositoryQDSL posts) {
        this.catalog = catalog;
        this.posts = posts;
    }

    /** 공지 게시판 — 없거나 꺼졌거나 소모임 게시판이면 empty */
    public Optional<EffectiveBoard> noticeBoard() {
        return catalog.snapshot().board(SiteNoticeBars.NOTICE_BOARD_SLUG).filter(b -> b.active() && !BoardFields.KIND_GROUP.equals(b.kind()));
    }

    /** 지금 저장값의 출처 기준으로 보일 줄(켜짐 여부와 상관없이) */
    public List<SiteNoticeLine> lines(SiteSettingEntity row) {
        if (SiteNoticeBars.SOURCE_CUSTOM.equals(row.getNoticeBarSource())) {
            String text = row.getNoticeBarCustomText();
            return text == null ? List.of() : List.of(new SiteNoticeLine(null, text, row.getNoticeBarCustomLink(), null));
        }
        int count = SiteNoticeBars.isBoardCount(row.getNoticeBarBoardCount()) ? row.getNoticeBarBoardCount() : SiteNoticeBars.DEFAULT_BOARD_COUNT;
        return noticeBoard()
                .map(board -> posts.guestLatest(board.id(), count, Instant.now()).stream()
                        .map(p -> new SiteNoticeLine(p.id(), p.title(), "/posts/" + p.id(), p.createdAt()))
                        .toList())
                .orElse(List.of());
    }

    /** 줄 묶음의 표식 — 출처별({@link SiteNoticeBars#boardVersion}·{@link SiteNoticeBars#customVersion}) */
    public static String version(SiteSettingEntity row, List<SiteNoticeLine> lines) {
        if (SiteNoticeBars.SOURCE_CUSTOM.equals(row.getNoticeBarSource())) {
            return lines.isEmpty() ? SiteNoticeBars.customVersion(null, null) : SiteNoticeBars.customVersion(lines.getFirst().title(), lines.getFirst().link());
        }
        return SiteNoticeBars.boardVersion(lines.stream().map(SiteNoticeLine::postId).toList());
    }
}
