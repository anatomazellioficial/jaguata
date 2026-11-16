// Dublê local das APIs públicas usadas pelo backend (BrasilAPI: CNPJ e CEP). Deixa o teste ponta a ponta
// determinístico e independente da internet. Uso: node e2e/mock-apis.mjs  (porta 4010; BRASILAPI_URL aponta para cá)
//   CNPJ: prefixo "55" = ONG (natureza jurídica 3999, sem fins lucrativos); qualquer outro = empresa ativa.
//   CEP:  13015904 = Campinas; os demais = Av. Paulista, São Paulo.
import http from "node:http";

const PORTA = Number(process.env.MOCK_PORT ?? 4010);

const cnpj = (n) => ({
  razao_social: `${n.startsWith("55") ? "ASSOCIACAO" : "EMPRESA"} E2E ${n}`,
  nome_fantasia: "E2E",
  cnae_fiscal: 6201501,
  codigo_porte: 5,
  opcao_pelo_mei: false,
  situacao_cadastral: 2,
  descricao_situacao_cadastral: "ATIVA",
  cep: "13015904",
  logradouro: "RUA TESTE",
  bairro: "CENTRO",
  municipio: "CAMPINAS",
  uf: "SP",
  codigo_natureza_juridica: n.startsWith("55") ? 3999 : 2062,
  natureza_juridica: n.startsWith("55") ? "Associação Privada" : "Sociedade Empresária Limitada",
});

const cep = (c) =>
  c === "13015904"
    ? { cep: c, street: "Rua Teste", neighborhood: "Centro", city: "Campinas", state: "SP",
        location: { coordinates: { latitude: "-22.9099", longitude: "-47.0626" } } }
    : { cep: c, street: "Avenida Paulista", neighborhood: "Bela Vista", city: "São Paulo", state: "SP",
        location: { coordinates: { latitude: "-23.5614", longitude: "-46.6559" } } };

http
  .createServer((req, res) => {
    const m = req.url.match(/^\/api\/(cnpj\/v1|cep\/v2)\/(\d+)/);
    res.setHeader("content-type", "application/json");
    if (!m) { res.statusCode = 404; return res.end("{}"); }
    res.end(JSON.stringify(m[1] === "cnpj/v1" ? cnpj(m[2]) : cep(m[2])));
  })
  .listen(PORTA, "0.0.0.0", () => console.log(`mock BrasilAPI em http://0.0.0.0:${PORTA}`));
