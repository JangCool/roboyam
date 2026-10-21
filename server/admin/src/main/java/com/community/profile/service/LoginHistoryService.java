package com.community.profile.service;

import com.community.audit.dto.AuditActions;
import com.community.audit.repository.master.AuditLogRepositoryQDSL;
import com.community.permission.dto.AdminActor;
import com.community.profile.dto.response.LoginHistoryItem;
import com.community.profile.dto.response.LoginHistoryResponse;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내 로그인 이력 — 감사 기록({@code audit_logs})에서 <b>내 계정의</b> 로그인 성공·실패(최근 20건)를 읽는다. 별도 이력 표를 두지 않는다.
 *
 * <ul>
 *   <li>대상은 요청한 사람({@link AdminActor})의 ID 뿐이라 남의 이력을 읽을 수 없다. 내 계정을 노린 실패 시도(다른 사람이 틀린 비밀번호로 시도)도 내 이력에 나온다.</li>
 *   <li>없는 이메일로 시도한 실패는 계정을 특정할 수 없어 어느 이력에도 나오지 않는다. 로그인 잠금(10분) 중의 시도는 감사 기록으로 남지 않아 나오지 않는다.</li>
 *   <li>실패 사유는 밖으로 구분해 알리지 않는다 — 실패는 모두 {@code INVALID_CREDENTIALS}(로그인 응답과 같은 정책). 이메일·비밀번호·기록의 {@code detail} 은 내보내지 않는다.</li>
 *   <li>User-Agent 는 지금 기록에 남기지 않아 {@code agent} 는 null 이다(후속: 로그인 감사 기록에 UA 를 덧붙이면 채워진다).</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class LoginHistoryService {

    /** 보여 주는 건수 — 프론트 목업의 「최근 20건 고정」과 같다 */
    public static final int LIMIT = 20;

    private final AuditLogRepositoryQDSL audits;

    public LoginHistoryService(AuditLogRepositoryQDSL audits) {
        this.audits = audits;
    }

    public LoginHistoryResponse recent(AdminActor actor) {
        List<LoginHistoryItem> items = audits.recentLogins(actor.operatorId(), LIMIT).stream().map(row -> {
            boolean ok = AuditActions.LOGIN_SUCCESS.equals(row.action());
            return new LoginHistoryItem(Long.toString(row.id()), row.occurredAt(), row.ip(), null, ok ? "OK" : "FAIL", ok ? null : "INVALID_CREDENTIALS");
        }).toList();
        return new LoginHistoryResponse(items);
    }
}
