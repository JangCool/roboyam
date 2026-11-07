package com.community.dashboard.repository.master;

import com.community.ad.entity.QAdDailyStatEntity;
import com.community.bizapplication.entity.BizApplicationKind;
import com.community.bizapplication.entity.BizApplicationStatus;
import com.community.bizapplication.entity.BizProduct;
import com.community.bizapplication.entity.QBizApplicationEntity;
import com.community.bizapplication.entity.QBizApplicationItemEntity;
import com.community.audit.entity.QAuditLogEntity;
import com.community.batch.entity.BatchRunStatus;
import com.community.batch.entity.QBatchRunEntity;
import com.community.dashboard.dto.response.gadget.DashboardGroupRequestsView;
import com.community.dashboard.dto.response.gadget.DashboardHotdealStatusView;
import com.community.dashboard.dto.response.gadget.DashboardHotdealTopView;
import com.community.dashboard.dto.response.gadget.DashboardOperatorSecurityView;
import com.community.dashboard.dto.response.gadget.DashboardPointShopView;
import com.community.dashboard.dto.response.gadget.DashboardRecentSignupsView;
import com.community.dashboard.dto.response.gadget.DashboardReportsView;
import com.community.group.entity.GroupStatus;
import com.community.group.entity.QGroupEntity;
import com.community.hotdeal.entity.QPostDealEntity;
import com.community.hotdeal.repository.master.AdminDealQueries;
import com.community.mfa.entity.QOperatorMfaEntity;
import com.community.nicknameimage.entity.NicknameImageStatus;
import com.community.nicknameimage.entity.QNicknameImageRequestEntity;
import com.community.operator.entity.QOperatorEntity;
import com.community.point.entity.PointLotKind;
import com.community.point.entity.QPointLedgerEntity;
import com.community.point.entity.QPointLotEntity;
import com.community.pointshop.entity.QPointShopItemEntity;
import com.community.pointshop.entity.QPointShopPurchaseEntity;
import com.community.post.entity.PostEntity;
import com.community.post.entity.QPostEntity;
import com.community.report.entity.QReportCaseEntity;
import com.community.report.entity.QReportEntity;
import com.community.report.entity.QReportEventEntity;
import com.community.social.entity.SocialProvider;
import com.community.user.entity.QProfileEntity;
import com.community.user.entity.QUserEntity;
import com.community.user.entity.QUserIdentityEntity;
import com.community.user.entity.UserStatus;
import com.community.withdrawal.entity.QMemberWithdrawalEntity;
import com.community.withdrawal.entity.WithdrawalStatus;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import com.community.zconfiguration.jpa.RoboyamRepositoryQDSL;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.JPQLQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Repository;

/**
 * 관리자 대시보드 가젯의 숫자·목록 읽기(admin-dashboard-contract 「서버 부담 원칙」). <b>부르는 쪽 트랜잭션에 참여한다.</b>
 *
 * <p>원칙: 숫자 = 상태별 COUNT 한 번(부분 인덱스·상태 인덱스·Snowflake ID 범위로) · 목록 = 최대 10행(인덱스 차례로 LIMIT) · 추이 = 이미 있는 일별 집계 표만(원본 GROUP BY 없음).
 * 「오늘」은 서비스 날짜(한국 시간) 0시부터이고, 만든 시각 인덱스가 없는 표는 Snowflake ID 의 시각 하한({@link SnowflakeIdGenerator#floorIdAt})으로 PK 범위를 센다.
 * 상태별 건수는 {@code CaseBuilder} 합({@code count(*) FILTER} 와 같다)으로 한 번에 센다. 질의마다 쓰는 인덱스를 주석으로 적는다 — 표·인덱스를 바꾸면 여기도 본다.
 *
 * <p>네이티브 SQL 은 없다 — 광고 일별 집계({@code ad_daily_stats})·광고 신청({@code biz_applications}, 대표 상품은 상관 서브쿼리)도 엔티티 질의다.
 * 운영자 2차 인증({@code operator_mfa})은 admin 모듈의 엔티티({@code com.community.mfa.entity})로 QueryDSL 이다.
 */
@Repository
public class DashboardGadgetStore extends RoboyamRepositoryQDSL {

    private static final QReportCaseEntity CASE = QReportCaseEntity.reportCaseEntity;
    private static final QReportEntity REPORT = QReportEntity.reportEntity;
    private static final QReportEventEntity EVENT = QReportEventEntity.reportEventEntity;
    private static final QPostEntity POST = QPostEntity.postEntity;
    private static final QPostDealEntity DEAL = QPostDealEntity.postDealEntity;
    private static final QGroupEntity GROUP = QGroupEntity.groupEntity;
    private static final QUserEntity USER = QUserEntity.userEntity;
    private static final QUserIdentityEntity IDENTITY = QUserIdentityEntity.userIdentityEntity;
    private static final QProfileEntity PROFILE = QProfileEntity.profileEntity;
    private static final QNicknameImageRequestEntity NICK_IMAGE = QNicknameImageRequestEntity.nicknameImageRequestEntity;
    private static final QPointLedgerEntity LEDGER = QPointLedgerEntity.pointLedgerEntity;
    private static final QPointLotEntity LOT = QPointLotEntity.pointLotEntity;
    private static final QPointShopPurchaseEntity PURCHASE = QPointShopPurchaseEntity.pointShopPurchaseEntity;
    private static final QPointShopItemEntity ITEM = QPointShopItemEntity.pointShopItemEntity;
    private static final QBatchRunEntity RUN = QBatchRunEntity.batchRunEntity;
    private static final QAuditLogEntity AUDIT = QAuditLogEntity.auditLogEntity;
    private static final QOperatorEntity OPERATOR = QOperatorEntity.operatorEntity;
    private static final QOperatorMfaEntity MFA = QOperatorMfaEntity.operatorMfaEntity;
    private static final QMemberWithdrawalEntity WITHDRAWAL = QMemberWithdrawalEntity.memberWithdrawalEntity;
    private static final QAdDailyStatEntity AD_STAT = QAdDailyStatEntity.adDailyStatEntity;
    private static final QBizApplicationEntity AD_APP = QBizApplicationEntity.bizApplicationEntity;
    private static final QBizApplicationItemEntity AD_ITEM = QBizApplicationItemEntity.bizApplicationItemEntity;

    private static final List<String> OPEN_CASE = List.of("PENDING", "IN_REVIEW");

    /** 예약된 광고 건 한 줄(유형 = 한도가 있는 상품 — {@code AdStatsAdminStore} 의 유형과 같다) */
    public record AdBooking(long id, String title, String product, Instant startAt, Instant endAt) {
    }

    /** 포인트 상점 판매 시작·끝 */
    public record ShopSale(long id, String name, String nameEn, Instant startAt, Instant endAt) {
    }

    /** 배치 실행 한 줄 */
    public record BatchRun(long id, String jobKey, Instant at, String errorMessage, String messageKey, String messageArgs) {
    }

    /** 감사 기록 한 줄 */
    public record AuditRow(long id, String actorName, String action, Instant at) {
    }

    /** 닉네임 이미지 심사 대기 한 줄 */
    public record NicknameImageRow(long id, String nickname, String imageKey) {
    }

    /** 일별 광고 합 */
    public record AdDay(LocalDate day, long impressions, long clicks) {
    }

    public DashboardGadgetStore(JPAQueryFactory query) {
        super(query);
    }

    // ── 신고 ────────────────────────────────────────────────────────────────

    /** 대기·검토 중 건수 — ix_report_cases_status_last(status, …) */
    public long[] reportCounts() {
        NumberExpression<Long> pending = countIf(CASE.status.eq("PENDING"));
        NumberExpression<Long> reviewing = countIf(CASE.status.eq("IN_REVIEW"));
        Tuple t = query.select(pending, reviewing).from(CASE).where(CASE.status.in(OPEN_CASE)).fetchOne();
        return new long[] {nz(t == null ? null : t.get(pending)), nz(t == null ? null : t.get(reviewing))};
    }

    /**
     * 오래된 대기 사건(먼저 신고된 차례) — 대기 사건은 상태 인덱스로 고르고(대기 건수만큼), 사건마다 자동 숨김 기록(ix_report_events_case)을 본다.
     * 대표 사유(가장 많은 사유, 같으면 사유 이름 차례 — 사유가 없는 줄은 뒤)는 고른 사건의 신고만(ix_reports_case) 사유별로 세어 고른다
     * (JPQL 하위 질의에 LIMIT 이 없어 한 번 더 읽는다).
     */
    public List<DashboardReportsView.Oldest> oldestPendingReports(int limit) {
        NumberExpression<Integer> autoHidden = new CaseBuilder()
                .when(JPAExpressions.selectOne().from(EVENT).where(EVENT.caseId.eq(CASE.id), EVENT.type.eq("AUTO_HIDDEN")).exists()).then(1).otherwise(0);
        List<Tuple> cases = query.select(CASE.id, CASE.targetType, CASE.firstReportedAt, autoHidden).from(CASE).where(CASE.status.eq("PENDING"))
                .orderBy(CASE.firstReportedAt.asc(), CASE.id.asc()).limit(limit).fetch();
        if (cases.isEmpty()) {
            return List.of();
        }
        List<Long> ids = cases.stream().map(t -> t.get(CASE.id)).toList();
        NumberExpression<Long> n = REPORT.count();
        Map<Long, List<Tuple>> reasons = new HashMap<>();
        for (Tuple t : query.select(REPORT.caseId, REPORT.reason, n).from(REPORT).where(REPORT.caseId.in(ids)).groupBy(REPORT.caseId, REPORT.reason).fetch()) {
            reasons.computeIfAbsent(t.get(REPORT.caseId), k -> new ArrayList<>()).add(t);
        }
        Comparator<Tuple> top = Comparator.<Tuple>comparingLong(t -> nz(t.get(n))).reversed()
                .thenComparing(t -> t.get(REPORT.reason), Comparator.nullsLast(Comparator.naturalOrder()));
        List<DashboardReportsView.Oldest> out = new ArrayList<>();
        for (Tuple c : cases) {
            String reason = reasons.getOrDefault(c.get(CASE.id), List.of()).stream().min(top).map(t -> t.get(REPORT.reason)).orElse(null);
            Integer hidden = c.get(autoHidden);
            out.add(new DashboardReportsView.Oldest(Long.toString(c.get(CASE.id)), reason == null ? "OTHER" : reason, c.get(CASE.targetType),
                    c.get(CASE.firstReportedAt), hidden != null && hidden == 1));
        }
        return out;
    }

    // ── 글 ──────────────────────────────────────────────────────────────────

    /** 최근 글 ID(공개 상태로 쓴 글, 지운 글 제외) — 파티션마다 PK 를 거꾸로 읽어 합친다 */
    public List<Long> recentPostIds(int limit) {
        return query.select(POST.id).from(POST).where(POST.deletedAt.isNull(), POST.status.eq(PostEntity.PUBLISHED)).orderBy(POST.id.desc()).limit(limit).fetch();
    }

    /** 열린 신고 사건(대기·검토 중)이 있는 글 — ux_report_cases_open_target(target_type, target_id) 부분 인덱스 */
    public Set<Long> openlyReportedPosts(Collection<Long> postIds) {
        if (postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(query.select(CASE.targetId).from(CASE)
                .where(CASE.targetType.eq("POST"), CASE.status.in(OPEN_CASE), CASE.targetId.in(postIds)).fetch());
    }

    // ── 소모임 ──────────────────────────────────────────────────────────────

    /** 개설 신청 대기 건수 — ix_groups_status_opened(status, …) */
    public long pendingGroups() {
        return nz(query.select(GROUP.count()).from(GROUP).where(GROUP.status.eq(GroupStatus.PENDING)).fetchOne());
    }

    /** 최근 개설 신청(대기 중, 최근 차례) — 대기 건만 상태 인덱스로 고른 뒤 정렬 */
    public List<DashboardGroupRequestsView.Recent> recentGroupRequests(int limit) {
        return query.select(GROUP.id, GROUP.name, PROFILE.nickname, GROUP.requestedAt).from(GROUP)
                .leftJoin(PROFILE).on(PROFILE.userId.eq(GROUP.creatorId.coalesce(GROUP.leaderId)))
                .where(GROUP.status.eq(GroupStatus.PENDING)).orderBy(GROUP.requestedAt.desc(), GROUP.id.desc()).limit(limit).fetch().stream()
                .map(t -> new DashboardGroupRequestsView.Recent(Long.toString(t.get(GROUP.id)), t.get(GROUP.name), nz(t.get(PROFILE.nickname)), t.get(GROUP.requestedAt)))
                .toList();
    }

    // ── 회원 ────────────────────────────────────────────────────────────────

    /**
     * 최근 가입 회원(탈퇴 제외) — ix_users_created(created_at DESC, id DESC). 가입 방법은 관리자 회원 목록과 같다(비밀번호가 있으면 EMAIL, 아니면 가장 먼저 연결한 소셜 —
     * ix_user_identities_user, 고른 회원만 한 번 더 읽는다. 연결도 없으면 EMAIL).
     */
    public List<DashboardRecentSignupsView.Item> recentSignups(int limit) {
        NumberExpression<Integer> hasPassword = new CaseBuilder().when(USER.passwordHash.isNotNull()).then(1).otherwise(0);
        List<Tuple> users = query.select(USER.id, PROFILE.nickname, hasPassword, USER.createdAt).from(USER).leftJoin(PROFILE).on(PROFILE.userId.eq(USER.id))
                .where(USER.status.ne(UserStatus.WITHDRAWN)).orderBy(USER.createdAt.desc(), USER.id.desc()).limit(limit).fetch();
        List<Long> social = users.stream().filter(t -> !Integer.valueOf(1).equals(t.get(hasPassword))).map(t -> t.get(USER.id)).toList();
        Map<Long, SocialProvider> first = new HashMap<>();
        if (!social.isEmpty()) {
            for (Tuple t : query.select(IDENTITY.userId, IDENTITY.provider).from(IDENTITY).where(IDENTITY.userId.in(social))
                    .orderBy(IDENTITY.userId.asc(), IDENTITY.createdAt.asc(), IDENTITY.id.asc()).fetch()) {
                first.putIfAbsent(t.get(IDENTITY.userId), t.get(IDENTITY.provider));
            }
        }
        return users.stream().map(t -> {
            long id = t.get(USER.id);
            SocialProvider provider = Integer.valueOf(1).equals(t.get(hasPassword)) ? null : first.get(id);
            return new DashboardRecentSignupsView.Item(Long.toString(id), nz(t.get(PROFILE.nickname)), provider == null ? "EMAIL" : provider.name(), t.get(USER.createdAt));
        }).toList();
    }

    /**
     * 회원 숫자 {가입, 탈퇴, 정지 중, 탈퇴 대기} — 네 COUNT, 모두 인덱스로만 센다(한 표의 서로 다른 인덱스라 따로 센다).
     *
     * <ul>
     *   <li>가입: 범위 {@code [from, to)} 에 만든 회원(지금 상태와 무관 — 그날 가입한 사실을 센다) — {@code ix_users_created(created_at DESC, id DESC)} 범위.</li>
     *   <li>탈퇴: 범위에 탈퇴를 **완료**한 회원({@code status = 'WITHDRAWN'}, 탈퇴 완료 시각 = {@code deleted_at} — 회원 탈퇴의 완료 처리 {@code WithdrawalEraseStore} 가 쓴다) —
     *       부분 인덱스 {@code ix_users_withdrawn_at(deleted_at) WHERE status = 'WITHDRAWN'}(admin V6).</li>
     *   <li>정지 중: 기간 정지·영구 정지({@code SUSPENDED · BANNED} — 관리자 회원 목록의 「정지」 거르기와 같은 뜻) — 부분 인덱스
     *       {@code ix_users_suspended(id) WHERE status IN ('SUSPENDED', 'BANNED')}(admin V6).</li>
     *   <li>탈퇴 대기: 탈퇴를 신청해 남은 일이 끝나기를 기다리는 예약({@code member_withdrawals.status = 'SCHEDULED'}) — 부분 인덱스
     *       {@code ux_member_withdrawals_scheduled(user_id) WHERE status = 'SCHEDULED'}(shared V132).</li>
     * </ul>
     */
    public long[] memberStats(Instant from, Instant to) {
        long joined = nz(query.select(USER.count()).from(USER).where(USER.createdAt.goe(from), USER.createdAt.lt(to)).fetchOne());
        long withdrawn = nz(query.select(USER.count()).from(USER)
                .where(USER.status.eq(UserStatus.WITHDRAWN), USER.deletedAt.goe(from), USER.deletedAt.lt(to)).fetchOne());
        long suspended = nz(query.select(USER.count()).from(USER).where(USER.status.in(UserStatus.SUSPENDED, UserStatus.BANNED)).fetchOne());
        long scheduled = nz(query.select(WITHDRAWAL.count()).from(WITHDRAWAL).where(WITHDRAWAL.status.eq(WithdrawalStatus.SCHEDULED)).fetchOne());
        return new long[] {joined, withdrawn, suspended, scheduled};
    }

    /** 닉네임 이미지 심사 대기 건수 — ix_nickname_image_requests_status(status, id) */
    public long pendingNicknameImages() {
        return nz(query.select(NICK_IMAGE.count()).from(NICK_IMAGE).where(NICK_IMAGE.status.eq(NicknameImageStatus.PENDING)).fetchOne());
    }

    /** 심사 대기 오래된 차례 — 같은 인덱스(status, id) 차례 */
    public List<NicknameImageRow> oldestNicknameImages(int limit) {
        return query.select(NICK_IMAGE.id, PROFILE.nickname, NICK_IMAGE.stillKey, NICK_IMAGE.imageKey).from(NICK_IMAGE)
                .leftJoin(PROFILE).on(PROFILE.userId.eq(NICK_IMAGE.userId))
                .where(NICK_IMAGE.status.eq(NicknameImageStatus.PENDING)).orderBy(NICK_IMAGE.id.asc()).limit(limit).fetch().stream()
                .map(t -> new NicknameImageRow(t.get(NICK_IMAGE.id), nz(t.get(PROFILE.nickname)),
                        t.get(NICK_IMAGE.stillKey) != null ? t.get(NICK_IMAGE.stillKey) : t.get(NICK_IMAGE.imageKey)))
                .toList();
    }

    // ── 핫딜 ────────────────────────────────────────────────────────────────

    /**
     * 핫딜 현황 — 진행 중 딜(ix_post_deals_key 등 status = 'ACTIVE' 부분 인덱스 범위)과 오늘 등록(글 ID = Snowflake — PK 범위)만 읽어 살아 있는 글(posts PK)과 잇는다.
     *
     * @param todayStart    오늘(한국 날짜) 0시
     * @param tomorrowStart 내일 0시 — 오늘 마감 = 진행 중이고 마감 시각이 이보다 앞
     */
    public DashboardHotdealStatusView hotdealStatus(Instant now, Instant todayStart, Instant tomorrowStart, int autoExpireDays) {
        Instant expireFrom = now.minus(Duration.ofDays(Math.max(0, autoExpireDays)));
        long todayId = SnowflakeIdGenerator.floorIdAt(todayStart);
        BooleanExpression live = AdminDealQueries.liveDeal(DEAL, now, autoExpireDays, expireFrom);
        NumberExpression<Long> running = countIf(live);
        NumberExpression<Long> flagged = countIf(live.and(DEAL.flaggedAt.isNotNull()));
        NumberExpression<Long> endingToday = countIf(live.and(DEAL.endsAt.lt(tomorrowStart)));
        NumberExpression<Long> registeredToday = countIf(DEAL.postId.goe(todayId));
        Tuple t = query.select(running, flagged, endingToday, registeredToday).from(DEAL).join(POST).on(POST.id.eq(DEAL.postId))
                .where(DEAL.status.eq("ACTIVE").or(DEAL.postId.goe(todayId)), AdminDealQueries.alivePost(POST, now)).fetchOne();
        return new DashboardHotdealStatusView(nz(t == null ? null : t.get(running)), nz(t == null ? null : t.get(registeredToday)),
                nz(t == null ? null : t.get(endingToday)), nz(t == null ? null : t.get(flagged)));
    }

    /** 지금 인기 핫딜 — 최근 7일 등록(글 ID 범위)의 진행 중 딜 · 공개 글을 추천 많은 차례로 */
    public List<DashboardHotdealTopView.Item> hotdealTop(Instant now, int autoExpireDays, int limit) {
        long fromId = SnowflakeIdGenerator.floorIdAt(now.minus(Duration.ofDays(7)));
        Instant expireFrom = now.minus(Duration.ofDays(Math.max(0, autoExpireDays)));
        return query.select(DEAL.postId, DEAL.storeName, POST.title, POST.likeCount, DEAL.dealPrice).from(DEAL).join(POST).on(POST.id.eq(DEAL.postId))
                .where(DEAL.postId.goe(fromId), AdminDealQueries.liveDeal(DEAL, now, autoExpireDays, expireFrom), AdminDealQueries.publicPost(POST, now))
                .orderBy(POST.likeCount.desc(), DEAL.postId.desc()).limit(limit).fetch().stream()
                .map(t -> new DashboardHotdealTopView.Item(Long.toString(t.get(DEAL.postId)), t.get(DEAL.storeName), t.get(POST.title), t.get(POST.likeCount),
                        t.get(DEAL.dealPrice)))
                .toList();
    }

    // ── 광고 ────────────────────────────────────────────────────────────────

    /**
     * 일별 광고 합 — 일별 집계 표(ad_daily_stats, ix_ads_day)만 읽는다. 범위 {@code [from, to]} 끝 포함, 값이 없는 날은 빠진다.
     */
    public List<AdDay> adDays(LocalDate from, LocalDate to) {
        NumberExpression<Long> impressions = Expressions.numberTemplate(Long.class, "sum({0})", AD_STAT.impressions);
        NumberExpression<Long> clicks = Expressions.numberTemplate(Long.class, "sum({0})", AD_STAT.clicks);
        return query.select(AD_STAT.day, impressions, clicks).from(AD_STAT).where(AD_STAT.day.between(from, to)).groupBy(AD_STAT.day).orderBy(AD_STAT.day.asc())
                .fetch().stream().map(t -> new AdDay(t.get(AD_STAT.day), num(t.get(impressions)), num(t.get(clicks)))).toList();
    }

    /**
     * 범위 {@code [from, to)} 안에 시작하거나 끝나는 예약된 광고 건(SCHEDULED — ix_ba_scheduled(start_at, end_at) 부분 인덱스). 핫딜 글 게시 건은 광고가 아니라 뺀다.
     * 건마다 대표 상품 = 핫딜 게시가 아닌 상품 줄 중 이름이 가장 앞선 것(상관 서브쿼리 {@code min(product)} — 예전 {@code LEFT JOIN LATERAL … ORDER BY product LIMIT 1} 과 같은 값, 없으면 null).
     */
    public List<AdBooking> adBookings(Instant from, Instant to) {
        JPQLQuery<BizProduct> product = JPAExpressions.select(AD_ITEM.product.min()).from(AD_ITEM)
                .where(AD_ITEM.applicationId.eq(AD_APP.id), AD_ITEM.product.ne(BizProduct.HOTDEAL_POST));
        return query.select(AD_APP.id, AD_APP.title, product, AD_APP.startAt, AD_APP.endAt).from(AD_APP)
                .where(AD_APP.status.eq(BizApplicationStatus.SCHEDULED), AD_APP.kind.ne(BizApplicationKind.HOTDEAL_POST),
                        AD_APP.startAt.goe(from).and(AD_APP.startAt.lt(to)).or(AD_APP.endAt.goe(from).and(AD_APP.endAt.lt(to))))
                .fetch().stream().map(t -> {
                    BizProduct p = t.get(product);
                    return new AdBooking(t.get(AD_APP.id), t.get(AD_APP.title), p == null ? null : p.name(), t.get(AD_APP.startAt), t.get(AD_APP.endAt));
                }).toList();
    }

    // ── 포인트 ──────────────────────────────────────────────────────────────

    /** 범위 {@code [from, to)} 의 지급(EARN 합)·사용(USE 합, 양수) — ix_point_ledger_created(created_at DESC, id DESC) 범위 */
    public long[] pointFlow(Instant from, Instant to) {
        NumberExpression<Long> earn = new CaseBuilder().when(LEDGER.kind.eq("EARN")).then(LEDGER.delta.longValue()).otherwise(0L).sum();
        NumberExpression<Long> use = new CaseBuilder().when(LEDGER.kind.eq("USE")).then(LEDGER.delta.longValue()).otherwise(0L).sum();
        Tuple t = query.select(earn, use).from(LEDGER).where(LEDGER.createdAt.goe(from), LEDGER.createdAt.lt(to)).fetchOne();
        return new long[] {num(t == null ? null : t.get(0, Object.class)), -num(t == null ? null : t.get(1, Object.class))};
    }

    /** 범위 {@code [from, to)} 에 만료될 무료 포인트 남은 양 — ix_point_lots_expiry(expires_at) WHERE kind = 'FREE' AND remaining_amount > 0 */
    public long pointsExpiring(Instant from, Instant to) {
        return num(query.select(LOT.remainingAmount.sum()).from(LOT)
                .where(LOT.kind.eq(PointLotKind.FREE), LOT.remainingAmount.gt(0L), LOT.expiresAt.goe(from), LOT.expiresAt.lt(to)).fetchOne());
    }

    /** 그 시각 뒤의 포인트 상점 구매 수 — 구매 ID(Snowflake) PK 범위 */
    public long pointShopPurchasesSince(Instant from) {
        return nz(query.select(PURCHASE.count()).from(PURCHASE).where(PURCHASE.id.goe(SnowflakeIdGenerator.floorIdAt(from))).fetchOne());
    }

    /** 남은 수량이 적은 판매 중 품목(수량 제한 품목만) — 품목 표는 작다(운영자가 만든 수십 줄) */
    public List<DashboardPointShopView.LowStock> lowStockItems(Instant now, int maxStock, int limit) {
        return query.select(ITEM.id, ITEM.name, ITEM.nameEn, ITEM.stock).from(ITEM)
                .where(ITEM.active.isTrue(), ITEM.stock.isNotNull(), ITEM.stock.loe(maxStock), ITEM.saleStartAt.isNull().or(ITEM.saleStartAt.loe(now)),
                        ITEM.saleEndAt.isNull().or(ITEM.saleEndAt.gt(now)))
                .orderBy(ITEM.stock.asc(), ITEM.sortOrder.asc(), ITEM.id.asc()).limit(limit).fetch().stream()
                .map(t -> new DashboardPointShopView.LowStock(Long.toString(t.get(ITEM.id)), t.get(ITEM.name), t.get(ITEM.nameEn), t.get(ITEM.stock)))
                .toList();
    }

    /** 범위 {@code [from, to)} 안에 판매를 시작하거나 끝내는 사용 중 품목 */
    public List<ShopSale> shopSales(Instant from, Instant to) {
        return query.select(ITEM.id, ITEM.name, ITEM.nameEn, ITEM.saleStartAt, ITEM.saleEndAt).from(ITEM)
                .where(ITEM.active.isTrue(), ITEM.saleStartAt.goe(from).and(ITEM.saleStartAt.lt(to)).or(ITEM.saleEndAt.goe(from).and(ITEM.saleEndAt.lt(to))))
                .fetch().stream()
                .map(t -> new ShopSale(t.get(ITEM.id), t.get(ITEM.name), t.get(ITEM.nameEn), t.get(ITEM.saleStartAt), t.get(ITEM.saleEndAt)))
                .toList();
    }

    // ── 배치·감사·운영자 ─────────────────────────────────────────────────────

    /** 그 시각 뒤에 시작해 실패한 실행(최근 차례) — ix_batch_runs_started(started_at DESC, id DESC) 범위. 시각은 끝난 시각, 없으면 시작 시각 */
    public List<BatchRun> failedBatchRuns(Instant since, int limit) {
        return query.select(RUN.id, RUN.jobKey, RUN.finishedAt, RUN.startedAt, RUN.errorMessage, RUN.messageKey, RUN.messageArgs).from(RUN)
                .where(RUN.startedAt.goe(since), RUN.status.eq(BatchRunStatus.FAIL)).orderBy(RUN.startedAt.desc(), RUN.id.desc()).limit(limit).fetch().stream()
                .map(t -> new BatchRun(t.get(RUN.id), t.get(RUN.jobKey), t.get(RUN.finishedAt) != null ? t.get(RUN.finishedAt) : t.get(RUN.startedAt),
                        t.get(RUN.errorMessage), t.get(RUN.messageKey), t.get(RUN.messageArgs)))
                .toList();
    }

    /** 실행 중(시작한 차례) — ux_batch_runs_running(job_key) WHERE status = 'RUNNING' 부분 인덱스(배치마다 하나) */
    public List<BatchRun> runningBatchRuns(int limit) {
        return query.select(RUN.id, RUN.jobKey, RUN.startedAt).from(RUN).where(RUN.status.eq(BatchRunStatus.RUNNING))
                .orderBy(RUN.startedAt.asc(), RUN.id.asc()).limit(limit).fetch().stream()
                .map(t -> new BatchRun(t.get(RUN.id), t.get(RUN.jobKey), t.get(RUN.startedAt), null, null, null))
                .toList();
    }

    /** 최근 운영자 작업(성공) — ix_audit_logs_occurred_id(occurred_at DESC, id DESC) 차례로 거른다 */
    public List<AuditRow> recentOperatorAudits(int limit) {
        return query.select(AUDIT.id, OPERATOR.operatorName, AUDIT.action, AUDIT.occurredAt).from(AUDIT).leftJoin(OPERATOR).on(OPERATOR.id.eq(AUDIT.actorId))
                .where(AUDIT.actorType.eq("OPERATOR"), AUDIT.result.eq("SUCCESS")).orderBy(AUDIT.occurredAt.desc(), AUDIT.id.desc()).limit(limit).fetch().stream()
                .map(t -> new AuditRow(t.get(AUDIT.id), t.get(OPERATOR.operatorName), t.get(AUDIT.action), t.get(AUDIT.occurredAt)))
                .toList();
    }

    /**
     * 운영자 보안 — 운영자 표는 작다(수십 줄). 2차 인증 미등록 = 승인·사용 중·만료 아님 운영자 중 operator_mfa(PK) 에 등록이 없는 사람.
     * 세 건수를 한 문장으로 센다(전체 · 잠김 · 미등록 — {@code count(*) FILTER} 는 {@code CaseBuilder} 합, 빈 표면 0).
     */
    public DashboardOperatorSecurityView operatorSecurity() {
        NumberExpression<Long> total = OPERATOR.count();
        NumberExpression<Long> locked = countIf(OPERATOR.locked.isTrue());
        NumberExpression<Long> unenrolled = countIf(OPERATOR.approved.isTrue().and(OPERATOR.enabled.isTrue()).and(OPERATOR.expired.isFalse())
                .and(JPAExpressions.selectOne().from(MFA).where(MFA.operatorId.eq(OPERATOR.id), MFA.enrolledAt.isNotNull()).notExists()));
        Tuple t = query.select(total, locked, unenrolled).from(OPERATOR).fetchOne();
        return new DashboardOperatorSecurityView(nz(t == null ? null : t.get(total)), nz(t == null ? null : t.get(locked)),
                nz(t == null ? null : t.get(unenrolled)));
    }

    // ── 도우미 ──────────────────────────────────────────────────────────────

    /** 조건을 만족하는 줄 수({@code count(*) FILTER (WHERE ..)} 와 같다 — 빈 결과면 null) */
    private static NumberExpression<Long> countIf(BooleanExpression condition) {
        return new CaseBuilder().when(condition).then(1L).otherwise(0L).sum();
    }

    private static long nz(Long value) {
        return value == null ? 0 : value;
    }

    /** 합계 값(DB 가 고른 수 타입 — 빈 결과면 null) */
    private static long num(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

}
