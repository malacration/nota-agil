# Consultas de instancias e usuarios do SoftExpert

O backend utiliza a conexao REST existente para listar instancias por usuario iniciador
e o SOAP para consultar detalhes da instancia e o cadastro do iniciador. As consultas
podem usar usuarios diferentes daquele dono da credencial, conforme as permissoes do SE.

## Instancias iniciadas por usuario

```http
GET /workflow?cdUser=2&fgStatus=1&qtLimit=2&fgLanguage=2
```

O backend encaminha para `GET /apigateway/se/exp/chatbot/api/instance.php`, com `cdProduct=39`.
Retorna `{ "total": N, "results": [...] }`, com identificador, titulo, estado e situacao.

- `cdUser` e o codigo interno `ADUSER.CDUSER` do **iniciador**, nao login, matricula ou executor.
- `cdUser` tambem pode vir no header homonimo, preservando o contrato anterior. O parametro
  da URL tem precedencia sobre o header.
- `fgStatus=1` foi confirmado na homologacao como em andamento.
- `qtLimit` e opcional e deve ser positivo. O total retornado pode refletir esse limite;
  nao presumir que seja uma contagem global ou que exista paginacao automatica.
- `fgLanguage=2` retorna rotulos em portugues; o parametro e opcional.
- Usuario sem instancias retorna `200` com lista vazia. Isso nao comprova que o usuario exista.
- A API nao documenta filtro de instancias por modelo de processo nem listagem global sem iniciador.

Esta consulta nao lista tarefas atribuidas ao usuario e nao faz varredura automatica de codigos.

## Detalhes da instancia

```http
GET /workflow/FXP-ADT003275
```

Executa SOAP `getWorkflow` em `/apigateway/se/ws/wf_ws.php`. Exemplo de campos retornados:

```json
{
  "workflowId": "FXP-ADT003275",
  "title": "Titulo da instancia",
  "processId": "FXP-ADT",
  "instanceStatus": "1",
  "workflowStatus": "sitprestcont - Prestacao de Contas",
  "starterUser": "001",
  "starterExternalUser": null,
  "startDate": "2026-07-27 14:27:08"
}
```

`starterUser` e a **matricula** que deve ser utilizada na consulta de cadastro abaixo.
Os estados/datas sao mantidos no formato retornado pelo SE.

## Cadastro de usuario por matricula

```http
GET /softexpert/usuarios/001
```

Executa SOAP `getUserData` em `/apigateway/se/ws/adm_ws.php`, com `iduser=001`.
Retorna `matricula`, `login`, `nome`, `email`, `telefone` e listas de `areas`, `funcoes`
e `equipes`, cada vinculo contendo `id` e `nome`. Preserva os zeros a esquerda.

Nao e uma listagem completa de usuarios: exige uma matricula conhecida. Na homologacao,
o exemplo `cdUser=2` foi relacionado a `starterUser=001`; nao presumir que esses codigos
sejam iguais ou que a mesma correspondencia exista em outro ambiente.

O WSDL publicado descreve `getUserData.data` como lista de strings, mas a resposta real
contem `data/item` com campos nomeados e listas de vinculos. Por isso, as duas consultas
SOAP usam XML explicito e DTOs, sem editar stubs gerados. O parser desabilita DTD,
entidades externas e XInclude. O titulo da instancia aceita `WorkflowTitle`, retornado
pela homologacao, e `Title`, descrito no WSDL local.

## Configuracao e erros

As rotas do backend exigem `Authorization: Bearer <access_token>` do Keycloak.
Essa credencial do usuario e diferente da API Key utilizada na conexao com o SE.
Veja [keycloak.md](./keycloak.md).

Utiliza as propriedades ja existentes:

- `softexpert.api.url`: URL do ambiente.
- `softexpert.api.authorization`: API Key enviada diretamente em `Authorization`, inclusive
  nas consultas SOAP. Nao acrescentar `Bearer` se o valor atual nao o utiliza.

Nao ha novas credenciais. Os clientes de consulta usam a base REST configurada;
as integracoes SOAP anteriores continuam com sua configuracao propria.

Parametros invalidos retornam `400`. Falhas logicas SOAP de usuario sao mapeadas por
codigo: `12` para `404`, `56` para `400` e `71` para `403`. Outras falhas, SOAP Fault,
XML inesperado e recusa HTTP do servico remoto retornam `502`.

## Validacao

```sh
./gradlew test --offline
```

Teste opcional somente de leitura contra a homologacao (nao movimenta workflows):

```sh
SOFTEXPERT_READONLY_IT=true ./gradlew test --offline --tests '*SoftExpertConsultaReadOnlyIntegrationTest'
```

Esse teste utiliza o perfil `hmg`, exige `cdUser=2` com ao menos uma instancia em andamento
e consulta o iniciador encontrado. Nao habilitar `SOFTEXPERT_IT` para executa-lo: essa outra
variavel ativa os testes anteriores que criam documentos e movimentam workflows.

Referencias:
- https://developer.softexpert.com/docs/openapi/rest/workflow/get-instance
- https://developer.softexpert.com/docs/3.0.0/data-integration/reference/api-rest/rest-api-resources/workflow/instance
- https://developer.softexpert.com/docs/3.0.0/data-integration/reference/web-service-soap/administration/getUserData
