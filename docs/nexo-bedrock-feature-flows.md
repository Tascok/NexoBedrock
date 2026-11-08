# Fluxos para ampliar suporte ao Nexo no Bedrock

Este documento registra os formatos e fronteiras de runtime encontrados na
implementação atual e na documentação pública consultada em 2026-11-07. Ele não
declara suporte implementado: cada fluxo só deve ser marcado como suportado
depois de gerar os arquivos necessários e validar o comportamento em Bedrock.

## Estado encontrado no NexoBedrock

- O conversor lê modelos, definições de itens, fontes bitmap e PNGs do pack Java.
  `NexoPackReader` ainda ignora blockstates, sons e texturas independentes de GUI.
- `NexoItemIndex` consulta os itens via API pública do Nexo e marca a mechanic
  `furniture`; modelos resolvidos passam pelo conversor e são listados em
  `nexobedrock/furniture-catalog.json`. O catálogo declara `runtime_ready: false`:
  é preparação de assets, não sincronização de instâncias.
- O pack inclui uma definição `entity/*.entity.json` para cada furniture 3D e
  `nexobedrock/furniture-mappings.properties` com índices determinísticos por ID.
  A extensão Geyser agora lê esses índices (copiados para sua pasta de dados),
  registra as definições e reconhece UUIDs sintéticos marcados `NEXO` no evento
  de spawn. O protocolo de UUID preserva um identificador único de instância e
  o índice de modelo; testes cobrem parser e codificação.
- O pack Bedrock atual gera `item_texture.json`, geometria/attachables de itens e
  mappings v2 do Geyser. Isso resolve a aparência do item, não a entidade que o
  Nexo coloca no mundo.
- O leitor/conversor agora reconhece JSON de fonte com providers `bitmap`, cria
  folhas Bedrock `font/glyph_E2.png`–`glyph_F8.png` com codepoints de saída e
  registra o mapa fonte/codepoint em `nexobedrock/glyph-map.json` dentro do pack.
  O listener Paper usa ProtocolLib para remapear texto `OPEN_WINDOW` de clientes
  Bedrock com o prefixo configurado. Providers `space`/`reference` são avisados,
  e ascent/avanço do Java não são reproduzidos precisamente. Para Geyser remoto,
  o pack precisa estar acessível no caminho configurado no Paper; sincronização
  automática da tabela para a extensão Geyser ainda não foi implementada.
- O smoke test executado contra o pack real do servidor converteu 99 glyphs para
  16 folhas quadradas e incluiu o mapa de codepoints; nenhum Bedrock client foi
  conectado para validação visual, portanto alinhamento e nitidez ainda precisam
  de teste in-game.

## Fluxos por funcionalidade

### Decorações e NexoFurniture

1. Ler a configuração do item Nexo e seus assets/modelo, incluindo textura,
   transformação de display, escala, rotação, tintas/estados e propriedades de
   interação que possam ser representadas.
2. Converter o modelo Java para geometria/atlas Bedrock e criar o identificador
   estável que a camada de runtime vai referenciar.
3. Produzir um manifesto/mapping de furniture (identificador Nexo → geometria,
   textura e parâmetros de transformação), junto com o `.mcpack`.
4. Em runtime, detectar as entidades Java de furniture e encaminhar spawn,
   posição, rotação, escala, estado e despawn somente aos clientes Bedrock; a
   extensão Geyser constrói/renderiza a entidade customizada Bedrock.
5. Fazer deploy dos dois lados de modo coordenado: pack/mappings em Geyser e
   mapping de furniture na extensão, preservando backups e pedindo reconexão dos
   clientes para baixar o pack.

**Limite confirmado:** NexoFurniture é um `ItemDisplay` que carrega um
`ItemModel`. Geyser não renderiza display entities por padrão. Só gerar um item
mapping, attachable ou geometria não faz o móvel aparecer no mundo. A solução
completa requer uma extensão compatível com Geyser (a documentação do
Scaffolding cita API Geyser 2.11.0+) e um canal de sincronização de estado entre
Paper e Geyser. Os parâmetros `block_tags`, `block_types` e `nexo_blocks` da
mechanic controlam onde o servidor Java permite colocar o móvel; não são
Bedrock block tags e não afetam o desenho do modelo.

**Etapa implementada:** furniture com modelo Java de item válido gera geometria
e atlas Bedrock, mais uma entrada de catálogo com identificador, geometry,
textura, attachable, item Java e modelo de origem. Transformações específicas da
mechanic e associação de cada spawn Java à entrada continuam pendentes.

**Runtime em implementação:** no Paper, `FurnitureProxyBridge` acompanha os
`ItemDisplay` via API `NexoFurniture`, cria Armor Stands de proxy com índice no
PDC, suprime spawn/updates/destroy para Java via ProtocolLib e reescreve o UUID
de spawn para o marcador `NEXO` somente para jogadores Bedrock. Os proxies são
reconciliados com furniture carregada e teletransportados a cada 2 ticks; remoção
do proxy gera o destroy normal para Bedrock. A extensão consome o marcador no
`ServerSpawnEntityEvent` e troca a definição pela custom entity correspondente.

Quando `geyser.extension-data-folder` aponta para a pasta de dados da extensão,
Paper publica o mapping atomicamente, preserva backup e avisa que o Geyser deve
ser reiniciado. Com essa opção vazia, nenhuma cópia é tentada. No servidor atual,
Geyser fica em outro container, o caminho compartilhado não está configurado e
a extensão ainda não está instalada; não foi feito deploy nem validação visual
com cliente Bedrock. Escala, quaternion,
iluminação, tintas/estados e interações de furniture ainda não são sincronizados.
Por isso o catálogo permanece `runtime_ready: false` e não se declara suporte
completo.

### Entidades e ModelEngine

1. Descobrir entidades/modelos suportados através da integração do plugin de
   modelos (por exemplo, ModelEngine), e serializar blueprint, rig, texturas,
   animações, skins/remaps e propriedades de hitbox.
2. Converter rigs/animações para os formatos de entidade Bedrock e empacotar as
   definições no resource pack.
3. No Paper, acompanhar entidades e transmitir spawn/despawn por alcance,
   posição/olhar, animação ativa, visibilidade de ossos, escala, estado de dano,
   hitbox e remaps aos espectadores Bedrock. O mob Java base pode precisar ser
   ocultado desses clientes.
4. Na extensão Geyser, registrar a definição customizada Bedrock, instanciar a
   entidade e aplicar updates recebidos.

**Limite confirmado:** a API custom entity do Geyser é experimental e não é
configurável por JSON mappings. A implementação deve fixar/validar a versão da
API e manter o protocolo Paper↔Geyser versionado. Modelos de entidade no pack,
sozinhos, não convertem entidades Java nem sincronizam animações.

**Pesquisa/etapa parcial:** o ModelEngine R4 instalado armazena blueprints
Blockbench `.bbmodel`; o pack gerado contém modelos por osso, texturas e a
hierarquia/clips ficam no blueprint. O pipeline agora lê a pasta configurada de
blueprints e `resource pack.zip`, extrai a textura do modelo, preserva rig/pivots
e gera geometry, definições de entidade Bedrock, catálogo e keyframes básicos
de `position`/`rotation`/`scale`. Interpolação Catmull-Rom é aproximada como
linear e avisada. O smoke de integração usou `nm_black_widow.bbmodel`: 22
elementos, 12 bones, 1 textura, 3 clips, geometry com 19 cubos; o `.mcpack`
gerado continha modelo, textura e animações. `runtime_ready` segue false: falta
sincronizar spawn/identidade/estado de cada entidade ModelEngine e disparar seus
clips ativos na extensão Geyser. Não é validação visual em Bedrock.

### GUIs, HUDs, glyphs e ícones de menu

1. Ler `assets/<namespace>/font/*.json` e os providers usados (bitmap, reference,
   espaço/shift e glyphs animados), além de PNG/GIF e texturas em
   `textures/gui`/`textures/font`.
2. Atribuir codepoints Bedrock determinísticos, rasterizar/redimensionar glyphs
   para resolução e ascent compatíveis, registrar páginas de fonte e reescrever
   os codepoints em texto enviado a jogadores Bedrock.
3. Para fundos de inventário, identificar o glyph de título usado pelo menu,
   gerar a textura Bedrock e calcular offset/escala por tipo de container;
   permitir override por GUI porque a disposição Java e Bedrock não coincide
   exatamente.
4. Para overrides de HUD e telas vanilla, mapear as texturas Java para os
   arquivos/UI assets Bedrock equivalentes e validar os elementos suportados.
5. Tratar hopper/dropper/dispenser e outras telas hardcoded como limitações, não
   prometer skin quando o cliente Bedrock não oferece override compatível.

**Limites confirmados:** glyphs Bedrock são desenhados no tamanho baked, então
escala e nitidez estão ligadas; alguns casos exigem escala/altura/supersampling.
Geyser em Velocity/Standalone precisa de Floodgate devidamente configurado para
detectar Bedrock e remapear glyphs. GUIs customizadas são aproximadas, não
pixel-perfect. É preciso testar os formatos de providers realmente usados pelo
Nexo antes de declarar compatibilidade ampla.

### Ícones e tags

“Ícones/tags” cobre mais de um fluxo e não deve ser tratado como uma feature
única:

- **Ícone do item Nexo:** PNG da textura do pack ou ícone renderizado do modelo;
  publicar em `textures/item_texture.json` e apontar `options.icon` no Geyser
  mapping. Esse pipeline já existe para os itens que passam pelo conversor.
- **Ícone de GUI/menu:** glyph/font ou textura da GUI, seguindo o fluxo acima;
  não é o mesmo que o ícone do item no inventário criativo.
- **Tags de colocação da furniture:** `block_tags`, `block_types` e `nexo_blocks`
  da configuração Nexo são critérios de validação/colocação no servidor Java.
  Só precisam de tradução se também forem implementadas regras de colocação
  Bedrock; não são visualmente exibidas pelo cliente.
- **Tags de blocos customizados:** exigem registrar custom blocks/estados e
  overrides de blocos/itens em Geyser. Geyser alerta que componentes de bloco
  mudam com frequência no Bedrock; requer testes de versão e uma extensão/API,
  especialmente para comportamentos que não cabem em JSON.

## Compatibilidade e implantação

- Os docs consultados do Scaffolding indicam Nexo 1.26+, Geyser 2.11.0-SNAPSHOT+
  e extensão para furniture/ModelEngine; isso é referência de compatibilidade,
  não garantia de que NexoBedrock suporte tais APIs.
- Geyser carrega custom mappings no startup e os clientes baixam packs ao
  reconectar. Alterações de pack/mapping exigem reinício ou reload compatível
  com a versão e reconexão de clientes Bedrock.
- Em Geyser de proxy/Standalone, os outputs precisam ser sincronizados ao
  destino remoto; caminhos locais configurados no Paper não bastam.
- O deploy deve publicar pack, mappings e artefatos de runtime como uma unidade
  versionada, mantendo backups e verificando hashes/logs após reiniciar.

## Fontes primárias

- [Nexo Scaffolding: suporte, setup e compatibilidade](https://docs.nexomc.com/addons/scaffolding)
- [ScaffoldingExtension: NexoFurniture e ModelEngine](https://docs.nexomc.com/addons/scaffolding/scaffoldingextension)
- [Scaffolding: GUIs e HUDs](https://docs.nexomc.com/addons/scaffolding/custom-guis-huds)
- [Scaffolding: glyphs e emotes](https://docs.nexomc.com/addons/scaffolding/emotes-and-glyphs)
- [Nexo Furniture mechanic](https://docs.nexomc.com/mechanics/furniture-mechanic)
- [Geyser custom blocks](https://geysermc.org/wiki/geyser/custom-blocks)
- [Geyser custom entities](https://geysermc.org/wiki/geyser/custom-entities)
- [Geyser custom items](https://geysermc.org/wiki/geyser/custom-items)
