# NexoBedrock — regras para agentes de IA

## Início obrigatório

1. Antes de qualquer tarefa, leia integralmente `docs/ai/PROJECT_STATE.md` e `docs/ai/ARCHITECTURE.md`.
2. Depois, inspecione o código e a configuração afetados; o Memory Bank é contexto, não substitui a verificação no código atual.
3. Confira `git status --short --branch` antes de editar. Preserve arquivos não relacionados e nunca inclua segredos, logs, dados de jogadores, backups ou conteúdo de `Contexto/` no Git.

## Regras de APIs externas

- Nunca invente, adivinhe ou extrapole métodos, classes, campos, eventos ou assinaturas das APIs do Nexo, Geyser ou Floodgate.
- Confirme cada API no código existente, no artefato da dependência usada pelo build (`javap`/sources, quando necessário) e na documentação oficial da versão aplicável.
- As versões declaradas são: Nexo `1.29.1`, Geyser API `2.11.0-SNAPSHOT`, Paper API `1.21.10-R0.1-SNAPSHOT` e ProtocolLib `5.4.0`. Floodgate é runtime opcional e não possui versão fixada neste repositório; não atribua uma versão sem evidência.
- Se a API necessária não puder ser confirmada, não implemente por suposição: registre a lacuna e o bloqueio em `docs/ai/PROJECT_STATE.md`.
- Toda falha de compilação ou runtime relevante deve ser registrada na seção **Erros conhecidos** de `docs/ai/PROJECT_STATE.md`, incluindo data, versão/commit, passos de reprodução, impacto e solução alternativa. Marque como resolvida somente após validar a correção.

## Regras de implementação

- Siga a estrutura, a nomenclatura e o estilo existentes; prefira mudanças pequenas e compatíveis com Java 21.
- Respeite as fronteiras de thread: snapshots da API do Nexo/Bukkit e callbacks Bukkit na main thread; conversão pesada em tarefa assíncrona.
- Preserve publicação atômica, backups, limites de tamanho, cache e arquivos antigos quando uma conversão falhar.
- Não altere o resource pack do Nexo nem dados do servidor; o plugin apenas lê fontes e publica artefatos derivados.
- Não inclua capturas, imagens operacionais, credenciais, tokens, keystores, relatórios de produção ou backups no commit, salvo pedido explícito do usuário.

## Validação obrigatória

- Execute os testes relevantes. Para mudanças que afetem o projeto Java, rode `gradle test build` quando o ambiente permitir.
- Rode `git diff --check` e revise `git diff` e `git status --short` antes de publicar.
- Se a tarefa proibir alterações `.java`, confirme explicitamente que nenhum arquivo `.java` foi modificado.
- Não declare uma integração como funcional sem evidência de teste ou validação visual/runtime adequada.

## Conclusão e manutenção do Memory Bank

- Após qualquer alteração relevante, atualize `docs/ai/PROJECT_STATE.md` com estado real, funcionalidades, limitações, erros conhecidos, validação e próximos passos.
- Atualize `docs/ai/PROJECT_STATE.md` mesmo quando uma alteração relevante falhar ou for revertida; registre o resultado real.
- Atualize `docs/ai/ARCHITECTURE.md` quando dependências, versões, módulos, mapa de classes ou fluxo técnico mudarem.
- Antes de terminar, use `docs/ai/skills/HANDOFF_SKILL.md` quando uma passagem de bastão ou um resumo for solicitado.
- Publique somente mudanças relacionadas, conforme `AGENTS.md`, e nunca faça push forçado nem altere histórico remoto.
