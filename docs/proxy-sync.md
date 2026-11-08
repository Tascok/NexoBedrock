# Sincronização opcional com Geyser remoto

O NexoBedrock pode disponibilizar uma geração com o pack, mappings de itens e mappings de móveis para cada instância Geyser buscar automaticamente. A sincronização é **desativada por padrão**. Instale versões correspondentes do plugin Paper e da extensão Geyser.

## Segurança e pré-requisitos

- O proxy deve conseguir alcançar a porta HTTPS configurada no Paper; restrinja-a por firewall ou rede privada. Não exponha portas sem necessidade.
- Configure um certificado TLS válido para o nome usado na URL. Um certificado privado ou autoassinado pode ser confiado explicitamente na extensão, em `ca-certificate`; não desative a verificação TLS ou de hostname.
- Gere um token aleatório exclusivo, com 32 a 256 caracteres `A-Z`, `a-z`, `0-9`, `_` ou `-`. Ele deve ser igual no Paper e em cada proxy. Use arquivos com permissões restritas ou variáveis de ambiente; nunca o publique em Git, logs ou capturas.
- O Geyser precisa permitir conteúdo personalizado. Consulte a [documentação Geyser](https://geysermc.org/wiki/geyser/custom-items/#loading-mappings-and-resource-packs).

## Paper

No `plugins/NexoBedrock/config.yml` configure, por exemplo:

```yaml
proxy-sync:
  enabled: true
  bind-address: "127.0.0.1" # altere apenas se o proxy estiver em outro host/container
  port: 8443
  keystore-path: "plugins/NexoBedrock/sync-keystore.p12"
  keystore-password-file: "plugins/NexoBedrock/sync-keystore-password.txt"
  token-file: "plugins/NexoBedrock/sync-token.txt"
```

O keystore PKCS#12 precisa conter a chave privada e o certificado. Em vez de arquivos de senha e token, deixe os campos `*-file` vazios e use as variáveis indicadas por `keystore-password-env` e `token-env`. Caminhos relativos são resolvidos a partir da raiz do servidor. Proteja os arquivos de segredo com permissões de leitura apenas para o usuário do servidor. A configuração do endpoint é lida no início do plugin: reinicie o Paper após alterá-la.

Após a conversão, o endpoint autenticado `https://HOST:8443/v1/bundle` disponibiliza somente o último pacote completo. Durante a inicialização ou uma conversão, e até que ela termine com sucesso, responde 503 em vez de oferecer uma geração antiga. Se a conversão automática estiver desativada, execute `/nexobedrock convert` para disponibilizar a primeira geração. Falhas de configuração do endpoint são registradas sem expor o segredo.

## Proxy

Depois de instalar a extensão e iniciá-la uma vez, edite `extensions/nexobedrock/sync.properties` dentro da pasta de configuração do Geyser:

```properties
enabled=true
url=https://paper.exemplo:8443/v1/bundle
token-file=sync-token.txt
ca-certificate=sync-ca.pem
interval-seconds=60
startup-wait-seconds=90
```

`token-file` e `ca-certificate` são relativos à pasta de dados da extensão; caminhos absolutos também são aceitos. O token precisa ser idêntico ao do Paper. Se usar uma autoridade certificadora já confiável pela JVM, deixe `ca-certificate` vazio. Em vez de `token-file`, deixe-o vazio e use a variável de ambiente nomeada por `token-env`. Reinicie o Geyser após alterar essas opções. Cada proxy precisa da própria extensão e acesso de rede ao Paper.

## Quando uma atualização entra em uso

A extensão baixa a geração durante a inicialização e verifica novamente em intervalos de pelo menos 30 segundos. Downloads recebidos enquanto o Geyser está em execução ficam **pendentes**: não substituem arquivos ativos imediatamente. No próximo reinício completo do Geyser, a extensão valida e instala o pack e ambos os mappings antes do carregamento dos registries. O arquivo `sync-applied.sha256` identifica a geração instalada. Reinício automático não é executado; jogadores Bedrock precisarão reconectar.

Se o proxy iniciar antes do Paper, ele tenta aguardar o endpoint por até `startup-wait-seconds` (padrão 90, máximo 300) antes de carregar o Geyser. Cada tentativa de rede também tem seus próprios limites de tempo, e a transferência de um pack pode prolongar o início. Certificados inválidos e respostas de autenticação incorreta falham sem repetir. Se o prazo esgotar, usa a última geração verificada; quando o Paper voltar, a nova geração será baixada em segundo plano e ficará pendente até outro reinício. Configure `startup-wait-seconds=0` para não aguardar.

Os arquivos anteriores são preservados em diretórios `sync-backup-*` na pasta da extensão. Se uma troca for interrompida após iniciar, o journal `sync-transaction` permite restaurar os arquivos anteriores no próximo início. Não remova o journal durante uma recuperação; verifique os logs antes de permitir jogadores se houver erro. O mecanismo depende do comportamento do sistema de arquivos e não substitui backups externos.

Após editar `gui-offsets.json`, execute `/nexobedrock convert`, aguarde o log de download no proxy e reinicie o Geyser para ativar. Não é necessário copiar os arquivos manualmente. A documentação do Geyser recomenda reinicialização para mappings; `geyser reload` pode desconectar jogadores e não é garantido para atualizar todos os registries.

## Limitações

Os destinos deste mecanismo são `packs/nexobedrock.mcpack`, `custom_mappings/nexobedrock.json` e os mappings de móveis da extensão. Ele não transfere configurações, credenciais ou outros plugins. O limite de download é 240 MiB. Uma falha de rede mantém a última geração verificada, mas uma geração pendente ainda exige reinício. Arquivos/paths inacessíveis ou certificados não confiáveis exigem correção pelo administrador.
