// 전체 빌드: 이모티콘마다 24프레임 SVG 계산 → 190px 렌더 + 5px 투명 여백(200px) → 12fps 무한 루프 GIF
// 남(m)·여(f) 두 벌을 out/m, out/f 에 따로 만든다(디시콘 묶음도 따로 등록)
// 사용: node build.mjs [m|f] [id ...]   (버전 생략 시 둘 다, id 생략 시 전부)
import { mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { Resvg } from '@resvg/resvg-js'
import gifenc from 'gifenc'
import pngjs from 'pngjs'
import emotes from './src/emotes/index.mjs'
import { SIZE, VARIANT_IDS, resetIds, setVariant } from './src/parts.mjs'

const { GIFEncoder, applyPalette, quantize } = gifenc
const { PNG } = pngjs

const OUT = 200
const ART = 190
const PAD = (OUT - ART) / 2
const FRAMES = 24
const LOOP_CS = 200 // 24프레임 × 12fps = 2초
const ALPHA_CUT = 128

const ROOT = import.meta.dirname
const meta = JSON.parse(readFileSync(join(ROOT, 'meta.json'), 'utf8'))
const FONTS = ['C:/Windows/Fonts/NotoSansKR-VF.ttf', 'C:/Windows/Fonts/malgunbd.ttf', 'C:/Windows/Fonts/consolab.ttf']

/** 360 원본을 190px 로 그리고 사방 5px 여백을 둔 200px RGBA */
function render(svg) {
  const inner = svg.replace(/^<svg[^>]*>/, '').replace(/<\/svg>\s*$/, '')
  const framed = `<svg xmlns="http://www.w3.org/2000/svg" width="${OUT}" height="${OUT}" viewBox="0 0 ${OUT} ${OUT}">`
    + `<svg x="${PAD}" y="${PAD}" width="${ART}" height="${ART}" viewBox="0 0 ${SIZE} ${SIZE}" overflow="hidden">${inner}</svg></svg>`
  const png = new Resvg(framed, {
    fitTo: { mode: 'width', value: OUT },
    font: { fontFiles: FONTS, loadSystemFonts: false, defaultFontFamily: 'Noto Sans KR' },
  }).render().asPng()
  return { png, rgba: PNG.sync.read(png).data }
}

/** GIF 는 투명이 1비트라 알파 128 기준으로 자르고, 모든 프레임이 하나의 팔레트를 쓴다(색 깜빡임 방지) */
function encodeGif(frames) {
  const opaque = []
  for (const rgba of frames) {
    for (let p = 0; p < rgba.length; p += 4) {
      if (rgba[p + 3] >= ALPHA_CUT) opaque.push(rgba[p], rgba[p + 1], rgba[p + 2], 255)
    }
  }
  const palette = quantize(new Uint8Array(opaque), 255, { format: 'rgb565' })
  const tIndex = palette.length
  palette.push([0, 0, 0])
  const gif = GIFEncoder()
  frames.forEach((rgba, i) => {
    const index = applyPalette(rgba, palette.slice(0, tIndex), 'rgb565')
    for (let p = 0; p < index.length; p++) if (rgba[p * 4 + 3] < ALPHA_CUT) index[p] = tIndex
    // 8·9cs 를 섞어 24프레임 합이 정확히 2초가 되게
    const cs = Math.round(((i + 1) * LOOP_CS) / FRAMES) - Math.round((i * LOOP_CS) / FRAMES)
    gif.writeFrame(index, OUT, OUT, {
      palette: i === 0 ? palette : undefined,
      delay: cs * 10, repeat: 0, transparent: true, transparentIndex: tIndex, dispose: 2,
    })
  })
  gif.finish()
  return gif.bytes()
}

const args = process.argv.slice(2)
const variants = VARIANT_IDS.filter((v) => args.includes(v))
const want = args.filter((a) => !VARIANT_IDS.includes(a))
const targets = emotes.filter((e) => !want.length || want.includes(e.id))
const names = new Map(meta.items.map((m) => [m.id, m.name]))

for (const v of variants.length ? variants : VARIANT_IDS) {
  setVariant(v)
  const outDir = join(ROOT, 'out', v)
  mkdirSync(outDir, { recursive: true })
  console.log(`[${v}] ${meta.packs[v].name}`)

  for (const e of targets) {
    const name = names.get(e.id) ?? e.name
    const dir = join(ROOT, 'frames', v, e.id)
    rmSync(dir, { recursive: true, force: true })
    mkdirSync(dir, { recursive: true })
    const frames = []
    for (let i = 0; i < FRAMES; i++) {
      resetIds()
      const svg = e.frame(i / FRAMES, i)
      const { png, rgba } = render(svg)
      const nn = String(i).padStart(2, '0')
      writeFileSync(join(dir, `${nn}.svg`), svg)
      writeFileSync(join(dir, `${nn}.png`), png)
      frames.push(rgba)
    }
    const bytes = encodeGif(frames)
    const file = `robotikon_${e.id}_${name}.gif`
    writeFileSync(join(outDir, file), bytes)
    console.log(`  ${file}  ${FRAMES}프레임  ${(bytes.length / 1024).toFixed(1)}KB`)
  }

  // 대표 디시콘(PNG): 대표 id 가 아직 없으면 건너뛴다
  const rep = emotes.find((e) => e.id === meta.packs[v].representative)
  if (rep && (!want.length || want.includes(rep.id))) {
    resetIds()
    const { png } = render(rep.frame(0, 0))
    const file = `robotikon_${rep.id}_${names.get(rep.id) ?? rep.name}_대표.png`
    writeFileSync(join(outDir, file), png)
    console.log(`  ${file}  ${(png.length / 1024).toFixed(1)}KB`)
  }
}
