package com.community.profile.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.repository.master.AuditLogRepositoryQDSL;
import com.community.adminauth.service.UserAgentSummary;
import com.community.permission.dto.AdminActor;
import com.community.profile.dto.response.LoginHistoryItem;
import com.community.profile.dto.response.LoginHistoryResponse;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 내 로그인 이력 — 감사 기록({@code audit_logs})에서 <b>내 계정의</b> 로그인 성공·실패(최근 20건)를 읽는다. 별도 이력 표를 두지 않는다.
 *
 * <ul>
 *   <li>대상은 요청한 사람({@link AdminActor})의 ID 뿐이라 남의 이력을 읽을 수 없다. 내 계정을 노린 실패 시도(다른 사람이 틀린 비밀번호로 시도)도 내 이력에 나온다.</li>
 *   <li>없는 이메일로 시도한 실패는 계정을 특정할 수 없어 어느 이력에도 나오지 않는다. 로그인 잠금(10분) 중의 시도는 감사 기록으로 남지 않아 나오지 않는다.</li>
 *   <li>실패 사유는 밖으로 구분해 알리지 않는다 — 실패는 모두 {@code INVALID_CREDENTIALS}(로그인 응답과 같은 정책). 이메일·비밀번호·기록의 {@code detail} 은 내보내지 않는다.</li>
 *   <li>{@code agent} 는 로그인 감사 기록 {@code detail.agent}(User-Agent 를 {@code UserAgentSummary} 로 요약한 {@code 브라우저 / 운영체제 / 기기} 글자, 원문 아님)다. UA 를 남기기 전의 옛 기록·UA 가 없던 요청은 null.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class LoginHistoryService {

    /** 보여 주는 건수 — 프론트 목업의 「최근 20건 고정」과 같다 */
    public static final int LIMIT = 20;

    private static final JsonMapper MAPPER = JsonMapper.builder().build();
    /** 내보내는 글자 길이 상한 — 기록은 이미 80자 이하이고, 이것은 방어용이다 */
    private static final int AGENT_MAX = 200;

    private final AuditLogRepositoryQDSL audits;

    public LoginHistoryService(AuditLogRepositoryQDSL audits) {
        this.audits = audits;
    }

    public LoginHistoryResponse recent(AdminActor actor) {
        List<LoginHistoryItem> items = audits.recentLogins(actor.operatorId(), LIMIT).stream().map(row -> {
            boolean ok = AuditActions.LOGIN_SUCCESS.equals(row.action());
            return new LoginHistoryItem(Long.toString(row.id()), row.occurredAt(), row.ip(), agentOf(row.detail()), ok ? "OK" : "FAIL", ok ? null : "INVALID_CREDENTIALS");
        }).toList();
        return new LoginHistoryResponse(items);
    }

    /** 기록의 {@code detail.agent}(로그인이 남긴 정제된 요약). 없거나 읽을 수 없는 옛 기록은 null — 화면이 「알 수 없음」으로 보인다 */
    @SuppressWarnings("unchecked")
    private static String agentOf(String detail) {
        if (detail == null || detail.isBlank()) {
            return null;
        }
        try {
            Object parsed = MAPPER.readValue(detail, Map.class);
            Object agent = parsed instanceof Map<?, ?> map ? ((Map<String, Object>) map).get(UserAgentSummary.DETAIL_KEY) : null;
            return agent instanceof String text && !text.isBlank() ? (text.length() > AGENT_MAX ? text.substring(0, AGENT_MAX) : text) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
