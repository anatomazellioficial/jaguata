# Jaguatá

Plataforma de *matchmaking* B2B que conecta **empresas** (foco em PMEs) a **ONGs** do terceiro setor. A ONG funciona como a "máquina de SDR" do RH da empresa: pré-qualifica, **chancela** e envia talentos em vulnerabilidade social (todos **maiores de 18 anos**) para as vagas cadastradas. Alinhada ao ODS 8 (trabalho decente).

## Sumário

- [Regras de negócio](#regras-de-negócio)
- [Arquitetura](#arquitetura)
- [Como rodar](#como-rodar)
- [Variáveis de ambiente](#variáveis-de-ambiente)
- [Integrações externas (Receita e CEP)](#integrações-externas-receita-e-cep)
- [API](#api)
- [Testes](#testes)
- [Segurança](#segurança)
- [Estrutura do repositório](#estrutura-do-repositório)
- [Limitações conhecidas e próximos passos](#limitações-conhecidas-e-próximos-passos)

## Regras de negócio

| Regra | Onde é garantida |
|---|---|
| **Idade mínima de 18 anos** | `CHECK` + *trigger* no banco, validação na entidade e no serviço |
| **Porte da empresa** (MEI, ME, EPP, Demais) definido pela **Receita Federal**, nunca pelo nº de funcionários nem informado pelo usuário | consulta do CNPJ no cadastro |
| **Dados oficiais não são digitados**: razão social, CNAE, porte, endereço e coordenadas vêm da Receita e do CEP | `AuthService` + `ConsultaService` |
| **Só CNPJ ativo** pode se cadastrar | situação cadastral na Receita |
| **Cardápio de cargos fechado**: Aprendiz, Estagiário, Trainee, Assistente, Analista, Coordenador, Gerente, Diretor, CEO | `enum` no banco, na API e no frontend |
| **Matriz de Complexidade**: MEI/ME/EPP que abrem "Diretor" ou "CEO" são exibidas à ONG como *Coordenador/Gerente* | `NivelCargo.tituloExibicaoPara` |
| **Jovem Aprendiz**: só candidatos de 18 a 24 anos | *trigger* no banco, entidade `Match` e consulta de match |
| **Match por território, perfil e CNAE**: raio em km (PostGIS), modelo de trabalho e CNAE | `MatchService` + `MatchScoreCalculator` |
| **Funil**: Sugerido → Enviado → Entrevista → Contratado (ou Recusado) | `StatusMatch` + validação de transições |
| **Só ONGs verificadas** entram em matches | consulta de elegíveis + painel de admin |
| **Contratar preenche a vaga**: a vaga vira `PREENCHIDA`, as demais pendências dela (sugeridas, enviadas, em entrevista) são encerradas como recusadas e só se avança em vaga aberta | `MatchService.avancar` |
| **Quem já foi contratado não é sugerido de novo** e não pode ser contratado duas vezes | consulta de elegíveis + `MatchService` |

O *score* (0–100) combina território (50), modelo de trabalho (20) e CNAE (30). A compatibilidade de CNAE hoje é a mesma divisão (2 primeiros dígitos); está isolada em `CnaeCompatibilidade` para ser trocada por uma tabela de afinidade.

## Arquitetura

```
Navegador ──► Next.js (BFF)  ──► Spring Boot (API REST) ──► PostgreSQL + PostGIS
              cookie httpOnly      JWT HS256, rate limit        Flyway
```

- **Banco**: PostgreSQL 16 com PostGIS. Esquema versionado com Flyway (`V1` schema, `V2` usuários).
- **Backend**: Java 17, Spring Boot 3 (JPA/Hibernate, Spring Security como *resource server*, Flyway).
- **Frontend**: Next.js (App Router), TypeScript, Tailwind CSS 4, Framer Motion, fonte Lexend.
- **BFF**: o navegador nunca fala com a API diretamente. O Next guarda o JWT em um cookie `httpOnly` e o repassa como `Bearer` pelo proxy `/api/proxy/*`; o JavaScript da página nunca vê o token.
- **Papéis**: `EMPRESA`, `ONG` e `ADMIN`, com checagem de posse (uma empresa só age sobre as próprias vagas, uma ONG só sobre os próprios candidatos e matches).

## Como rodar

Pré-requisitos: Docker (e, para desenvolvimento sem Docker, JDK 17, Maven 3.9+ e Node 22+).

### Com Docker Compose

```bash
export JWT_SECRET="$(openssl rand -base64 48)"
export ADMIN_EMAIL="voce@exemplo.com"
export ADMIN_PASSWORD="uma-senha-longa-e-unica"
docker compose up --build
```

- Frontend: <http://localhost:3000>
- API e banco ficam acessíveis **somente em `127.0.0.1`** (backend `8080`, banco `5432`).
- Se alguma porta já estiver em uso, defina `FRONTEND_PORT`, `BACKEND_PORT` ou `DB_PORT`.
- O primeiro usuário `ADMIN` é criado a partir de `ADMIN_EMAIL` / `ADMIN_PASSWORD` **se ainda não existir** (o bootstrap não altera a senha de uma conta existente).

### Sem Docker (desenvolvimento)

```bash
# banco (PostGIS)
docker compose up -d db

# backend
cd backend
export JWT_SECRET="$(openssl rand -base64 48)"
mvn spring-boot:run

# frontend
cd frontend
npm install
BACKEND_URL=http://localhost:8080 npm run dev
```

## Variáveis de ambiente

### Backend

| Variável | Padrão | Descrição |
|---|---|---|
| `JWT_SECRET` | — (**obrigatória**) | Segredo HS256, mínimo de 32 bytes. Sem valor padrão de propósito. |
| `JWT_TTL` | `PT1H` | Validade do token. |
| `JWT_ISSUER` | `jaguata` | Emissor exigido na validação do token. |
| `DB_URL` / `DB_USER` / `DB_PASSWORD` | `…/jaguata` | Conexão com o banco. |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | vazio | Cria o primeiro admin (senha com 10+ caracteres). |
| `RATE_LIMIT_ENABLED` | `true` | Liga/desliga o rate limit. |
| `RATE_LIMIT_LOGIN_PER_MINUTE` | `10` | Tentativas de login por minuto, por IP. |
| `RATE_LIMIT_REGISTRO_PER_HOUR` | `5` | Cadastros por hora, por IP. |
| `RATE_LIMIT_API_PER_MINUTE` | `120` | Requisições por minuto, por usuário (ou IP sem token). |
| `RATE_LIMIT_CONSULTA_PER_MINUTE` | `30` | Consultas de CNPJ/CEP por minuto, por usuário (ou IP sem token). |
| `BRASILAPI_URL` | `https://brasilapi.com.br` | Fonte de CNPJ (Receita) e CEP. |
| `NOMINATIM_URL` | `https://nominatim.openstreetmap.org` | Fonte de reserva para coordenadas. |
| `INTEGRACOES_CONNECT_TIMEOUT_MS` / `INTEGRACOES_READ_TIMEOUT_MS` | `3000` / `6000` | Timeouts das consultas externas. |
| `CNPJ_CACHE_HOURS` / `CEP_CACHE_DAYS` | `24` / `30` | Validade do cache das consultas. |
| `RATE_LIMIT_TRUSTED_PROXY_HOPS` | `0` | Nº de proxies confiáveis à frente (veja [Segurança](#segurança)). |
| `LOGIN_MAX_FAILURES` / `LOGIN_LOCK_MINUTES` | `5` / `15` | Bloqueio da conta após falhas seguidas de login. |
| `MAX_BODY_BYTES` | `65536` | Tamanho máximo do corpo das requisições. |

### Frontend

| Variável | Padrão | Descrição |
|---|---|---|
| `BACKEND_URL` | `http://localhost:8080` | Endereço interno da API. |

## Integrações externas (Receita e CEP)

O cadastro **não aceita** razão social, CNAE, porte, endereço nem coordenadas do usuário: o cliente informa o CNPJ e o servidor busca o resto.

| Dado | Fonte | Observação |
|---|---|---|
| CNPJ (razão social, CNAE, porte, situação, sede, natureza jurídica) | [BrasilAPI](https://brasilapi.com.br) (dados da Receita Federal) | só CNPJs com situação **ativa** se cadastram |
| CEP → endereço e coordenadas | BrasilAPI (CEP v2) | fonte principal |
| Coordenadas de reserva | [Nominatim / OpenStreetMap](https://nominatim.org) | por CEP → por rua e cidade → por cidade; a precisão (`CEP`, `RUA`, `CIDADE`) é informada |

**Como o porte é definido:** MEI se a empresa é optante pelo MEI; senão, o código de porte da Receita (1 = ME, 3 = EPP, 5 = demais). Porte "não informado" é tratado como `DEMAIS` (régua de cargos estrita, sem rebaixamento).

**Comportamento e resiliência**
- Validação local do CNPJ (dígitos verificadores) e do CEP antes de qualquer chamada externa.
- Cache em memória (CNPJ 24 h, CEP 30 dias) e timeouts curtos; **falhas nunca são cacheadas**.
- Fonte fora do ar ou lenta → `503` com `Retry-After`; CNPJ/CEP inexistente → `404`/`422`.
- As consultas externas rodam **antes** da transação do banco, para não segurar conexão enquanto esperam a rede.
- O Nominatim público pede no máximo ~1 requisição por segundo e um `User-Agent` identificando a aplicação: ambos são respeitados (configure `INTEGRACOES_USER_AGENT`). Em alto volume, use uma instância própria ou outro provedor.
- Pontos fora do território brasileiro ou (0,0) são descartados.

**Privacidade (LGPD):** para as fontes externas só trafegam **CNPJ, CEP e nomes de rua/cidade** (dados públicos). Nenhum nome, e-mail ou dado de candidato é enviado. A resposta da Receita inclui o quadro societário (nomes de pessoas físicas): ele **nunca é lido, guardado nem exposto**. As rotas `/api/consulta/**` são públicas (o cadastro ainda não tem conta), por isso têm rate limit próprio e só devolvem dados públicos.

## API

Todas as rotas exigem `Authorization: Bearer <jwt>`, exceto `/api/auth/**`. Erros seguem RFC 7807 (`application/problem+json`).

| Método e rota | Quem | Função |
|---|---|---|
| `POST /api/auth/registro/empresa` · `/registro/ong` | público | Cria a conta informando **só o CNPJ** (+ e-mail e senha); dados vêm da Receita. A ONG nasce **não verificada** |
| `GET /api/consulta/cnpj/{cnpj}` | público (rate limit próprio) | Pré-visualiza os dados públicos do CNPJ na Receita |
| `GET /api/consulta/cep/{cep}` | público (rate limit próprio) | Endereço e coordenadas do CEP |
| `POST /api/auth/login` | público | Devolve o JWT |
| `GET /api/cardapio/cargos` | autenticado | Cardápio de cargos |
| `POST /api/empresas/{id}/vagas` · `GET …/vagas` | empresa dona | Cria e lista vagas (informa o **CEP**; coordenadas são obtidas no servidor) |
| `GET`/`PATCH /api/vagas/{id}[/status]` | empresa dona | Consulta e altera a vaga |
| `POST /api/vagas/{id}/matches/sugestoes?raioKm=&limite=` | empresa dona | Roda o motor de match |
| `GET /api/vagas/{id}/matches` | empresa dona | Lista matches da vaga |
| `POST /api/ongs/{id}/candidatos` | ONG dona | Cadastra candidato (18+) com tags (informa o **CEP**) |
| `GET /api/ongs/{id}/matches?status=` | ONG dona | Fila/funil da ONG |
| `PATCH /api/matches/{id}/status` | ONG ou empresa (conforme a etapa) | Avança o funil |
| `GET /api/admin/ongs` · `/resumo` · `POST /api/admin/ongs/{id}/verificar` · `/revogar` | admin | Verificação de ONGs e indicadores |

Contratar é terminal: fecha a vaga (reabra com `PATCH /api/vagas/{id}/status` se precisar de outra contratação; pendências já encerradas não voltam).

Quem move cada etapa do funil: **ONG** → `ENVIADO`; **empresa dona da vaga** → `ENTREVISTA` e `CONTRATADO`; **ambas** → `RECUSADO`.

Códigos relevantes: `400` dados inválidos (inclui CNPJ com dígito verificador errado) · `401` sem/inválido token · `403` sem permissão · `413` corpo grande demais · `404` CNPJ/CEP inexistente · `422` regra de negócio (inclui CNPJ inativo) · `503` Receita/CEP indisponíveis · `429` limite de taxa (cabeçalho `Retry-After`).

## Testes

```bash
cd backend && mvn test        # requer Docker (Testcontainers sobe um PostGIS real)
cd frontend && npm run typecheck
```

A suíte do backend (80 testes) cobre: regras de negócio, migration e restrições do banco, consulta geográfica, API ponta a ponta com papéis e posse, área admin e **segurança** (rate limit, bloqueio de conta, cabeçalhos, política de senha, validação de JWT, limite de corpo) e **integrações** (clientes reais testados contra um servidor HTTP local que simula respostas, falhas e lentidão; nenhum teste usa a internet).

## Segurança

### Autenticação e autorização
- JWT **HS256** com algoritmo fixado, `iss` obrigatório e validado, `exp` e `jti`; assinatura adulterada, `alg: none` e token de outro emissor/segredo são recusados.
- Senhas com **BCrypt (custo 12)**. Política: 10–72 caracteres, sem senhas comuns, sem repetições triviais e sem conter o e-mail. Tempo de resposta do login igualado quando o e-mail não existe.
- Autorização por papel **e por posse do recurso** (`AccessPolicy` + `@PreAuthorize`), contra acesso a dados de terceiros (IDOR).
- O token fica em cookie `httpOnly` + `SameSite=Lax` (+ `Secure` em produção): inacessível ao JavaScript da página.

### Rate limit e anti-força-bruta
| Proteção | Regra padrão | Chave |
|---|---|---|
| Login | 10 tentativas/min | IP |
| Cadastro | 5/hora | IP |
| Demais rotas da API | 120/min | usuário autenticado (mudar de IP não ajuda); sem token, IP |
| Consultas de CNPJ/CEP | 30/min | usuário autenticado; sem token, IP (evita virar proxy aberto das APIs públicas) |
| Bloqueio de conta | 5 falhas seguidas → 15 min | e-mail normalizado (exista ou não, para não vazar cadastros) |

Implementação: *token bucket* em memória com cache limitado (Caffeine), `429` com `Retry-After`. Funciona por instância; para várias réplicas, troque por um armazenamento compartilhado (ex.: Redis).

> **IP do cliente atrás de proxy.** Por padrão o backend **ignora** `X-Forwarded-For` (`RATE_LIMIT_TRUSTED_PROXY_HOPS=0`), o único valor seguro se ele puder ser acessado diretamente, pois o cabeçalho seria forjável. Em produção, coloque um proxy reverso (Nginx, Caddy, balanceador da nuvem) na frente do frontend que **anexe** o IP real e **não exponha o frontend diretamente**; então defina `RATE_LIMIT_TRUSTED_PROXY_HOPS=1`. O backend usa a n-ésima entrada **da direita**, ignorando o que o cliente forjar à esquerda. Sem isso, atrás do BFF todos os usuários aparecem com o mesmo IP e compartilham o balde de login.

### Proteções de entrada e saída
- Corpo limitado (64 KB por padrão, inclusive requisições *chunked*) → `413`.
- Erros sem *stack trace*, SQL ou nomes de classe; erro inesperado vira `500` genérico (detalhe só no log). Mensagens de erro do Boot desativadas.
- Cabeçalhos: API com `Content-Security-Policy: default-src 'none'`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, HSTS, `nosniff`, `Cache-Control: no-store`. Frontend com CSP restritiva, `Permissions-Policy`, COOP e HSTS em produção.
- **BFF**: verificação de origem em requisições que mudam estado (anti-CSRF, além do `SameSite`), *allowlist* de caracteres nos caminhos do proxy (sem `..`, `%2f`…), `auth/login` bloqueado no proxy (o token nunca volta ao navegador), teto de corpo, respostas autenticadas com `no-store`.
- CORS desativado: não há consumo direto da API por outras origens.

### Infraestrutura
- Containers da aplicação como usuário **não-root**, sistema de arquivos **somente leitura**, `cap_drop: ALL` e `no-new-privileges`.
- Banco e API publicados apenas em `127.0.0.1`.
- Segredos só por variáveis de ambiente (`JWT_SECRET` é obrigatório e o *compose* se recusa a subir sem ele). Nenhum `.env` é versionado.
- `npm audit` (dependências de produção): 0 vulnerabilidades no momento da escrita.

### Antes de ir para produção
1. Gere um `JWT_SECRET` forte e único; defina `DB_PASSWORD` e as credenciais do admin.
2. Sirva por **HTTPS** atrás de um proxy reverso e ajuste `RATE_LIMIT_TRUSTED_PROXY_HOPS`.
3. Use um usuário de banco **sem privilégio de DDL** em tempo de execução (rode o Flyway com outro usuário).
4. Revise as limitações abaixo.

## Estrutura do repositório

```
backend/    API Spring Boot (domain, repository, service, controller, security, dto)
  src/main/resources/db/migration/   migrations Flyway (V1, V2)
frontend/   Next.js (app/ páginas e rotas BFF, components/, lib/)
docker-compose.yml
```

## Limitações conhecidas e próximos passos

- **Dependência de serviços públicos**: BrasilAPI e Nominatim não têm SLA; se ficarem fora do ar, o cadastro e a criação de vagas/candidatos retornam `503`. O porte reflete o que a Receita publica (declaração cadastral), não um cálculo de faturamento feito pela plataforma.
- **Precisão geográfica**: coordenadas por CEP são o ponto do CEP (ou aproximações por rua/cidade) — adequadas para raios em km, não para endereços exatos.
- **Sessão**: não há *refresh token* nem revogação/logout no servidor; o token vale até expirar (1 h por padrão). Troca de senha e "sair de todos os dispositivos" dependem disso.
- **Recuperação de senha e verificação de e-mail** ainda não existem.
- **Rate limit em memória**: não é compartilhado entre réplicas.
- **Bloqueio de conta** permite que terceiros travem temporariamente uma conta alheia (contrapartida conhecida dessa defesa).
- **CSP do frontend** usa `'unsafe-inline'` por causa da hidratação do Next; o próximo passo é CSP com *nonce*.
- O cadastro revela se um e-mail já existe (`422`); mitigado pelo rate limit.
- **Pool de ONGs** (distribuir vagas em lote entre várias ONGs) e relatórios de impacto (ESG) estão fora do escopo atual.
- A logo é uma recriação em SVG/texto; substitua pelo arquivo vetorial oficial quando disponível.
