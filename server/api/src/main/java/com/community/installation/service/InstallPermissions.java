package com.community.installation.service;

import com.community.installation.repository.master.TablePackRepository;
import com.community.permission.entity.MemberPermission;
import com.community.permission.entity.Permission;
import java.util.ArrayList;
import java.util.List;

/**
 * 설치 때 쓰는 코드의 권한 카탈로그 — 구조 스냅숏의 운영자 역할({@link PresetStructureApplier})과 데모 팩({@link DemoDataInstaller})이 권한 줄(role_permissions)을 넣기 전에
 * 권한 표(permissions)를 채운다. 권한 표는 관리자 서버가 뜰 때 채우므로 설치 직후(관리자 서버 전)엔 비어 있다.
 */
final class InstallPermissions {

    private InstallPermissions() {
    }

    /** 코드의 권한 카탈로그 — 관리자 서버 PermissionCatalogSynchronizer 가 표에 넣는 값과 같다(PermissionEntity.of) */
    static List<TablePackRepository.PermissionRow> catalog() {
        List<TablePackRepository.PermissionRow> rows = new ArrayList<>();
        for (Permission p : Permission.values()) {
            rows.add(new TablePackRepository.PermissionRow(p.code(), "OPERATOR", p.type().id(), p.unit().name(), p.featureReady()));
        }
        for (MemberPermission p : MemberPermission.values()) {
            rows.add(new TablePackRepository.PermissionRow(p.code(), "MEMBER", p.typeId(), p.group().name(), p.featureReady()));
        }
        return rows;
    }
}
