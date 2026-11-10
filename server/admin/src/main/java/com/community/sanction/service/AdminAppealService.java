package com.community.sanction.service;

import com.community.permission.dto.AdminActor;
import com.community.sanction.dto.request.AppealAnswerRequest;
import com.community.sanction.dto.response.AppealCountsView;
import com.community.sanction.dto.response.AppealDetailView;
import com.community.sanction.dto.response.AppealItemView;
import com.community.sanction.dto.response.AppealListView;
import com.community.sanction.dto.response.AppealSanctionBriefView;
import com.community.sanction.dto.response.AppealSummaryView;
import com.community.sanction.entity.AppealStatus;
import com.community.sanction.exception.AppealNotFoundException;
import com.community.sanction.repository.master.AdminSanctionStore;
import com.community.sanction.repository.master.BoardSanctionStore;
import com.community.sanction.repository.master.BoardSanctionStore.BoardSanctionRow;
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 이의제기(admin-web {@code useAppealApi.ts}) — 목록({@code status} PENDING·ACCEPTED·REJECTED, 생략·ALL·모르는 값 = 전체 · {@code sort} oldest(기본)·newest ·
 * {@code page}·{@code size} 기본 20 최대 50) · 요약 · 상세 · 인용·기각(바뀐 상세를 돌려준다). 답변 규칙은 shared {@link MemberSanctionService#answer}
 * (게시판 제재 이의제기는 {@link BoardSanctionService#answerAppeal} 로 넘긴다).
 * 게시판 제재 이의제기(board-sanction-contract 결정 4)는 {@code kind = BOARD} · {@code sanction = null} · {@code boardSanction}(게시판 이름 · 처리자 닉네임 + 자리)이다.
 */
@Service
@Transactional(readOnly = true)
public class AdminAppealService {

    static final String KIND_SITE = "SITE";
    static final String KIND_BOARD = "BOARD";

    private final MemberSanctionService sanctions;
    private final BoardSanctionService boardSanctions;
    private final AdminSanctionStore store;
    private final BoardSanctionStore boardStore;
    private final SanctionViews views;
    private final BoardSanctionViews boardViews;

    public AdminAppealService(MemberSanctionService sanctions, BoardSanctionService boardSanctions, AdminSanctionStore store, BoardSanctionStore boardStore,
                              SanctionViews views, BoardSanctionViews boardViews) {
        this.sanctions = sanctions;
        this.boardSanctions = boardSanctions;
        this.store = store;
        this.boardStore = boardStore;
        this.views = views;
        this.boardViews = boardViews;
    }

    public AppealListView list(String statusRaw, String sort, String pageRaw, String sizeRaw) {
        AppealStatus status = statusOf(statusRaw);
        boolean newest = "newest".equalsIgnoreCase(AdminSanctionService.trim(sort));
        int page = Math.max(0, AdminSanctionService.number(pageRaw, 0));
        int size = Math.min(AdminSanctionService.SIZE_MAX, Math.max(1, AdminSanctionService.number(sizeRaw, AdminSanctionService.SIZE_DEFAULT)));
        long[] counts = store.appealCounts();
        List<AppealRow> rows = store.appealPage(status, newest, page, size);
        Map<Long, SanctionRow> bySanction = store.byIds(rows.stream().map(AppealRow::sanctionId).filter(Objects::nonNull).toList());
        Map<Long, BoardSanctionRow> byBoardSanction = boardStore.byIds(rows.stream().map(AppealRow::boardSanctionId).filter(Objects::nonNull).toList());
        Instant now = sanctions.now();
        List<AppealItemView> items = new ArrayList<>();
        for (AppealRow a : rows) {
            if (a.board()) {
                BoardSanctionRow b = byBoardSanction.get(a.boardSanctionId());
                if (b == null) {
                    continue; // 제재가 지워지면 이의제기도 함께 지워진다(연쇄) — 그 사이의 줄만
                }
                items.add(new AppealItemView(Long.toString(a.id()), a.no(), a.status().name(), BoardSanctionViews.user(b), KIND_BOARD, null, BoardSanctionViews.brief(b, now),
                        a.createdAt(), a.answeredAt()));
                continue;
            }
            SanctionRow s = bySanction.get(a.sanctionId());
            if (s == null) {
                continue;
            }
            items.add(new AppealItemView(Long.toString(a.id()), a.no(), a.status().name(), SanctionViews.user(s), KIND_SITE,
                    new AppealSanctionBriefView(Long.toString(s.id()), s.no(), s.kind().name(), s.effectiveStatus(now).name(), s.warningNo(), s.startsAt(), s.endsAt(),
                            SanctionViews.actor(s.issuedBy(), s.issuedByName())),
                    null, a.createdAt(), a.answeredAt()));
        }
        return new AppealListView(new AppealCountsView(counts[0], counts[1], counts[2]), store.appealTotal(status), page, size, items);
    }

    public AppealSummaryView summary() {
        return new AppealSummaryView(store.pendingAppeals());
    }

    public AppealDetailView detail(String idText) {
        Long id = AdminSanctionService.parseId(idText);
        AppealRow appeal = (id == null ? java.util.Optional.<AppealRow>empty() : sanctions.findAppeal(id)).orElseThrow(AppealNotFoundException::new);
        Instant now = sanctions.now();
        if (appeal.board()) {
            BoardSanctionRow board = boardSanctions.find(appeal.boardSanctionId()).orElseThrow(AppealNotFoundException::new);
            return new AppealDetailView(Long.toString(appeal.id()), appeal.no(), appeal.status().name(), appeal.reason(), appeal.createdAt(), appeal.answer(),
                    appeal.answeredAt(), SanctionViews.actor(appeal.answeredBy(), appeal.answeredByName()), BoardSanctionViews.user(board), KIND_BOARD, null,
                    board.sealed() ? List.of() : views.history(board.userId()), BoardSanctionViews.line(board, now),
                    board.sealed() ? List.of() : boardViews.history(board.userId())); // 봉인 제재(V184)는 회원 이력을 싣지 않는다(이력 줄로 신원이 이어지지 않게)
        }
        SanctionRow sanction = sanctions.find(appeal.sanctionId()).orElseThrow(AppealNotFoundException::new);
        return new AppealDetailView(Long.toString(appeal.id()), appeal.no(), appeal.status().name(), appeal.reason(), appeal.createdAt(), appeal.answer(),
                appeal.answeredAt(), SanctionViews.actor(appeal.answeredBy(), appeal.answeredByName()), SanctionViews.user(sanction), KIND_SITE,
                SanctionViews.line(sanction, now), sanction.sealed() ? List.of() : views.history(sanction.userId()), null, List.of());
    }

    @Transactional
    public AppealDetailView answer(AdminActor actor, String idText, boolean accept, AppealAnswerRequest body) {
        Long id = AdminSanctionService.parseId(idText);
        if (id == null) {
            throw new AppealNotFoundException();
        }
        sanctions.answer(id, accept, body == null ? null : body.answer(), actor.operatorId(), actor.ip());
        return detail(Long.toString(id));
    }

    private static AppealStatus statusOf(String raw) {
        String s = AdminSanctionService.trim(raw);
        if (s == null) {
            return null;
        }
        try {
            return AppealStatus.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null; // ALL · 모르는 값 = 전체
        }
    }
}
