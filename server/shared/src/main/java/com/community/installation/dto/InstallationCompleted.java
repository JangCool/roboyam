package com.community.installation.dto;

/**
 * 설치가 끝났다는 알림. 설치 트랜잭션이 <b>커밋된 뒤에만</b> 듣는 쪽({@code InstallationState})이 반영한다 — 롤백되면 아무도 받지 못한다.
 */
public record InstallationCompleted(long rootUserId) {
}
