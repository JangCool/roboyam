package com.community.title.service;

import com.community.board.dto.BoardCatalogSnapshot;
import com.community.board.dto.EffectiveBoard;
import com.community.board.service.BoardCatalog;
import com.community.title.dto.TitleCondition;
import com.community.title.entity.TitleConditionType;
import com.community.title.entity.TitleEntity;
import com.community.title.entity.TitleMetric;
import com.community.title.repository.master.MemberTitleRepository;
import com.community.title.repository.master.TitleQueries;
import com.community.title.repository.master.TitleRepository;
import com.community.user.entity.ProfileEntity;
import com.community.user.repository.master.ProfileRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 칭호 조건 판정(member-title-contract 「기본 칭호」·「구현 결정」 1) — 조건마다 지금 수치와 채움 여부, 단계 칭호가 세는 수. 신청 검사·내 칭호(진행 막대·신청 조건)·자동 판정 배치가 같이 쓴다.
 * 게시판은 조건의 {@code boardId} → {@code boardSlug} 차례로 찾는다(slug 인데 게시판이 없으면 채울 수 없다).
 */
@Component
public class TitleEligibility {

    /** 조건 하나의 판정 — {@code current} 는 센 수(등급 조건은 회원 등급의 서열, 신고 숨김은 숨겨진 글이 있으면 1) */
    public record Check(TitleConditionType type, Integer value, Long boardId, String gradeKey, long current, boolean met) {
    }

    private final TitleQueries queries;
    private final ProfileRepository profiles;
    private final BoardCatalog catalog;
    private final TitleRepository titles;
    private final MemberTitleRepository memberTitles;

    public TitleEligibility(TitleQueries queries, ProfileRepository profiles, BoardCatalog catalog, TitleRepository titles, MemberTitleRepository memberTitles) {
        this.queries = queries;
        this.profiles = profiles;
        this.catalog = catalog;
        this.titles = titles;
        this.memberTitles = memberTitles;
    }

    /** 조건마다 판정(조건 차례 그대로) */
    public List<Check> check(TitleEntity title, long userId) {
        BoardCatalogSnapshot s = catalog.snapshot();
        List<Check> out = new ArrayList<>();
        for (TitleCondition c : TitleConditions.read(title.getConditions())) {
            out.add(check(c, userId, s));
        }
        return out;
    }

    /** 조건을 모두 채웠나 */
    public boolean allMet(TitleEntity title, long userId) {
        return check(title, userId).stream().allMatch(Check::met);
    }

    private Check check(TitleCondition c, long userId, BoardCatalogSnapshot s) {
        return switch (c.type()) {
            case GRADE_AT_LEAST -> {
                ProfileEntity profile = profiles.findById(userId).orElse(null);
                Integer mine = profile == null ? null : s.gradeLevel(profile.getGradeKey());
                Integer need = s.gradeLevel(c.gradeKey());
                yield new Check(c.type(), c.value(), null, c.gradeKey(), mine == null ? 0 : mine, mine != null && need != null && mine >= need);
            }
            case POSTS -> {
                Optional<Long> board = board(c, s);
                long n = board == null ? 0 : queries.posts(userId, board.orElse(null));
                yield new Check(c.type(), c.value(), board == null ? null : board.orElse(null), null, n, board != null && n >= value(c));
            }
            case POPULAR_DAYS -> {
                long n = queries.popularDays(userId);
                yield new Check(c.type(), c.value(), null, null, n, n >= value(c));
            }
            case ATTENDANCE_DAYS -> {
                long n = queries.attendanceDays(userId);
                yield new Check(c.type(), c.value(), null, null, n, n >= value(c));
            }
            case NO_REPORT_HIDDEN -> {
                boolean hidden = queries.hasReportHidden(userId);
                yield new Check(c.type(), null, null, null, hidden ? 1 : 0, !hidden);
            }
        };
    }

    private static long value(TitleCondition c) {
        return c.value() == null ? 1 : Math.max(1, c.value());
    }

    /**
     * 조건의 게시판 — 빈 Optional = 모든 게시판, null = 정한 slug 의 게시판이 없다(채울 수 없음).
     */
    public static Optional<Long> board(TitleCondition c, BoardCatalogSnapshot s) {
        if (c.boardId() != null) {
            return Optional.of(c.boardId());
        }
        if (c.boardSlug() != null) {
            return s.board(c.boardSlug()).map(EffectiveBoard::id).map(Optional::of).orElse(null);
        }
        return Optional.empty();
    }

    /** 칭호의 연결 게시판(매니저 심사) — {@code board_id}, 없으면 처음 값 slug 의 게시판, 둘 다 없으면 null */
    public Long linkedBoard(TitleEntity title) {
        if (title.getBoardId() != null) {
            return title.getBoardId();
        }
        return title.getBoardSlug() == null ? null : catalog.snapshot().board(title.getBoardSlug()).map(EffectiveBoard::id).orElse(null);
    }

    /**
     * 단계 칭호가 세는 수(진행 막대·자동 판정) — 인기글 일수 · 출석 일수 · 첫 {@code POSTS} 조건 게시판의 글 수 · 체험단 약속 지킴(체험단 기능 전에는 0).
     * 단계가 없으면 세는 첫 조건의 수, 세는 조건도 없으면 0.
     */
    public long metric(TitleEntity title, long userId) {
        if (BuiltinTitles.HONORARY_WRITER.equals(title.getTitleKey())) {
            return titles.findByTitleKey(BuiltinTitles.MONTHLY_WRITER).map(m -> memberTitles.countByUserIdAndTitleIdAndRevokedAtIsNull(userId, m.getId())).orElse(0L);
        }
        TitleMetric metric = driver(title);
        if (metric == null) {
            return 0;
        }
        return switch (metric) {
            case POPULAR_DAYS -> queries.popularDays(userId);
            case ATTENDANCE_DAYS -> queries.attendanceDays(userId);
            case POSTS -> {
                TitleCondition posts = firstPosts(title).orElse(null);
                Optional<Long> board = posts == null ? Optional.empty() : board(posts, catalog.snapshot());
                yield board == null ? 0 : queries.posts(userId, board.orElse(null));
            }
            case TRIAL_KEPT -> 0;
        };
    }

    /** 칭호가 세는 것 — 단계의 것, 없으면 세는 첫 조건(인기글 일수·출석·글 수), 없으면 null */
    public static TitleMetric driver(TitleEntity title) {
        if (title.getTierMetric() != null) {
            return title.getTierMetric();
        }
        for (TitleCondition c : TitleConditions.read(title.getConditions())) {
            switch (c.type()) {
                case POPULAR_DAYS -> {
                    return TitleMetric.POPULAR_DAYS;
                }
                case ATTENDANCE_DAYS -> {
                    return TitleMetric.ATTENDANCE_DAYS;
                }
                case POSTS -> {
                    return TitleMetric.POSTS;
                }
                default -> {
                }
            }
        }
        return null;
    }

    /** 세는 첫 조건의 문턱(단계 없는 칭호의 목표) — 없으면 null */
    public static Integer driverTarget(TitleEntity title) {
        TitleMetric metric = driver(title);
        if (metric == null) {
            return null;
        }
        for (TitleCondition c : TitleConditions.read(title.getConditions())) {
            if (c.type().name().equals(metric.name())) {
                return c.value() == null ? 1 : Math.max(1, c.value());
            }
        }
        return null;
    }

    /** 첫 {@code POSTS} 조건 */
    public static Optional<TitleCondition> firstPosts(TitleEntity title) {
        return TitleConditions.read(title.getConditions()).stream().filter(c -> c.type() == TitleConditionType.POSTS).findFirst();
    }
}
