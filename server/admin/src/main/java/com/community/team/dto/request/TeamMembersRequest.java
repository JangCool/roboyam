package com.community.team.dto.request;

import java.util.List;

/** 팀원 전체 교체 — 운영자 id 목록(Snowflake, 문자열). 빈 목록이면 팀원을 모두 뺀다. 목록 자체가 없으면 400 {@code fields.operatorIds} */
public record TeamMembersRequest(List<String> operatorIds) {
}
