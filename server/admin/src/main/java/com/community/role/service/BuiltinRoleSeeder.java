package com.community.role.service;

import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.permission.entity.MemberPermission;
import com.community.permission.service.BootstrapLock;
import com.community.role.entity.RoleEntity;
import com.community.role.entity.RoleGrantMode;
import com.community.role.entity.RolePermissionEntity;
import com.community.role.entity.RoleScope;
import com.community.role.entity.RoleTypes;
import com.community.role.repository.master.RolePermissionRepository;
import com.community.role.repository.master.RoleRepository;
import com.community.zconfiguration.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품이 넣는 <b>내장 회원 역할</b>을 서버가 뜰 때 한 번 넣는다 — 회원 유형(일반 회원 · 사업자 회원)과 게시판 단위(게시판 매니저 · 게시판 스텝).
 * 이미 있는 역할은 건드리지 않는다(운영자가 조정한 권한을 되돌리지 않는다). 처음 넣을 때만 기본 권한 세트를 준다 — 값은 목업의 기본값이고 운영자가 조정할 수 있다.
 * 등급 역할(파생, {@code ROLE_GRADE_<등급키>})은 등급 설정이 원천이라 {@link GradeRoleSynchronizer} 가 만든다 — 기동 때는 아직 역할이 없는 등급의 역할만 채운다(이미 있는 역할은 이름만 맞춘다). 운영자 역할은 시드가 없다(최고 관리자가 만든다).
 * 이름·설명은 운영자가 고칠 수 있는 데이터라 한국어 한 값으로 둔다. 카탈로그 맞춤({@code PermissionCatalogSynchronizer}) 뒤에 돌아야 한다(권한 외래키).
 */
@Service
public class BuiltinRoleSeeder {

    private static final Logger log = LoggerFactory.getLogger(BuiltinRoleSeeder.class);

    private record Seed(String roleId, String roleTypeId, String name, String description, RoleScope scope, RoleGrantMode grantMode,
                        List<MemberPermission> permissions) {
    }

    private static final List<MemberPermission> USE_REGULAR = List.of(MemberPermission.POST_WRITE, MemberPermission.COMMENT_WRITE, MemberPermission.POST_REACT,
            MemberPermission.REPORT_CREATE, MemberPermission.MESSAGE_SEND, MemberPermission.FILE_ATTACH, MemberPermission.SHOP_USE,
            MemberPermission.NICKNAME_IMAGE_REQUEST);

    private static final List<Seed> SEEDS = List.of(
            new Seed("ROLE_MEMBER_GENERAL", RoleTypes.KIND, "일반 회원", "가입하면 기본으로 갖는 회원 유형입니다.", RoleScope.SITE, RoleGrantMode.AUTO, USE_REGULAR),
            new Seed("ROLE_MEMBER_BUSINESS", RoleTypes.KIND, "사업자 회원", "사업자 승인을 받은 회원입니다. 읽기·프로필·허용 게시판 위주로 제한됩니다.", RoleScope.SITE,
                    RoleGrantMode.AUTO, List.of(MemberPermission.REPORT_CREATE, MemberPermission.NICKNAME_IMAGE_REQUEST)),
            new Seed("ROLE_BOARD_MANAGER", RoleTypes.BOARD, "게시판 매니저", "게시판 하나를 맡아 운영합니다. 스텝 지정도 합니다.", RoleScope.BOARD, RoleGrantMode.APPOINTED,
                    List.of(MemberPermission.BOARD_POST_HIDE, MemberPermission.BOARD_POST_DELETE, MemberPermission.BOARD_COMMENT_DELETE,
                            MemberPermission.BOARD_SANCTION_ISSUE, MemberPermission.BOARD_NOTICE_PIN, MemberPermission.BOARD_STAFF_APPOINT,
                            MemberPermission.BOARD_STAFF_ACTION_RESTORE)),
            new Seed(MemberPermission.BOARD_STAFF_ROLE_ID, RoleTypes.BOARD, "게시판 스텝", "맡은 게시판에서 글·댓글을 정리하고 가벼운 제재를 합니다.", RoleScope.BOARD,
                    RoleGrantMode.APPOINTED, List.of(MemberPermission.BOARD_POST_HIDE, MemberPermission.BOARD_POST_DELETE,
                            MemberPermission.BOARD_COMMENT_DELETE, MemberPermission.BOARD_SANCTION_ISSUE)));

    private final RoleRepository roles;
    private final RolePermissionRepository rolePermissions;
    private final SnowflakeIdGenerator ids;
    private final EntityManager entityManager;
    private final GradeRepositoryQDSL gradeQueries;
    private final GradeRoleSynchronizer gradeRoles;

    public BuiltinRoleSeeder(RoleRepository roles, RolePermissionRepository rolePermissions, SnowflakeIdGenerator ids, EntityManager entityManager,
                             GradeRepositoryQDSL gradeQueries, GradeRoleSynchronizer gradeRoles) {
        this.roles = roles;
        this.rolePermissions = rolePermissions;
        this.ids = ids;
        this.entityManager = entityManager;
        this.gradeQueries = gradeQueries;
        this.gradeRoles = gradeRoles;
    }

    @Transactional
    public void seed() {
        BootstrapLock.acquire(entityManager);

        Instant base = Instant.now();
        int created = 0;
        for (Seed seed : SEEDS) {
            if (roles.existsById(seed.roleId())) {
                continue;
            }
            // 만든 시각을 시드 차례대로 1ms 씩 벌려 목록의 차례(제품 역할 먼저, 그다음 만든 순서)가 늘 같게 한다
            Instant now = base.plusMillis(created);
            roles.persistAndFlush(RoleEntity.createBuiltinMember(seed.roleId(), seed.roleTypeId(), seed.name(), seed.description(), seed.scope(), seed.grantMode(), now)); // 아래 권한 행이 참조하므로 먼저 내보낸다
            for (MemberPermission permission : seed.permissions()) {
                rolePermissions.persist(RolePermissionEntity.create(ids.nextId(), seed.roleId(), permission.code(), now));
            }
            created++;
        }
        log.info("내장 회원 역할: {}개 새로 넣음(전체 {}개)", created, SEEDS.size());

        // 등급 역할(파생) — 등급 설정(grades)에 있는데 역할이 없는 등급의 역할을 채운다
        gradeRoles.sync(gradeQueries.findAllOrdered(), List.of(), base.plusMillis(SEEDS.size()));
    }
}
