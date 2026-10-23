#!/usr/bin/env bash
# =============================================================================
# make-package.sh — 설치 패키지(tar.gz)를 만든다
# =============================================================================
#
# 설치기·템플릿·앱 jar·사용 설명서를 한 묶음으로 만든다. 받은 서버에서 풀어 바로 쓴다.
#
#   installer/dist/roboyam-installer-<버전>.tar.gz
#     roboyam-installer-<버전>/
#       roboyam-install.sh  roboyam-uninstall.sh  roboyam-mfa-reset.sh   (mfa-reset = 관리자 2차 인증 OTP 초기화 명령 — 서버 접근자 전용)
#       lib/common.sh  templates/{compose.yml,Dockerfile,postgres/{Dockerfile,init-pg-cron.sh}}   (postgres/ = pg_cron 이 든 DB 이미지의 빌드 파일)
#       app/roboyam-api.jar        (앱 jar — 화면 포함. 먼저 pnpm --filter user-web generate → ./gradlew :server:api:bootJar)
#       README.md                    (설치 안내)
#       VERSION
#
# 사용법
#   installer/make-package.sh                       버전은 gradle.properties 의 version
#   installer/make-package.sh --version 1.0.0 --jar /path/to/roboyam-api.jar --out /tmp/pkg
#   installer/make-package.sh --no-jar              jar 없이(설치할 때 --jar/--image 를 준다)
#
# 옵션
#   --version <v>   패키지 버전(기본 gradle.properties 의 version)
#   --jar <경로>    앱 jar(기본 server/api/build/libs/roboyam-api.jar)
#   --out <폴더>    산출물 폴더(기본 installer/dist)
#   --no-jar        jar 를 넣지 않는다
#   --help
# =============================================================================
set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPO_ROOT=$(cd "$SCRIPT_DIR/.." && pwd)

VERSION=""
JAR="$REPO_ROOT/server/api/build/libs/roboyam-api.jar"
OUT="$SCRIPT_DIR/dist"
NO_JAR=0

usage() { sed -n '/^# 사용법/,/^# ====/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }
die() { printf '%s\n' "오류: $*" >&2; exit 1; }
need_arg() { if [ $# -lt 2 ] || [ -z "$2" ]; then die "$1 에 값이 필요합니다"; fi; }

while [ $# -gt 0 ]; do
    case "$1" in
        --version) need_arg "$@"; VERSION=$2; shift 2 ;;
        --jar)     need_arg "$@"; JAR=$2; shift 2 ;;
        --out)     need_arg "$@"; OUT=$2; shift 2 ;;
        --no-jar)  NO_JAR=1; shift ;;
        --help|-h) usage; exit 0 ;;
        *)         die "알 수 없는 옵션: $1 (--help)" ;;
    esac
done

if [ -z "$VERSION" ]; then
    VERSION=$(sed -n 's/^version=//p' "$REPO_ROOT/gradle.properties" 2> /dev/null | head -1 | tr -d '\r')
    [ -n "$VERSION" ] || die "버전을 알 수 없습니다. --version 으로 지정하세요."
fi
case "$VERSION" in
    *[!A-Za-z0-9._-]*|"") die "버전에는 영숫자 . _ - 만 쓸 수 있습니다: $VERSION" ;;
esac

for f in roboyam-install.sh roboyam-uninstall.sh roboyam-mfa-reset.sh lib/common.sh templates/compose.yml templates/Dockerfile templates/postgres/Dockerfile templates/postgres/init-pg-cron.sh; do
    [ -f "$SCRIPT_DIR/$f" ] || die "필요한 파일이 없습니다: $SCRIPT_DIR/$f"
done
for f in roboyam-install.sh roboyam-uninstall.sh roboyam-mfa-reset.sh lib/common.sh; do
    bash -n "$SCRIPT_DIR/$f" || die "문법 오류: $f"
done
if [ "$NO_JAR" != 1 ]; then
    [ -f "$JAR" ] || die "앱 jar 가 없습니다: $JAR (화면을 먼저 빌드하고 ./gradlew :server:api:bootJar, 또는 --no-jar)"
    [ "$(head -c 2 "$JAR")" = "PK" ] || die "jar 가 아닙니다: $JAR"
fi

NAME="roboyam-installer-$VERSION"
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
PKG="$STAGE/$NAME"
mkdir -p "$PKG/lib" "$PKG/templates/postgres"

cp "$SCRIPT_DIR/roboyam-install.sh" "$SCRIPT_DIR/roboyam-uninstall.sh" "$SCRIPT_DIR/roboyam-mfa-reset.sh" "$PKG/"
cp "$SCRIPT_DIR/lib/common.sh" "$PKG/lib/"
cp "$SCRIPT_DIR/templates/compose.yml" "$SCRIPT_DIR/templates/Dockerfile" "$PKG/templates/"
cp "$SCRIPT_DIR/templates/postgres/Dockerfile" "$SCRIPT_DIR/templates/postgres/init-pg-cron.sh" "$PKG/templates/postgres/"
if [ -f "$REPO_ROOT/docs/install-script.md" ]; then
    cp "$REPO_ROOT/docs/install-script.md" "$PKG/README.md"
else
    printf '%s\n' "사용법: ./roboyam-install.sh --help" > "$PKG/README.md"
fi
if [ "$NO_JAR" != 1 ]; then
    mkdir -p "$PKG/app"
    cp "$JAR" "$PKG/app/roboyam-api.jar"
fi
printf '%s\n' "$VERSION" > "$PKG/VERSION"
chmod +x "$PKG/roboyam-install.sh" "$PKG/roboyam-uninstall.sh" "$PKG/roboyam-mfa-reset.sh"

mkdir -p "$OUT"
TARBALL="$OUT/$NAME.tar.gz"
( cd "$STAGE" && tar -czf "$STAGE/$NAME.tar.gz" "$NAME" )
mv "$STAGE/$NAME.tar.gz" "$TARBALL"

SUM=""
if command -v sha256sum > /dev/null 2>&1; then
    SUM=$(sha256sum "$TARBALL" | awk '{ print $1 }')
elif command -v shasum > /dev/null 2>&1; then
    SUM=$(shasum -a 256 "$TARBALL" | awk '{ print $1 }')
fi
[ -z "$SUM" ] || printf '%s  %s\n' "$SUM" "$NAME.tar.gz" > "$TARBALL.sha256"

SIZE=$(wc -c < "$TARBALL" | tr -d ' ')
printf '%s\n' "패키지를 만들었습니다: $TARBALL ($((SIZE / 1024 / 1024))MB)"
[ -z "$SUM" ] || printf '%s\n' "sha256: $SUM"
printf '%s\n' "서버에서: tar xzf $NAME.tar.gz && cd $NAME && ./roboyam-install.sh --dir /opt/roboyam"
