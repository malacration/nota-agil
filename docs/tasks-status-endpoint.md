# Alteracao de status das tarefas

## Objetivo

Permitir que o frontend altere manualmente o status de uma `task` operacional, sem quebrar o fluxo automatizado do backend.

## Endpoint

- Metodo: `PUT`
- URL: `/tasks/{id}/status`
- Content-Type: `application/json`

## Body

```json
{
  "status": "READY"
}
```

## Status aceitos

- `READY`
- `FAILED`
- `FINISHED`

## Regras de transicao

O backend **nao permite qualquer combinacao**. As transicoes manuais aceitas sao:

- `FAILED -> READY`
- `READY -> FAILED`

Casos bloqueados pelo backend:

- `READY -> FINISHED`
- `FAILED -> FINISHED`
- `FINISHED -> READY`
- `FINISHED -> FAILED`

Se a task ja estiver no status informado, a chamada e idempotente e devolve a task sem erro.

## Resposta de sucesso

`200 OK`

```json
{
  "id": "35284765000102-12345-CreateTask",
  "name": "20/05/2026 Nfe Nº 12345 - Empresa X",
  "status": "READY",
  "taskType": "CreateTask",
  "duplicata": {
    "...": "..."
  },
  "lastRunAt": null,
  "recordID": "123",
  "recordKey": "ABC",
  "createdAt": "2026-05-26T12:00:00Z"
}
```

## Respostas de erro esperadas

### Status invalido

`400 Bad Request`

Exemplo:

```json
{
  "mensagem": "Status invalido. Use READY, FAILED ou FINISHED"
}
```

### Transicao nao permitida

`400 Bad Request`

Exemplo:

```json
{
  "mensagem": "Nao e permitido alterar status manualmente de FINISHED para READY. Transicoes permitidas: READY <-> FAILED."
}
```

### Task nao encontrada

`404 Not Found`

Exemplo:

```json
{
  "mensagem": "Task abc nao encontrada"
}
```

## Como o frontend deve usar

### 1. Reprocessar uma task com erro

Quando a task estiver em `FAILED`, enviar:

```json
{
  "status": "READY"
}
```

Isso recoloca a task na fila para o backend processar novamente no scheduler.

### 2. Bloquear temporariamente uma task pendente

Quando a task estiver em `READY`, enviar:

```json
{
  "status": "FAILED"
}
```

Isso impede que o scheduler execute essa task enquanto ela nao for reaberta para `READY`.

### 3. Nao oferecer edicao para task concluida

Se o status atual for `FINISHED`, o frontend deve desabilitar a acao de troca de status.

## Exemplo com fetch

```ts
async function atualizarStatusTask(id: string, status: "READY" | "FAILED" | "FINISHED") {
  const response = await fetch(`/tasks/${id}/status`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ status }),
  });

  if (!response.ok) {
    const erro = await response.json();
    throw new Error(erro.mensagem ?? "Falha ao atualizar status da task");
  }

  return response.json();
}
```

## Recomendacao para a UI

- Mostrar acao `Reprocessar` apenas para `FAILED`, enviando `READY`.
- Mostrar acao `Marcar como falha` apenas para `READY`, enviando `FAILED`.
- Nao mostrar botao de alteracao para `FINISHED`.
- Apos sucesso, atualizar a linha da task na tela com o objeto retornado pela API.
