// Gera os ícones e a imagem de compartilhamento a partir do símbolo "J" da logo.
// Uso: npm run icones   (os arquivos gerados são versionados; rode de novo só se a logo mudar)
//   src/app/icon.svg · src/app/favicon.ico · src/app/apple-icon.png · src/app/opengraph-image.png
//   public/icon-192.png · public/icon-512.png · public/icon-maskable-512.png
import { readFile, writeFile, mkdir } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import satori from "satori";
import sharp from "sharp";

const raiz = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const NAVY = "#102e50";
const CREAM = "#fbf5ee";
const ORANGE = "#e78b48";

// Mesmo desenho de src/components/Logo.tsx (viewBox 60x72): gancho externo + traço interno laranja.
const J_EXTERNO = "M52 5 V37 A22 22 0 0 1 8 37 V33";
const J_INTERNO = "M40 19 V37 A10 10 0 0 1 20 37 V35";

/** Quadrado 64x64 com o J centralizado. `escala` controla o respiro (maskable precisa de mais margem). */
function icone({ arredondado, escala, traco = 8 }) {
  const w = 64;
  // o J ocupa ~ x 4..56 e y 1..63 (60x72 de viewBox); centraliza e escala
  const s = escala;
  const tx = w / 2 - 30 * s;
  const ty = w / 2 - 36 * s;
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64" width="64" height="64">
  <rect width="64" height="64" ${arredondado ? 'rx="15"' : ""} fill="${NAVY}"/>
  <g transform="translate(${tx} ${ty}) scale(${s})" fill="none" stroke-width="${traco}" stroke-linecap="round">
    <path d="${J_EXTERNO}" stroke="${CREAM}"/>
    <path d="${J_INTERNO}" stroke="${ORANGE}"/>
  </g>
</svg>`;
}

const png = (svg, tamanho) => sharp(Buffer.from(svg), { density: 384 }).resize(tamanho, tamanho).png().toBuffer();

/** ICO com imagens PNG embutidas (aceito por todos os navegadores atuais). */
function ico(imagens) {
  const cab = Buffer.alloc(6 + imagens.length * 16);
  cab.writeUInt16LE(0, 0);
  cab.writeUInt16LE(1, 2);
  cab.writeUInt16LE(imagens.length, 4);
  let offset = cab.length;
  imagens.forEach(({ tamanho, dados }, i) => {
    const p = 6 + i * 16;
    cab.writeUInt8(tamanho >= 256 ? 0 : tamanho, p);
    cab.writeUInt8(tamanho >= 256 ? 0 : tamanho, p + 1);
    cab.writeUInt16LE(1, p + 4);
    cab.writeUInt16LE(32, p + 6);
    cab.writeUInt32LE(dados.length, p + 8);
    cab.writeUInt32LE(offset, p + 12);
    offset += dados.length;
  });
  return Buffer.concat([cab, ...imagens.map((i) => i.dados)]);
}

const arredondado = icone({ arredondado: true, escala: 0.74 });
const pequeno = icone({ arredondado: true, escala: 0.8, traco: 10 });   // traço mais grosso: legível em 16/32 px
const cheio = icone({ arredondado: false, escala: 0.74 });
const maskable = icone({ arredondado: false, escala: 0.56 });            // J dentro da zona segura (80%)

await mkdir(path.join(raiz, "public"), { recursive: true });
await writeFile(path.join(raiz, "src/app/icon.svg"), arredondado);
await writeFile(path.join(raiz, "src/app/apple-icon.png"), await png(cheio, 180));
await writeFile(path.join(raiz, "public/icon-192.png"), await png(arredondado, 192));
await writeFile(path.join(raiz, "public/icon-512.png"), await png(arredondado, 512));
await writeFile(path.join(raiz, "public/icon-maskable-512.png"), await png(maskable, 512));
await writeFile(
  path.join(raiz, "src/app/favicon.ico"),
  ico(await Promise.all([16, 32, 48].map(async (t) => ({ tamanho: t, dados: await png(pequeno, t) })))),
);

// ---- imagem de compartilhamento (Open Graph / Twitter), 1200x630 ---------------------------------------
const fonte = (peso) =>
  readFile(path.join(raiz, `node_modules/@fontsource/lexend/files/lexend-latin-${peso}-normal.woff`));
const logoJ = `data:image/svg+xml;base64,${Buffer.from(
  `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 60 72" width="240" height="288" fill="none" stroke-width="7" stroke-linecap="round">
     <path d="${J_EXTERNO}" stroke="${CREAM}"/><path d="${J_INTERNO}" stroke="${ORANGE}"/></svg>`,
).toString("base64")}`;

const el = (type, style, children) => ({ type, props: { style, children } });
const arvore = el("div", {
  display: "flex", width: "100%", height: "100%", background: NAVY, padding: "72px", alignItems: "center", gap: "64px",
  fontFamily: "Lexend",
}, [
  { type: "img", props: { src: logoJ, width: 240, height: 288 } },
  el("div", { display: "flex", flexDirection: "column", color: CREAM, width: 740 }, [
    el("div", { display: "flex", fontSize: 92, fontWeight: 800, letterSpacing: "-2px" }, "jaguatá"),
    el("div", { display: "flex", fontSize: 44, fontWeight: 500, marginTop: 20, lineHeight: 1.25, color: ORANGE },
      "Talento chancelado, vaga preenchida."),
    el("div", { display: "flex", fontSize: 28, fontWeight: 500, marginTop: 28, lineHeight: 1.4, opacity: 0.85 },
      "Conectamos PMEs a ONGs que chancelam e enviam talentos 18+ para as suas vagas."),
  ]),
]);
const svg = await satori(arvore, {
  width: 1200, height: 630,
  fonts: [
    { name: "Lexend", data: await fonte(500), weight: 500, style: "normal" },
    { name: "Lexend", data: await fonte(800), weight: 800, style: "normal" },
  ],
});
await writeFile(path.join(raiz, "src/app/opengraph-image.png"), await sharp(Buffer.from(svg)).png().toBuffer());
await writeFile(path.join(raiz, "src/app/twitter-image.png"), await sharp(Buffer.from(svg)).png().toBuffer());
console.log("ícones e imagem de compartilhamento gerados");
