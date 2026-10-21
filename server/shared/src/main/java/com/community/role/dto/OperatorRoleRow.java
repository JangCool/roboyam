package com.community.role.dto;

/** 운영자가 받은 역할 한 줄(목록 조회용 행) */
public record OperatorRoleRow(long operatorId, String roleId, String roleName) {
}
