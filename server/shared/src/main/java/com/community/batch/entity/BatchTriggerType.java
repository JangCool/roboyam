package com.community.batch.entity;

/** 배치 실행을 시작한 방식 */
public enum BatchTriggerType {
    /** 예약(cron) */
    SCHEDULED,
    /** 운영자가 「지금 실행」 */
    MANUAL
}
