# Dev log — NexoBedrock

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
