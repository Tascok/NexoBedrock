# Instruções de contribuição para agentes

## Publicação de alterações

- Ao concluir uma tarefa que altere código, testes ou documentação do projeto, valide as mudanças apropriadas (execute os testes relevantes e, quando viável, `gradle test build`).
- Antes de publicar, revise `git diff` e `git status` para confirmar que o commit contém somente alterações relacionadas à tarefa atual. Preserve alterações alheias; se não for possível separar com segurança, pare e pergunte ao usuário.
- Crie um commit com mensagem curta e descritiva, em português ou inglês conforme o contexto do projeto, e publique-o com `git push origin main`.
- Não faça push forçado, não altere o histórico remoto e não publique se os testes relevantes falharem. Informe o bloqueio ao usuário.
- Se o push falhar por conflito, divergência ou falta de autorização, não reescreva o histórico nem troque de remoto/branch sem autorização; explique o problema.
- Após publicar uma alteração de código, teste ou configuração, implante o JAR validado no servidor Nexo configurado quando ele estiver acessível: preserve o JAR anterior como backup, instale o novo, reinicie o servidor e confirme nos logs que a versão esperada iniciou. Se o Geyser estiver em outro servidor/proxy, publique também os arquivos gerados nas pastas configuradas, preserve backups e reinicie/recarregue o Geyser; informe qualquer etapa que não puder ser concluída.
- Não crie commits vazios. Para solicitações exclusivamente de análise/conversa, não faça commit.

## Proteção de dados

- Nunca inclua credenciais, tokens, arquivos de configuração local, dados de jogadores, logs, relatórios de produção, backups, artefatos de servidor ou conteúdo de `Contexto/`.
- Confira `.gitignore` e o conteúdo de cada arquivo novo antes de adicioná-lo. Não publique imagens, capturas ou outros dados operacionais sem pedido explícito do usuário.
- O destino padrão autorizado é o remoto `origin`, branch `main`. Não publique em outros destinos sem autorização explícita.
