package com.community.batch.dto;

import com.community.batch.entity.BatchRunEntity;

/** 실행 로그 한 줄과 수동 실행한 운영자 이름(예약 실행·운영자가 지워진 경우는 null) */
public record BatchRunRow(BatchRunEntity run, String operatorName) {
}
