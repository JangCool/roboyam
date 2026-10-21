package com.community.member.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.dto.AuditEvent;
import com.community.audit.entity.AuditResult;
import com.community.audit.service.AuditLogService;
import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.GradeRoles;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.member.dto.MemberRecord;
import com.community.member.dto.MemberRoles;
import com.community.member.dto.MemberSearchCriteria;
import com.community.member.dto.request.MemberSearchRequest;
import com.community.member.dto.response.MemberConsentRecord;
import com.community.member.dto.response.MemberConsents;
import com.community.member.dto.response.MemberDetailView;
import com.community.member.dto.response.MemberPage;
import com.community.member.dto.response.MemberRoleDetail;
import com.community.member.dto.response.MemberRoleRef;
import com.community.member.dto.response.MemberSocialLink;
import com.community.member.dto.response.MemberView;
import com.community.member.exception.MemberNotFoundException;
import com.community.member.repository.master.MemberRepositoryQDSL;
import com.community.permission.dto.AdminActor;
import com.community.permission.entity.Permission;
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
 *   <li>서버에 아직 데이터가 없는 칸(포인트·휴면·제재·활동 수·알림 설정·공동구매 등)은 null/빈 값으로 내려간다 — {@link MemberDetailView}.</li>
 *   <li>탈퇴한 회원은 축약해 보인다(역할·소셜·동의·소개 없음, 탈퇴 시각만).</li>
 * </ul>
 */
@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private final MemberRepositoryQDSL members;
    private final MemberSearchParser parser;
    private final MemberRoleResolver roleResolver;
    private final GradeRepositoryQDSL gradeQueries;
    private final RoleRepositoryQDSL roleQueries;
    private final UserRepository users;
    private final ProfileRepository profiles;
    private final AuditLogService audit;

    public MemberService(MemberRepositoryQDSL members, MemberSearchParser parser, MemberRoleResolver roleResolver, GradeRepositoryQDSL gradeQueries,
                         RoleRepositoryQDSL roleQueries, UserRepository users, ProfileRepository profiles, AuditLogService audit) {
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
                    row.createdAt(), row.lastLoginAt(), 0, memberRoles.roles().stream().map(role -> new MemberRoleRef(role.roleId(), role.roleName())).toList(), null,
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

        MemberDetailView view = new MemberDetailView(Long.toString(id), canPii ? record.email() : MemberMasking.email(record.email()), emailMasked, record.nickname(),
                record.gradeKey(), memberRoles.memberType(), record.status().name(), authMethodOf(record, providers), record.emailVerifiedAt(), record.createdAt(),
                record.lastLoginAt(), 0, memberRoles.roles(), null, statusChangedAt(record), memberRoles.boardRole(), 0,
                null, null, List.of(), null, null,
                withdrawn ? null : profile.getBio(), social, null, null, null, withdrawn ? record.deletedAt() : null,
                withdrawn || canPii ? List.of() : null, !canPii,
                null, null, null, null, consents, null);

        audit.record(AuditEvent.byOperator(actor.operatorId(), actor.ip(), AuditActions.MEMBER_VIEW, AuditResult.SUCCESS).target("MEMBER", Long.toString(id))
                .with("emailRevealed", canPii && record.email() != null));
        log.info("회원 상세를 열었다 memberId={} operatorId={}", id, actor.operatorId());
        return view;
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
