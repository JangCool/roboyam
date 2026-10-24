package com.community.nicknameimage.repository.master;

import com.community.grade.entity.GradeRoles;
import com.community.role.entity.MemberRoleIds;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 닉네임 그림 신청 자격의 「필요한 등급」 계산에 쓰는 읽기 — 회원 역할 권한 {@code nickname-image.request} 를 가진 역할(일반 회원 · 등급 역할 {@code ROLE_GRADE_<등급 키>}).
 * native SQL 인 이유: 역할 아이디 머리로 등급 역할만 골라 한 번에 읽는다(요청마다 한 번).
 */
@Repository
public class NicknameImageQueries {

    private final JdbcTemplate jdbc;

    public NicknameImageQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * @param generalGrants 일반 회원 역할이 그 권한을 준다(사업자가 아닌 회원은 등급과 상관없이 갖는다)
     * @param gradeKeys     그 권한을 주는 등급 역할의 등급 키
     */
    public record Grants(boolean generalGrants, Set<String> gradeKeys) {
    }

    public Grants grants(String permissionCode) {
        List<String> roles = jdbc.queryForList("select role_id from role_permissions where permission_code = ? and (role_id = ? or role_id like ?)",
                String.class, permissionCode, MemberRoleIds.GENERAL, GradeRoles.PREFIX + "%");
        Set<String> grades = new HashSet<>();
        boolean general = false;
        for (String role : roles) {
            if (MemberRoleIds.GENERAL.equals(role)) {
                general = true;
            } else {
                GradeRoles.gradeKeyOf(role).ifPresent(grades::add);
            }
        }
        return new Grants(general, grades);
    }
}
