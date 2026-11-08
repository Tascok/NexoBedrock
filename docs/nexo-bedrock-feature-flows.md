# Fluxos para ampliar suporte ao Nexo no Bedrock

Este documento registra os formatos e fronteiras de runtime encontrados na
implementação atual e na documentação pública consultada em 2026-11-07. Ele não
declara suporte implementado: cada fluxo só deve ser marcado como suportado
depois de gerar os arquivos necessários e validar o comportamento em Bedrock.

## Estado encontrado no NexoBedrock

- O conversor lê modelos, definições de itens e PNGs do pack Java. `NexoPackReader`
  ignora fontes, blockstates, sons e texturas de GUI.
- `NexoItemIndex` consulta os itens via API pública do Nexo e marca a mechanic
  `furniture`; `ItemConverter` então rejeita esse item como fora do MVP.
- O pack Bedrock atual gera `item_texture.json`, geometria/attachables de itens e
  mappings v2 do Geyser. Isso resolve a aparência do item, não a entidade que o
  Nexo coloca no mundo.
- A saída publicada no servidor foi inspecionada: o pack tem 147 entradas, não
  contém páginas `font/glyph_*.png`, e os mappings não contêm codepoints PUA.
  Logo, a menção a glyph pages na lista de arquivos gerados do README era obsoleta.

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
