# Estado do projeto — NexoBedrock

Atualizado em 2026-11-20.

## Identificação

- Branch de trabalho: `main`; destino autorizado: `origin/main`.
- Versão do plugin e da extensão: `0.1.33`.
- Último commit no início desta revisão: `720e65e Adiciona Memory Bank para agentes de IA`.
- Último commit de código: `0470f0a Aguarda conversão ao iniciar sincronização Geyser`; tag mais recente: `v0.1.33`.
- Módulos Gradle: plugin Paper na raiz e `geyser-extension`.
- Java 21; Paper API `1.21.10-R0.1-SNAPSHOT`; Nexo `1.29.1`; Geyser API `2.11.0-SNAPSHOT`.
- Floodgate é opcional e não tem versão fixada no build; o backend identifica Bedrock pelo prefixo de nome.
- Havia arquivos locais não rastreados alheios à revisão (imagens e `.brud/`); devem ser preservados e nunca adicionados ao commit.

## Funcionalidades implementadas

### Conversão e publicação

- Conversão manual (`/nexobedrock convert [--force]`) e automática por eventos do Nexo.
- Leitura do diretório de assets pré-obfuscação e fallback para `pack.zip` final.
- Snapshot de itens pela API pública do Nexo na main thread; conversão pesada assíncrona.
- Ícones 2D, modelos 3D, atlas, geometry, attachables, animações, variantes de arco/besta e armadura Nexo.
- Mapping Geyser v2 nos modos `legacy`, `modern` e `auto`.
- Cache por pack/config/armadura/aparência/ModelEngine/versão e relatório por item.
- Publicação com arquivos temporários, rollback, backups e preservação dos arquivos antigos em falhas.
- Cópia opcional em `plugins/NexoBedrock/output/` e relatórios em `plugins/NexoBedrock/reports/`.

### GUI, glyphs e texto

- Providers bitmap Java convertidos em páginas de fonte Bedrock e mapa de codepoints por fonte.
- Rewrite de títulos de inventário, mensagens de sistema/action bar e nomes/lore de itens para Bedrock.
- Overlays de fundos de baús clássicos, scoreboard Bedrock e offsets persistentes por glyph.
- `gui-offsets.json` é gerado/preservado, identifica glyphs pelos nomes do Nexo e invalida cache/identidade do pack quando alterado.
- A integração usa ProtocolLib e o prefixo Floodgate configurado; chat assinado não é alterado.

### Runtime e sincronização

- Bridge opcional de FancyHolograms por reflexão, desabilitado por padrão.
- Bridge parcial de NexoFurniture: proxy Armor Stand no Paper, UUID sintético para Bedrock e definição customizada na extensão.
- Pipeline ModelEngine R4 gera assets estáticos de geometry, textura, animação básica e catálogo.
- Sync remoto opcional Paper -> Geyser por HTTPS autenticado com TLS, token e endpoint `/v1/bundle`.
- Cada geração contém pack, mappings, mappings de furniture e hashes SHA-256; o endpoint fica indisponível com 503 durante conversões.
- A extensão baixa para área temporária, limita o tamanho, valida o bundle e deixa a geração pendente até restart explícito.
- No startup, a extensão instala pack/mappings de forma transacional, com journal, backup e recuperação.
- O proxy pode aguardar o Paper no startup; se o prazo terminar, reutiliza a última geração validada.

## Limitações, pendências e stubs

- Não há anotações `TODO`/`FIXME` no código Java; as lacunas estão implementadas como limites explícitos.
- Furniture e ModelEngine mantêm `runtime_ready: false`: faltam escala, tinta, estado, interação, identidade/estado por instância e clips ModelEngine ao vivo.
- O overlay de GUI cobre apenas baús clássicos; outros containers, Pocket, HUD e override global de tela não são suportados.
- Providers `space`/`reference`, métricas Java exatas, sons, texturas Bedrock animadas, blocos customizados e blockstates não são convertidos.
- `composite`/`select` Java e modelos de entidades customizadas/elytra/capes não têm suporte completo.
- A sync não reinicia Geyser automaticamente; os jogadores precisam reconectar para obter o novo pack.
- O certificado TLS de produção ainda precisa ser válido para o hostname público; certificado autoassinado exige CA configurada na extensão.

## Erros conhecidos

### Abertos no código

- Nenhuma falha de compilação ou runtime reproduzida está aberta nesta revisão.
- O build usa recursos deprecated do Gradle e o compilador Java sinaliza uso de API deprecated; são avisos não bloqueantes, não falhas.

### Operacionais e resolvidos

- Reiniciar apenas o proxy antes de converter mantinha o pack anterior. A ordem validada é: salvar configuração -> converter no Paper -> aguardar download -> reiniciar Geyser/proxy -> reconectar o cliente.
- `gui-offsets.json` inválido ou alterado durante a conversão interrompe a operação e preserva o pack publicado anterior.
- Token inválido, TLS não confiável e respostas HTTP 4xx (exceto 429) não são repetidos pelo puller; corrigir configuração/certificado e reiniciar o proxy.
- Se o Paper não responder durante o startup, a extensão usa a última geração validada; a próxima geração continuará pendente até outro restart.

## Validação

- Comandos canônicos: `gradle test build`, `git diff --check`.
- `gradle clean test build`: **BUILD SUCCESSFUL** em 2026-11-20; 69 testes, 64 aprovados e 5 smoke tests opt-in ignorados por ausência dos assets/packs externos opcionais.
- `gradle test build`: **BUILD SUCCESSFUL** em 2026-11-20 para esta revisão documental; 10 tarefas executadas/atualizadas sem falhas.
- Confirmação visual do usuário em 2026-11-20: os ícones do menu de conquistas estão resolvidos no cliente Bedrock; a captura operacional local permaneceu não rastreada.
- A atualização deste registro não altera arquivos `.java`; confirmar com `git diff --name-only -- '*.java'` e `git status --short -- '*.java'`.

## Próximos passos lógicos

1. Emitir/usar certificado TLS válido para qualquer exposição pública da sincronização.
2. Completar e testar a sincronização viva de furniture (transformações, estado e interação).
3. Implementar tracking live de ModelEngine e reprodução de clips por instância.
4. Expandir overlays de GUI para containers Bedrock compatíveis e provider de fontes ausente.
5. Manter testes de integração para publicação atômica, startup remoto e compatibilidade de versões.

## Regra de atualização

Após qualquer mudança relevante, atualizar esta seção de estado, os erros conhecidos, a validação e os próximos passos; atualizar `docs/ai/ARCHITECTURE.md` se dependências, estrutura ou fluxo mudarem. Manter este arquivo abaixo de 150 linhas durante o handoff.
