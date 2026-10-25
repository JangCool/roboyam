package com.community.member.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.GradeRoles;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.identity.service.IdentityVerificationService;
import com.community.member.dto.MemberRecord;
import com.community.member.dto.MemberRoles;
import com.community.member.dto.MemberSearchCriteria;
import com.community.member.dto.request.MemberSearchRequest;
import com.community.member.dto.response.MemberActivity;
import com.community.member.dto.response.MemberConsentRecord;
import com.community.member.dto.response.MemberConsents;
import com.community.member.dto.response.MemberDetailView;
import com.community.member.dto.response.MemberGradePoint;
import com.community.member.dto.response.MemberIdentityView;
import com.community.member.dto.response.MemberPage;
import com.community.member.dto.response.MemberReportSummary;
import com.community.member.dto.response.MemberRoleDetail;
import com.community.member.dto.response.MemberRoleRef;
import com.community.member.dto.response.MemberSocialLink;
import com.community.member.dto.response.MemberView;
import com.community.member.exception.MemberNotFoundException;
import com.community.member.repository.master.MemberActivityRepository;
import com.community.member.repository.master.MemberRepositoryQDSL;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
import com.community.report.repository.master.ReportRepositoryQDSL;
import com.community.role.entity.RoleAxis;
import com.community.role.entity.RoleEntity;
import com.community.role.entity.RoleTypes;
import com.community.role.repository.master.RoleRepositoryQDSL;
import com.community.social.entity.SocialProvider;
import com.community.user.entity.ProfileEntity;
import com.community.user.entity.UserEntity;
import com.community.user.entity.UserStatus;
import com.community.user.repository.master.ProfileRepository;
import com.community.user.repository.master.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 조회(읽기 전용) — 목록·상세·역할 선택지 (docs/features/roles-contract.md 2단계). 입구의 권한(member.read)은 컨트롤러가 선언한다.
 *
 * <ul>
 *   <li><b>이메일 가림</b>: {@code member.pii} 가 없으면 이메일을 {@code ab***@gmail.com} 으로 가려서 주고 {@code emailMasked=true}. 가려진 값으로 짐작해 찾을 수 없게
 *       이메일 검색은 {@code member.pii} 가 있으면 부분 일치, 없으면 <b>전체 주소가 같을 때만</b> 맞는다.</li>
 *   <li><b>상세 열람 기록</b>: 상세를 열면 감사 기록({@code MEMBER_VIEW})을 남긴다(대상 회원 ID와 이메일 원문 공개 여부만 — 이메일 값은 남기지 않는다). 목록은 남기지 않는다(가정).</li>
 *   <li>활동 요약({@code activity}: 글·댓글 수·최근 7일·받은 추천), 신고 요약({@code reports})과 등급·포인트 구획의 등급 변경 이력·승급 상태·다음 등급 달성도({@code gradePoint})는
 *       채운다 — {@link MemberActivityRepository}·{@link ReportRepositoryQDSL#countsOf}·{@link MemberGradePointAssembler}. 읽기 전용 조회라 새 감사 기록은 없다(열람은 {@code MEMBER_VIEW} 하나).</li>
 *   <li>포인트 잔액({@code point})은 목록·상세 모두 잔액 행({@code point_balances})의 값이고, 등급·포인트 구획의 최근 포인트 내역도 원장에서 채운다.</li>
 *   <li>서버에 아직 데이터가 없는 칸(휴면·제재·알림 설정·공동구매 등)은 null/빈 값으로 내려간다 — {@link MemberDetailView}.</li>
 *   <li>본인 인증 구획({@code identity})은 {@link IdentityVerificationService#summaryOf}(인증 여부·시각·만 19세 이상·수단 — 생년월일·해시 없음, 커머스 0-4).</li>
 *   <li>탈퇴한 회원은 축약해 보인다(역할·소셜·동의·소개·활동 요약·신고 요약·등급·본인 인증 구획 없음, 탈퇴 시각만).</li>
 * </ul>
 */
@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    /** 활동 요약의 「최근」 구간 일수 */
    private static final long RECENT_DAYS = 7;

    private final MemberRepositoryQDSL members;
    private final MemberSearchParser parser;
    private final MemberRoleResolver roleResolver;
    private final GradeRepositoryQDSL gradeQueries;
    private final RoleRepositoryQDSL roleQueries;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final AuditLogService audit;
    private final MemberActivityRepository activities;
    private final ReportRepositoryQDSL reportQueries;
    private final MemberGradePointAssembler gradePoints;
    private final IdentityVerificationService identities;

    public MemberService(MemberRepositoryQDSL members, MemberSearchParser parser, MemberRoleResolver roleResolver, GradeRepositoryQDSL gradeQueries,
                         RoleRepositoryQDSL roleQueries, UserRepository users, ProfileRepository profiles, AuditLogService audit, MemberActivityRepository activities,
                         ReportRepositoryQDSL reportQueries, MemberGradePointAssembler gradePoints, IdentityVerificationService identities) {
        this.identities = identities;
        this.activities = activities;
        this.reportQueries = reportQueries;
        this.gradePoints = gradePoints;
        this.members = members;
        this.parser = parser;
        this.roleResolver = roleResolver;
        this.gradeQueries = gradeQueries;
        this.roleQueries = roleQueries;
        this.users = users;
        this.profiles = profiles;
        this.audit = audit;
    }

    /** 조건에 맞는 회원 한 쪽 — 잘못된 조건은 400 (검사 규칙은 {@link MemberSearchParser}) */
    @Transactional(readOnly = true)
    public MemberPage list(AdminActor actor, MemberSearchRequest request) {
        MemberSearchCriteria criteria = parser.parse(request);
        boolean canPii = actor.can(Permission.MEMBER_PII);

        long total = members.count(criteria, canPii);
        List<MemberRecord> rows = total == 0 || (long) criteria.page() * criteria.size() >= total ? List.of() : members.search(criteria, canPii);
        List<Long> ids = rows.stream().map(MemberRecord::id).toList();
        Map<Long, MemberRoles> roles = roleResolver.resolve(rows);
        Map<Long, List<SocialProvider>> providers = members.providersOf(ids);

        List<MemberView> items = new ArrayList<>();
        for (MemberRecord row : rows) {
            MemberRoles memberRoles = roles.get(row.id());
            items.add(new MemberView(Long.toString(row.id()), canPii ? row.email() : MemberMasking.email(row.email()), row.email() != null && !canPii, row.nickname(),
                    row.gradeKey(), memberRoles.memberType(), row.status().name(), authMethodOf(row, providers.getOrDefault(row.id(), List.of())), row.emailVerifiedAt(),
                    row.createdAt(), row.lastLoginAt(), pointOf(row), memberRoles.roles().stream().map(role -> new MemberRoleRef(role.roleId(), role.roleName())).toList(), null,
                    statusChangedAt(row), memberRoles.boardRole(), 0));
        }
        return new MemberPage(items, total, criteria.page(), criteria.size());
    }

    /** 회원 한 명의 상세. 없는 회원은 404. 열람을 감사 기록으로 남긴다(읽기 전용 트랜잭션에서는 기록이 저장되지 않아 쓰기 트랜잭션이다) */
    @Transactional
    public MemberDetailView detail(AdminActor actor, String memberId) {
        long id = parseId(memberId);
        MemberRecord record = members.findRecord(id).orElseThrow(MemberNotFoundException::new);
        UserEntity user = users.findById(id).orElseThrow(MemberNotFoundException::new);
        ProfileEntity profile = profiles.findById(id).orElseThrow(MemberNotFoundException::new);
        boolean canPii = actor.can(Permission.MEMBER_PII);
        boolean withdrawn = record.status() == UserStatus.WITHDRAWN;

        MemberRoles memberRoles = roleResolver.resolve(List.of(record)).get(id);
        List<SocialProvider> providers = members.providersOf(List.of(id)).getOrDefault(id, List.of());

        boolean emailMasked = record.email() != null && !canPii;
        MemberConsents consents = withdrawn ? null : new MemberConsents(new MemberConsentRecord(user.getTermsAgreedAt(), user.getTermsVersion()), null,
                user.getAgeConfirmedAt(), List.of(), List.of());
        List<MemberSocialLink> social = withdrawn ? List.of() : List.of(SocialProvider.KAKAO, SocialProvider.NAVER, SocialProvider.GOOGLE).stream()
                .map(provider -> new MemberSocialLink(provider.name(), providers.contains(provider))).toList();

        // 활동 요약·신고 요약·등급 이력은 탈퇴 회원에게는 내리지 않는다(축약). 글·댓글 집계는 작성자 색인(V46)을 탄다 — 최근 구간은 지금 - 7일 이후
        Instant now = Instant.now();
        MemberActivity activity = withdrawn ? null : activityOf(id, now);
        MemberReportSummary reports = withdrawn ? null : reportsOf(id);
        MemberGradePoint gradePoint = withdrawn ? null : gradePoints.assemble(user, profile, activity, now);
        // 본인 인증(커머스 0-4) — 탈퇴 회원은 null, 미인증은 verified:false. 생년월일·해시는 싣지 않는다
        MemberIdentityView identity = withdrawn ? null : identities.summaryOf(id).map(MemberIdentityView::of).orElseGet(MemberIdentityView::unverified);

        MemberDetailView view = new MemberDetailView(Long.toString(id), canPii ? record.email() : MemberMasking.email(record.email()), emailMasked, record.nickname(),
                record.gradeKey(), memberRoles.memberType(), record.status().name(), authMethodOf(record, providers), record.emailVerifiedAt(), record.createdAt(),
                record.lastLoginAt(), pointOf(record), memberRoles.roles(), null, statusChangedAt(record), memberRoles.boardRole(), 0,
                null, activity, List.of(), null, reports,
                withdrawn ? null : profile.getBio(), social, null, null, null, withdrawn ? record.deletedAt() : null,
                withdrawn || canPii ? List.of() : null, !canPii,
                gradePoint, null, null, null, consents, null, identity);

        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.MEMBER_VIEW, AuditResult.SUCCESS).target("MEMBER", Long.toString(id))
                .with("emailRevealed", canPii && record.email() != null));
        log.info("회원 상세를 열었다 memberId={} operatorId={}", id, actor.operatorId());
        return view;
    }

    /**
     * 활동 요약 — 공개 게시판에 지금 보이는 글·댓글을 센다({@link MemberActivityRepository} — 즉시 승급과 같은 정의). 받은 추천은 그 글·댓글의 추천 수 합.
     * 인기글 기능은 아직 없어 0, 접속 일수는 날짜별 로그인 성공 기록이 없어 0(마지막 로그인 시각 하나뿐이라 「최근 7일 중 접속한 날 수」를 알 수 없다).
     */
    private MemberActivity activityOf(long userId, Instant now) {
        MemberActivityRepository.Counts counts = activities.countOf(userId, now.minus(Duration.ofDays(RECENT_DAYS)), now);
        return new MemberActivity(counts.posts(), counts.comments(), counts.likesReceived(), 0, counts.recentPosts(), counts.recentComments(), 0);
    }

    /** 신고 요약 — 받은 신고(대상 작성자)·그중 조치(수용)·한 신고. 보유기간 배치가 지운 신고는 세지 않는다 */
    private MemberReportSummary reportsOf(long userId) {
        ReportRepositoryQDSL.MemberReportCounts counts = reportQueries.countsOf(userId);
        return new MemberReportSummary(counts.received(), counts.actioned(), counts.filed());
    }

    /** 역할 고르개의 선택지 — 회원 역할 전부. 등급 역할이 먼저(등급 서열 순서), 그다음 회원 유형·게시판 단위·사용자 정의(역할 목록의 차례) */
    @Transactional(readOnly = true)
    public List<MemberRoleRef> roleOptions() {
        Map<String, Integer> gradeLevel = new HashMap<>();
        for (GradeEntity grade : gradeQueries.findAllOrdered()) {
            gradeLevel.put(GradeRoles.roleIdOf(grade.getGradeKey()), grade.getLevel());
        }
        List<RoleEntity> memberRoles = roleQueries.findAllOrdered().stream().filter(role -> role.getAxis() == RoleAxis.MEMBER).toList();
        List<MemberRoleRef> options = new ArrayList<>();
        memberRoles.stream().filter(role -> RoleTypes.GRADE.equals(role.getRoleTypeId()))
                .sorted(Comparator.comparingInt(role -> gradeLevel.getOrDefault(role.getRoleId(), Integer.MAX_VALUE)))
                .forEach(role -> options.add(new MemberRoleRef(role.getRoleId(), role.getRoleName())));
        memberRoles.stream().filter(role -> !RoleTypes.GRADE.equals(role.getRoleTypeId()))
                .forEach(role -> options.add(new MemberRoleRef(role.getRoleId(), role.getRoleName())));
        return options;
    }

    /** 가입 방식 — 비밀번호가 있으면 이메일, 아니면 가장 먼저 연결한 소셜 */
    private static String authMethodOf(MemberRecord record, List<SocialProvider> providers) {
        if (Boolean.TRUE.equals(record.hasPassword()) || providers.isEmpty()) {
            return "EMAIL";
        }
        return providers.get(0).name();
    }

    /** 포인트 잔액 — 탈퇴한 회원은 포인트가 소멸하므로 0 으로 보인다 */
    private static long pointOf(MemberRecord record) {
        return record.status() == UserStatus.WITHDRAWN || record.point() == null ? 0 : record.point();
    }

    /** 가장 최근 상태 변경 시각 — 정지·휴면 이력 표가 아직 없어 탈퇴 시각만 안다 */
    private static Instant statusChangedAt(MemberRecord record) {
        return record.status() == UserStatus.WITHDRAWN ? record.deletedAt() : null;
    }

    private static long parseId(String memberId) {
        if (memberId == null || memberId.isEmpty() || memberId.length() > 19 || !memberId.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
            throw new MemberNotFoundException();
        }
        try {
            return Long.parseLong(memberId);
        } catch (NumberFormatException e) {
            throw new MemberNotFoundException();
        }
    }
}
