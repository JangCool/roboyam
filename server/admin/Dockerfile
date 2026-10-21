# 관리자 앱 이미지 — 공개 사이트 이미지(server/api/Dockerfile)와 마찬가지로 환경(dev · prod)과 무관하게 한 벌이다.
# 환경은 실행 때 환경 변수로 갈린다.
#
# 빌드 전에 jar 를 만든다 (관리자 화면을 먼저 빌드해 static 에 넣은 뒤):
#   pnpm --filter admin-web generate
#   ./gradlew :server:admin:bootJar
#   docker build -t roboyam-admin -f server/admin/Dockerfile server/admin
#
# 관리자 앱은 공개 사이트와 같이 쓰는 서비스를 shared 모듈에서 라이브러리로 쓰므로 jar 안에 shared 클래스가 들어 있다(api 는 들어 있지 않다).
FROM eclipse-temurin:25-jre

WORKDIR /app
COPY build/libs/roboyam-admin.jar app.jar

# 컨테이너 안 포트는 8080 으로 고정한다. 바깥 포트는 compose 가 정한다
ENV SERVER_PORT=8080
ENV JAVA_OPTS=""
EXPOSE 8080

RUN useradd --system --no-create-home roboyam
USER roboyam

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
