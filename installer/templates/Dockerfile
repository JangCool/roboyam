# roboyam-install.sh 가 jar 로 앱 이미지를 만들 때 쓴다(server/api/Dockerfile 과 같은 꼴 + 이미지 폴더).
#
# 빌드 컨텍스트는 설치 폴더의 app/ 이고, 거기에 roboyam-api.jar 가 들어 있다.
# 베이스 이미지는 --base-image 로 바꿀 수 있다(오프라인 · 사설 레지스트리). 환경(prod 등)은 이미지에 들어 있지 않고
# 실행할 때 환경 변수로 갈린다.
ARG BASE_IMAGE=eclipse-temurin:25-jre
FROM ${BASE_IMAGE}

WORKDIR /app
COPY roboyam-api.jar app.jar

# 컨테이너 안 포트는 8080 으로 고정한다. 바깥 포트는 compose 가 정한다
ENV SERVER_PORT=8080
ENV JAVA_OPTS=""
EXPOSE 8080

# 실행 계정(root 가 아니게). Debian/Ubuntu 계열은 useradd, Alpine 은 adduser
RUN (useradd --system --uid 10001 --no-create-home roboyam 2>/dev/null \
      || adduser -S -u 10001 -H roboyam)
USER 10001

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
