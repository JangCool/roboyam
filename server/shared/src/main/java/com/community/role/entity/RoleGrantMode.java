package com.community.role.entity;

/** 부여 방식 — AUTO 자동(등급·가입·사업자 승인) · APPOINTED 지정(게시판별) · MANUAL 수동(운영자가 부여). 운영자 역할은 늘 MANUAL */
public enum RoleGrantMode {
    AUTO,
    APPOINTED,
    MANUAL
}
