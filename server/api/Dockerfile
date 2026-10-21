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
USER roboyam

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
