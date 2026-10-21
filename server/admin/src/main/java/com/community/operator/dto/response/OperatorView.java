package com.community.operator.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

/**
 * 운영자 한 명 — admin-web 의 {@code OperatorView} 모양이다. {@code operatorId} 는 <b>문자열</b>(Snowflake)이다. 비밀번호 해시는 싣지 않는다.
 *
 * @param isSuper   최고 관리자(루트 계정) — 역할·상태·기간을 바꿀 수 없다. 역할은 비어 있다
 * @param periodOver 기간이 끝났나(또는 아직 시작 전인가) — 표시({@code expired})와 별개로 지금 시각으로 판정한 값
 */
public record OperatorView(String operatorId, String email, String operatorName, @JsonProperty("isSuper") boolean isSuper, List<OperatorRoleRef> roles, Instant accountStartAt,
                           Instant accountEndAt, boolean approved, boolean locked, boolean expired, boolean enabled, boolean periodOver, Instant createdAt) {
}
