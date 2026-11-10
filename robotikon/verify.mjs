// 디시콘 규격 검증(남 out/m · 여 out/f 둘 다). 위반은 파일명과 함께 출력하고 하나라도 있으면 종료 코드 1
// 사용: node verify.mjs [m|f]
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import omggif from 'omggif'
import pngjs from 'pngjs'
import emotes from './src/emotes/index.mjs'
import { VARIANT_IDS } from './src/parts.mjs'

const { GifReader } = omggif
const { PNG } = pngjs

const ROOT = import.meta.dirname
const SIZE = 200
const PAD = 5
const MAX_FRAMES = 24
const MAX_GIF = 2 * 1024 * 1024
const MAX_REP = 1024 * 1024
const TARGET = 300 * 1024

const errors = []
const warns = []
const err = (file, msg) => errors.push(`${file}: ${msg}`)
const warn = (file, msg) => warns.push(`${file}: ${msg}`)
const hangul = (s, min, max) => new RegExp(`^[가-힣]{${min},${max}}$`).test(s)

const meta = JSON.parse(readFileSync(join(ROOT, 'meta.json'), 'utf8'))
const { packs, items } = meta
const asked = VARIANT_IDS.filter((v) => process.argv.includes(v))
const variants = asked.length ? asked : VARIANT_IDS

// 메타: 묶음(버전)마다 디시콘명·태그·설명, 이름은 서로 달라야 한다(디시콘명 중복 불가)
const packNames = new Set()
for (const v of variants) {
  const pack = packs?.[v]
  if (!pack) { err('meta.json', `packs.${v} 없음`); continue }
  if (!hangul(pack.name, 1, 10)) err('meta.json', `[${v}] 디시콘명 "${pack.name}" — 한글 1~10자`)
  if (packNames.has(pack.name)) err('meta.json', `[${v}] 디시콘명 "${pack.name}" — 다른 묶음과 겹침`)
  packNames.add(pack.name)
  if (!Array.isArray(pack.tags) || pack.tags.length > 5) err('meta.json', `[${v}] 태그는 5개 이하`)
  for (const tag of pack.tags ?? []) if (tag.length < 1 || tag.length > 5) err('meta.json', `[${v}] 태그 "${tag}" — 1~5자`)
  if ([...pack.description].length > 100) err('meta.json', `[${v}] 설명 ${[...pack.description].length}자 — 100자 이내`)
}
const seen = new Set()
for (const it of items) {
  if (seen.has(it.id)) err('meta.json', `id ${it.id} 중복`)
  seen.add(it.id)
  if (!hangul(it.alt, 1, 3)) err('meta.json', `${it.id} 알트값 "${it.alt}" — 한글 1~3자`)
}

/** 바깥 5px 띠가 완전 투명한지 */
function marginClear(rgba) {
  for (let y = 0; y < SIZE; y++) {
    for (let x = 0; x < SIZE; x++) {
      if (x >= PAD && x < SIZE - PAD && y >= PAD && y < SIZE - PAD) continue
      if (rgba[(y * SIZE + x) * 4 + 3] !== 0) return `(${x},${y})`
    }
  }
  return null
}

for (const v of variants) {
  const outDir = join(ROOT, 'out', v)
  const files = existsSync(outDir) ? readdirSync(outDir) : []
  const label = (file) => `${v}/${file}`

  // GIF
  for (const e of emotes) {
    const it = items.find((m) => m.id === e.id)
    if (!it) { err('meta.json', `${e.id} 항목 없음`); continue }
    const file = `robotikon_${e.id}_${it.name}.gif`
    if (!files.includes(file)) { err(label(file), '파일 없음 — node build.mjs 먼저'); continue }
    const path = join(outDir, file)
    const bytes = statSync(path).size
    if (bytes > MAX_GIF) err(label(file), `${(bytes / 1024).toFixed(0)}KB — 2MB 초과`)
    else if (bytes > TARGET) warn(label(file), `${(bytes / 1024).toFixed(0)}KB — 목표 300KB 초과`)
    const gif = new GifReader(readFileSync(path))
    if (gif.width !== SIZE || gif.height !== SIZE) err(label(file), `${gif.width}x${gif.height} — 200x200 이어야 함`)
    const n = gif.numFrames()
    if (n > MAX_FRAMES) err(label(file), `${n}프레임 — 24 이하`)
    if (gif.loopCount() !== 0) err(label(file), `루프 ${gif.loopCount()} — 무한 루프(0)여야 함`)
    let cs = 0
    for (let i = 0; i < n; i++) {
      const info = gif.frameInfo(i)
      cs += info.delay
      if (info.transparent_index === null) err(label(file), `${i}번 프레임 투명색 없음`)
      const rgba = new Uint8Array(SIZE * SIZE * 4)
      gif.decodeAndBlitFrameRGBA(i, rgba)
      const at = marginClear(rgba)
      if (at) err(label(file), `${i}번 프레임 여백 ${at} 불투명`)
    }
    const fps = (n * 100) / cs
    if (Math.abs(fps - 12) > 0.3) err(label(file), `${fps.toFixed(2)}fps — 12fps 여야 함`)
  }

  // 대표 PNG
  const repId = packs?.[v]?.representative
  const rep = items.find((m) => m.id === repId)
  const repFile = rep && `robotikon_${repId}_${rep.name}_대표.png`
  if (!emotes.some((e) => e.id === repId)) {
    warn(label('대표 PNG'), `${repId}번이 아직 없어 검사하지 않음`)
  } else if (!files.includes(repFile)) {
    err(label(repFile), '파일 없음')
  } else {
    const path = join(outDir, repFile)
    if (statSync(path).size > MAX_REP) err(label(repFile), '1MB 초과')
    const png = PNG.sync.read(readFileSync(path))
    if (png.width !== SIZE || png.height !== SIZE) err(label(repFile), `${png.width}x${png.height} — 200x200 이어야 함`)
    const at = marginClear(png.data)
    if (at) err(label(repFile), `여백 ${at} 불투명`)
  }
}

for (const w of warns) console.log(`경고  ${w}`)
for (const e of errors) console.log(`실패  ${e}`)
console.log(errors.length ? `\n검증 실패 ${errors.length}건` : `\n검증 통과 — ${variants.join('·')} 각 GIF ${emotes.length}종`)
process.exit(errors.length ? 1 : 0)
