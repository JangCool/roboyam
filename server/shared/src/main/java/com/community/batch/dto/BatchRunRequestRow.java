package com.community.batch.dto;

import com.community.batch.entity.BatchRunRequestEntity;

/** 실행 요청 한 줄과 요청한 운영자 이름(운영자가 지워졌으면 null) */
public record BatchRunRequestRow(BatchRunRequestEntity request, String operatorName) {
}
