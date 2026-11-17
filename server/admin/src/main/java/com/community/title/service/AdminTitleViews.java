package com.community.title.service;

import com.community.board.service.BoardCatalog;
import com.community.operator.entity.OperatorEntity;
import com.community.operator.repository.master.OperatorRepository;
import com.community.title.dto.TitleCondition;
import com.community.title.dto.response.AdminTitleApplicationView;
import com.community.title.dto.response.AdminTitleGrantView;
import com.community.title.entity.MemberTitleEntity;
import com.community.title.entity.TitleApplicationEntity;
import com.community.title.entity.TitleEntity;
import com.community.title.repository.master.TitleRepository;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.ProfileRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 관리자 칭호 응답 조립 — 받은 줄·신청 줄에 칭호 표시·회원 닉네임·운영자 이름을 붙인다. 묶음마다 칭호 한 번·프로필 한 번·운영자 한 번(줄마다 읽지 않는다).
 */
@Component
public class AdminTitleViews {

    private final TitleRepository titles;
    private final ProfileRepository profiles;
    private final OperatorRepository operators;
    private final TitleEligibility eligibility;
    private final TitleTexts texts;
    private final BoardCatalog catalog;

    public AdminTitleViews(TitleRepository titles, ProfileRepository profiles, OperatorRepository operators, TitleEligibility eligibility, TitleTexts texts,
                           BoardCatalog catalog) {
        this.titles = titles;
        this.profiles = profiles;
        this.operators = operators;
        this.eligibility = eligibility;
        this.texts = texts;
        this.catalog = catalog;
    }

    /** 이름 사전 — 칭호·회원·운영자 */
    private record Lookup(Map<Long, TitleEntity> titles, Map<Long, String> nicknames, Map<Long, String> operatorNames) {

        AdminTitleGrantView.MemberView member(Long id) {
            return id == null ? null : new AdminTitleGrantView.MemberView(Long.toString(id), nicknames.get(id), Long.toString(id));
        }

        String operator(Long id) {
            return id == null ? null : operatorNames.get(id);
        }
    }

    private Lookup lookup(Collection<Long> userIds, Collection<Long> operatorIds) {
        Map<Long, TitleEntity> t = new HashMap<>();
        titles.findAll().forEach(x -> t.put(x.getId(), x));
        Map<Long, String> nick = new HashMap<>();
        List<Long> users = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!users.isEmpty()) {
            profiles.findAllById(users).forEach((ProfileEntity p) -> nick.put(p.getUserId(), p.getNickname()));
        }
        Map<Long, String> ops = new HashMap<>();
        List<Long> opIds = operatorIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!opIds.isEmpty()) {
            operators.findAllById(opIds).forEach((OperatorEntity o) -> ops.put(o.getId(), o.getOperatorName()));
        }
        return new Lookup(t, nick, ops);
    }

    /** 회원 하나 — 닉네임 한 번 읽기 */
    public AdminTitleGrantView.MemberView member(long userId) {
        return new AdminTitleGrantView.MemberView(Long.toString(userId), profiles.findById(userId).map(ProfileEntity::getNickname).orElse(null), Long.toString(userId));
    }

    /** 회원들 — 닉네임 한 질의 */
    public Map<Long, AdminTitleGrantView.MemberView> members(Collection<Long> userIds) {
        Lookup l = lookup(userIds, List.of());
        Map<Long, AdminTitleGrantView.MemberView> out = new HashMap<>();
        userIds.stream().filter(Objects::nonNull).forEach(id -> out.put(id, l.member(id)));
        return out;
    }

    public List<AdminTitleGrantView> grants(List<MemberTitleEntity> rows) {
        Set<Long> users = new HashSet<>();
        Set<Long> ops = new HashSet<>();
        for (MemberTitleEntity r : rows) {
            users.add(r.getUserId());
            users.add(r.getGrantedByMember());
            ops.add(r.getGrantedBy());
            ops.add(r.getRevokedBy());
        }
        Lookup l = lookup(users, ops);
        return rows.stream().map(r -> grant(r, l, null)).toList();
    }

    public AdminTitleGrantView grant(MemberTitleEntity row, MemberTitleEntity honorary) {
        Set<Long> users = new HashSet<>(Arrays.asList(row.getUserId(), row.getGrantedByMember()));
        Set<Long> ops = new HashSet<>(Arrays.asList(row.getGrantedBy(), row.getRevokedBy()));
        Lookup l = lookup(users, ops);
        return grant(row, l, honorary == null ? null : grant(honorary, l, null));
    }

    private AdminTitleGrantView grant(MemberTitleEntity r, Lookup l, AdminTitleGrantView honorary) {
        TitleEntity t = l.titles().get(r.getTitleId());
        String by = r.getGrantedBy() != null ? l.operator(r.getGrantedBy()) : r.getGrantedByMember() != null ? l.nicknames().get(r.getGrantedByMember()) : null;
        String byKind = r.getGrantedBy() != null ? "OPERATOR" : r.getGrantedByMember() != null ? "BOARD_MANAGER" : null;
        return new AdminTitleGrantView(Long.toString(r.getId()), t == null ? null : TitleDisplayService.badge(t, r.getPeriod(), r.getTier()), l.member(r.getUserId()),
                r.getPeriod() == null || r.getPeriod().isEmpty() ? null : r.getPeriod(), r.getSource().name(), r.getGrantedAt(), by, byKind, r.getGrantReason(), r.getRevokedAt(),
                l.operator(r.getRevokedBy()), texts.revokeReason(r.getRevokedBy(), r.getRevokedReason()), r.isActive(), honorary); // 시스템 회수는 번들 문구(점검 L2)
    }

    public List<AdminTitleApplicationView> applications(List<TitleApplicationEntity> rows) {
        Set<Long> users = new HashSet<>();
        Set<Long> ops = new HashSet<>();
        for (TitleApplicationEntity a : rows) {
            users.add(a.getUserId());
            users.add(a.getReviewedByMember());
            ops.add(a.getReviewedBy());
        }
        Lookup l = lookup(users, ops);
        List<AdminTitleApplicationView> out = new ArrayList<>();
        for (TitleApplicationEntity a : rows) {
            TitleEntity t = l.titles().get(a.getTitleId());
            if (t == null) {
                continue;
            }
            List<AdminTitleApplicationView.ConditionView> conditions = new ArrayList<>();
            if (a.isPending()) {
                List<TitleCondition> defs = TitleConditions.read(t.getConditions());
                List<TitleEligibility.Check> checks = eligibility.check(t, a.getUserId());
                for (int i = 0; i < checks.size() && i < defs.size(); i++) {
                    conditions.add(new AdminTitleApplicationView.ConditionView(checks.get(i).type().name(), texts.condition(defs.get(i)), checks.get(i).met()));
                }
            }
            Long board = eligibility.linkedBoard(t);
            String boardName = board == null ? null : catalog.snapshot().board(board).map(b -> b.name()).orElse(null);
            String by = a.getReviewedBy() != null ? l.operator(a.getReviewedBy()) : a.getReviewedByMember() != null ? l.nicknames().get(a.getReviewedByMember()) : null;
            String byKind = a.getReviewedBy() != null ? "OPERATOR" : a.getReviewedByMember() != null ? "BOARD_MANAGER" : null;
            out.add(new AdminTitleApplicationView(Long.toString(a.getId()), TitleDisplayService.badge(t, "", null), l.member(a.getUserId()), a.getStatus().name(), conditions,
                    boardName, a.getReason(), a.getReapplyAt(), a.getCreatedAt(), a.getReviewedAt(), by, byKind));
        }
        return out;
    }
}
