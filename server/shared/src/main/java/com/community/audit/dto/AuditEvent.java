package com.community.audit.dto;

import com.community.audit.entity.AuditActorType;
import com.community.audit.entity.AuditResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 감사 기록으로 남길 일 하나. <b>비밀번호·토큰은 {@code detail} 에도 넣지 않는다</b> — 호출하는 쪽이 지킨다.
 *
 * @param actorId    행위자 운영자 ID(회원 행위자 {@link AuditActorType#MEMBER} 면 회원 ID). 로그인하지 않은 행위(로그인 실패 등)는 null
 * @param targetType 대상 종류(예: {@code ROLE} · {@code OPERATOR}). 없으면 null
 * @param targetId   대상 식별자(문자열). 없으면 null
 * @param ip         요청자 IP. 요청 밖의 일이면 null
 * @param detail     덧붙일 사실(권한 {@code granted}/{@code revoked}, 상태 전후 …). 비어 있으면 저장하지 않는다
 */
public record AuditEvent(AuditActorType actorType, Long actorId, String action, AuditResult result, String targetType, String targetId, String ip,
                         Map<String, Object> detail) {

    public AuditEvent {
        // 값이 null 인 사실도 받는다(「전에는 없었다」 — 예: 처음 넣은 오픈소스 안내 문구의 from). Map.copyOf 는 null 값에 NullPointerException 을 던져
        // 감사 기록을 남기려던 저장이 500 이 됐다(관리자 QA 2026-11-17 F-5). JSON 에는 null 로 남는다
        detail = detail == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(detail));
    }

    /** 로그인한 운영자가 한 일 */
    public static AuditEvent byOperator(long actorId, String ip, String action, AuditResult result) {
        return new AuditEvent(AuditActorType.OPERATOR, actorId, action, result, null, null, ip, Map.of());
    }

    /** 로그인한 회원이 공개 사이트에서 한 운영 일(게시판 매니저·스텝의 핫딜 종료·재개) — {@code actorId} = 회원 ID */
    public static AuditEvent byMember(long memberId, String ip, String action, AuditResult result) {
        return new AuditEvent(AuditActorType.MEMBER, memberId, action, result, null, null, ip, Map.of());
    }

    /** 로그인하지 않은 사람의 일(로그인 실패 등) */
    public static AuditEvent anonymous(String ip, String action, AuditResult result) {
        return new AuditEvent(AuditActorType.ANONYMOUS, null, action, result, null, null, ip, Map.of());
    }

    public AuditEvent target(String type, String id) {
        return new AuditEvent(actorType, actorId, action, result, type, id, ip, detail);
    }

    /** 덧붙일 사실을 더한다(같은 이름은 덮어쓴다). 넣은 차례를 지키고 값이 null 이어도 된다 */
    public AuditEvent with(String name, Object value) {
        Map<String, Object> next = new LinkedHashMap<>(detail);
        next.put(name, value);
        return new AuditEvent(actorType, actorId, action, result, targetType, targetId, ip, next);
    }

    /** 행위자를 바꾼다(로그인 성공처럼 행위자가 요청 도중에 정해지는 경우) */
    public AuditEvent actor(long operatorId) {
        return new AuditEvent(AuditActorType.OPERATOR, operatorId, action, result, targetType, targetId, ip, detail);
    }
}
