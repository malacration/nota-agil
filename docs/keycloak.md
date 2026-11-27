# Keycloak de producao no Nota Agil

A integracao segue o padrao de `sap-rovema` / `front-sap`: o backend fornece
`GET /auth/config`, o frontend inicializa o adapter oficial antes do bootstrap e envia
o access token a API, que valida a assinatura RS256 com as chaves publicas JWKS do realm.
Nao cria nem altera usuarios, realms ou clients no Keycloak de producao.

## Configuracao

Valores padrao, configuraveis em properties ou variaveis de ambiente:

```properties
keycloak.url=https://sso.gruporovema.com.br
keycloak.realm=rovema
keycloak.client-id=nota-agil
```

Variaveis equivalentes: `KEYCLOAK_URL`, `KEYCLOAK_REALM`, `KEYCLOAK_CLIENT_ID`.
No Compose de homologacao da raiz, os overrides sao `NOTA_AGIL_KEYCLOAK_URL`,
`NOTA_AGIL_KEYCLOAK_REALM`, `NOTA_AGIL_KEYCLOAK_CLIENT_ID`.
Nao e necessario client secret para o login do frontend: client publico com
Authorization Code + PKCE S256. O client `nota-agil` deve existir no realm `rovema`.

O frontend mantem apenas o endereco da API em `/assets/config.json`:

```json
{ "apiUrl": "http://localhost:8091" }
```

No Compose, utiliza `/api` e o Nginx encaminha para o backend. A URL, o realm e o client
do Keycloak vem exclusivamente de `/auth/config`, evitando divergencia entre front e back.
Se a API/configuracao nao estiver disponivel, o frontend mostra erro de inicializacao.

No Keycloak, as Redirect URIs, Web Origins e Post Logout Redirect URIs devem aceitar
o endereco efetivamente usado no navegador, por exemplo `http://localhost:8092/*`
e `http://localhost:8092`. Essa alteracao no servidor de producao nao e feita pelo projeto.

Acesso por IP privado nao funciona: o WAF na frente do SSO bloqueia a tela de login quando
o `redirect_uri` aponta para um IP privado (ex.: `http://172.18.10.76:8092/`, `http://10.x`,
`http://127.0.0.1`), exibindo "This transfer is blocked by a Web Application Firewall".
Publique a homologacao em um nome DNS (ex.: `https://nota-agil-hmg.gruporovema.com.br`)
e cadastre esse endereco no client. Para testes locais use `http://localhost:8092`.

## Validacao no backend

Rotas de aplicacao exigem:

```http
Authorization: Bearer <ACCESS_TOKEN_DO_KEYCLOAK>
```

O backend verifica algoritmo RS256, assinatura, issuer exato do realm, expiracao,
subject e o claim `typ=Bearer`. Tambem exige `azp` (client autorizado) igual a
`nota-agil` ou um client adicional explicitamente permitido. ID tokens, tokens de
outros clients, tokens adulterados e expirados recebem `401` com `WWW-Authenticate: Bearer`.
Ha tolerancia de 30 segundos para diferenca de relogio.

JWKS e carregado sob demanda, com timeout de cinco segundos, cache de cinco minutos
e recarga quando surge um `kid` desconhecido. Como o `kid` vem de um token ainda nao
autenticado, ha um intervalo minimo global entre consultas ao SSO
(`keycloak.jwks-refresh-min-interval`, padrao `30s`, contado a partir da ultima tentativa,
com sucesso ou falha): `kid` desconhecido dentro desse intervalo e recusado sem chamada de
rede, e no maximo uma consulta fica em andamento por vez. Chaves ja em cache sao lidas sem
bloqueio, mesmo durante uma recarga; se o SSO falhar, as chaves antigas continuam valendo.
Uma rotacao real de chave e aceita no maximo apos esse intervalo. Nenhum token, refresh token ou segredo
e registrado em logs. O backend e stateless e nao armazena sessao de usuario.

Cada Bearer recusado gera um `WARN` com metodo, caminho e motivo (por exemplo
`claim 'iss' com valor diferente do esperado`, `claim 'typ' ...`, `Client do token nao permitido`,
`Access token incompleto`, `token expirado`, `assinatura invalida` ou a classe da falha ao
buscar o JWKS), sem o token nem valores de claims. Nao existe usuario local: o Spring nao
gera mais a senha padrao em log.

Somente `GET /auth/config`, `/error` e os preflights `OPTIONS` sao publicos.
O healthcheck do Compose usa `/auth/config`, que nao chama APIs de negocio.
As roles do realm e do client sao disponibilizadas como authorities `ROLE_*`.
Nao foram copiadas as regras de negocio/provisionamento `sap_code` do sistema SAP;
o Nota Agil exige autenticacao, sem criar uma politica de perfis SAP.

O adapter conclui a autenticacao antes de iniciar o router. O primeiro acesso utiliza
`login-required`; o retorno do SSO aceita `fragment` ou `query`, preservando a
validacao de state, nonce e PKCE S256. Se faltar a solicitacao original no navegador,
a aplicacao oferece reiniciar o login, sem redirecionamentos em loop. Guard e
interceptor compartilham uma unica tentativa em memoria, sem flag persistente de login.
O interceptor renova o token antes das chamadas quando necessario e o envia somente
para a URL da API configurada. O logout e o auto-refresh usam `keycloak-angular`.

## Integracoes servidor-servidor

As chamadas recebidas do SoftExpert, inclusive `POST /integracoes/erp/consultas`,
tambem exigem um access token do Keycloak. A API Key do SoftExpert utilizada pelo
backend para chamar o SE e uma credencial distinta e nao autentica a API do Nota Agil.

Quando houver um client de service account no SSO, pode-se permiti-lo explicitamente:

```properties
keycloak.allowed-client-ids[0]=nota-agil-integracao
```

Obter o token pelo fluxo `client_credentials` desse client e envia-lo como Bearer no
POST. Client secret, criacao do client e permissoes devem ser configurados no servidor
de identidade, sem colocar segredos no frontend ou no repositorio.

Exemplo para o SoftExpert: criar o client OpenID Connect `nota-agil-integracao` no
realm `rovema`, com **Client authentication** e **Service accounts roles** habilitados.
O client do frontend `nota-agil` permanece publico e separado. Nao sao necessarias
permissoes administrativas do realm para chamar o Nota Agil.

O SE deve obter o token com:

```http
POST https://sso.gruporovema.com.br/realms/rovema/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

grant_type=client_credentials&client_id=nota-agil-integracao&client_secret=<SECRET_CODIFICADO_PARA_FORM>
```

Usar `access_token` da resposta em `Authorization: Bearer <access_token>` no
`POST /integracoes/erp/consultas`. Solicitar um novo token quando expirar. Se o
conector do SE oferecer OAuth2 client credentials, configurar esses parametros nele;
caso contrario, o fluxo precisa obter e aplicar o token em duas chamadas.

O endereco do backend deve ser alcancavel a partir do servidor do SE. O Compose local
publica portas em `127.0.0.1`; o `localhost` do tenant nao aponta para a maquina local.
O segredo fica armazenado na configuracao protegida da integracao no SE.

Atualmente a allowlist de clients autentica acesso as rotas protegidas em geral.
Nao existe ainda uma regra exclusiva para a service account apenas nessa rota;
restringir essa autorizacao exige implementar uma politica por client/role no backend.

Referencia: [Service accounts no Keycloak](https://www.keycloak.org/docs/latest/server_admin/index.html#_service_accounts).

## Seguranca desativada (somente teste local/homologacao)

> **ATENCAO: somente para teste local ou de homologacao. NUNCA em producao.**
> Nunca coloque esta chave em `application*.properties` versionado (hmg, prd ou outro), em
> Dockerfile ou em Compose versionado. Use apenas variavel de ambiente na maquina de teste.

Para testar o fluxo ponta a ponta sem a service account do Keycloak:

| Variavel de ambiente | Propriedade equivalente | Padrao |
| --- | --- | --- |
| `NOTA_AGIL_SECURITY_DISABLED=true` | `nota-agil.seguranca.desativada=true` | desligada |

A propriedade, quando definida, prevalece sobre a variavel (`nota-agil.seguranca.desativada=false`
desliga mesmo com a variavel ligada). Pelo relaxed binding do Spring a propriedade tambem responde a
`NOTA_AGIL_SEGURANCA_DESATIVADA`; ao procurar a chave na configuracao de um ambiente, procure por
`NOTA_AGIL_SECURITY_DISABLED`, `NOTA_AGIL_SEGURANCA_DESATIVADA` e `nota-agil.seguranca.desativada`.

Os nomes sao especificos do Nota Agil de proposito: `seguranca.desativada` ou um
`SEGURANCA_DESATIVADA` generico (ex.: num arquivo de ambiente compartilhado com o sap-rovema)
**nao** ligam a chave.

Ligada:

- requisicoes **sem** `Authorization` passam como o principal fixo `auth-disabled-local`, sem roles.
  Vale para a API inteira (`/integracoes/erp/consultas`, `/companys`, `/tasks`...): o Nota Agil nao
  tem autorizacao por rota, entao isso equivale a qualquer token valido;
- requisicao **com** `Authorization` continua validada: Bearer invalido segue recebendo `401`
  (o login pelo frontend continua funcionando);
- a consulta ao sap-rovema (`integracao.erp.sap.*`) dispensa as credenciais de service account:
  com `INTEGRACAO_ERP_SAP_URL` definido e sem `TOKENURL`/`CLIENTID`/`CLIENTSECRET`, a chamada vai
  **sem** `Authorization`. O sap-rovema local precisa estar com `SAP_ROVEMA_SECURITY_DISABLED=true`
  (que libera so `POST /contas-pagar/pagamento-parcela/consulta`). Com as credenciais configuradas, o
  token continua sendo obtido e enviado normalmente.

Protecoes:

- **Nao sobe com profile de producao**: chave ligada com algum profile `prd`, `prod`,
  `production` ou `producao` (sem diferenciar maiusculas), ativo (`SPRING_PROFILES_ACTIVE`) ou
  padrao (`SPRING_PROFILES_DEFAULT` / `spring.profiles.default`), falha na subida com
  `nota-agil.seguranca.desativada / NOTA_AGIL_SECURITY_DISABLED=true nao e permitido com o profile [...]`.
  Sem profile de producao nenhum (ex.: producao rodando sem profile e com `application.properties`
  externo) a protecao nao tem como reconhecer producao: confira o `SPRING_PROFILES_ACTIVE`.
- Um `WARN` destacado na subida e um `WARN` por requisicao aceita sem token, com metodo e caminho
  (sem query string nem corpo).

- Com a chave ligada e a chamada ao sap-rovema sem token, um `WARN` na subida avisa
  (`consultas ao sap-rovema vao SEM Authorization (credenciais ausentes: ...)`, so com os nomes das
  propriedades ausentes). Se o sap-rovema responder "integracao nao autorizada", confira esse aviso.

Exemplo (sap-rovema local na porta padrao 8080; o Nota Agil tambem usaria 8080, entao muda a porta):

```sh
NOTA_AGIL_SECURITY_DISABLED=true SPRING_PROFILES_ACTIVE=hmg SERVER_PORT=8091 \
INTEGRACAO_ERP_SAP_URL=http://localhost:8080 ./gradlew bootRun
```

Implementacao: `SegurancaDesativada` e `SegurancaDesativadaFilter` (`infrastructure/security`),
`SapRovemaTokenProvider.semToken`; testes em `SegurancaDesativadaTest` e
`SapSemTokenSegurancaDesativadaTest`.

## Validacao local

```sh
./gradlew test --offline
```

Os testes de seguranca assinam tokens com chaves temporarias e usam JWKS local de teste.
Verificam acesso anonimo, assinatura, issuer, expiracao, client, ID token, CORS,
stateless, roles, cache e rotacao de chaves. Nao fazem login no SSO de producao.
Os testes do frontend cobrem `/auth/config`, callbacks com o adapter real e respostas
simuladas, troca de codigo com PKCE, nonce divergente, state ausente e tentativas de
login concorrentes. Nao usam credenciais nem iniciam sessao no SSO de producao.

## Perfis

Roles do Keycloak no client `nota-agil` (ou de realm), lidas do access token:

| Perfil | Role | Acesso |
|---|---|---|
| Admin | `admin` | Tudo, inclusive a tela "Cache de filiais" |
| Operador | `operador` | So a tela Notas ERP (`/erp/notas-entrada/**`, `/me`), e so nas filiais liberadas para ele |
| Integracao | service account em `keycloak.allowed-client-ids` (azp) | So `/integracoes/...` |

Usuario do front sem `admin` nem `operador` recebe 403 em tudo (o front mostra a tela "Sem acesso").

### Filiais do operador

O Nota Agil pergunta a cada ERP, **com o token do proprio usuario**:

- SAP: `GET /branch` do sap-rovema (mesma regra do SAP: filiais do vendedor, do colaborador ou todas
  para super vendedor). O token do client `nota-agil` precisa ser aceito pelo sap-rovema: claim
  `sap_code` (mapper de atributo do usuario tambem no client `nota-agil`) e uma role do sap-rovema
  que libere `/branch`. Sem isso o operador ve "O SAP recusou seu acesso".
- Dealer: `GET /usuario/empresas` do dealerlenium (empresas cujas roles o usuario tem, ex.:
  `AUTOVEMA_PVH`).

Os BPLIds/ids viram CNPJ pelas filiais de cada ERP e o resultado fica em cache em memoria por
usuario e ERP (`nota-agil.filiais-usuario.cache-ttl`, padrao 12 h; falha nao e cacheada). O admin
invalida tudo ou um usuario na tela "Cache de filiais" (`GET|DELETE /admin/cache-filiais`,
`DELETE /admin/cache-filiais/{login}`); o logout do front chama `DELETE /me/cache-filiais`.
