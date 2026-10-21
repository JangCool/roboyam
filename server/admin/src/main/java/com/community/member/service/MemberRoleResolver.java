package com.community.member.service;

import com.community.grade.entity.GradeEntity;
import com.community.grade.entity.GradeRoles;
import com.community.grade.repository.master.GradeRepositoryQDSL;
import com.community.member.dto.MemberRecord;
import com.community.member.dto.MemberRoles;
import com.community.member.dto.UserRoleRow;
import com.community.member.dto.response.MemberRoleDetail;
import com.community.member.repository.master.MemberRepositoryQDSL;
import com.community.role.entity.MemberRoleIds;
import com.community.role.entity.RoleEntity;
import com.community.role.entity.RoleTypes;
import com.community.role.repository.master.RoleRepository;
import com.community.user.entity.UserStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 회원들이 갖는 회원 역할을 모은다 — 직접 부여받은 역할({@code user_roles})에 <b>파생 역할</b>을 채워 넣는다: 회원 유형(사업자 회원 역할이 없으면 일반 회원), 등급 역할(프로필의 등급).
 * 순서는 회원 유형 → 등급 → 직접 부여받은 역할이고, 탈퇴한 회원은 역할을 모두 잃는다(빈 목록). 트랜잭션은 부르는 서비스가 연다.
 */
@Component
public class MemberRoleResolver {

    private final MemberRepositoryQDSL members;
    private final GradeRepositoryQDSL gradeQueries;
    private final RoleRepository roles;

    public MemberRoleResolver(MemberRepositoryQDSL members, GradeRepositoryQDSL gradeQueries, RoleRepository roles) {
        this.members = members;
        this.gradeQueries = gradeQueries;
        this.roles = roles;
    }

    /** 회원마다의 역할(키는 회원 ID) */
    public Map<Long, MemberRoles> resolve(List<MemberRecord> records) {
        Map<Long, List<UserRoleRow>> granted = members.userRolesOf(records.stream().map(MemberRecord::id).toList());
        Map<String, String> gradeLabels = new HashMap<>();
        for (GradeEntity grade : gradeQueries.findAllOrdered()) {
            gradeLabels.put(grade.getGradeKey(), grade.getLabel());
        }
        Map<String, String> kindNames = new HashMap<>();
        for (RoleEntity role : roles.findAllById(List.of(MemberRoleIds.GENERAL, MemberRoleIds.BUSINESS))) {
            kindNames.put(role.getRoleId(), role.getRoleName());
        }

        Map<Long, MemberRoles> resolved = new HashMap<>();
        for (MemberRecord record : records) {
            List<UserRoleRow> rows = granted.getOrDefault(record.id(), List.of());
            boolean business = rows.stream().anyMatch(row -> MemberRoleIds.BUSINESS.equals(row.roleId()));
            boolean manager = rows.stream().anyMatch(row -> MemberRoleIds.BOARD_MANAGER.equals(row.roleId()));
            boolean staff = rows.stream().anyMatch(row -> MemberRoleIds.BOARD_STAFF.equals(row.roleId()));
            String boardRole = record.status() == UserStatus.WITHDRAWN ? "NONE" : manager ? "MANAGER" : staff ? "STAFF" : "NONE";

            List<MemberRoleDetail> list = new ArrayList<>();
            if (record.status() != UserStatus.WITHDRAWN) {
                String kindId = business ? MemberRoleIds.BUSINESS : MemberRoleIds.GENERAL;
                list.add(new MemberRoleDetail(kindId, kindNames.getOrDefault(kindId, kindId), RoleTypes.KIND, "AUTO", List.of()));
                String gradeRoleId = GradeRoles.roleIdOf(record.gradeKey());
                list.add(new MemberRoleDetail(gradeRoleId, gradeLabels.getOrDefault(record.gradeKey(), record.gradeKey()), RoleTypes.GRADE, "AUTO", List.of()));
                for (UserRoleRow row : rows) {
                    if (RoleTypes.KIND.equals(row.roleTypeId()) || RoleTypes.GRADE.equals(row.roleTypeId())) {
                        continue; // 회원 유형·등급은 위에서 파생해 넣었다
                    }
                    list.add(new MemberRoleDetail(row.roleId(), row.roleName(), row.roleTypeId(), row.grantMode(), List.of()));
                }
            }
            resolved.put(record.id(), new MemberRoles(list, business ? "BUSINESS" : "GENERAL", boardRole));
        }
        return resolved;
    }
}
