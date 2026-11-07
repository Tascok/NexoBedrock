# NexoBedrock

Converte o resource pack gerado pelo [Nexo](https://nexomc.com/) em um pack
Bedrock (`.mcpack`) e em custom mappings do Geyser, para que itens 2D e 3D do
Nexo apareçam corretamente em clientes Bedrock.

O plugin foi feito para Paper/Purpur 1.21+ com Java 21. Ele escuta o evento
`NexoPostPackGenerateEvent` **antes da obfuscação** do Nexo e lê o diretório
`plugins/Nexo/pack/assets`; se esse diretório não existir, usa o
`plugins/Nexo/pack/pack.zip` final como fallback.

> Status: MVP. Blocos customizados, armaduras/elytra, móveis/display entities,
> fontes, GUIs, sons e animações de textura não fazem parte desta versão. Eles
> são registrados no relatório como `unsupported` e nunca derrubam a conversão.

## Requisitos

- Paper ou Purpur 1.21+ (API 1.21)
- Java 21
- Nexo instalado e gerando pack (`depend: Nexo`)
- Geyser-Spigot (ou Geyser em proxy, apontando as pastas na config)
- Floodgate é opcional (`softdepend`)

## Instalação

1. Gere o jar:

   ```bash
   gradle build
   ```

2. Copie `build/libs/NexoBedrock-0.1.0.jar` para `plugins/`.
3. Inicie o servidor uma vez para gerar `plugins/NexoBedrock/config.yml`.
4. Ajuste os caminhos do Nexo e do Geyser, se necessário.
5. Rode `/nexobedrock convert` ou deixe o `auto-convert-on-pack-generate`
   disparar a conversão quando o Nexo gerar o pack.

O comando `/nexobedrock reload` recarrega a config sem reiniciar o servidor.
O deploy no Geyser é feito por arquivo; para recarregar o Geyser use
`/geyser reload` (ou habilite `geyser.restart-geyser`). Em alguns casos o
Geyser só recarrega custom mappings com um restart completo.

## Comandos

Permissão: `nexobedrock.admin` (padrão: OP).

| Comando | Descrição |
| --- | --- |
| `/nexobedrock convert [--force]` | Converte o pack em segundo plano. `--force` ignora o cache. |
| `/nexobedrock status` | Mostra config, estado da conversão e estatísticas da última execução. |
| `/nexobedrock reload` | Recarrega `config.yml`. |
| `/nexobedrock report` | Resume o último `report.json`, incluindo motivos de itens ignorados. |

Aliases: `/nb`, `/nexob`.

## Configuração

Caminhos relativos são resolvidos a partir da raiz do servidor.

### Fonte

```yml
enabled: true
auto-convert-on-pack-generate: true

source:
  pack-path: "plugins/Nexo/pack/pack.zip"
  assets-path: "plugins/Nexo/pack/assets"
  prefer-assets-on-event: true
```

- `pack-path`: pack final do Nexo, usado no comando manual e como fallback.
- `assets-path`: assets pré-obfuscação, preferido no evento.
- `prefer-assets-on-event`: quando `true`, usa os assets no evento se existirem.

### Geyser

```yml
geyser:
  packs-folder: "plugins/Geyser-Spigot/packs"
  mappings-folder: "plugins/Geyser-Spigot/custom_mappings"
  pack-name: "nexobedrock"
  restart-geyser: false
  reload-command: "geyser reload"
  create-folders: true
  backup: true
  backup-keep: 10
  attachable-material: "entity_alphatest_one_sided"
```

- `pack-name`: nome base dos arquivos (`nexobedrock.mcpack` e
  `nexobedrock.json`).
- `backup`: copia pack/mappings anteriores para
  `plugins/NexoBedrock/backups/<timestamp>/` antes de sobrescrever.
- `backup-keep`: quantidade de backups mantidos (mínimo 1).
- `attachable-material`: material dos attachables 3D. Use
  `entity_alphatest_one_sided` (padrão) ou `entity_alphatest`.

### Conversão

```yml
conversion:
  include-2d: true
  include-3d: true
  max-pack-size-mb: 100
  debug: false
  mapping-mode: "legacy"
  namespace: "nexobedrock"
  creative-category: "items"
  display-handheld: "auto"
  min-engine-version: [1, 20, 0]
  max-atlas-tiles: 64
  max-atlas-tile-size: 256
  keep-output-copy: true
```

- `mapping-mode`:
  - `legacy`: usa `custom_model_data` (padrão, indicado para setups antigos).
  - `modern`: usa `item_model` e o tipo `definition` do Geyser v2.
  - `auto`: usa `modern` quando o item tem `item_model`; senão `legacy`.
- `namespace`: namespace dos itens Bedrock. Não pode ser `minecraft`.
- `creative-category`: `all`, `construction`, `nature`, `equipment`, `items`,
  `item_command_only` ou `none`.
- `display-handheld`: `auto`, `true` ou `false`.
- `max-pack-size-mb`: limite de entrada e saída. Se o pack gerado passar do
  limite, a conversão falha **sem** tocar nos arquivos antigos do Geyser.
- `max-atlas-tiles` / `max-atlas-tile-size`: proteção contra modelos
  patológicos no atlas de texturas.
- `keep-output-copy`: copia também para `plugins/NexoBedrock/output/`.

## O que é gerado

No Geyser:

```text
plugins/Geyser-Spigot/packs/nexobedrock.mcpack
plugins/Geyser-Spigot/custom_mappings/nexobedrock.json
```

No plugin:

```text
plugins/NexoBedrock/
├── backups/<timestamp>/{packs,custom_mappings}/
├── cache/<hash>/{pack.mcpack,mappings.json,report.json,meta.json}
├── output/                  # cópia opcional
├── reports/latest.json
├── reports/<timestamp>.json
└── work/                    # temporário; removido ao final, exceto em debug
```

O `.mcpack` contém:

- `manifest.json`
- `textures/item_texture.json` (ícones 2D e 3D)
- `textures/nexobedrock/atlas/<id>.png` para itens 3D
- `textures/nexobedrock/icons/<id>.png` para ícones
- `models/entity/<id>.geo.json` (geometria)
- `attachables/<id>.json` (attachable)
- `animations/<id>.animation.json` (display transforms)
- `animations/nexobedrock_disable.json`
- `pack_icon.png`

Os mappings usam o schema oficial do Geyser v2 (`format_version: 2`, mapa
`items`). Itens `legacy` usam `custom_model_data`; itens `modern` usam
`model` + `bedrock_identifier`. O `bedrock_identifier` nunca fica no namespace
`minecraft`.

## Como a conversão funciona

1. No evento do Nexo, o plugin tira um snapshot dos itens via API pública
   (`NexoItems`), sem chamar `event.getResourcePack()`.
2. Lê modelos, item definitions, texturas e `.mcmeta` do pack.
3. Resolve `parent`, variáveis de textura, `texture_size`, overrides legados e
   `range_dispatch` moderno de `custom_model_data`.
4. Para cada item:
   - 2D: gera ícone e entrada em `item_texture.json`.
   - 3D: gera atlas multi-textura, geometria Bedrock, attachable e animações.
5. Gera as mappings e publica pack + mappings com arquivo temporário e
   `ATOMIC_MOVE` (com fallback), guardando backup dos anteriores.
6. Escreve `report.json` com o resultado item a item.

A conversão pesada roda em thread assíncrona. O snapshot do Nexo e o callback
final (reload do Geyser) rodam na main thread.

### Detalhes de geometria/UV

- UVs Java são interpretadas no espaço 0–16, independentemente da resolução da
  textura; `texture_size` apenas escala esse espaço.
- Faces com rotação 90/180/270 têm o tile do atlas rotacionado e a UV
  remapeada.
- Elementos rotacionados viram bones `rot_N` parenteados em `root_z`, seguindo
  a abordagem do java2bedrock.
- Coordenadas: `origin = [8 - to.x, from.y, from.z - 8]`,
  `size = to - from`; rotações `x: -angle`, `y: -angle`, `z: angle`.
- Texturas animadas (tiras verticais) usam apenas o primeiro frame no MVP,
  evitando distorção de UV e inflação do atlas.

## Relatório

Cada item recebe um status:

- `converted`: convertido com sucesso (2D ou 3D).
- `unsupported`: fora do escopo do MVP ou sem dados suficientes.
- `skipped`: duplicado ou desabilitado por configuração.
- `failed`: erro isolado naquele item; os demais continuam.

O `report.json` inclui fonte, hash, duração, estatísticas, lista de itens,
avisos, erros e caminhos de saída. Use `/nexobedrock report` para um resumo.

## Escopo do MVP

Suportado:

- Itens 2D (ícone) e 3D (geometria + attachable + display transforms).
- `custom_model_data` legado e `item_model` moderno.
- Parents, overrides, variáveis de textura, `texture_size`.
- Atlas multi-textura, rotações de face, texturas animadas (primeiro frame).

Não suportado (reportado como `unsupported`, sem crash):

- Blocos customizados e blockstates.
- Armaduras, elytra, capas e modelos de entidade.
- Móveis, display entities, item frames e entidades.
- Fontes, glifos, GUIs, ícones de menu e sons.
- Animações de textura no cliente Bedrock (só o primeiro frame é usado).
- Componentes/atributos customizados de item no Geyser.
- `composite`/`select` completos: o MVP usa o primeiro modelo como
  aproximação e registra aviso.
- ModelEngine e outros plugins de modelo.
- Conversão em nuvem ou extensão do Geyser.

## Segurança e cache

- O pack e as mappings são escritos em arquivos temporários no mesmo diretório
  e movidos atomicamente.
- Se a publicação das mappings falhar, o pack anterior é restaurado (assim como
  as mappings anteriores).
- Se nenhum item converter ou o pack exceder o limite, os arquivos antigos do
  Geyser permanecem intactos.
- O cache é indexado por hash do pack + fingerprint da config + versão do
  plugin. Mudar opções que afetam a saída invalida o cache.
- O cache mantém as 10 conversões mais recentes.

## Desenvolvimento

```bash
gradle test          # testes unitários
gradle build         # jar final em build/libs/
```

Smoke test opcional contra um pack real (não escreve em `Contexto/`):

```bash
NEXOBEDROCK_SMOKE=true \
NEXOBEDROCK_SMOKE_PACK=Contexto/Plugins/Nexo/pack/pack.zip \
NEXOBEDROCK_SMOKE_ASSETS=Contexto/Plugins/Nexo/pack/assets \
gradle test --tests 'dev.nexobedrock.SmokeRealPackTest'
```

Saídas do smoke ficam em `build/smoke/` e `build/smoke-assets/`.

## Clean-room e licença

Esta implementação foi escrita a partir da API pública do Nexo, da
documentação/schema oficial do Geyser e da observação do comportamento do
`java2bedrock.sh`. Não foram copiados código, assets ou schema do Scaffolding.

O `Contexto/` é somente leitura e não é alterado pelo plugin nem pelos testes.

O projeto ainda não possui um arquivo `LICENSE`. Antes de distribuir, defina a
licença. Se qualquer código AGPL (por exemplo, trechos do java2bedrock.sh) for
incorporado, o plugin inteiro deve ser distribuído sob AGPL.
