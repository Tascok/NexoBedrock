# Skill — Handoff do NexoBedrock

Use esta skill ao encerrar uma sessão ou ao preparar contexto para a próxima conversa.

## Objetivo

Produzir um resumo compacto e verificável do estado atual, sem perder decisões, bloqueios, erros ou próximos passos.

## Entrada obrigatória

- `docs/ai/PROJECT_STATE.md`
- `docs/ai/ARCHITECTURE.md`
- `.cursorrules` e `CLAUDE.md`
- `AGENTS.md`
- `git status --short --branch`
- `git diff --stat`, `git diff`, `git diff --cached` e, quando houver mudanças grandes, `git diff -- <arquivo>`

## Procedimento

1. Ler o Memory Bank e confirmar a versão, o branch e o último commit.
2. Identificar no `git diff` e no `git diff --cached` somente mudanças relacionadas à sessão atual; leia separadamente arquivos novos ainda não rastreados e ignore artefatos locais, imagens, logs, backups e segredos.
3. Separar o diff em:
   - funcionalidades/alterações concluídas;
   - decisões técnicas e restrições relevantes;
   - testes executados e resultado;
   - erros conhecidos ou falhas ainda abertas;
   - arquivos alterados que a próxima sessão deve revisar.
4. Atualizar `docs/ai/PROJECT_STATE.md` para refletir o estado real. Não registrar uma tarefa como concluída sem evidência.
5. Compactar `docs/ai/PROJECT_STATE.md` para **menos de 150 linhas**, preservando: identidade do projeto, funcionalidades prontas, limitações/stubs, erros conhecidos, validação e próximos passos priorizados.
6. Se dependências, versões, módulos, classes ou fluxo mudarem, atualizar `docs/ai/ARCHITECTURE.md` (máximo de 120 linhas).
7. Rodar `git diff --check` e revisar `git status --short` antes do handoff.
8. Se a tarefa alterou código, teste ou configuração, validar conforme `AGENTS.md` e registrar o resultado.

## Regras de conteúdo

- Preferir fatos verificáveis no código, no diff e nos testes.
- Não inventar métodos ou versões das APIs Nexo, Geyser ou Floodgate.
- Registrar falhas de compilação/runtime em **Erros conhecidos** de `docs/ai/PROJECT_STATE.md`.
- Manter descrições externas curtas e orientadas a ação; evitar narrar toda a sessão.
- Não copiar tokens, senhas, keystores, logs, dados de jogadores, relatórios de produção, backups ou conteúdo de `Contexto/`.
- Preservar alterações alheias e distinguir claramente “implementado”, “testado” e “validado visualmente”.

## Modelo de estado compacto

Organize `docs/ai/PROJECT_STATE.md` nesta ordem:

1. Identificação: branch, versão, commit/tag, módulos e versões.
2. Funcionalidades implementadas: agrupadas por subsistema.
3. Limitações, pendências e stubs.
4. Erros conhecidos: abertos, operacionais e resolvidos.
5. Validação: comandos, resultado e lacunas.
6. Próximos passos: ordenados por prioridade.
7. Nota de atualização do Memory Bank.

## Prompt curto para a próxima sessão

Adapte apenas o objetivo; preserve as exigências de leitura e atualização:

```text
Leia docs/ai/PROJECT_STATE.md e docs/ai/ARCHITECTURE.md antes de começar. Confira git status --short --branch e siga as regras de CLAUDE.md. Retome o primeiro item pendente priorizado no PROJECT_STATE.md, sem inventar APIs do Nexo, Geyser ou Floodgate. Ao terminar, valide os testes relevantes, atualize PROJECT_STATE.md, revise git diff/status e publique conforme AGENTS.md.
```

## Saída esperada da skill

- `PROJECT_STATE.md` atualizado e com menos de 150 linhas.
- `ARCHITECTURE.md` atualizado apenas se o fluxo ou a estrutura mudarem.
- Um prompt curto e copiável para abrir a próxima sessão.
- Nenhum segredo ou artefato operacional adicionado ao repositório.
