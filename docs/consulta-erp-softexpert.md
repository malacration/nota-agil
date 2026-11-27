# Consulta de pagamento no ERP a partir do SoftExpert

O SoftExpert registra uma solicitacao quando o processo entra na integracao de baixa.
O POST apenas persiste a tarefa no MongoDB; o job consulta o ERP e entrega o resultado
posteriormente. Nao e preciso listar tarefas de usuarios no SE.

SAP e Dealer sao implementacoes independentes de `ConsultaErpClient`. O SAP consulta
contas a pagar pelo sap-rovema (ver [Consulta SAP](#consulta-sap-contas-a-pagar)); sem
configuracao retorna `ERP_NAO_CONFIGURADO`. O Dealer ainda e esqueleto e retorna
`ERP_NAO_IMPLEMENTADO`. CNPJ de tomador sem mapeamento retorna `CNPJ_NAO_MAPEADO`.
Excecao na consulta retorna `ERRO_CONSULTA_ERP`. Somente o codigo `PAGO` segue a acao de
sucesso; todos os demais seguem a acao de falha para insercao manual. Antes disso, resultados
que ainda podem mudar (parcela em aberto, nota ainda nao lancada) aguardam reconsulta por ate
4 dias (inclusive durante indisponibilidade do ERP) e falhas tecnicas nas primeiras consultas
ganham novas tentativas por ate 24 h: ver
[Espera por pagamento e novas tentativas](#espera-por-pagamento-e-novas-tentativas).

## ERP de pagamento por empresa

O cadastro de empresas permite vincular um ERP para consulta de pagamento ou selecionar
`Nenhum`. O catalogo `GET /companys/erps-disponiveis` retorna somente clients com
`consultaPagamentoImplementada=true`. O SAP entra no catalogo somente quando as quatro
variaveis `INTEGRACAO_ERP_SAP_*` estao preenchidas; o Dealer ainda e esqueleto. Sem essa
configuracao (padrao em hmg e prd) a unica escolha e `Nenhum`. O backend tambem rejeita vinculos
nao implementados com `400`, tanto no cadastro como na atualizacao.

No `POST /companys`, o campo opcional e `erpPagamento`. Para alterar apenas o vinculo:

```http
PUT /companys/{cnpj}/erp
Content-Type: application/json
Authorization: Bearer <ACCESS_TOKEN_DO_KEYCLOAK>

{"erpPagamento": null}
```

O job procura a empresa pelo CNPJ do tomador a cada nova consulta, sem exigir reinicio
apos alterar o ERP. Uma empresa cadastrada com `Nenhum` nao usa o mapeamento legado
de properties. Esse mapeamento continua como fallback para CNPJs sem empresa cadastrada.
Resultados ja consultados e persistidos nao sao refeitos ao mudar o vinculo.

Para disponibilizar um novo metodo, implementar a consulta real e marcar
`consultaPagamentoImplementada=true` no client correspondente. O catalogo passa a
oferece-lo sem duplicar a lista de ERPs no frontend.

## Consulta SAP (contas a pagar)

A solicitacao do FXP-NA e tratada como **contas a pagar**: a filial (tomador) recebeu a nota
do fornecedor (prestador) e o SE controla o pagamento. Tabelas SAP: OPCH (nota de entrada),
PCH6 (parcelas), VPM2/OVPM (pagamentos efetuados).

Caminho: Nota Agil -> sap-rovema `POST /contas-pagar/pagamento-parcela/consulta` -> Service
Layer (filial) + sap-odbc (fornecedor, nota, parcelas e pagamentos). Nada e gravado no SAP,
nem na subida do sap-rovema. Detalhes da consulta em
[estrutura-parcelas-se-sap.md](./estrutura-parcelas-se-sap.md#consulta-implementada).

| Etapa | Fonte |
| --- | --- |
| Filial pelo CNPJ do tomador | Service Layer, entidade `BusinessPlaces`, `FederalTaxID` comparado sem mascara |
| Fornecedor pelo CNPJ do prestador | sap-odbc, `odbc/contas-pagar-fornecedor.sql` (CRD7.TaxId0 so digitos, OCRD.CardType = 'S') |
| Nota | sap-odbc, `odbc/contas-pagar-notas-chave.sql` (OPCH.U_ChaveAcesso) e `odbc/contas-pagar-notas-numero.sql` (OPCH.Serial + CardCode), nao cancelada (CANCELED = 'N'); filial, modelo e serie filtrados no sap-rovema |

**Localizacao da nota** (regras completas no KDoc de `ConsultaPagamentoParcelaService`, sap-rovema):

- **Chave de acesso primeiro.** Com `chaveacesso` (do SE ou completada, ver abaixo), procura
  `OPCH.U_ChaveAcesso` (campo oficial do TaxPlus; a tabela `DocReceived` do TaxPlus nunca e usada
  por DocEntry). Antes, a chave precisa ser coerente com o pedido: numero (o embutido na chave;
  NFS-e aceita o numero completo ou so os 9 ultimos), serie (chave de 44), tipo e CNPJ do
  emitente = `cnpjprestador`. Achou pela chave: precisa ter o mesmo numero (`Serial`) e modelo.
  Nao achou (nota ainda sem chave no SAP): segue pelo numero, descartando notas com outra chave.
- **Modelo.** Com tipo (da chave ou `tipodoc`), so notas do modelo do tipo (`OPCH.Model` =
  ONFM.AbsEntry; padrao 39 NFe, 45 CTe, 46/60 NFS-e, configuravel no sap-rovema em
  `contas-pagar.modelos.nfe|cte|nfse`). Evita a colisao CTe x NFS-e do mesmo transportador.
- **Numero de NFS-e com mais de 9 digitos** (ex.: `2600000001234`): o SAP grava os 9 ultimos em
  `Serial`. Procura pelos 9 ultimos (e pelo inteiro, se couber em Int) e so aceita a nota se a
  chave NFS-e dela no SAP tiver o numero completo do pedido; sem chave no SAP: `AMBIGUO`.
- **Serie.** NFe/CTe: a de `seriedocfiscal` ou, sem ela, a da chave; filtro exato sem zeros a
  esquerda. NFS-e (serie do RPS/DPS): nunca filtra; so desempata contra `SeriesStr` preenchida
  (vazia em ~72% das NFS-e no SAP).
| Parcela, retencao e moeda | sap-odbc, `odbc/contas-pagar-parcelas.sql` (PCH6: InsTotal, PaidToDate, Status, WTSum; PCH5 categoria P; OPCH.DocCur; OADM.MainCurncy) |
| Pagamentos | sap-odbc, `odbc/contas-pagar-pagamentos.sql` (VPM2 InvType 18 da parcela + OVPM nao cancelado; SumApplied e WtAppld) |

**Valor liquido.** O SE manda o valor LIQUIDO da parcela (`valorparcela`). No SAP B1:

- retencao na fonte (IRRF, PIS/COFINS/CSLL, INSS, ISS) de categoria **Fatura** ja e descontada do
  `DocTotal` e das parcelas: `PCH6.InsTotal` ja e liquido;
- retencao de categoria **Pagamento** (`PCH5.Category = 'P'`) fica dentro do `InsTotal` e so e
  descontada no pagamento. Liquido da parcela = `InsTotal - PCH6.WTSum`, usado somente quando a
  soma de `WTSum` de todas as parcelas bate com o total de `PCH5.WTAmnt` categoria P; senao
  `RETENCAO_NAO_SUPORTADA` (manual);
- o que foi pago ao fornecedor em cada pagamento e `VPM2.SumApplied - VPM2.WtAppld`
  (`SumApplied` inclui a retencao descontada naquele pagamento).

`VALOR_DIVERGENTE` compara `valorparcela` com esse liquido. `PAGO` exige que a soma paga ao
fornecedor (pagamentos nao cancelados da parcela) **e** a baixa no SAP (`PaidToDate`) cubram o
liquido. Nao ha teto: pagamento a maior (juros, multa) continua `PAGO`. Pagamento a menor e
`PAGO_PARCIAL`. Campos e regra conferidos na referencia do SDK (PCH5, PCH6, VPM2); ainda falta
validar com notas reais com retencao em homologacao.

Codigos (`resultado.codigo`):

| Codigo | Significado | Espera? | Acao no SE |
| --- | --- | --- | --- |
| `PAGO` | liquido > 0 e igual ao `valorparcela` (tolerancia); pago ao fornecedor **e** PaidToDate cobrem o liquido (a maior tambem vale) | nao, final na hora | sucesso |
| `PAGO_PARCIAL` | ha valor baixado/pago, abaixo do liquido | sim, ate o prazo | falha |
| `EM_ABERTO` | nada baixado nem pago | sim, ate o prazo | falha |
| `NOTA_NAO_ENCONTRADA` | sem nota nao cancelada para filial + fornecedor + numero (+ serie exata; serie "0" nao casa com nota sem serie); inclui numero nao numerico ou maior que o campo `Serial` | sim, ate o prazo (nota ainda nao lancada) | falha |
| `FORNECEDOR_NAO_ENCONTRADO` | CNPJ do prestador sem cadastro no SAP | sim, ate o prazo (fornecedor novo e cadastrado junto com a nota) | falha |
| `BAIXADA_SEM_PAGAMENTO` | baixada no SAP sem pagamento que cubra o liquido (devolucao, reconciliacao, desconto, lancamento manual). Retencao nao cai mais aqui | nao | falha |
| `VALOR_DIVERGENTE` | InsTotal ou liquido ausente/zero, ou liquido difere do `valorparcela` em mais que a tolerancia (R$ 0,01) | nao | falha |
| `RETENCAO_NAO_SUPORTADA` | retencao de categoria Pagamento cuja divisao por parcela nao fecha com o documento | nao | falha |
| `MOEDA_NAO_SUPORTADA` | nota em moeda diferente da moeda local da empresa, ou moeda desconhecida (o SE nao informa moeda) | nao | falha |
| `AMBIGUO` | mais de uma nota atende (ex.: series diferentes sem `seriedocfiscal`), ou NFS-e casada so pelos 9 ultimos digitos sem chave no SAP para confirmar | nao | falha |
| `DADOS_NOTA_DIVERGENTES` | chave de acesso em formato invalido, ou com numero/serie/tipo/CNPJ do emitente diferentes do pedido, ou achada no SAP em nota de outro numero/modelo | nao | falha |
| `DADOS_NOTA_INSUFICIENTES` | (Nota Agil, sem chamar o SAP) sem chave de acesso e sem tipo, nem no pedido nem na task de criacao: o numero sozinho pode casar documento de outro tipo | nao | falha |
| `PARCELA_NAO_ENCONTRADA` | parcela inexistente, ou `numeroparcela` ausente em nota com varias parcelas | nao | falha |
| `FILIAL_NAO_ENCONTRADA` | CNPJ do tomador sem filial no SAP (cadastro/configuracao) | nao | falha |
| `ERRO_CONSULTA_ERP` | falha tecnica (rede, timeout, token, HTTP 5xx/de erro, sap-odbc indisponivel, resposta invalida, `PAGO` sem evidencia) | sem resultado de negocio ainda: ate 3 novas tentativas em ate 24 h; ja em espera: reconsulta ate o prazo e entrega o ultimo resultado de negocio | falha |
| `ERP_NAO_CONFIGURADO` / `CNPJ_NAO_MAPEADO` / `ERP_NAO_IMPLEMENTADO` | configuracao | nao | falha |

Agendamento de pagamento (assistente nao executado / PaymentOrdered) nao gera OVPM e nao
conta como pago. A tolerancia de R$ 0,01 e so para arredondamento
(`contas-pagar.tolerancia-valor` no sap-rovema, aceita de 0 a 0,10). Ela nunca deixa zero cobrir
a parcela: em parcela menor ou igual a tolerancia exige-se o valor inteiro.

`dados` sempre traz as mesmas chaves (vazias quando nao se aplicam), para que possam ser
mapeadas em `integracao.erp.campos-retorno` sem quebrar o retorno de resultados de falha:
`notaDocEntry`, `notaDocNum`, `notaSerie`, `notaData`, `notaMoeda`, `parcelaNumero`, `parcelaVencimento`,
`parcelaValor` (InsTotal), `parcelaRetencao` (retencao no pagamento da parcela),
`parcelaValorLiquido`, `parcelaValorBaixado` (PaidToDate), `parcelaSaldo` (liquido ainda nao
pago), `parcelaStatus`, `pagamentosValor` (pago ao fornecedor, sem retencao),
`pagamentosQuantidade`, `pagamentoDocEntry`, `pagamentoDocNum`, `pagamentoData` (ultimo
pagamento), `pagamentos` (lista legivel, com pago e retido por pagamento), `notaCriterio`
(`CHAVE_ACESSO`, `NUMERO` ou `NUMERO_TRUNCADO`) e `origemDadosFiscais` (quem forneceu chave/serie/tipo
enviados ao sap-rovema, ex. `REQUISICAO+TASK_NUMERO`; ver o resolver abaixo). No comentario do SE,
chaves vazias sao omitidas.
Os resultados `CNPJ_NAO_MAPEADO`, `ERP_NAO_IMPLEMENTADO` e a falha tecnica gerada fora do client
nao trazem essas chaves; no retorno ao SE, chave padrao ausente vale `""` (o campo mapeado fica
vazio e a entrega segue). Chave que nao e padrao nem de resultado (`sucesso`, `codigo`,
`mensagem`, `erp`) continua sendo erro de configuracao e falha a entrega.

Configuracao (somente variaveis de ambiente; nada versionado):

```sh
INTEGRACAO_ERP_SAP_URL=https://<host-do-sap-rovema>
INTEGRACAO_ERP_SAP_TOKENURL=https://<keycloak>/realms/<realm>/protocol/openid-connect/token
INTEGRACAO_ERP_SAP_CLIENTID=<client-da-integracao>
INTEGRACAO_ERP_SAP_CLIENTSECRET=<segredo-no-cofre>
# opcionais: INTEGRACAO_ERP_SAP_CONNECTTIMEOUTSECONDS (1-30, padrao 10)
#            INTEGRACAO_ERP_SAP_READTIMEOUTSECONDS (5-110, padrao 90)
```

Os tetos de timeout mantem o pior caso (token + consulta + nova tentativa apos 401) abaixo
da reserva de 10 minutos da tarefa. A service account do Keycloak e criada manualmente: ver
[Checklist do Keycloak](#checklist-do-keycloak-para-o-analista).

`INTEGRACAO_ERP_SAP_URL` deve apontar para um endereco **interno** do sap-rovema, que nao
passe pelo proxy publico (Cloudflare corta requisicoes acima de ~1,6 s e a consulta faz 5-6
chamadas ao SAP). Pelo proxy, a consulta tende a virar `ERRO_CONSULTA_ERP` (falha fechada,
nunca `PAGO` falso).

### Checklist do Keycloak para o analista

Ha dois clients distintos; nenhum e criado por codigo. Realm: o mesmo aceito pelo sap-rovema
e pelo Nota Agil (`rovema` em producao).

**A. SoftExpert -> Nota Agil** (`POST /integracoes/erp/consultas`), ja descrito em
[keycloak.md](./keycloak.md#integracoes-servidor-servidor):

1. Client OpenID Connect `nota-agil-integracao`: Client authentication **ON**, Service accounts
   roles **ON**, Standard flow/Direct access grants **OFF**.
2. No Nota Agil: `keycloak.allowed-client-ids[0]=nota-agil-integracao` (o backend confere o `azp`).
3. Segredo do client guardado na configuracao protegida da integracao no SE.

**B. Nota Agil -> sap-rovema** (`POST /contas-pagar/pagamento-parcela/consulta`):

1. Client OpenID Connect novo, sugestao `nota-agil-sap`: Client authentication **ON**, Service
   accounts roles **ON**, Standard flow/Direct access grants/Implicit **OFF**. Nao reutilizar o
   client publico do front (`nota-agil`) nem o do SE.
2. Role `integracao_contas_pagar` atribuida ao **usuario da service account** desse client
   (aba Service accounts roles). O sap-rovema le roles de `realm_access.roles` e de
   `resource_access.<keycloak.client-id do sap-rovema>.roles` (o client do front-sap). Portanto
   a role tem de ser **role de realm** ou **role do client do front-sap**; role criada no proprio
   client `nota-agil-sap` e ignorada. O nome tem de ser exatamente `integracao_contas_pagar`.
   Nao atribuir roles `filial-*` nem outros perfis de tela.
3. Claim `sap_code` (nome em `keycloak.sap-id-claim` do sap-rovema) no access token: mapper
   **Hardcoded claim** no client, Token Claim Name `sap_code`, valor = EmployeeID de um
   colaborador tecnico **ativo** no SAP (OHEM), Claim JSON Type `int` (ou `String` numerico),
   "Add to access token" **ON**. Sem ele o sap-rovema responde 403 ("Acesso via SSO
   incompleto"). Preferir colaborador sem vendedor vinculado (`SalesPersonCode`).
4. Nenhuma audience/`azp` especifica e exigida pelo sap-rovema (valida assinatura RS256, issuer
   do realm e expiracao). O sap-rovema precisa estar com `keycloak.enabled=true` e
   `keycloak.url`/`keycloak.realm` do mesmo realm.
5. Perfil `integracao_contas_pagar` presente nas regras do sap-rovema (`rules.yml`; se as regras
   vierem do SAP, importar pela tela de Regras de Acesso). Ele so libera `POST` desta consulta.
6. No Nota Agil (variaveis de ambiente, nunca em arquivo versionado):
   `INTEGRACAO_ERP_SAP_TOKENURL=https://<sso>/realms/<realm>/protocol/openid-connect/token`,
   `INTEGRACAO_ERP_SAP_CLIENTID=nota-agil-sap`, `INTEGRACAO_ERP_SAP_CLIENTSECRET=<segredo>`,
   `INTEGRACAO_ERP_SAP_URL=<sap-rovema interno>`.
7. Conferencia: pedir um token por `client_credentials` e decodificar (sem colar o token em
   chamado/log): deve ter `iss` do realm, `realm_access.roles` (ou `resource_access`) com
   `integracao_contas_pagar` e `sap_code` numerico. Um `POST` com corpo `{}` deve voltar 400
   (autorizado), nao 401/403.

No sap-rovema, o acesso ao sap-odbc e o mesmo ja usado pelo painel de vendas (`OdbcClient`),
configurado so por variavel de ambiente: `ODBC_BASE_URL` (`odbc.base-url`), `ODBC_API_KEY`
(`odbc.api-key`, enviada no header `X-API-Key`) e `ODBC_QUERY_TIMEOUT_SECONDS`
(`odbc.query-timeout-seconds`, padrao 30; o cliente espera ate 20 s a mais). Falha do sap-odbc
volta `502` do sap-rovema e vira `ERRO_CONSULTA_ERP` aqui.

## Registrar e acompanhar

```http
POST /integracoes/erp/consultas
Content-Type: application/json
Authorization: Bearer <ACCESS_TOKEN_DO_KEYCLOAK>
```

```json
{
  "workflowId": "FXP-NA000001",
  "solicitacaoId": "baixa-1",
  "cpfcnpjtomador": "12345678000190",
  "cnpjprestador": "98765432000100",
  "numdocfiscal": "000123",
  "valorparcela": 125.50,
  "seriedocfiscal": "001",
  "tipodoc": "Nfse",
  "chaveacesso": "<50 digitos da NFS-e nacional, ou 44 da NFe/CTe>",
  "numeroparcela": "1",
  "activityOrder": 1
}
```

Os CNPJs acima sao ilustrativos; nao representam mapeamentos reais.

- `workflowId`: identificador da instancia do SE, nao o ID do modelo de processo.
- `solicitacaoId`: identificador estavel para esta passagem pela etapa. Reutilizar em
  tentativas de envio; gerar outro se o processo retornar a etapa para uma nova consulta.
- `cpfcnpjtomador`, `cnpjprestador`, `numdocfiscal` e `tipodoc`: nomes baseados nos campos
  enviados por `Duplicata.getSoftexpertMap`.
- `valorparcela`: valor LIQUIDO (positivo) da parcela consultada, comparado com o liquido da
  parcela no SAP. Aceita tambem o alias `valorliquido`.
- `seriedocfiscal` e `numeroparcela`: opcionais, preservados como texto, inclusive zeros
  a esquerda. NFe/CTe: a serie da nota; NFS-e: a serie do RPS/DPS.
- `chaveacesso` (alias `chaveAcesso`): opcional, ate 60 caracteres, espacos removidos. NFe/CTe:
  44 digitos; NFS-e nacional: 50 digitos (com ou sem o prefixo `NFS`). Entra na comparacao de
  idempotencia como os demais campos.
- Serie, chave e tipo ausentes no pedido sao completados (`DadosFiscaisNotaResolver`), sem trocar
  o que o SE mandou:
  - `CREATE_TASK`: task `CreateTask` com `recordID` = `workflowId` (a do proprio processo) completa
    tipo, chave e serie. NFe/CTe: chave = `chaveAcesso` gravada, serie = caracteres 23-25 dela.
    So vale se a task confere com o pedido: mesmo `cnpjprestador` (so digitos), mesmo
    `numdocfiscal` sem zeros a esquerda (NFS-e: um lado com mais de 9 digitos tambem casa com o
    outro igual aos 9 ultimos) e, se o pedido traz `tipodoc`/`seriedocfiscal`, mesmo tipo e mesma
    serie (serie comparada sem zeros a esquerda, quando a da task e conhecida pela task ou pelo
    Qive). Nao conferiu: a task e ignorada (log WARN com identificadores mascarados, so os 3
    ultimos caracteres) e segue como se nao houvesse task do processo.
  - `TASK_NUMERO`: sem a task do processo, task `CreateTask` do mesmo prestador + numero + tomador.
    Pode ser OUTRO documento (ex.: CTe e NFS-e de mesmo numero), entao so completa a **chave**, e so
    quando o pedido ja traz `tipodoc` `Nfe` ou `Cte` **e** `seriedocfiscal`, a task tem o mesmo
    tipo e a mesma serie e e a unica candidata (task de serie desconhecida conta como candidata).
    Nunca completa tipo nem serie. **NFS-e nunca usa este passo**: emitente + modelo + serie +
    numero so e unico na SEFAZ (NFe/CTe); numero e serie de RPS/DPS podem repetir e levariam a
    chave de uma nota antiga.
  - `QIVE`: NFS-e de task antiga do proprio processo sem chave/serie gravadas: um GET no Qive por
    id (cache em memoria).
  - Nada encontrado: segue so com o numero se houver tipo (sem serie, notas iguais no SAP dao
    `AMBIGUO`); sem chave e sem tipo: `DADOS_NOTA_INSUFICIENTES`. A origem vai em
    `dados.origemDadosFiscais` (`REQUISICAO`, `CREATE_TASK`, `TASK_NUMERO`, `QIVE`, unidos por `+`;
    `NENHUMA` sem dado algum), para a conferencia manual saber que a chave foi derivada.
- `tipodoc`: opcional; valores `Nfe`, `Nfse`, `Cte`, `NaoDetectado`.
- `activityOrder`: opcional, ordem de habilitacao da atividade no SE. Enviar quando
  disponivel, principalmente em fluxos com repeticao da atividade.

Tambem sao aceitos os aliases `cnpjTomador`, `cnpjFornecedor`, `numeroNota`,
`valorParcela`, `serieNota`, `tipoNota` e `numeroParcela`.
CNPJs podem ser enviados com ou sem a pontuacao padrao. Ha validacao de formato
(14 digitos), nao dos digitos verificadores.

Resposta: `202 Accepted`, corpo com `id`, `workflowId`, `solicitacaoId`, `status`,
`erp`, `resultado`, `tentativasRetorno` e `ultimoErro`. O header `Location` aponta para:

```http
GET /integracoes/erp/consultas/{id}
```

A tela **Tasks** usa uma unica tabela, o endpoint `GET /tasks` e os filtros existentes.
O POST registra uma task nativa do tipo `ConsultaErp`, com o protocolo e os dados da
parcela nos detalhes. Os tipos `CreateTask`, `UploadPdf`, `CancelTask` e `ConsultaErp`
compartilham ordenacao, contagem, paginacao e filtros por tipo, status e tomador.
Os registros ERP anteriores sao recuperados na fila nativa ao iniciar a aplicacao,
sem recriar o protocolo nem mover processos. O endpoint de acompanhamento por ID
permanece disponivel; a listagem tecnica de protocolos nao e usada pela tela Tasks.

O mesmo par `workflowId` + `solicitacaoId`, com os mesmos dados normalizados, retorna a
tarefa existente. Dados diferentes para o mesmo par retornam `409 Conflict`.
Campos invalidos/ausentes retornam `400`; protocolo inexistente, `404`.
Sem ID da atividade configurado ou com acoes invalidas/iguais, novas solicitacoes
retornam `503` sem criar tarefas. Tarefas existentes continuam consultaveis e deduplicadas.

## Configuracao antes de conectar o fluxo

Exemplo de propriedades locais (nao existe atividade ou CNPJ real preconfigurado):

```properties
integracao.erp.atividade-id=ID_REAL_DA_ATIVIDADE_DE_ESPERA
integracao.erp.acao-sucesso=1
integracao.erp.acao-falha=2

# Exemplos: substituir pelos CNPJs efetivamente atendidos por cada ERP.
# integracao.erp.cnpjs[12345678000190]=SAP
# integracao.erp.cnpjs[98765432000100]=DEALER
```

O ID pode ser configurado pela variavel `INTEGRACAO_ERP_ATIVIDADE_ID`.
Os numeros de acao sao sequencias `ActionSequence` do SE, nao identificadores de
atividades de destino. Os defaults 1/2 sao convencoes do esqueleto e devem ser
conferidos no fluxo. O POST guarda uma copia da atividade, acoes e mapeamento de campos
para que mudancas posteriores nao redirecionem tarefas ja aceitas.

O mapa legado de CNPJs inicia vazio. Sem vinculo na empresa ou mapeamento legado,
todas as consultas seguem a falha.
Para implementar o Dealer futuramente, substituir o corpo do client correspondente;
o resultado de sucesso ja tem tratamento e acao proprios.

## Retorno ao SoftExpert

1. Persiste o resultado da consulta antes de qualquer retorno ao SE.
2. Opcionalmente atualiza campos no formulario, via SOAP `editEntityRecord`, usando
   `softexpert.workflow.tableName` como tabela (configuracao ja existente).
3. Registra no historico da atividade um comentario com solicitacao, ERP, sucesso/falha,
   codigo, mensagem e dados retornados.
4. Executa a atividade via SOAP `executeActivity` com a acao de sucesso ou falha.
5. Marca `CONCLUIDA` somente depois de o SE responder `SUCCESS` ao avanco.

Por padrao o resultado e devolvido no comentario, sem exigir novos campos no formulario.
Se desejar campos estruturados, criar os campos no SE e mapear seus identificadores:

```properties
# Somente habilitar apos criar os campos correspondentes no formulario.
# integracao.erp.campos-retorno[sucesso]=erpok
# integracao.erp.campos-retorno[codigo]=erpcodigo
# integracao.erp.campos-retorno[mensagem]=erpmensagem
# integracao.erp.campos-retorno[erp]=erpnome
```

Chaves adicionais de `ResultadoConsultaErp.dados` tambem podem ser mapeadas para campos.
Falha ao gravar campo/comentario impede o avanco. Se o comentario ja foi confirmado
e persistido, uma falha posterior no avanco nao o envia novamente.

Falha em qualquer passo da entrega (campos, comentario ou `executeActivity` recusado) tem novas
tentativas com intervalo crescente e limite (`integracao.erp.retorno-se.*`, padrao: 1 min, 4 min,
16 min, 64 min, 4h16, depois a cada 6 h, dentro de 24 h da primeira falha seguida: 9 envios).
Esgotou: o protocolo vai para `RETORNO_FALHOU`, a task nativa fica `FAILED` (pausada, visivel na
tela Tasks com `ultimoErro`) e nada mais e enviado ao SE. Depois de corrigir no SE, voltar a task
para `READY` retoma a entrega com nova janela de tentativas, sem reconsultar o ERP e sem repetir
comentario ja confirmado (campos sao regravados com o mesmo valor; o comentario so e reenviado
se nunca foi confirmado).

No desenho do processo, a chamada REST pode ocorrer numa atividade de sistema anterior
a atividade de espera. A atividade identificada em `atividade-id` deve permitir as duas
acoes de `executeActivity` (atividade de usuario/decisao), com permissoes para o usuario
de integracao. A chamada inicial deve terminar apos o `202`, deixando o job retornar
pela API. Se a atividade de espera ainda nao estiver habilitada, o retorno fica pendente
para nova tentativa; nao interpretar o `202` como resultado do ERP.

## Espera por pagamento e novas tentativas

O SE so recebe o resultado quando ele e final; reconsultas nao escrevem nada no SE (sem
comentario, campo ou avanco). O comentario final informa quantas consultas houve e, quando as
ultimas falharam tecnicamente, avisa quantas e quando, junto com o horario do resultado entregue.

Na tarefa, `ultimaConsulta`/`ultimaConsultaEm` guardam o ultimo resultado **de negocio**; falha
tecnica vai para `ultimaFalhaTecnica`/`ultimaFalhaTecnicaEm` e nunca apaga o de negocio.

Linha do tempo (padroes):

```text
T0      POST aceito: PENDENTE, esperaPagamentoAte = T0 + 96 h (gravado na tarefa)
T0+~5m  1a consulta (job de 5 min)
        PAGO ................................ resultado final -> acao de SUCESSO
        EM_ABERTO / PAGO_PARCIAL /
        NOTA_NAO_ENCONTRADA /
        FORNECEDOR_NAO_ENCONTRADO ........... ultimaConsulta, reconsulta em +3 h
        ERRO_CONSULTA_ERP ................... nova tentativa em +15 min, +1 h, +4 h; esgotou
                                              (ou passaria de 24 h) -> FALHA com ERRO_CONSULTA_ERP
        demais codigos ...................... resultado final -> acao de FALHA
...     reconsultas a cada 3 h; PAGO em qualquer uma -> SUCESSO na hora
        ERRO_CONSULTA_ERP durante a espera .. NAO encerra: nova tentativa em +15 min, +1 h, depois
                                              a cada 3 h (backoff tecnico limitado ao intervalo
                                              da espera), sem limite de 3/24 h, nunca depois do prazo
T0+96h  ultima reconsulta exatamente no fim do prazo; sem PAGO -> FALHA com o ultimo resultado
        de negocio (mesmo que essa ultima consulta tenha falhado tecnicamente)
depois  entrega ao SE recusada -> novas tentativas 1/4/16/64 min, 4h16, 6 h... em ate 24 h;
        esgotou -> RETORNO_FALHOU / task FAILED ate alguem voltar para READY
```

Falha tecnica: conta falhas **seguidas**; um resultado de negocio no meio zera a contagem.

- **Sem resultado de negocio ainda** (primeiras consultas): depois da primeira falha, ate 3
  novas tentativas com intervalo `intervalo-inicial * multiplicador^(n-1)` (15 min, 1 h, 4 h),
  todas dentro de 24 h da primeira falha seguida; esgotou (ou a proxima passaria das 24 h) ->
  acao de FALHA com `ERRO_CONSULTA_ERP`.
- **Ja em espera** (houve `EM_ABERTO`, `PAGO_PARCIAL`, `NOTA_NAO_ENCONTRADA` ou
  `FORNECEDOR_NAO_ENCONTRADO`): a indisponibilidade do ERP nao encerra a espera. Nova tentativa
  em `min(intervalo tecnico, espera-pagamento.intervalo)` (15 min, 1 h, 3 h, 3 h...), sem o limite
  de 3 tentativas/24 h, mas nunca depois de `esperaPagamentoAte`; a ultima tentativa cai no fim do
  prazo. Se o ERP voltar e der `PAGO`, sucesso na hora. Se a consulta no fim do prazo (ou depois)
  falhar tecnicamente, vai a acao de FALHA com o **ultimo resultado de negocio** e o comentario
  avisa das falhas tecnicas.

Falha ao **entregar** no SE nao reconsulta o ERP: segue `integracao.erp.retorno-se` (ver
[Retorno ao SoftExpert](#retorno-ao-softexpert)).

Configuracao (`integracao.erp.*`, ou variaveis `INTEGRACAO_ERP_*` por relaxed binding;
duracoes no formato Spring, ex.: `96h`, `15m`):

| Chave | Padrao | Limites |
| --- | --- | --- |
| `integracao.erp.espera-pagamento.prazo` | `96h` | 0 a 30 dias; `0` desliga a espera. Contado do aceite e gravado na tarefa: mudar depois nao altera tarefas aceitas |
| `integracao.erp.espera-pagamento.intervalo` | `3h` | 1 min a 1 dia (na pratica o job roda a cada 5 min) |
| `integracao.erp.espera-pagamento.codigos` | `EM_ABERTO,PAGO_PARCIAL,NOTA_NAO_ENCONTRADA,FORNECEDOR_NAO_ENCONTRADO` | nao pode conter `ERRO_CONSULTA_ERP` |
| `integracao.erp.falha-tecnica.tentativas` | `3` | 0 a 10 |
| `integracao.erp.falha-tecnica.intervalo-inicial` | `15m` | 1 min a 24 h |
| `integracao.erp.falha-tecnica.multiplicador` | `4` | 1 a 10 |
| `integracao.erp.falha-tecnica.janela-maxima` | `24h` | ate 24 h (so primeiras consultas; durante a espera vale o prazo) |
| `integracao.erp.retorno-se.intervalo-inicial` | `1m` | 1 min a 1 dia |
| `integracao.erp.retorno-se.multiplicador` | `4` | 1 a 10 |
| `integracao.erp.retorno-se.intervalo-maximo` | `6h` | do intervalo-inicial a 1 dia |
| `integracao.erp.retorno-se.tentativas` | `10` | 0 a 100 novas tentativas (com os padroes, a janela esgota antes: 8) |
| `integracao.erp.retorno-se.janela-maxima` | `24h` | 1 min a 7 dias, contada da primeira falha seguida da entrega |

Valor fora dos limites impede a subida da aplicacao.

Politica para os codigos nao pagos: `NOTA_NAO_ENCONTRADA` e `FORNECEDOR_NAO_ENCONTRADO` aguardam
porque o pedido do SE pode chegar antes do lancamento da nota (e do cadastro do fornecedor
novo) no SAP. `FILIAL_NAO_ENCONTRADA`, `AMBIGUO`, `VALOR_DIVERGENTE`, `PARCELA_NAO_ENCONTRADA`,
`DADOS_NOTA_DIVERGENTES`, `DADOS_NOTA_INSUFICIENTES`,
`MOEDA_NAO_SUPORTADA`, `RETENCAO_NAO_SUPORTADA` e `BAIXADA_SEM_PAGAMENTO` dependem de cadastro
ou de correcao humana e nao mudam sozinhos em poucos dias: vao ao manual na hora, para nao
atrasar a tratativa.

## Job e recuperacao

O worker existente `SendToWorkFlowSchedule-all` consulta as tasks `READY` e chama
`TaskService.executa`. O tipo `ConsultaErp` despacha para a consulta de pagamento,
preservando a reserva atomica e o resultado do protocolo. Nao ha worker ERP paralelo.
Uma falha em uma task nao impede as seguintes. O periodo inicial desse worker
continua em cinco minutos, configuravel pela tela de jobs.

Na fila nativa, `READY` significa aguardando consulta ou nova tentativa de retorno;
`FINISHED` significa que o SE confirmou o retorno e o avanco. Marcar `FAILED` pausa a
execucao automatica; voltar a `READY` permite tentar novamente. O proprio sistema marca
`FAILED` quando a entrega ao SE esgota as novas tentativas (`RETORNO_FALHOU`); voltar a
`READY` pelo `PUT /tasks/{id}/status` reativa o protocolo antes de liberar a task. Run ou job
sobre um protocolo `RETORNO_FALHOU` nao chamam o SE e so reafirmam `FAILED`. O detalhe do
protocolo preserva `PENDENTE`, `RETORNO_PENDENTE`, `RETORNO_FALHOU` ou `CONCLUIDA`, com
resultado e ultimo erro.
`jobs.disableAll=true` bloqueia o agendamento, conforme a estrutura existente.
O botao Run usa a execucao manual existente; nao foi acionado contra o SE nos testes.

Estados na colecao `consulta_erp_task`:

- `PENDENTE`: ainda nao tem resultado final. Pode estar aguardando a primeira consulta ou uma
  reconsulta (`ultimaConsulta` preenchida, `proximaTentativa` no futuro). Campos de
  acompanhamento: `consultas`, `ultimaConsulta`, `ultimaConsultaEm` (ultimo resultado de
  negocio), `ultimaFalhaTecnica`, `ultimaFalhaTecnicaEm`, `esperaPagamentoAte`, `falhasTecnicas`,
  `primeiraFalhaTecnicaEm` (tambem no detalhe da task e no `GET` por ID).
- `RETORNO_PENDENTE`: resultado salvo, aguardando confirmacao do SE (`falhasRetorno`,
  `primeiraFalhaRetornoEm`, `ultimoErro` contam as tentativas da entrega).
- `RETORNO_FALHOU`: entrega esgotada; resultado e `ultimoErro` preservados, task nativa `FAILED`,
  nenhuma chamada ao SE ate a volta manual para `READY`.
- `CONCLUIDA`: resultado e acao entregues; pode ter `resultado.sucesso=false`.

Uma reserva atomica no MongoDB impede processamento simultaneo durante dez minutos;
se o processo morrer, outra rodada pode recuperar a tarefa apos a reserva vencer.
Todo o estado da espera (prazo, proxima consulta, contadores) fica na tarefa, entao reinicios
nao perdem nem reiniciam a espera. Cair entre a consulta e a gravacao so repete a consulta
(somente leitura). Na fila nativa a task segue `READY` durante a espera; o job e o botao Run
nao consultam antes de `proximaTentativa`. Marcar `FAILED` pausa tambem as reconsultas.
Falha no retorno agenda outra tentativa com intervalo crescente (`retorno-se`) e preserva o
resultado do ERP; esgotada, a tarefa para em `RETORNO_FALHOU`.
Uma tarefa com erro nao impede o processamento das seguintes.

O SOAP nao participa da transacao do MongoDB. Se o SE aplicar uma operacao e a resposta
se perder (ou houver queda antes de salvar a confirmacao local), uma repeticao e possivel.
Isso pode duplicar um comentario; o avanco requer atencao em atividades recorrentes.
Usar `activityOrder` quando disponivel e conferir o estado do processo em casos de timeout.
As futuras consultas aos ERPs devem ser somente leitura e ter timeout menor que a reserva.

O endpoint exige um access token do Keycloak, como as demais rotas do backend.
Para chamadas do SoftExpert, configurar um client de integracao e permitir seu `azp`
conforme [keycloak.md](./keycloak.md). Nenhuma credencial e recebida no body; o retorno
ao SE usa as credenciais SOAP ja configuradas no ambiente.

Os testes automatizados usam SOAP simulado e nao movem processos reais.

```sh
./gradlew test --offline
```

Os testes de persistencia/concorrencia sao opcionais e exigem
`NOTA_AGIL_MONGO_IT_URI` apontando para um MongoDB de testes. Criam e removem somente
um banco aleatorio com prefixo `nota_agil_erp_it_` em cada teste:

```sh
NOTA_AGIL_MONGO_IT_URI='mongodb://USUARIO:SENHA@localhost:PORTA/?authSource=admin' \
  ./gradlew test --offline --tests '*ConsultaErpRepositoryIntegrationTest'
```

## Cadastro manual e vinculo posterior com a Qive

`POST /companys` aceita `vinculadoQive=false`, CNPJ e nome para cadastro manual,
sem consultar a Qive. CNPJ e normalizado para 14 digitos. Sem o campo, o default
continua `true` para compatibilidade; nesse caso o CNPJ precisa estar disponivel na Qive.
Empresas antigas sem esse campo continuam vinculadas. O ERP de pagamento e independente.

`PUT /companys/{cnpj}/qive` vincula posteriormente uma empresa manual, verificando se o
CNPJ ja esta disponivel na Qive e preservando nome, CNPJ e ERP. A alteracao registra
os workers Qive existentes (NFe, NFSe e CTe) sem reinicio. Empresas sem vinculo nao
ganham esses workers. O estado de ativacao dos jobs continua sob controle da tela de
jobs; vincular nao habilita automaticamente um job desabilitado.

## Serie e chave de acesso na criacao do processo

Na criacao do processo FXP-NA (task `CreateTask`, `Duplicata.getSoftexpertMap`) o Nota Agil pode
enviar serie e chave de acesso, para o SE devolve-las em `seriedocfiscal` / `chaveacesso` na
consulta de pagamento:

| Tipo | Serie | Chave |
| --- | --- | --- |
| NFe / CTe | caracteres 23-25 da chave (3 digitos, ex. `001`) | `access_key` do Qive (44) |
| NFS-e | `Rps/IdentificacaoRps/Serie` do Qive (RPS/DPS) | `InfNfse/@Id` sem o prefixo `NFS` (50 digitos) ou id `DPS` + 42; id municipal fica sem chave |

Os IDs dos campos no formulario ainda nao existem (card do administrador do SE). Enquanto nao
forem configurados, nada muda no envio:

```properties
# ID do campo no formulario (tabela softexpert.workflow.tableName); vazio = nao envia.
# softexpert.workflow.campoSerie=<id do campo de serie>
# softexpert.workflow.campoChaveAcesso=<id do campo de chave de acesso>
```

Os IDs nao podem ser iguais entre si nem repetir um campo ja enviado (`numdocfiscal`, `tipodoc`...).
Campo sem valor (ex.: NFS-e municipal sem chave) nao e enviado.
