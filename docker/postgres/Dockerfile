# ROBOYAM PostgreSQL 이미지 — 공식 postgres 이미지에 pg_cron 만 더한 얇은 이미지다.
#
# 왜: DB 배치(저장 프로시저 + pg_cron)를 쓰려면 pg_cron 확장이 필요한데 공식 이미지에는 없다. 패키지(postgresql-<메이저>-cron)는 공식 이미지에 이미 설정된
#     PostgreSQL 공식 apt 저장소(pgdg)에서 받는다. 이 패키지 말고 달라지는 것은 없다 — 데이터 위치·로캘·환경 변수는 공식 이미지 그대로다.
# 설정: 이미지에는 서버 설정을 넣지 않는다. compose 의 command 가 shared_preload_libraries=pg_cron · cron.database_name · cron.timezone 을 준다
#       (docker/local · docker/dev · installer/templates/compose.yml). 이 설정이 없으면 확장이 있어도 DB 배치는 「사용 불가」로 보인다 — 서버는 그대로 뜬다.
# 확장 생성: 처음 만드는 DB 는 init 스크립트가 CREATE EXTENSION 한다. 이미 있던 DB 는 서버(Flyway V20)가 확장을 만든다(앱 계정이 superuser 일 때) —
#           아니면 docs/features/batch-contract.md 「DB 배치」의 업그레이드 절차를 따른다.
#
# 빌드:  docker build -t roboyam-postgres:18 docker/postgres
# (installer/templates/postgres/ 는 이 폴더의 사본이다 — 설치 패키지가 docker/ 를 가지고 다니지 않아서 둔다. 고치면 둘을 같이 고친다.)
ARG PG_MAJOR=18
FROM postgres:${PG_MAJOR}
ARG PG_MAJOR
RUN apt-get update \
    && apt-get install -y --no-install-recommends "postgresql-${PG_MAJOR}-cron" \
    && rm -rf /var/lib/apt/lists/*
COPY init-pg-cron.sh /docker-entrypoint-initdb.d/10-pg-cron.sh
