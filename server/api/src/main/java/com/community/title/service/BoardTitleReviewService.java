package com.community.title.service;

import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.pointshop.dto.AuthorLook;
import com.community.pointshop.service.ShopDecorationResolver;
import com.community.title.dto.request.TitleReviewRequest;
import com.community.title.dto.response.BoardTitleApplicationPageView;
import com.community.title.dto.response.BoardTitleApplicationView;
import com.community.title.entity.TitleApplicationEntity;
import com.community.title.entity.TitleApplicationStatus;
import com.community.title.entity.TitleEntity;
import com.community.title.exception.TitleException;
import com.community.title.repository.master.TitleApplicationRepository;
import com.community.title.service.TitleReviewService.Reviewer;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.ProfileRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 매니저의 칭호 신청 심사(member-title-contract 「구현 결정」 5 — 회원 앱 게시판 스텝 화면 「칭호 신청」 탭). 그 게시판의 매니저(스텝 아님)만, 그 게시판에 연결돼 「운영자 + 연결 게시판
 * 매니저」가 심사하는 칭호의 신청만 본다·처리한다. 자기 신청은 처리하지 못한다. 없는 게시판 404 {@code TITLE_NOT_FOUND} 가 아니라 403 {@code TITLE_REVIEW_FORBIDDEN}(게시판 존재를 가리지 않아도
 * 되지만 매니저가 아닌 사람에게 같은 답을 준다).
 */
@Service
public class BoardTitleReviewService {

    private final BoardCatalog catalog;
    private final TitleReviewService reviews;
    private final TitleApplicationRepository applications;
    private final TitleEligibility eligibility;
    private final MyTitleService myTitles;
    private final ProfileRepository profiles;
    private final ShopDecorationResolver decorations;

    public BoardTitleReviewService(BoardCatalog catalog, TitleReviewService reviews, TitleApplicationRepository applications, TitleEligibility eligibility,
                                   MyTitleService myTitles, ProfileRepository profiles, ShopDecorationResolver decorations) {
        this.catalog = catalog;
        this.reviews = reviews;
        this.applications = applications;
        this.eligibility = eligibility;
        this.myTitles = myTitles;
        this.profiles = profiles;
        this.decorations = decorations;
    }

    /** 그 게시판의 매니저면 게시판 ID, 아니면 403 */
    private long managedBoard(String slug, long memberId) {
        EffectiveBoard board = catalog.snapshot().board(slug).orElseThrow(TitleException::reviewForbidden);
        if (!reviews.isManager(memberId, board.id())) {
            throw TitleException.reviewForbidden();
        }
        return board.id();
    }

    /** 신청 목록 — {@code status} PENDING(기본)·APPROVED·REJECTED·ALL */
    @Transactional(readOnly = true)
    public BoardTitleApplicationPageView list(String slug, long memberId, String statusRaw) {
        long boardId = managedBoard(slug, memberId);
        Map<Long, TitleEntity> managed = new HashMap<>();
        reviews.managedTitles(boardId).forEach(t -> managed.put(t.getId(), t));
        TitleApplicationStatus status = "ALL".equalsIgnoreCase(statusRaw) ? null : TitleApplicationStatus.parse(statusRaw).orElse(TitleApplicationStatus.PENDING);
        List<TitleApplicationEntity> rows = reviews.list(status, List.copyOf(managed.keySet()));
        List<Long> userIds = rows.stream().map(TitleApplicationEntity::getUserId).distinct().toList();
        Map<Long, ProfileEntity> profile = new HashMap<>();
        profiles.findAllById(userIds).forEach(p -> profile.put(p.getUserId(), p));
        Map<Long, AuthorLook> looks = userIds.isEmpty() ? Map.of() : decorations.resolve(userIds);
        return new BoardTitleApplicationPageView(rows.stream().map(a -> {
            TitleEntity t = managed.get(a.getTitleId());
            ProfileEntity p = profile.get(a.getUserId());
            AuthorLook look = looks.get(a.getUserId());
            return new BoardTitleApplicationView(Long.toString(a.getId()), TitleDisplayService.badge(t, "", null),
                    new BoardTitleApplicationView.Applicant(Long.toString(a.getUserId()), p == null ? "" : p.getNickname(), look == null ? null : look.avatarUrl()),
                    a.getStatus().name(), a.isPending() ? myTitles.conditions(t, eligibility.check(t, a.getUserId())) : List.of(), a.getReason(), a.getReapplyAt(),
                    a.getCreatedAt(), a.getReviewedAt(), a.isPending() && a.getUserId() != memberId);
        }).toList());
    }

    @Transactional
    public void approve(String slug, long memberId, String idText, TitleReviewRequest body, String ip) {
        managedBoard(slug, memberId);
        reviews.approve(applicationId(idText), Reviewer.manager(memberId, ip), body == null ? null : body.reason());
    }

    @Transactional
    public void reject(String slug, long memberId, String idText, TitleReviewRequest body, String ip) {
        managedBoard(slug, memberId);
        reviews.reject(applicationId(idText), Reviewer.manager(memberId, ip), body == null ? null : body.reason());
    }

    private long applicationId(String idText) {
        if (idText == null || !idText.matches("[0-9]{1,19}")) {
            throw TitleException.applicationNotFound();
        }
        long id = Long.parseLong(idText);
        if (applications.findById(id).isEmpty()) {
            throw TitleException.applicationNotFound();
        }
        return id;
    }
}
