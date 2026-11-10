// ROBOYAM 사이트 아이콘·링크 미리보기 SVG → PNG(resvg). 사용: node design/brand/render.cjs <resvg-js 경로(robotikon/node_modules/@resvg/resvg-js)>
// 아이콘은 512px 한 장(관리자 「사이트 아이콘」 올리기가 32·48·180·192 를 만든다), 미리보기는 1200×630.
const fs = require('fs');
const path = require('path');
const { Resvg } = require(process.argv[2] || '@resvg/resvg-js');

const dir = __dirname;
const jobs = [
    ['roboyam-site-icon.svg', 'roboyam-site-icon-512.png', { mode: 'width', value: 512 }],
    ['roboyam-og.svg', 'roboyam-og-1200x630.png', { mode: 'width', value: 1200 }],
];
for (const [src, out, fitTo] of jobs) {
    const svg = fs.readFileSync(path.join(dir, src), 'utf8');
    const png = new Resvg(svg, { fitTo, font: { loadSystemFonts: true, defaultFontFamily: 'Malgun Gothic' } }).render().asPng();
    fs.writeFileSync(path.join(dir, out), png);
    console.log(out, png.length, 'bytes');
}
