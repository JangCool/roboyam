package com.community.title.service;

import com.community.title.dto.response.OwnedTitleView;
import com.community.title.dto.response.TitleBadgeView;
import com.community.title.entity.MemberTitleEntity;
import com.community.title.entity.MemberTitlePrefEntity;
import com.community.title.entity.TitleEntity;
import com.community.title.entity.TitleTier;
import com.community.title.repository.master.MemberTitlePrefRepository;
import com.community.title.repository.master.TitleQueries;
import com.community.title.repository.master.TitleRepository;
import com.community.user.entity.UserStatus;
import com.community.zconfiguration.time.ServiceTime;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 칭호 표시(member-title-contract 「화면」·「구현 결정」 7) — 작성자 줄의 대표 칭호, 공개 프로필·내 칭호의 가진 칭호 모음.
 *
 * <ul>
 *   <li><b>대표 칭호</b>: 「안 보이기」면 없음 · 고른 칭호가 살아 있으면 그것 · 아니면 가진 작가 칭호 중 가장 높은 것(명예 작가 > 올해의 작가 > 이달의 작가 — 이달의 작가는 가장 최근 달),
 *       없으면 없음. 제재 기간(회원 상태 SUSPENDED·BANNED)·탈퇴면 없음. 고른 칭호가 휴면 숨김 칭호이고 6개월 동안 글·댓글이 없으면 없음(다시 쓰면 돌아온다).</li>
 *   <li>꺼진 칭호는 어디에도 보이지 않는다(가진 줄은 남는다).</li>
 *   <li>묶어 읽기: 회원 묶음마다 받은 줄 한 질의, 받은 줄이 있는 회원만 대표 설정·회원 상태 각 한 질의, 휴면 숨김 칭호가 고른 회원이 있을 때만 마지막 활동 두 질의 — 줄마다 읽지 않는다.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class TitleDisplayService {

    private final TitleRepository titles;
    private final TitleQueries queries;
    private final MemberTitlePrefRepository prefs;
    private final Clock clock;

    public TitleDisplayService(TitleRepository titles, TitleQueries queries, MemberTitlePrefRepository prefs, ObjectProvider<Clock> clocks) {
        this.titles = titles;
        this.queries = queries;
        this.prefs = prefs;
        this.clock = clocks.getIfUnique(Clock::systemUTC);
    }

    /** 칭호 하나의 대표 칭호 꼴(기간·단계를 안다) */
    public static TitleBadgeView badge(TitleEntity title, String period, TitleTier tier) {
        return new TitleBadgeView(title.getTitleKey(), TitleLabels.label(title, period), TitleLabels.labelEn(title, period), title.getIcon(),
                tier == null ? null : tier.name(), title.getChip());
    }

    /** 켜진 칭호 — ID → 칭호 */
    public Map<Long, TitleEntity> enabledTitles() {
        return titles.findAllByOrderBySortOrderAscIdAsc().stream().filter(TitleEntity::isEnabled)
                .collect(Collectors.toMap(TitleEntity::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    /** 회원들의 대표 칭호(없는 회원은 빠진다) — 작성자 DTO {@code title} */
    public Map<Long, TitleBadgeView> representatives(Collection<Long> userIds) {
        List<Long> wanted = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) {
            return Map.of();
        }
        List<MemberTitleEntity> rows = queries.activeOf(wanted);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<Long, TitleEntity> enabled = enabledTitles();
        Map<Long, List<MemberTitleEntity>> byUser = new HashMap<>();
        for (MemberTitleEntity row : rows) {
            if (enabled.containsKey(row.getTitleId())) {
                byUser.computeIfAbsent(row.getUserId(), k -> new ArrayList<>()).add(row);
            }
        }
        if (byUser.isEmpty()) {
            return Map.of();
        }
        Map<Long, MemberTitlePrefEntity> pref = new HashMap<>();
        prefs.findAllById(byUser.keySet()).forEach(p -> pref.put(p.getUserId(), p));
        Map<Long, UserStatus> status = queries.statuses(byUser.keySet());
        Map<Long, Pick> picked = new HashMap<>();
        for (Map.Entry<Long, List<MemberTitleEntity>> e : byUser.entrySet()) {
            if (status.get(e.getKey()) != UserStatus.ACTIVE) {
                continue; // 제재 기간·탈퇴 진행 중 — 숨김(회수 아님)
            }
            Pick pick = pick(e.getValue(), pref.get(e.getKey()), enabled);
            if (pick != null) {
                picked.put(e.getKey(), pick);
            }
        }
        dropDormant(picked);
        Map<Long, TitleBadgeView> out = new HashMap<>();
        picked.forEach((userId, p) -> out.put(userId, badge(p.title(), p.row().getPeriod(), p.row().getTier())));
        return out;
    }

    /** 회원 하나의 대표 칭호(없으면 null) */
    public TitleBadgeView representative(long userId) {
        return representatives(List.of(userId)).get(userId);
    }

    /** 내 칭호의 대표 칭호 키 — 상태·휴면과 상관없이 고른(또는 기본으로 잡힌) 것. 「안 보이기」·없음이면 null */
    public String chosenKey(long userId) {
        Map<Long, TitleEntity> enabled = enabledTitles();
        List<MemberTitleEntity> rows = queries.activeOf(List.of(userId)).stream().filter(r -> enabled.containsKey(r.getTitleId())).toList();
        Pick pick = pick(rows, prefs.findById(userId).orElse(null), enabled);
        return pick == null ? null : pick.title().getTitleKey();
    }

    private record Pick(TitleEntity title, MemberTitleEntity row) {
    }

    private static Pick pick(List<MemberTitleEntity> rows, MemberTitlePrefEntity pref, Map<Long, TitleEntity> enabled) {
        if (rows.isEmpty() || (pref != null && pref.isHidden())) {
            return null;
        }
        if (pref != null && pref.getRepresentativeTitleId() != null) {
            MemberTitleEntity chosen = latest(rows, pref.getRepresentativeTitleId());
            if (chosen != null) {
                return new Pick(enabled.get(chosen.getTitleId()), chosen);
            }
        }
        for (String key : BuiltinTitles.WRITER_RANK) {
            TitleEntity writer = enabled.values().stream().filter(t -> key.equals(t.getTitleKey())).findFirst().orElse(null);
            MemberTitleEntity row = writer == null ? null : latest(rows, writer.getId());
            if (row != null) {
                return new Pick(writer, row);
            }
        }
        return null;
    }

    /** 그 칭호의 가장 최근 기간 줄(이달의 작가 — 가장 최근 달) */
    private static MemberTitleEntity latest(List<MemberTitleEntity> rows, long titleId) {
        return rows.stream().filter(r -> r.getTitleId() == titleId).max(Comparator.comparing(MemberTitleEntity::getPeriod).thenComparing(MemberTitleEntity::getGrantedAt))
                .orElse(null);
    }

    /** 휴면 숨김 칭호가 대표인 회원 중 6개월 동안 글·댓글이 없는 회원을 뺀다 */
    private void dropDormant(Map<Long, Pick> picked) {
        Set<Long> watch = picked.entrySet().stream().filter(e -> e.getValue().title().isDormantHide()).map(Map.Entry::getKey).collect(Collectors.toSet());
        if (watch.isEmpty()) {
            return;
        }
        Instant since = clock.instant().atZone(ServiceTime.ZONE).minusMonths(6).toInstant();
        Map<Long, Instant> last = queries.lastActivity(watch);
        for (Long userId : watch) {
            Instant at = last.get(userId);
            if (at == null || at.isBefore(since)) {
                picked.remove(userId);
            }
        }
    }

    /** 가진 칭호 모음(켜진 칭호만, 칭호 차례) — 같은 칭호는 한 줄로 묶는다 */
    public List<OwnedTitleView> owned(long userId) {
        Map<Long, TitleEntity> enabled = enabledTitles();
        Map<Long, List<MemberTitleEntity>> byTitle = new LinkedHashMap<>();
        enabled.keySet().forEach(id -> byTitle.put(id, new ArrayList<>()));
        for (MemberTitleEntity row : queries.activeOf(List.of(userId))) {
            List<MemberTitleEntity> list = byTitle.get(row.getTitleId());
            if (list != null) {
                list.add(row);
            }
        }
        List<OwnedTitleView> out = new ArrayList<>();
        byTitle.forEach((titleId, list) -> {
            if (!list.isEmpty()) {
                out.add(group(enabled.get(titleId), list));
            }
        });
        return out;
    }

    /** 한 칭호의 받은 줄들을 한 줄로 */
    public static OwnedTitleView group(TitleEntity title, List<MemberTitleEntity> rows) {
        TitleTier tier = null;
        List<String> periods = new ArrayList<>();
        for (MemberTitleEntity r : rows) {
            tier = TitleTier.max(tier, r.getTier());
            if (r.getPeriod() != null && !r.getPeriod().isEmpty()) {
                periods.add(r.getPeriod());
            }
        }
        periods.sort(Comparator.reverseOrder());
        return new OwnedTitleView(title.getTitleKey(), title.getName(), title.getNameEn(), title.getIcon(), tier == null ? null : tier.name(), title.getChip(), rows.size(),
                List.copyOf(periods));
    }

    /**
     * 공개 프로필의 칭호 모음 — 본인은 늘 전부. 남에게는: 제재 기간·탈퇴면 없음, 비공개 프로필이면 대표 칭호 하나만(횟수 1·기간 없음), 그 밖 전부.
     */
    public List<OwnedTitleView> profile(long userId, boolean self, boolean privateProfile) {
        if (self) {
            return owned(userId);
        }
        if (privateProfile) {
            TitleBadgeView badge = representative(userId);
            return badge == null ? List.of()
                    : List.of(new OwnedTitleView(badge.key(), badge.label(), badge.labelEn(), badge.icon(), badge.tier(), badge.chip(), 1, List.of()));
        }
        if (queries.statuses(List.of(userId)).get(userId) != UserStatus.ACTIVE) {
            return List.of();
        }
        return owned(userId);
    }
}
