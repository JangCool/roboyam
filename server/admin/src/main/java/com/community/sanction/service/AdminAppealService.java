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
import com.community.sanction.repository.master.MemberSanctionStore.AppealRow;
import com.community.sanction.repository.master.MemberSanctionStore.SanctionRow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 이의제기(admin-web {@code useAppealApi.ts}) — 목록({@code status} PENDING·ACCEPTED·REJECTED, 생략·ALL·모르는 값 = 전체 · {@code sort} oldest(기본)·newest ·
 * {@code page}·{@code size} 기본 20 최대 50) · 요약 · 상세 · 인용·기각(바뀐 상세를 돌려준다). 답변 규칙은 shared {@link MemberSanctionService#answer}.
 */
@Service
@Transactional(readOnly = true)
public class AdminAppealService {

    private final MemberSanctionService sanctions;
    private final AdminSanctionStore store;
    private final SanctionViews views;

    public AdminAppealService(MemberSanctionService sanctions, AdminSanctionStore store, SanctionViews views) {
        this.sanctions = sanctions;
        this.store = store;
        this.views = views;
    }

    public AppealListView list(String statusRaw, String sort, String pageRaw, String sizeRaw) {
        AppealStatus status = statusOf(statusRaw);
        boolean newest = "newest".equalsIgnoreCase(AdminSanctionService.trim(sort));
        int page = Math.max(0, AdminSanctionService.number(pageRaw, 0));
        int size = Math.min(AdminSanctionService.SIZE_MAX, Math.max(1, AdminSanctionService.number(sizeRaw, AdminSanctionService.SIZE_DEFAULT)));
        long[] counts = store.appealCounts();
        List<AppealRow> rows = store.appealPage(status, newest, page, size);
        Map<Long, SanctionRow> bySanction = store.byIds(rows.stream().map(AppealRow::sanctionId).toList());
        Instant now = sanctions.now();
        List<AppealItemView> items = new ArrayList<>();
        for (AppealRow a : rows) {
            SanctionRow s = bySanction.get(a.sanctionId());
            if (s == null) {
                continue; // 제재가 지워지면 이의제기도 함께 지워진다(연쇄) — 그 사이의 줄만
            }
            items.add(new AppealItemView(Long.toString(a.id()), a.no(), a.status().name(), SanctionViews.user(s),
                    new AppealSanctionBriefView(Long.toString(s.id()), s.no(), s.kind().name(), s.effectiveStatus(now).name(), s.warningNo(), s.startsAt(), s.endsAt()),
                    a.createdAt(), a.answeredAt()));
        }
        return new AppealListView(new AppealCountsView(counts[0], counts[1], counts[2]), store.appealTotal(status), page, size, items);
    }

    public AppealSummaryView summary() {
        return new AppealSummaryView(store.pendingAppeals());
    }

    public AppealDetailView detail(String idText) {
        Long id = AdminSanctionService.parseId(idText);
        AppealRow appeal = (id == null ? java.util.Optional.<AppealRow>empty() : sanctions.findAppeal(id)).orElseThrow(AppealNotFoundException::new);
        SanctionRow sanction = sanctions.find(appeal.sanctionId()).orElseThrow(AppealNotFoundException::new);
        Instant now = sanctions.now();
        return new AppealDetailView(Long.toString(appeal.id()), appeal.no(), appeal.status().name(), appeal.reason(), appeal.createdAt(), appeal.answer(),
                appeal.answeredAt(), SanctionViews.actor(appeal.answeredBy(), appeal.answeredByName()), SanctionViews.user(sanction), SanctionViews.line(sanction, now),
                views.history(sanction.userId()));
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
