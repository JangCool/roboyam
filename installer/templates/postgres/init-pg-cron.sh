#!/bin/sh
# 처음 만드는 DB 에서만 실행된다(공식 이미지의 /docker-entrypoint-initdb.d 규칙). POSTGRES_USER(superuser)로 POSTGRES_DB 에 pg_cron 을 만든다.
# pg_cron 은 cron.database_name 으로 정한 DB 하나에서만 돌므로 그 DB 와 POSTGRES_DB 가 같아야 한다(compose 가 같은 값을 준다).
# 이미 있던 DB(볼륨)에는 실행되지 않는다 — 그 경우는 서버(Flyway V20)가 앱 계정으로 확장을 만든다. 확장을 못 만들어도 DB 를 못 쓰게 하지 않는다(ON_ERROR_STOP=0).
psql -v ON_ERROR_STOP=0 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE EXTENSION IF NOT EXISTS pg_cron;
SQL
exit 0
