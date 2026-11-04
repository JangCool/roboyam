package com.community.sanction.dto;

import java.util.function.Consumer;

/**
 * 제재를 다루는 운영자 — 요청 내용에 따라 갈리는 권한(경고·기간 정지 {@code sanction.manage} · 영구 정지·단계 건너뛰기·영구 정지 해제 {@code sanction.ban})은 서비스가 본다.
 *
 * @param deny 권한이 모자랄 때 부른다 — 받은 권한 코드로 거부 기록을 남기고 <b>반드시 던진다</b>(관리자 서버의 {@code 403 PERMISSION_DENIED})
 */
public record SanctionOperator(long operatorId, String ip, boolean canManage, boolean canBan, Consumer<String> deny) {
}
