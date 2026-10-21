package com.community.audit.dto;

import com.community.audit.entity.AuditResult;
import java.time.Instant;
import java.util.Set;

/**
 * 감사 로그 조회 조건. 비어 있는(null) 조건은 걸지 않는다.
 *
 * @param from        발생 시각의 하한(포함)
 * @param to          발생 시각의 상한(포함)
 * @param actorId     행위자 ID 와 같은 것(검색어가 숫자일 때)
 * @param actorName   행위자 이름의 일부(대소문자 무시). 이메일은 맞추지 않는다
 * @param action      작업 이름과 같은 것
 * @param targetType  대상 종류와 같은 것
 * @param target      대상 ID·이름의 일부(대소문자 무시)
 * @param batchKeys   {@code target} 이 이름에 들어 있는 배치의 키(배치 이름은 코드·번들에 있어 서비스가 찾아 넘긴다)
 * @param result      결과
 * @param page        0 부터
 */
public record AuditSearchCriteria(Instant from, Instant to, Long actorId, String actorName, String action, String targetType, String target, Set<String> batchKeys,
                                  AuditResult result, int page, int size) {
}
