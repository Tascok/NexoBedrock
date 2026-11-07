# Processos atuais e objetivos futuros

Este documento resume o funcionamento atual do NexoBedrock e registra os
objetivos planejados para sua evolução. O escopo futuro é uma proposta: não
significa que esses recursos já estejam implementados.

## Estado atual

O NexoBedrock é um plugin para Paper/Purpur que converte recursos de itens do
Nexo em um pacote Bedrock (`.mcpack`) e em custom mappings do Geyser v2.

### Fluxo de conversão

1. O plugin recebe uma solicitação manual (`/nexobedrock convert`) ou o evento
   de geração de pack do Nexo.
2. No evento, captura na thread principal um snapshot dos itens disponíveis
   pela API do Nexo. A conversão usa os assets pré-obfuscação quando disponíveis;
   o `pack.zip` final é usado como fallback ou pela conversão manual.
3. A leitura e a conversão pesada acontecem em segundo plano. O leitor coleta
   modelos, definições de itens, texturas e metadados de animação relevantes.
4. O conversor resolve herança de modelos, referências de textura, overrides
   legados e definições modernas compatíveis. Gera ícones para itens 2D e,
   para itens 3D suportados, atlas de texturas, geometria, attachables e
   animações de transformações de exibição.
5. O plugin gera os mappings Geyser v2 e um relatório item a item.
6. Os arquivos são preparados em temporários e publicados no destino do Geyser.
   Quando configurado, os arquivos existentes são guardados em backup. Se a
   publicação falhar, o deploy tenta restaurar os arquivos anteriores.
7. O resultado é registrado nos relatórios e no cache. A atualização/reload do
   Geyser, quando configurada, é executada na thread principal.

### Comandos disponíveis

- `/nexobedrock convert [--force]`: converte o pack; `--force` ignora o cache.
- `/nexobedrock status`: mostra configuração, execução e estatísticas recentes.
- `/nexobedrock reload`: recarrega a configuração do plugin.
- `/nexobedrock report`: resume o relatório mais recente.

Os comandos exigem `nexobedrock.admin` (padrão: OP).

### Saídas e proteção

- Pacote Bedrock e mappings na pasta configurada do Geyser.
- Relatórios em `plugins/NexoBedrock/reports/`.
- Cache em `plugins/NexoBedrock/cache/`, indexado pelo hash do pack, fingerprint
  da configuração e versão do plugin.
- Backups dos arquivos substituídos em `plugins/NexoBedrock/backups/`, quando
  `backup` estiver habilitado.
- Cópia opcional dos artefatos em `plugins/NexoBedrock/output/`.

Se nenhum item for convertido, o limite de tamanho for excedido ou ocorrer
falha de deploy, os arquivos anteriores do Geyser devem permanecer
preservados. A conversão pesada não bloqueia a thread principal.

### Implantação atual no Pelican

Purpur/Nexo e Velocity/Geyser estão em containers separados, sem diretório
compartilhado. A configuração instalada grava os artefatos em staging no
backend:

```text
plugins/NexoBedrock/output/packs/nexobedrock.mcpack
plugins/NexoBedrock/output/custom_mappings/nexobedrock.json
```

Depois de cada conversão, copie os dois arquivos para o container do proxy:

```bash
BACKEND=<container-purpur-com-nexo>
PROXY=<container-velocity-com-geyser>

docker cp "$BACKEND:/home/container/plugins/NexoBedrock/output/packs/nexobedrock.mcpack" \
  "$PROXY:/home/container/plugins/Geyser-Velocity/packs/nexobedrock.mcpack"
docker cp "$BACKEND:/home/container/plugins/NexoBedrock/output/custom_mappings/nexobedrock.json" \
  "$PROXY:/home/container/plugins/Geyser-Velocity/custom_mappings/nexobedrock.json"
```

Reinicie o Velocity pelo Pelican depois da cópia para o Geyser carregar os
mappings e o pack. A conversão automática atualiza o staging, mas não sincroniza
arquivos entre containers.

### Escopo e limitações atuais

O MVP contempla itens 2D e modelos 3D baseados em elementos cúbicos, com suporte
a `custom_model_data` legado, `item_model` moderno, herança de modelos,
texturas múltiplas e rotações de face. Para texturas animadas, usa apenas o
primeiro frame.

Não fazem parte do escopo atual: blocos customizados, armaduras/elytra, fontes,
glifos, GUIs, sons, móveis, display entities, ModelEngine, conversão em nuvem e
extensão do Geyser. Casos não suportados devem aparecer no relatório, sem
interromper a conversão.

## Processos de desenvolvimento e validação

1. Fazer alterações mantendo compatibilidade com Java 21, Paper/Purpur e a API
   pública do Nexo.
2. Não incorporar código, assets ou schemas de projetos incompatíveis com a
   licença do plugin; consultar documentação e fontes oficiais para formatos
   externos, especialmente o schema de mappings do Geyser.
3. Criar ou atualizar testes unitários para cada comportamento alterado.
4. Validar com `gradle clean test build` antes de distribuir o jar.
5. Quando a alteração afetar leitura ou conversão de packs, executar também os
   smoke tests opcionais com `NEXOBEDROCK_SMOKE=true`.
6. Tratar `Contexto/` como material de referência somente leitura. Testes e
   artefatos temporários devem ser escritos fora dessa pasta.
7. Conferir o jar final, o `plugin.yml`, a versão e as saídas antes da entrega.

## Objetivos futuros

Os itens abaixo são metas possíveis e devem ser priorizados conforme
compatibilidade, demanda e testes disponíveis.

### Prioridade 1 — confiabilidade e compatibilidade

- Testar em um servidor Paper/Purpur real com as versões de Nexo e Geyser
  usadas pelos administradores.
- Melhorar mensagens de diagnóstico para caminhos inválidos, mappings
  rejeitados e falhas de reload do Geyser.
- Ampliar testes para rollback, limites de tamanho, cache e interrupção do
  plugin durante uma conversão.
- Verificar e documentar combinações suportadas de versões de Paper, Nexo e
  Geyser.

### Prioridade 2 — cobertura de modelos e mappings

- Avaliar melhor suporte a definições modernas compostas (`select` e
  `composite`) e registrar limitações por item com precisão.
- Melhorar a correspondência entre identidade do item no Nexo e as definições
  modernas de item do resource pack.
- Expandir a validação dos arquivos Bedrock gerados e dos mappings antes do
  deploy, com erros apontados no relatório.
- Investigar suporte opcional a animação de textura no cliente Bedrock, sem
  comprometer a conversão atual baseada no primeiro frame.

### Prioridade 3 — operação e manutenção

- Adicionar métricas agregadas e identificação mais clara da origem de cada
  item convertido.
- Permitir políticas de retenção configuráveis para cache, relatórios e
  backups, com limites de espaço em disco.
- Documentar procedimentos de diagnóstico, restauração de backup e atualização
  segura do plugin.
- Definir e publicar a licença do projeto antes de aceitar contribuições ou
  distribuir versões que dependam de código de terceiros.

### Fora do roadmap imediato

Blocos, armaduras, móveis, entidades, fontes, GUIs e integração com ModelEngine
continuam fora do escopo imediato. Cada expansão deve ter desenho técnico,
validação com clientes Bedrock/Geyser e testes próprios antes de ser anunciada
como suportada.

## Critérios para considerar uma melhoria concluída

- A implementação não bloqueia a thread principal durante trabalho pesado.
- Falhas isoladas são reportadas e não derrubam o plugin nem descartam os
  arquivos anteriores do Geyser.
- Testes automatizados cobrem o comportamento novo e os casos de falha
  relevantes.
- O `report.json` explica claramente itens convertidos, ignorados e com falha.
- A documentação descreve requisitos, configuração, limitações e compatibilidade
  sem prometer suporte ainda não validado.
