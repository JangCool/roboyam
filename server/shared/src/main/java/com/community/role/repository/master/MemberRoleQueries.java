package com.community.role.repository.master;

import com.community.grade.entity.GradeRoles;
import com.community.role.entity.MemberRoleIds;
import java.sql.Array;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 회원 한 명이 가진 회원 역할 권한의 합집합 — 공개 게시판의 글쓰기·댓글·추천·신고 판정이 「역할 권한」 쪽을 볼 때 쓴다(docs/domain/operator-permissions.md 「회원 역할」).
 *
 * <p>역할: 일반 회원(사업자 회원이 아니면 모두 — 회원마다 행이 없다) + 등급 역할 {@code ROLE_GRADE_<등급 키>} + {@code user_roles} 의 사이트 범위 역할(사용자 정의·사업자).
 * 게시판 범위 역할(매니저·스텝)은 게시판 운영 권한만 가져 이용 권한 판정에 넣지 않는다.
 *
 * <p>native SQL 인 이유: 사업자 여부·내장 역할 유무·권한 목록을 배열 한 줄로 한 번에 읽는다(요청마다 부르므로 왕복을 줄인다).
 */
@Repository
public class MemberRoleQueries {

    private final JdbcTemplate jdbc;

    public MemberRoleQueries(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    /**
     * @param business    사업자 회원 역할이 있다
     * @param permissions 권한 코드의 합집합. null = 내장 회원 역할이 아직 없다(관리자 서버가 처음 뜰 때 시드한다) — 그때는 제한하지 않는다
     */
    public record MemberRoles(boolean business, Set<String> permissions) {
    }

    public MemberRoles of(long userId, String gradeKey) {
        String gradeRole = gradeKey == null ? "" : GradeRoles.roleIdOf(gradeKey);
        return jdbc.queryForObject("""
                WITH ur AS (SELECT role_id FROM user_roles WHERE user_id = ? AND board_id IS NULL),
                     biz AS (SELECT EXISTS (SELECT 1 FROM ur WHERE role_id = ?) AS b)
                SELECT (SELECT b FROM biz) AS business,
                       EXISTS (SELECT 1 FROM roles WHERE role_id = ?) AS seeded,
                       ARRAY(SELECT DISTINCT permission_code FROM role_permissions
                              WHERE role_id IN (SELECT role_id FROM ur) OR role_id = ? OR (role_id = ? AND NOT (SELECT b FROM biz))) AS perms
                """, (rs, i) -> {
            boolean business = rs.getBoolean("business");
            if (!rs.getBoolean("seeded")) {
                return new MemberRoles(business, null);
            }
            Array array = rs.getArray("perms");
            Object[] values = array == null ? new Object[0] : (Object[]) array.getArray();
            return new MemberRoles(business, Arrays.stream(values).map(String::valueOf).collect(Collectors.toUnmodifiableSet()));
        }, userId, MemberRoleIds.BUSINESS, MemberRoleIds.GENERAL, gradeRole, MemberRoleIds.GENERAL);
    }
}
