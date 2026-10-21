# 앱 이미지 — 환경(dev · prod)과 무관하게 한 벌이다. 환경은 실행 때 환경 변수로 갈린다.
#
# 빌드 전에 jar 를 만든다 (화면을 먼저 빌드해 static 에 넣은 뒤):
#   pnpm --filter user-web generate
#   ./gradlew :server:api:bootJar
#   docker build -t roboyam-api -f server/api/Dockerfile server/api
#
# 공개 사이트 jar 에는 shared 모듈 클래스가 함께 들어 있다(관리자 앱 클래스는 들어 있지 않다).
#
# eclipse-temurin 은 amd64 · arm64 를 모두 지원한다 (t4g = arm64).
FROM eclipse-temurin:25-jre

WORKDIR /app
COPY build/libs/roboyam-api.jar app.jar

# 컨테이너 안 포트는 8080 으로 고정한다. 바깥 포트는 compose 가 정한다
ENV SERVER_PORT=8080
ENV JAVA_OPTS=""
EXPOSE 8080

RUN useradd --system --no-create-home roboyam

# 올린 이미지(로고·사이트 아이콘·링크 미리보기 이미지) 저장 폴더 — 공개 사이트(api)와 관리자 앱(admin)이 같은 볼륨을 이 경로에 마운트한다(admin 이 쓰고 둘 다 /media/** 로 내보낸다).
# 빈 볼륨은 이 폴더의 소유자를 물려받으므로 실행 계정으로 맞춰 둔다
ENV ROBOYAM_STORAGE_PATH=/var/lib/roboyam/media
RUN mkdir -p /var/lib/roboyam/media && chown roboyam /var/lib/roboyam/media
USER roboyam

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
