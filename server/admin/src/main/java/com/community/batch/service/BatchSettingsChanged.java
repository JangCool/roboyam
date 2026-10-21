package com.community.batch.service;

/** 배치의 사용 여부나 cron 이 바뀌어 커밋됐다 — 이 서버의 예약을 곧바로 맞춘다(다른 서버는 주기적으로 DB 를 다시 읽어 따라온다) */
public record BatchSettingsChanged(String jobKey) {
}
