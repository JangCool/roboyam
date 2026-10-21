package com.community.permission.service;

import com.community.permission.entity.MemberPermission;
import com.community.permission.entity.Permission;
import com.community.permission.entity.PermissionEntity;
import com.community.permission.repository.master.PermissionRepository;
import com.community.role.repository.master.RoleRepositoryQDSL;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권한 카탈로그(코드 enum — {@link Permission}·{@link MemberPermission})를 {@code permissions} 표와 <b>맞춘다</b>. 서버가 뜰 때 한 번 돈다({@code BootstrapRunner}).
 *
 * <ul>
 *   <li>코드에 새로 생긴 권한은 넣고, 분류(유형·단위·준비 여부)가 바뀐 것은 고친다.</li>
 *   <li>코드에서 사라진 권한은 모든 역할에서 빼고 표에서도 지운다(역할이 모르는 권한을 들고 있지 않게). 지운 것은 로그에 남긴다.</li>
 *   <li>여러 서버가 동시에 떠도 겹치지 않게 DB 어드바이저리 잠금(트랜잭션 단위)으로 한 서버씩 돈다. 같은 결과로 끝나므로 다시 돌아도 안전하다.</li>
 *   <li>운영자 권한과 회원 권한의 코드가 겹치면 기동을 멈춘다(표의 키가 하나라 섞일 수 없다).</li>
 * </ul>
 */
@Service
public class PermissionCatalogSynchronizer {

    private static final Logger log = LoggerFactory.getLogger(PermissionCatalogSynchronizer.class);

    private final PermissionRepository permissions;
    private final RoleRepositoryQDSL roleQueries;
    private final EntityManager entityManager;

    public PermissionCatalogSynchronizer(PermissionRepository permissions, RoleRepositoryQDSL roleQueries, EntityManager entityManager) {
        this.permissions = permissions;
        this.roleQueries = roleQueries;
        this.entityManager = entityManager;
    }

    @Transactional
    public void sync() {
        BootstrapLock.acquire(entityManager);

        Instant now = Instant.now();
        Map<String, PermissionEntity> existing = new LinkedHashMap<>();
        permissions.findAll().forEach(entity -> existing.put(entity.getCode(), entity));

        Set<String> wanted = new HashSet<>();
        int added = 0;
        int changed = 0;
        for (Permission permission : Permission.values()) {
            checkUnique(wanted, permission.code());
            PermissionEntity entity = existing.get(permission.code());
            if (entity == null) {
                permissions.persist(PermissionEntity.of(permission, now));
                added++;
            } else if (entity.refresh(permission, now)) {
                changed++;
            }
        }
        for (MemberPermission permission : MemberPermission.values()) {
            checkUnique(wanted, permission.code());
            PermissionEntity entity = existing.get(permission.code());
            if (entity == null) {
                permissions.persist(PermissionEntity.of(permission, now));
                added++;
            } else if (entity.refresh(permission, now)) {
                changed++;
            }
        }

        Set<String> stale = new HashSet<>(existing.keySet());
        stale.removeAll(wanted);
        if (!stale.isEmpty()) {
            long revoked = roleQueries.deletePermissionsByCodes(stale);
            permissions.deleteAllByIdInBatch(stale);
            log.warn("카탈로그에서 사라진 권한 {}개를 지웠다(역할에서 {}건 뺌): {}", stale.size(), revoked, stale);
        }
        log.info("권한 카탈로그를 맞췄다: 운영자 권한 {}개 · 회원 권한 {}개 (새로 {} · 고침 {} · 지움 {})", Permission.values().length, MemberPermission.values().length,
                added, changed, stale.size());
    }

    private static void checkUnique(Set<String> seen, String code) {
        if (!seen.add(code)) {
            throw new IllegalStateException("권한 코드가 겹친다(운영자 권한과 회원 권한 포함): " + code);
        }
    }
}
