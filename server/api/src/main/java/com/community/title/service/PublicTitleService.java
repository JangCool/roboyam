package com.community.title.service;

import com.community.pointshop.dto.AuthorLook;
import com.community.pointshop.service.ShopDecorationResolver;
import com.community.title.dto.response.HallOfFameView;
import com.community.title.dto.response.TitleCatalogItemView;
import com.community.title.entity.MemberTitleEntity;
import com.community.title.entity.TitleEntity;
import com.community.title.entity.TitleTier;
import com.community.title.repository.master.TitleQueries;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.ProfileRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 누구나 보는 칭호 도감·명예의 전당(member-title-contract 「화면」·「구현 결정」 7) — {@code GET /api/titles}·{@code GET /api/hall-of-fame}.
 * 보유자 수는 칭호별 한 질의 + 단계별 한 질의, 분모 한 질의 — 칭호마다 읽지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class PublicTitleService {

    private final TitleDisplayService display;
    private final TitleQueries queries;
    private final TitleTexts texts;
    private final ProfileRepository profiles;
    private final ShopDecorationResolver decorations;

    public PublicTitleService(TitleDisplayService display, TitleQueries queries, TitleTexts texts, ProfileRepository profiles, ShopDecorationResolver decorations) {
        this.display = display;
        this.queries = queries;
        this.texts = texts;
        this.profiles = profiles;
        this.decorations = decorations;
    }

    /** 보유 비율(0~1, 소수 넷째 자리) */
    public static double ratio(long holders, long base) {
        if (base <= 0 || holders <= 0) {
            return 0;
        }
        return Math.min(1.0, Math.round(holders * 10000.0 / base) / 10000.0);
    }

    public List<TitleCatalogItemView> catalog() {
        Map<Long, Long> holders = queries.holders();
        Map<Long, Map<TitleTier, Long>> tierHolders = queries.tierHolders();
        long base = queries.memberBase();
        List<TitleCatalogItemView> out = new ArrayList<>();
        for (TitleEntity t : display.enabledTitles().values()) {
            long n = holders.getOrDefault(t.getId(), 0L);
            List<TitleCatalogItemView.TierView> tiers = new ArrayList<>();
            if (t.isTiered()) {
                Map<TitleTier, Long> byTier = tierHolders.getOrDefault(t.getId(), Map.of());
                for (TitleTier tier : TitleTier.values()) {
                    tiers.add(new TitleCatalogItemView.TierView(tier.name(), t.thresholdOf(tier), byTier.getOrDefault(tier, 0L)));
                }
            }
            out.add(new TitleCatalogItemView(t.getTitleKey(), t.getName(), t.getNameEn(), t.getIcon(), t.getChip(), t.getMethod().name(), t.isTiered(), texts.howTo(t), n,
                    ratio(n, base), List.copyOf(tiers)));
        }
        return out;
    }

    public HallOfFameView hallOfFame() {
        Map<String, TitleEntity> byKey = new HashMap<>();
        display.enabledTitles().values().forEach(t -> byKey.put(t.getTitleKey(), t));
        List<Long> ids = new ArrayList<>();
        for (String key : List.of(BuiltinTitles.HONORARY_WRITER, BuiltinTitles.YEARLY_WRITER, BuiltinTitles.MONTHLY_WRITER)) {
            Optional.ofNullable(byKey.get(key)).ifPresent(t -> ids.add(t.getId()));
        }
        List<MemberTitleEntity> rows = queries.hallOf(ids);
        List<Long> userIds = rows.stream().map(MemberTitleEntity::getUserId).distinct().toList();
        Map<Long, ProfileEntity> profile = new HashMap<>();
        profiles.findAllById(userIds).forEach(p -> profile.put(p.getUserId(), p));
        Map<Long, AuthorLook> looks = userIds.isEmpty() ? Map.of() : decorations.resolve(userIds);

        List<HallOfFameView.MemberView> honorary = new ArrayList<>();
        Map<String, List<HallOfFameView.MemberView>> yearly = new LinkedHashMap<>();
        Map<String, List<HallOfFameView.MemberView>> monthly = new LinkedHashMap<>();
        Long honoraryId = idOf(byKey, BuiltinTitles.HONORARY_WRITER);
        Long yearlyId = idOf(byKey, BuiltinTitles.YEARLY_WRITER);
        for (MemberTitleEntity r : rows) {
            ProfileEntity p = profile.get(r.getUserId());
            if (p == null) {
                continue;
            }
            AuthorLook look = looks.get(r.getUserId());
            HallOfFameView.MemberView m = new HallOfFameView.MemberView(Long.toString(r.getUserId()), p.getNickname(), look == null ? null : look.avatarUrl());
            if (r.getTitleId().equals(honoraryId)) {
                honorary.add(m);
            } else if (r.getTitleId().equals(yearlyId)) {
                yearly.computeIfAbsent(r.getPeriod(), k -> new ArrayList<>()).add(m);
            } else {
                monthly.computeIfAbsent(r.getPeriod(), k -> new ArrayList<>()).add(m);
            }
        }
        return new HallOfFameView(honorary, yearly.entrySet().stream().map(e -> new HallOfFameView.YearView(e.getKey(), e.getValue())).toList(),
                monthly.entrySet().stream().map(e -> new HallOfFameView.MonthView(e.getKey(), e.getValue())).toList());
    }

    private static Long idOf(Map<String, TitleEntity> byKey, String key) {
        TitleEntity t = byKey.get(key);
        return t == null ? null : t.getId();
    }
}
