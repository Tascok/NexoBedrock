# Estado do projeto — NexoBedrock

Atualizado em 2026-11-21.

## Identificação

- Branch de trabalho: `main`; destino autorizado: `origin/main`.
- Versão do plugin e da extensão: `0.1.34`.
- Último commit no início desta revisão: `0007658 Registra validação dos ícones de conquistas`; tag mais recente antes da revisão: `v0.1.33`.
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

### Modelos 3D em mão

- A animação da raiz usa offsets distintos: terceira pessoa `[0, 21, -3]` e primeira pessoa `[4, 18, 4]`.
- A geometria Java foi recentralizada em `[0, -8, 0]` no commit `ab5ca8f`; a equação `posição_nova = posição_base + [0, 8, 0]` preserva a posição calibrada para o rig com pivot `Y=8` do Java2Bedrock.
- Fontes: [Java2Bedrock](https://github.com/AZPixel-Team/Java2Bedrock/blob/ffe3f128db5ef251b267bd5ae7f9ff7f4315fd3e/converter.sh), [Rainbow](https://github.com/GeyserMC/Rainbow/blob/c98595574187ebc115fe7c71d467700a936f8ac5/rainbow/src/main/java/org/geysermc/rainbow/mapping/animation/AnimationMapper.java), [Bedrock Wiki](https://wiki.bedrock.dev/items/attachables), [Microsoft Learn](https://learn.microsoft.com/en-us/minecraft/creator/documents/attachables) e o preview de binding do [Blockbench](https://github.com/JannisX11/blockbench/blob/ec0b2827104d0799c153bfb5f867579c0c44652f/js/outliner/types/group.js).
- O deslocamento de binding `-24 Y` do Blockbench é anterior e posterior a `ab5ca8f`, portanto se cancela na compensação; não é um offset adicional do attachable.
- Rotações, escala e transformações por slot permanecem inalteradas; não há override individual por item/textura.

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
- Os offsets absolutos das mãos são empíricos e preservam o rig de referência; a confirmação final depende do cliente Bedrock e de modelos 3D específicos.
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
- `gradle :test --tests dev.nexobedrock.bedrock.AnimationWriterTest`: **BUILD SUCCESSFUL** em 2026-11-21; o novo teste de regressão validou os offsets `[0, 21, -3]` e `[4, 18, 4]`.
- `gradle test build`: **BUILD SUCCESSFUL** em 2026-11-21; 70 testes, 65 aprovados e 5 smoke tests opt-in ignorados por ausência dos assets/packs externos opcionais.
- `git diff --check`: sem erros em 2026-11-21.
- Confirmação visual do usuário em 2026-11-20: os ícones do menu de conquistas estão resolvidos no cliente Bedrock; a captura operacional local permaneceu não rastreada.

## Próximos passos lógicos

1. Emitir/usar certificado TLS válido para qualquer exposição pública da sincronização.
2. Completar e testar a sincronização viva de furniture (transformações, estado e interação).
3. Implementar tracking live de ModelEngine e reprodução de clips por instância.
4. Expandir overlays de GUI para containers Bedrock compatíveis e provider de fontes ausente.
5. Manter testes de integração para publicação atômica, startup remoto e compatibilidade de versões.

## Regra de atualização

Após qualquer mudança relevante, atualizar esta seção de estado, os erros conhecidos, a validação e os próximos passos; atualizar `docs/ai/ARCHITECTURE.md` se dependências, estrutura ou fluxo mudarem. Manter este arquivo abaixo de 150 linhas durante o handoff.
