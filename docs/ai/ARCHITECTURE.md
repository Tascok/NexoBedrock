# Arquitetura — NexoBedrock

Snapshot do código em `main`, versão `0.1.35`.

## Visão geral

O repositório tem dois artefatos:

- Plugin Paper `NexoBedrock`: lê o resource pack e os itens do Nexo, converte para Bedrock e publica o pack/mappings no Geyser.
- Extensão Geyser `NexoBedrock`: consome conteúdo remoto e registra módulos de entidades customizadas; também instala conteúdo sincronizado antes da inicialização do Geyser.

## Dependências e ambiente

| Item | Versão/pin | Papel |
|---|---|---|
| Java | 21 (`release = 21`) | Linguagem e toolchain dos dois módulos |
| Paper API | `1.21.10-R0.1-SNAPSHOT` | API do servidor; `plugin.yml` usa `api-version: 1.21` |
| Nexo | `1.29.1` | API pública de itens, eventos e furniture |
| Geyser API | `2.11.0-SNAPSHOT` | API da extensão; manifest declara `api: 2.11.0` |
| ProtocolLib | `5.4.0` | Rewrite de pacotes e proxies de furniture |
| Gson | `2.11.0` | JSON de packs, mappings, relatórios e sync |
| JUnit | `5.11.4` | Testes dos módulos |
| Floodgate | runtime opcional, sem versão declarada | Identifica Bedrock pelo prefixo configurado |
| ModelEngine | integração por arquivos, sem dependência Gradle | Blueprints R4 e resource pack |
| FancyHolograms | integração reflexiva, sem dependência Gradle | Hologramas de texto por jogador Bedrock |

Nexo, Paper, ProtocolLib e Geyser API são `compileOnly`; dependências opcionais são resolvidas em runtime. Não há uma versão de Floodgate fixada no repositório.

## Mapa resumido das classes

- `NexoBedrockPlugin`: lifecycle Paper, configuração, comandos, listeners e shutdown.
- `command/NexoBedrockCommand`: `convert`, `status`, `reload` e `report`.
- `config/*`: snapshot tipado de `config.yml`, modo de mapping, handheld e texto.
- `pack/NexoPackReader`: lê ZIP/diretório de assets, modelos, item definitions, fontes e texturas.
- `pack/NexoItemIndex`: snapshot da API pública do Nexo na main thread.
- `pack/ItemIndexBuilder`, `JavaModelResolver`, `PackItemDefinitionParser`: correlacionam itens, definições e modelos Java.
- `pack/NexoArmorIndex`, `NexoItemConfigIndex`, `NexoGlyphNames`: armor, ícones e nomes de glyphs do Nexo.
- `pack/ModelEngineAssetReader`, `ModelEngineBlueprintReader`: entrada ModelEngine por arquivos.
- `conversion/ConversionService`: orquestra fonte, cache, conversão, relatório e publicação.
- `conversion/ItemConverter`, `BedrockPackBuilder`: convertem itens e montam o `.mcpack` Bedrock.
- `bedrock/*`: atlas, ícones, geometria, attachables, animações, glyphs, overlays de chest e scoreboard.
- `geyser/MappingWriter`, `GeyserDeployer`, `FurnitureMappingPublisher`: mappings v2 e deploy transacional local.
- `listener/*`: eventos Nexo, rewrite de glyphs/nomes e proxy de furniture.
- `hologram/FancyHologramBedrockBridge`: rendering opcional de hologramas.
- `report/*`, `conversion/PackCache`: diagnóstico por item e reutilização de gerações.
- `sync/SyncHttpsServer`, `SyncBundleWriter`: endpoint Paper e bundle imutável remoto.
- `geyser-extension/*`: puller, validação, instalador transacional e bridge de entidades.
- `src/main/resources`: `config.yml` e `plugin.yml`; a extensão também possui manifest e mappings padrão.

## Fluxo Nexo -> Bedrock/Geyser

1. `NexoPackListener` recebe `NexoPostPackGenerateEvent` ou `NexoPackFinishedEvent`; prefere `plugins/Nexo/pack/assets` e usa `pack.zip` como fallback.
2. `ConversionService` captura itens via `NexoItems` na main thread e executa o trabalho pesado em task assíncrona.
3. O leitor extrai modelos, definições, fontes bitmap, texturas e metadados; `ItemIndexBuilder` resolve os candidatos do Nexo.
4. `ItemConverter` gera ícone 2D ou modelo 3D; também resolve armadura, arcos/bestas, ranged models e assets ModelEngine disponíveis.
5. `BedrockPackBuilder` escreve `manifest.json`, `item_texture.json`, atlas, geometry, attachables, animações, glyph pages, map de codepoints, overlays GUI, scoreboard e catálogos.
6. `MappingWriter` gera custom mappings Geyser v2; `BedrockPackValidator` valida pack e mappings.
7. `GeyserDeployer` publica pack e mappings com arquivos temporários, rollback e backups; `PackCache` reutiliza saídas válidas.
8. O Geyser local recebe `packs/nexobedrock.mcpack` e `custom_mappings/nexobedrock.json`; clientes Bedrock os obtêm na conexão/reconexão.
9. Opcionalmente, `SyncBundleWriter` cria uma geração única (`pack`, mappings, mappings de furniture e hashes SHA-256). `SyncHttpsServer` expõe-a por HTTPS autenticado; durante conversão responde 503.
10. A extensão baixa/verifica a geração, deixa-a pendente durante execução e a instala transacionalmente no próximo startup, com recuperação por journal e backup.

## Fronteiras atuais

- O proxy não reinicia o Geyser automaticamente; uma nova geração exige restart explícito.
- O catálogo de furniture e ModelEngine declara `runtime_ready: false`: assets estáticos existem, mas sincronização viva completa não.
- O overlay de GUI cobre baús clássicos; outros containers, Pocket, HUD e providers completos de fonte permanecem fora de suporte.
- Blocos customizados, blockstates, sons, texturas Bedrock animadas e modelos Java `composite`/`select` completos não são reproduzidos.
