package com.community.role;

import static org.assertj.core.api.Assertions.assertThat;

import com.community.commerce.Commerce04AdminTestBase;
import com.community.permission.entity.MemberPermission;
import com.community.role.entity.MemberRoleIds;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 내장 회원 역할 {@code ROLE_MEMBER_BUSINESS} = 「홍보 전용 계정」(사용자 결정 2026-10-31) — 새 설치는 시더가 새 이름·설명으로 넣고(권한 묶음은 그대로 신고만),
 * 이미 설치된 곳은 V113 이 <b>원래 시드 글자 그대로인 칸만</b> 바꾼다(운영자가 고친 이름·설명은 그대로). 실제 관리자 앱 문맥(기동 때 시더가 돈다) + PostgreSQL.
 */
class PromoAccountRoleNameTest extends Commerce04AdminTestBase {

    private static final String NAME = "홍보 전용 계정";
    private static final String DESCRIPTION = "따로 가입한 홍보용 계정 — 일반 활동이 제한됩니다(1차에는 가입 길이 없어 붙는 회원 없음)";
    private static final String OLD_NAME = "사업자 회원";
    private static final String OLD_DESCRIPTION = "사업자 승인을 받은 회원입니다. 읽기·프로필·허용 게시판 위주로 제한됩니다.";

    private Map<String, Object> row() {
        return jdbc.queryForMap("SELECT role_name, description FROM roles WHERE role_id = ?", MemberRoleIds.BUSINESS);
    }

    private void set(String name, String description) {
        jdbc.update("UPDATE roles SET role_name = ?, description = ? WHERE role_id = ?", name, description, MemberRoleIds.BUSINESS);
    }

    private void runV113() throws IOException {
        String sql = new ClassPathResource("db/migration/shared/V113__promo_account_role_name.sql").getContentAsString(StandardCharsets.UTF_8);
        jdbc.execute(sql);
    }

    @Test
    void 시더가_넣은_역할은_홍보_전용_계정이고_권한은_신고만() {
        Map<String, Object> role = row();
        assertThat(role.get("role_name")).isEqualTo(NAME);
        assertThat(role.get("description")).isEqualTo(DESCRIPTION);
        List<String> permissions = jdbc.queryForList("SELECT permission_code FROM role_permissions WHERE role_id = ?", String.class, MemberRoleIds.BUSINESS);
        assertThat(permissions).containsExactly(MemberPermission.REPORT_CREATE.code());
    }

    @Test
    void V113_은_원래_시드_글자인_칸만_바꾸고_운영자가_고친_칸은_두고_다시_돌아도_같다() throws IOException {
        try {
            // 이미 설치된 곳 — 예전 시드 그대로
            set(OLD_NAME, OLD_DESCRIPTION);
            runV113();
            assertThat(row()).containsEntry("role_name", NAME).containsEntry("description", DESCRIPTION);
            runV113(); // 다시 돌아도 바뀌는 것이 없다
            assertThat(row()).containsEntry("role_name", NAME).containsEntry("description", DESCRIPTION);

            // 운영자가 이름을 고친 곳 — 이름은 두고 예전 설명만 바꾼다
            set("우리 홍보 계정", OLD_DESCRIPTION);
            runV113();
            assertThat(row()).containsEntry("role_name", "우리 홍보 계정").containsEntry("description", DESCRIPTION);

            // 운영자가 설명을 고친 곳 — 설명은 두고 예전 이름만 바꾼다
            set(OLD_NAME, "운영자가 쓴 설명");
            runV113();
            assertThat(row()).containsEntry("role_name", NAME).containsEntry("description", "운영자가 쓴 설명");
        } finally {
            set(NAME, DESCRIPTION);
        }
    }
}
