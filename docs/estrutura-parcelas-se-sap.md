# Parcelas FXP-NA e consulta de pagamento SAP

Investigacao em 12/11/2026, somente leitura, na instancia de homologacao
`https://rovema-test.softexpert.app`. Nenhum formulario, pagamento ou atividade foi
alterado. SAP foi analisado pelo codigo de `sap-rovema` e documentacao oficial;
nao houve consulta ao SAP de producao.

## Estrutura confirmada no SoftExpert

O MCP confirmou o processo aprovado `FXP-NA`, nome `Fluxo de Pagamento - Nota Agil`.
O SOAP `getTableRecord`, em `/apigateway/se/ws/fm_ws.php`, retornou `SUCCESS` para
as tabelas `fxpna` e `fxpnagridparcel`. A leitura inicial foi limitada a pagina 1;
depois foram feitas consultas filtradas de uma instancia e suas tres parcelas,
confirmando que todos os relacionamentos apontam para o OID do mesmo formulario.
Os dados financeiros reais nao foram incluidos neste documento.

| Tabela | Campo | Evidencia / uso |
| --- | --- | --- |
| fxpna | OID | Identificador do registro principal |
| fxpna | IDPROCESS | Identificador da instancia FXP-NA |
| fxpna | IDLANCERP | Referencia do lancamento no ERP; ha valor numerico na amostra. Nao foi comprovado que representa DocEntry SAP |
| fxpna | CPFCNPJTOMADOR / CNPJPRESTADOR | Empresa tomadora e fornecedor |
| fxpna | NUMDOCFISCAL / TIPODOC | Identificacao fiscal |
| fxpna | TOTPARCEL / PAGAGUARDANDO | Campos encontrados; nao alterar sem verificar suas regras de calculo |
| fxpnagridparcel | OID | Chave exata da linha a atualizar |
| fxpnagridparcel | IDSOLICITACAO | Instancia FXP-NA da parcela |
| fxpnagridparcel | NUMPARCEL | Numero da parcela |
| fxpnagridparcel | VALORPARCEL | Valor da parcela |
| fxpnagridparcel | DTVENCPARCEL | Data de vencimento |
| fxpnagridparcel | DTAGEND | Data preenchida nos registros marcados |
| fxpnagridparcel | CHECKPAGAGEND | Valores 0 e 1 confirmados; o identificador mistura pagamento/agendamento |
| fxpnagridparcel | Arquivo | Campo de arquivo; comprovantes presentes nas parcelas marcadas da amostra |
| fxpnagridparcel | NMUSERUPDATE / BNCREATED / BNUPDATED | Metadados presentes; nao gravar manualmente |

O relacionamento no grid e `relgridfxpna`, cujo campo e `OIDABCMLR8H3U05OJA`.
Seu valor e o OID do formulario principal `fxpna`. O arquivo aparece no retorno como
`FileField` com `FieldID=Arquivo`, `FileName` e `FileHash`; nao foi baixado.

O checkbox e a data, por si, nao comprovam que a regra significa exclusivamente
"pago": a API nao expoe rotulos, obrigatoriedade nem regras do designer. E necessario
validar se o mesmo marcador tambem representa agendamento e se o comprovante e
obrigatorio antes de habilitar a baixa automatica.

## Atualizacao da parcela

O WSDL de Workflow e a documentacao oferecem `editChildEntityRecord`, com:

```text
WorkflowID          = identificador da instancia
MainEntityID        = fxpna
ChildRelationshipID = relgridfxpna
ChildRecordOID      = OID da linha em fxpnagridparcel
EntityAttributeList = campos especificos da linha
EntityAttributeFileList = Arquivo, quando houver comprovante
```

Esses parametros identificam a linha descendente. A chamada de edicao nao foi
executada. O metodo atual `ConsultaErpRetornoService.registrarResultado` registra
comentario e, opcionalmente, campos principais; ainda precisa ser estendido para
atualizar o grid. A atividade so deve ser avancada depois da confirmacao da gravacao.

Antes de atualizar, conferir que `IDSOLICITACAO` e `relgridfxpna` correspondem ao
workflow e ao registro principal esperados. Nao escolher a linha apenas pelo valor
ou pelo numero da nota, pois pode haver parcelas com valores iguais.

Proposta de campos adicionais da notificacao (ainda nao implementados no POST):

```json
{
  "workflowId": "FXP-NA<instancia>",
  "solicitacaoId": "<identificador-estavel-desta-passagem>",
  "oidParcela": "<OID-da-linha-fxpnagridparcel>",
  "oidFormulario": "<OID-do-registro-fxpna>",
  "idlancerp": "<referencia-IDLANCERP>",
  "numeroparcela": "1",
  "valorparcela": 125.50,
  "dtvencparcel": "2026-12-10"
}
```

Os demais campos de tomador, prestador e nota permanecem necessarios para validar
a correspondencia no ERP. O ID estavel deve ser reutilizado em retries do mesmo envio.

## Reaproveitamento do sap-rovema

| Codigo | O que fornece |
| --- | --- |
| services/documents/PurchaseInvoiceService.kt | Leitura de `/b1s/v1/PurchaseInvoices` |
| services/bank/VendorPaymentService.kt | Leitura de `/b1s/v1/VendorPayments`; `getPaymentBy` consulta pagamentos ligados a um DocEntry |
| resources/views/pagamento-by-docEntry.sql | Relaciona OVPM e VPM2 pelo pagamento e filtra a nota por DocEntry |
| model/bank/PaymentInvoice.kt | DocEntry da nota, InstallmentId, InvoiceType e SumApplied |
| model/sap/documents/base/Installment.kt | Parcela, vencimento, total e status; atualmente nao mapeia PaidToDate |

O metodo `getPaymentBy` usa:

```http
GET /b1s/v1/SQLQueries('pagamento-by-docEntry.sql')/List?DocEntry=<doc-entry-nota>
```

Depois busca os documentos em `VendorPayments` excluindo cancelados. O SQL atual
filtra somente a nota: nao filtra a parcela nem o tipo do documento. Para a integracao
de baixa, conferir os itens `PaymentInvoices` pelo DocEntry da nota, tipo de documento
e InstallmentId exato. Para notas de entrada, o tipo e `it_PurchaseInvoice` / 18.
O DocEntry do pagamento e diferente do DocEntry da nota.

A referencia oficial confirma:

- `PCH6`: parcelas de notas de entrada, chave DocEntry + InstlmntID; valores InsTotal,
  PaidToDate e Status O/C.
- `VPM2`: aplicacao de pagamentos, DocEntry da nota, InvType, InstId e SumApplied;
  `DocNum` referencia OVPM.
- `OVPM`: cabecalho do pagamento efetuado; excluir cancelados.

O saldo precisa ser avaliado na parcela, com a moeda e eventuais descontos/retencoes,
sem concluir quitacao apenas por existir um pagamento. Confirmar os campos expostos
no `$metadata` do Service Layer utilizado antes de estender o DTO de parcelas.
`PaymentOrdered`/agendamento e diferente de valor efetivamente pago.

Tambem falta confirmar como `IDLANCERP` foi preenchido no SE. O endpoint atual de
criacao de nota no SAP (`nf-entrada/criar`) retorna uma mensagem e nao devolve o
DocEntry, portanto esse codigo nao prova a correspondencia de IDLANCERP.

## Pontos para a implementacao real

1. Notificacao inclui OID da parcela, referencia ERP e numero/vencimento da parcela.
2. Estrategia SAP valida empresa, fornecedor, documento e parcela antes de confirmar
   quitacao; trata pagamentos parciais, cancelados e referencia ambigua.
3. Definir comportamento de parcela ainda aberta: aguardar/reconsultar ou seguir falha
   manual. Consulta tecnica bem-sucedida nao implica pagamento confirmado.
4. Confirmar significado do checkbox, data e obrigatoriedade/origem do comprovante.
5. Persistir o resultado, atualizar a linha do grid, confirmar e somente depois mover
   a atividade, preservando idempotencia e as tentativas da fila existente.

## Consulta implementada

Implementado em 16/11/2026 o ponto 2 acima. Em 17/11/2026, regras do financeiro: valor
**liquido**, pagamento a maior conta como pago e, no ponto 3, parcela em aberto (e nota ainda
nao lancada) aguarda reconsulta por ate 96 h antes do fluxo manual; falha tecnica ganha ate 3
novas tentativas em ate 24 h (ver
[Espera por pagamento](./consulta-erp-softexpert.md#espera-por-pagamento-e-novas-tentativas)).
Os demais pontos seguem abertos. O SAP so entra
no catalogo de ERPs com as variaveis `INTEGRACAO_ERP_SAP_*` configuradas; o Dealer segue
esqueleto. Codigos, `dados` e configuracao em
[consulta-erp-softexpert.md](./consulta-erp-softexpert.md#consulta-sap-contas-a-pagar).

### Escolha do caminho

Service Layer via sap-rovema onde as entidades atendem; o resto pelo sap-odbc (gateway SQL
somente leitura, ja usado pelo sap-rovema no painel de vendas e em outras telas):

| Necessidade | Entidade resolve? | Usado |
| --- | --- | --- |
| Filial por CNPJ | sim (`BusinessPlaces.FederalTaxID`) | entidade, comparacao sem mascara no sap-rovema |
| Fornecedor por CNPJ | nao: CNPJ na sub-colecao `BPFiscalTaxIDCollection` | sap-odbc `odbc/contas-pagar-fornecedor.sql` |
| Nota por chave/numero/filial/fornecedor/modelo | em parte (`PurchaseInvoices`), mas OPCH.Model e U_ChaveAcesso so foram conferidos no HANA | sap-odbc `odbc/contas-pagar-notas-chave.sql` e `contas-pagar-notas-numero.sql` |
| PaidToDate, Status e retencao da parcela, moeda | nao: `DocumentInstallments` nao expoe | sap-odbc `odbc/contas-pagar-parcelas.sql` |
| Pagamentos aplicados na parcela | nao: `VendorPayments` nao filtra por `PaymentInvoices` | sap-odbc `odbc/contas-pagar-pagamentos.sql` |

Por que sap-odbc e nao view `SQLQueries`: toda view em `sap-rovema/src/main/resources/views/`
e gravada no SAP na subida (`ProvisioningConfiguration` -> `QuerysServices.replace`) como
consulta global identificada so pelo nome do arquivo, criando-a ou **sobrescrevendo** outra de
mesmo nome. Isso e escrita no SAP a cada deploy. O sap-odbc so executa `SELECT` validado
(JSqlParser, sem comentario, uma instrucao, palavras proibidas, conexao read-only, usuario de
banco somente leitura) com bind parameters; nada e criado no SAP. O sap-odbc nao tem lista de
consultas nomeadas: o SQL vai no corpo da chamada, e os tres arquivos ficam versionados no
sap-rovema (`resources/odbc/`), lidos por `SqlResource` (que tira os comentarios `--`).
Conferidos com o `ReadOnlySqlValidator` do sap-odbc (validacao e parametros); nao foi preciso
alterar o sap-odbc.

### Consultas

```text
BusinessPlaces (SL)              -> BPLID com FederalTaxID (so digitos) = cnpjFilial
contas-pagar-fornecedor.sql      SELECT DISTINCT OCRD.CardCode FROM CRD7 JOIN OCRD
                                 WHERE CardType = 'S' AND TaxId0 sem . / - e espaco = :cnpj
contas-pagar-notas-chave.sql     SELECT OPCH.DocEntry, DocNum, Serial, SeriesStr, CardCode, BPLId, DocDate,
                                   CANCELED, Model, U_ChaveAcesso
                                 FROM OPCH WHERE U_ChaveAcesso = :chave AND CANCELED = 'N'
contas-pagar-notas-numero.sql    (mesmas colunas) WHERE Serial = :numero AND CardCode = :cardCode
                                   AND CANCELED = 'N'   -- uma consulta por CardCode do CNPJ
contas-pagar-parcelas.sql        SELECT PCH6.InstlmntID, DueDate, InsTotal, PaidToDate, Status, WTSum,
                                   (SELECT SUM(PCH5.WTAmnt) WHERE PCH5.AbsEntry = DocEntry
                                      AND PCH5.Category = 'P') AS RetencaoPagamento,
                                   OPCH.DocCur, OADM.MainCurncy
                                 FROM PCH6 JOIN OPCH CROSS JOIN OADM WHERE PCH6.DocEntry = :docEntry
contas-pagar-pagamentos.sql      SELECT OVPM.DocEntry, DocNum, DocDate, VPM2.InstId, SumApplied, WtAppld
                                 FROM VPM2 JOIN OVPM ON OVPM.DocEntry = VPM2.DocNum
                                 WHERE VPM2.DocEntry = :docEntry AND InvType = 18
                                   AND InstId = :parcela AND Canceled = 'N'
```

Todos os valores viajam como bind parameter do sap-odbc; numero e filial sao inteiros
validados, CNPJ tem 14 digitos, a chave e so letras/digitos e CardCode vem do proprio SAP.
Resultado truncado pelo sap-odbc e erro (nunca se confere com parte das linhas). Filial e
fornecedor da nota achada pela chave, modelo e serie sao filtrados no sap-rovema; CANCELED 'Y'
(cancelada) e 'C' (documento de cancelamento) nao contam. Os pagamentos sao refiltrados pela
parcela (`InstId`). Regras de chave, modelo, numero longo de NFS-e e serie: ver
[consulta-erp-softexpert.md](./consulta-erp-softexpert.md#consulta-sap-contas-a-pagar).

### Regras de seguranca da confirmacao

- **Serie exata (NFe/CTe/sem tipo)**: com `seriedocfiscal` (ou a serie da chave), a nota precisa
  ter a mesma serie (sem espacos, caixa e zeros a esquerda: "001" = "1", "000" = "0"). Serie "0"
  nao casa com nota sem serie. NFS-e: serie so desempata.
- **Chave e modelo**: chave incoerente com o pedido ou achada em nota de outro numero/modelo vira
  `DADOS_NOTA_DIVERGENTES`; com tipo, so notas do modelo do tipo; NFS-e casada so pelos 9 ultimos
  digitos exige a chave da nota no SAP com o numero completo (senao `AMBIGUO`).
- **Moeda**: InsTotal, PaidToDate e SumApplied sao da moeda local e o SE nao informa moeda.
  Nota com `DocCur` diferente de `OADM.MainCurncy`, ou com qualquer das duas ausente, vira
  `MOEDA_NAO_SUPORTADA` antes de comparar valores.
- **Total valido**: InsTotal ausente, zero ou negativo vira `VALOR_DIVERGENTE`, nunca `PAGO`.
- **Base liquida**: o SE manda o liquido. Retencao de categoria Fatura ja esta fora do
  InsTotal (o DocTotal e as parcelas ja sao liquidos dela), entao `PCH6.WTSum` e ignorado quando
  o documento nao tem retencao de categoria Pagamento. Com retencao de categoria Pagamento
  (`PCH5.Category = 'P'`, descontada so no pagamento e por isso dentro do InsTotal), liquido =
  InsTotal - `PCH6.WTSum` da parcela, desde que todas as parcelas tenham WTSum >= 0 e a soma
  bata com o total P do documento (tolerancia por parcela); senao `RETENCAO_NAO_SUPORTADA`.
  Liquido <= 0 vira `VALOR_DIVERGENTE`. Por que nao `OPCH.DocTotal`/`WTSum` do cabecalho: o
  pedido e por parcela, e o `WTSum` do cabecalho mistura as duas categorias.
- **Pago ao fornecedor**: soma de `VPM2.SumApplied - VPM2.WtAppld` (OVPM nao cancelado, desta
  parcela). `SumApplied` e o que baixou a parcela e inclui a retencao descontada no pagamento
  (`WtAppld`). Linha com `WtAppld` ausente ou negativo nao conta (falha fechada). Se `WtAppld`
  tiver outro significado num ambiente, o erro so reduz o pago (vira `PAGO_PARCIAL` /
  `BAIXADA_SEM_PAGAMENTO`, manual), nunca cria `PAGO`.
- **Pagamento efetivo**: `PAGO` so com o pago ao fornecedor **e** PaidToDate cobrindo o
  liquido. "Cobrir" = >= liquido - tolerancia, mas nunca abaixo do liquido quando ele e menor ou
  igual a tolerancia; como o liquido e > 0, zero nunca cobre. Sem teto: juros/multa pagos junto
  (pago acima do liquido) continuam `PAGO`. Tolerancia `contas-pagar.tolerancia-valor` aceita de
  0 a 0,10 (padrao 0,01).
- **BAIXADA_SEM_PAGAMENTO**: PaidToDate cobre o liquido, mas o pago nao (devolucao,
  reconciliacao, desconto, lancamento manual). Retencao deixou de cair aqui.
- **Defesa no Nota Agil**: `PAGO` sem parcela com valor positivo, sem pagamentos positivos ou
  com pago abaixo do liquido menos R$ 0,10 e tratado como `ERRO_CONSULTA_ERP`.

### Validacao

Testes unitarios e de contrato cobrem cada codigo (inclusive centavo, total ausente/zero,
limites de tolerancia, moeda estrangeira/desconhecida e serie zero), o filtro OData, os
parametros dos SQL contra o contrato do sap-odbc e o perfil de acesso. **Nada foi executado
contra SAP real** (nem homologacao): falta confirmar que os SQL rodam no HANA pelo sap-odbc (nomes de coluna
`PCH6.Status`, `PCH6.WTSum`, `PCH5.Category`/`WTAmnt`/`AbsEntry`, `VPM2.WtAppld`,
`OPCH.DocCur`, `OADM.MainCurncy`, conferidos so na referencia do SDK), e conferir algumas notas
conhecidas com o financeiro: paga, parcial, aberta, paga com juros, com retencao de categoria
Fatura, com retencao de categoria Pagamento (uma e varias parcelas).

### Questoes em aberto

- **NFS-e sem chave com numero curto (anterior a chave de acesso, nao e regressao)**: sem chave no
  pedido, NFS-e cujo numero cabe em 9 digitos (numeracao municipal que reinicia a cada ano) ainda
  pode casar a nota do ano anterior com o mesmo `Serial` quando a deste ano nao foi lancada; so a
  conferencia de valor protege, e servico mensal recorrente costuma ter o mesmo valor. Fechar exige
  uma janela de `DocDate` (data de emissao no pedido do SE); nao implementado. Com chave no pedido,
  o numero vem da chave e a confirmacao do numero completo se aplica.

## Referencias

- [SE: getTableRecord](https://developer.softexpert.com/docs/2.2.4/data-integration/reference/web-service-soap/form/getTableRecord)
- [SE: editChildEntityRecord](https://developer.softexpert.com/docs/2.2.4/data-integration/reference/web-service-soap/workflow/editChildEntityRecord)
- [SAP: PCH6](https://help.sap.com/doc/089315d8d0f8475a9fc84fb919b501a3/10.0/en-US/SDKHelp/PCH6.html)
- [SAP: VPM2](https://help.sap.com/doc/089315d8d0f8475a9fc84fb919b501a3/10.0/en-US/SDKHelp/VPM2.html)
- [SAP: PCH5 (retencao do documento, Category I/P)](https://help.sap.com/doc/089315d8d0f8475a9fc84fb919b501a3/10.0/en-US/SDKHelp/PCH5.html)
- [SAP: categorias de retencao (Fatura x Pagamento)](https://help.sap.com/docs/SAP_BUSINESS_ONE/68a2e87fb29941b5bf959a184d9c6727/cc13e454c2656574e10000000a44538d.html)
- [SAP: parcelas e PaidToDate](https://help.sap.com/doc/089315d8d0f8475a9fc84fb919b501a3/10.0/en-US/SDKHelp/SAPbobsCOM~Document_Installments_members.html)
