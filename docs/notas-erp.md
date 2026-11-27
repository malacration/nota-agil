# Notas de entrada dos ERPs (fonte alternativa ao Qive)

Tela **Notas ERP** (`/notas-erp`): o usuario escolhe o ERP (obrigatorio), a empresa e o periodo de
lancamento (ate 31 dias), filtra opcionalmente por fornecedor (CNPJ/CPF) e numero, e seleciona as
notas do contas a pagar que viram task. Tudo sob demanda: nao ha job agendado consultando os ERPs.

A importacao so cria a `CreateTask` (READY). O envio ao SE segue o fluxo atual (job SendWorkFlow ou
Run na tela Tasks). Depois que o processo abre no SE, entra a `UploadPdfErp`:

| ERP | PDF |
| --- | --- |
| SAP | Qive (chave de acesso; sem chave, busca a nota recebida por prestador + numero) -> anexo da nota no SAP -> sem anexo |
| Dealer | Qive (busca a nota recebida por prestador + numero, em NFe/CTe/NFS-e) -> sem anexo |

Sem PDF o processo segue sem anexo (`origemAnexo = NENHUM` na task).

## API

- `GET /erp/notas-entrada/erps`: ERPs, `disponivel`, `habilitado`, `configurado` e `motivo`.
- `GET /erp/notas-entrada/empresas?erp=DEALER`: empresas e `motivoIndisponivel` (Dealer exige o codigo).
- `GET /erp/notas-entrada?erp=&cnpj=&dataInicial=&dataFinal=[&fornecedor=&numero=]`
- `POST /erp/notas-entrada/tasks` `{"erp":"SAP","cnpj":"...","ids":["..."]}`: resultado por nota
  (`CRIADA`, `JA_EXISTE`, `NAO_ENCONTRADA`, `ERRO`). As notas sao relidas no ERP; o front so manda o id.

## Configuracao (somente variaveis de ambiente)

Por padrao a importacao de cada ERP esta ligada. Desligar (so a importacao; a consulta de pagamento
segue a propria configuracao):

```sh
NOTAS_ERP_SAP_ENABLED=false
NOTAS_ERP_DEALER_ENABLED=false
```

Faltando qualquer variavel de conexao, o ERP tambem fica indisponivel; o front mostra o motivo e
desabilita a opcao.

- SAP: `INTEGRACAO_ERP_SAP_URL`, `INTEGRACAO_ERP_SAP_TOKENURL`, `INTEGRACAO_ERP_SAP_CLIENTID`,
  `INTEGRACAO_ERP_SAP_CLIENTSECRET` (ver [consulta-erp-softexpert.md](./consulta-erp-softexpert.md)).
  Endpoints no sap-rovema: `POST /contas-pagar/notas-entrada/listagem` e
  `POST /contas-pagar/notas-entrada/{docEntry}/anexo-pdf` (perfil `integracao_contas_pagar`).
- Dealer: `INTEGRACAO_ERP_DEALER_URL` (URL base do **dealerlenium**, ex.: `http://dealerlenium:8080`).
  Token: `INTEGRACAO_ERP_DEALER_TOKENURL`, `_CLIENTID`, `_CLIENTSECRET`; vazias, usa as do SAP (mesma
  service account, que precisa da role `integracao_contas_pagar`). Opcional:
  `INTEGRACAO_ERP_DEALER_READTIMEOUTSECONDS` (padrao 120).
  Endpoints no dealerlenium: `POST /api/contas-pagar/notas-entrada/listagem` e
  `GET /api/contas-pagar/empresas` (exigem a role `integracao_contas_pagar`).

## Dealer

O Nota Agil nao fala com o WS do Dealer: quem concentra as integracoes com o Dealernet Workflow e o
dealerlenium. Ele usa o metodo `TITULO` em modo `CON` com `Titulo_MovimentoFinanceiro = P`, data
`ENT` (entrada), e agrupa os titulos (parcelas) da mesma nota (valor = soma das parcelas): pela
`Titulo_NotaFiscalCod` ou, sem ela, por pessoa + `Titulo_NotaFiscalNumero`. Titulo sem nota fiscal
aparece como nao importavel. Cancelados (`CAN`) sao ignorados. O Dealer nao informa chave, tipo nem
serie: o tipo vai como `NaoDetectado`.

A empresa e ligada ao Dealer pelo **Codigo no Dealer** (`Titulo_EmpresaCod`) na tela Empresas
(`PUT /companys/{cnpj}/dealer`) ou pelo cadastro de filiais do ERP. Empresa sem codigo nao lista
notas do Dealer.

Nomes de campo do retorno (`Titulo_NotaFiscalCod`, `Titulo_NotaFiscalNumero`,
`Titulo_PessoaDocIdentificador`, `Titulo_DataEntrada`) seguem o manual do WS; conferir com uma
consulta real em homologacao antes de producao.

## Cadastro de filiais do ERP

Na tela Empresas, **Cadastrar filiais do ERP** lista as filiais (SAP: `GET /contas-pagar/filiais` do
sap-rovema, BusinessPlaces com CNPJ; Dealer: empresas com codigo do dealerlenium) e cadastra as
escolhidas (`GET|POST /companys/filiais-erp`). Uma empresa por CNPJ. A empresa so nasce vinculada ao
Qive (`vinculadoQive`) se o CNPJ existir na Qive; sem vinculo, os jobs de busca de notas no Qive nao
sao criados para ela. Exige so a conexao com o ERP (a flag `NOTAS_ERP_*` nao bloqueia o cadastro).

## Duplicidade com o Qive

- NFe/CTe com chave (SAP): id da task = `{chave}-CreateTask`, o mesmo do job do Qive.
- Demais: `{erp}-{id}-CreateTask`. A listagem marca a nota que ja tem `CreateTask` do mesmo prestador
  + numero + tomador (qualquer origem); os jobs do Qive tambem nao criam task para nota ja importada
  de ERP (tipo desconhecido do Dealer e compativel com qualquer tipo).
