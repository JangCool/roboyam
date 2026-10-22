package com.community.team.dto.response;

import java.util.List;

/**
 * 팀 한 개(팀원 포함). {@code sortOrder} 는 1부터이고 화면 차례다. {@code usedByEventCount} 는 이 팀을 「추가 수신 팀」으로 쓰는 알림 이벤트 수(지울 때 확인 문구에 쓴다).
 */
public record TeamView(String teamId, String teamName, String description, int sortOrder, boolean enabled, List<TeamMemberView> members, int usedByEventCount) {
}
