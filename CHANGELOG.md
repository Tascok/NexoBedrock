# Dev log — NexoBedrock

## v0.1.35

- Completa a compensação vertical de 8 unidades no slot de cabeça, mantendo o alinhamento dos modelos 3D após o recentralizamento da geometria.
- Amplia a regressão para as duas mãos em primeira e terceira pessoa, para a cabeça e para os pivôs da hierarquia base.

## v0.1.34

- Correção da altura dos modelos 3D na mão, com offsets verticais distintos para primeira e terceira pessoa.
- Compensação de 8 unidades Y após o recentralizamento da geometria, preservando as posições calibradas para o rig original.

## v0.1.33

- A extensão aguarda temporariamente o endpoint do Paper na inicialização, evitando carregar um pack antigo quando o proxy inicia primeiro. O tempo é configurável e, caso o Paper continue inacessível, usa-se a última geração validada.

## v0.1.32

- Sincronização opcional de gerações completas entre Paper e extensões Geyser por HTTPS autenticado.
- Download em área temporária com limite de tamanho, nomes fixos e verificação SHA-256, seguido de instalação na inicialização com backup e recuperação.
- Sem reinício automático: novas gerações ficam pendentes até a reinicialização do Geyser, evitando desconectar jogadores inesperadamente.

## v0.1.31

- Identificação automática dos fundos de GUI pelos nomes dos glyphs e caminhos das texturas declarados no Nexo, preservando ajustes existentes e listando correspondências ambíguas.

## v0.1.30

- Geração automática de `plugins/NexoBedrock/gui-offsets.json` com ajustes individuais de posição para os glyphs de fundo detectados.
- Inclusão do caractere e da referência de textura para ajudar a identificar cada GUI; valores existentes são preservados quando novas entradas são geradas.
- Alterações de posição invalidam o cache de conversão e a identidade do pack Bedrock, permitindo que o cliente receba o pack atualizado.
- Arquivos inválidos ou alterados durante a conversão são preservados e interrompem a operação.
- Documentação do ajuste manual e testes para criação, preservação, validação e aplicação dos valores.

## v0.1.28

- Ajuste vertical de dois pixels nos fundos de menus grandes, mantendo a posição dos menus pequenos.

## v0.1.27

- Primeira release de produção, com o plugin Paper e a extensão Geyser.
- Ajuste vertical dos fundos de GUIs clássicas.

O alinhamento de GUIs depende do pack e da interface Bedrock; confira visualmente após atualizar. Para Geyser em outro servidor ou proxy, os arquivos gerados devem ser sincronizados e o Geyser reiniciado ou recarregado.
