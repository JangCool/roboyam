#!/usr/bin/env bash
# ============================================================================
# 원스톱 설치 묶음 만들기 — 로컬(개발 PC, Git Bash 가능)에서 실행한다
#
# 만드는 것: installer-native/dist/roboyam-native-<커밋>-<날짜>.tar.gz
#   roboyam-native/
#     roboyam-install.sh · install.conf.example · README.md · PACKAGE_INFO
#     lib/ · db/ · app/
#     application/roboyam-api.jar · roboyam-admin.jar
#     certs/            ← Origin CA 인증서를 DB 서버에서 여기에 넣는다(묶음에는 비어 있다)
#
# 사용법
#   installer-native/make-package.sh            이미 빌드된 jar 로 묶는다
#   installer-native/make-package.sh --build    화면 generate + bootJar 까지 한 뒤 묶는다
#
# 다음: scp dist/roboyam-native-*.tar.gz → DB 서버, 풀고 sudo ./roboyam-install.sh (README.md)
# ============================================================================
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
# shellcheck source=lib/common.sh
. "${SCRIPT_DIR}/lib/common.sh"
trap on_error ERR

BUILD=0
case "${1:-}" in
    --build) BUILD=1 ;;
    -h|--help) usage_from_header "$0"; exit 0 ;;
    "") ;;
    *) fail "알 수 없는 옵션: $1" ;;
esac

# 다른 곳의 jar 를 묶으려면 ROBOYAM_API_JAR · ROBOYAM_ADMIN_JAR 로 준다
API_JAR="${ROBOYAM_API_JAR:-${REPO_DIR}/server/api/build/libs/roboyam-api.jar}"
ADMIN_JAR="${ROBOYAM_ADMIN_JAR:-${REPO_DIR}/server/admin/build/libs/roboyam-admin.jar}"

cd "$REPO_DIR"

if [ "$BUILD" -eq 1 ]; then
    step "빌드 (화면 → jar)"
    pnpm --filter user-web generate
    ./gradlew :server:api:bootJar
    pnpm --filter admin-web generate
    ./gradlew :server:admin:bootJar
fi

step "jar 확인"
[ -f "$API_JAR" ] || fail "없습니다: ${API_JAR} (--build 로 만들거나 pnpm --filter user-web generate && ./gradlew :server:api:bootJar)"
[ -f "$ADMIN_JAR" ] || fail "없습니다: ${ADMIN_JAR} (--build 로 만들거나 pnpm --filter admin-web generate && ./gradlew :server:admin:bootJar)"

SHA="$(git rev-parse --short HEAD)"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
HEAD_TIME="$(git log -1 --format=%ct)"
jar_info() {
    local f="$1" t
    t="$(stat -c %Y "$f")"
    printf '   %-20s %s  %s' "$(basename "$f")" "$(date -d "@$t" '+%F %T')" "$(du -h "$f" | cut -f1)"
    # 낡은 jar 로 관리 화면을 저장하면 새 칸이 지워진다 — 마지막 커밋보다 오래된 jar 는 알린다
    if [ "$t" -lt "$HEAD_TIME" ]; then printf '  ⚠ 마지막 커밋(%s)보다 오래됐다 — --build 를 권한다' "$SHA"; fi
    echo
}
jar_info "$API_JAR"
jar_info "$ADMIN_JAR"
if [ -n "$(git status --porcelain -- server apps apps-packages 2>/dev/null)" ]; then
    warn "커밋하지 않은 변경이 있습니다(server·apps). 묶음 이름의 커밋(${SHA})과 jar 내용이 다를 수 있습니다."
fi

step "묶기"
STAGE="$(mktemp -d)"
PKG="${STAGE}/roboyam-native"
mkdir -p "$PKG/application" "$PKG/certs"
cp "${SCRIPT_DIR}/roboyam-install.sh" "${SCRIPT_DIR}/install.conf.example" "${SCRIPT_DIR}/README.md" "$PKG/"
cp -r "${SCRIPT_DIR}/lib" "${SCRIPT_DIR}/db" "${SCRIPT_DIR}/app" "$PKG/"
rm -f "$PKG/db/backup-hook.sh"
cp "$API_JAR" "$ADMIN_JAR" "$PKG/application/"
printf 'Origin CA 인증서(origin.pem)와 개인 키(origin.key)를 이 폴더에 둔다 — Cloudflare › SSL/TLS › Origin Server\n' > "$PKG/certs/README.txt"
{
    echo "묶음   : ${SHA} (${BRANCH}) · $(date '+%F %T')"
    (cd "$PKG/application" && sha256sum ./*.jar | sed 's|[ *]*\./| |; s/^/jar    : /')
} > "$PKG/PACKAGE_INFO"

# Windows 에서 만들어도 서버에서 돌아가게 — CRLF 가 섞이면 멈춘다, 실행 권한을 붙인다
if grep -rlq $'\r' "$PKG"/*.sh "$PKG"/lib "$PKG"/db "$PKG"/app; then
    fail "CRLF 줄 끝인 스크립트가 있습니다: $(grep -rl $'\r' "$PKG"/*.sh "$PKG"/lib "$PKG"/db "$PKG"/app | tr '\n' ' ')"
fi
find "$PKG" -name '*.sh' -exec chmod 755 {} +

mkdir -p "${SCRIPT_DIR}/dist"
OUT="${SCRIPT_DIR}/dist/roboyam-native-${SHA}-$(date '+%Y%m%d-%H%M').tar.gz"
tar -czf "$OUT" -C "$STAGE" roboyam-native
rm -rf "$STAGE"

echo
log "묶음: ${OUT} ($(du -h "$OUT" | cut -f1))"
echo "   DB 서버로: scp \"${OUT}\" rocky@<DB 공인 IP>:   (README 「접속 경로」)"
