// 한반도 윤곽을 공개 지도 자료(Natural Earth 1:10m — world-atlas)에서 뽑아 로고 크기의 SVG 패스로 만든다.
// node korea-map.mjs [높이=36] [단순화 허용치=0.1] → korea-map.json { d, width, height, islands }
import fs from 'node:fs'
import { createRequire } from 'node:module'
import * as topojson from 'topojson-client'
const require = createRequire(import.meta.url)
const topo = require('world-atlas/countries-10m.json')
const H = Number(process.argv[2] ?? 36)
const TOL = Number(process.argv[3] ?? 0.1)

const geoms = topo.objects.countries.geometries.filter((g) => g.id === '410' || g.id === '408')
if (geoms.length !== 2) throw new Error('남·북 두 나라를 못 찾음: ' + geoms.length)
const merged = topojson.merge(topo, geoms) // 남북 경계를 지워 하나의 한반도로

// 범위: 본토 + 제주(33.1N) ~ 온성(43.0N), 서쪽 124.3E ~ 독도 131.9E
const LAT_TOP = 43.05, LAT_BOT = 33.05, LON_L = 124.1
const k = H / (LAT_TOP - LAT_BOT)
const cos = Math.cos((38 * Math.PI) / 180)
const proj = ([lon, lat]) => [(lon - LON_L) * cos * k, (LAT_TOP - lat) * k]

// Douglas–Peucker
function simplify(pts, tol) {
  if (pts.length < 4) return pts
  const keep = new Array(pts.length).fill(false)
  keep[0] = keep[pts.length - 1] = true
  const stack = [[0, pts.length - 1]]
  while (stack.length) {
    const [a, b] = stack.pop()
    let max = 0, idx = -1
    const [x1, y1] = pts[a], [x2, y2] = pts[b]
    const dx = x2 - x1, dy = y2 - y1, len = Math.hypot(dx, dy) || 1e-9
    for (let i = a + 1; i < b; i++) {
      const d = Math.abs(dy * pts[i][0] - dx * pts[i][1] + x2 * y1 - y2 * x1) / len
      if (d > max) { max = d; idx = i }
    }
    if (max > tol && idx > 0) { keep[idx] = true; stack.push([a, idx], [idx, b]) }
  }
  return pts.filter((_, i) => keep[i])
}
const area = (pts) => Math.abs(pts.reduce((s, p, i) => { const q = pts[(i + 1) % pts.length]; return s + p[0] * q[1] - q[0] * p[1] }, 0) / 2)

const rings = []
for (const poly of merged.coordinates) {
  const ring = poly[0].map(proj)
  const A = area(ring)
  const cx = ring.reduce((s, p) => s + p[0], 0) / ring.length, cy = ring.reduce((s, p) => s + p[1], 0) / ring.length
  rings.push({ ring, A, cx, cy })
}
rings.sort((a, b) => b.A - a.A)
const main = rings[0]
// 섬: 넓이가 어느 정도 되는 것만(제주·거제·남해·진도·강화 등). 울릉도·독도는 작아서 따로 점으로 넣는다
const isles = rings.slice(1).filter((r) => r.A > 0.12)
const n = (v) => String(+v.toFixed(1))
// 닫힌 고리는 처음 점과 끝 점이 같아 그대로는 단순화가 안 된다 — 처음 점에서 가장 먼 점에서 둘로 갈라 각각 단순화한다
function simplifyRing(pts, tol) {
  const open = pts[0][0] === pts[pts.length - 1][0] && pts[0][1] === pts[pts.length - 1][1] ? pts.slice(0, -1) : pts
  let far = 0, best = -1
  open.forEach((p, i) => { const d = Math.hypot(p[0] - open[0][0], p[1] - open[0][1]); if (d > best) { best = d; far = i } })
  const a = simplify(open.slice(0, far + 1), tol), b = simplify([...open.slice(far), open[0]], tol)
  return [...a, ...b.slice(1, -1)]
}
const toD = (pts) => { const s = simplifyRing(pts, TOL); return 'M' + s.map((p) => n(p[0]) + ' ' + n(p[1])).join('L') + 'Z' }
const d = toD(main.ring) + isles.map((r) => toD(r.ring)).join('')
const ulleung = proj([130.87, 37.5]), dokdo = proj([131.87, 37.24])
const width = Math.max(...main.ring.map((p) => p[0]), dokdo[0]) + 0.8
const out = { d, width: +width.toFixed(1), height: H, ulleung: ulleung.map((v) => +v.toFixed(1)), dokdo: dokdo.map((v) => +v.toFixed(1)), islands: isles.length, mainPoints: simplifyRing(main.ring, TOL).length, mainArea: +main.A.toFixed(1), isleAreas: isles.map((r) => +r.A.toFixed(2)), bytes: d.length }
fs.writeFileSync(new URL('./korea-map.json', import.meta.url), JSON.stringify(out, null, 1))
console.log({ ...out, d: d.slice(0, 80) + '…' })
