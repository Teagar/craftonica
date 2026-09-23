# Migração de mundos 1.2 para 2.0

## Política de compatibilidade

A primeira abertura com a geração 2.0 cria e verifica um snapshot antes do primeiro
tick do Craftônica. Robôs fixos da 1.2 não são convertidos especulativamente em
montagens modulares: eles preservam UUID, dono, módulos, RoboBoard e pose, passam uma
vez para `LEGACY_INERT` e podem coexistir com novos robôs modulares. Isso evita
criação de blocos ou drops duplicados. NBT inválido ou de versão futura fica em
quarentena e seu payload bruto é salvo novamente para recuperação.

Componentes elétricos e RoboBoards usam migradores puros e idempotentes. A carga não
consome inventário, não gera drops e não avança firmware ou física.

## Antes de abrir uma cópia real

1. Feche cliente e servidor; confirme que não existe outro processo usando o save.
2. Duplique o save 1.2 fora da pasta `saves` e guarde essa cópia sem alterações.
3. Conte jogadores, robôs fixos e itens relevantes em baús/inventários. Registre os
   UUIDs dos robôs que serão comparados.
4. Abra somente uma segunda cópia com a build 2.0.
5. O carregamento deve falhar antes do primeiro tick caso o backup não possa ser
   criado e publicado com segurança.

O snapshot fica em `.craftonica-backups/<mundo>/<id>/world`. `manifest.bin` fixa o
caminho relativo, tamanho e SHA-256 de cada arquivo. `session.lock` e o marcador de
migração não pertencem ao snapshot.

## Confirmação depois da abertura

- os mesmos jogadores, UUIDs e inventários permanecem presentes;
- cada robô 1.2 aparece uma única vez como legado inerte e não produz drops;
- novos robôs modulares podem existir ao lado dos legados;
- payload inválido aparece em quarentena, sem sumir nem iniciar simulação;
- fechar e reabrir não executa a migração novamente.

Os gates automatizados confirmam preservação byte a byte da árvore original,
identidades do robô e RoboBoard, módulos, idempotência, NBT opaco, symlinks,
hardlinks, adulteração, falta de espaço e interrupção antes da publicação. A
contagem visual em uma cópia de mundo do usuário continua sendo um ensaio manual:
os testes não abrem nem modificam saves pessoais automaticamente.

## Rollback e downgrade

Compile a mesma revisão 2.0 usada para criar o backup e pare totalmente o Minecraft:

```sh
./scripts/rollback-world.sh "/saves/.craftonica-backups/Mundo/<id>"
./scripts/rollback-world.sh "/saves/.craftonica-backups/Mundo/<id>" "/saves/Mundo-restaurado"
```

O primeiro comando apenas verifica. O segundo aceita destino ausente ou vazio,
copia para uma árvore parcial, confere cada arquivo, força os dados em disco e só
então publica por rename atômico. Nunca selecione o mundo migrado como destino.

Para voltar à 1.2.0, instale o JAR público 1.2.0 em outra instância e abra apenas
`Mundo-restaurado`. Conte novamente UUIDs e inventários. Recursos criados na 2.0 não
são convertidos para 1.2; por isso o downgrade parte obrigatoriamente do snapshot
pré-migração, não do save escrito pela 2.0.

## Falhas seguras

- **sem espaço:** nenhum marcador é publicado e nenhum destino é substituído;
- **interrupção antes da publicação:** a árvore parcial é removida;
- **destino já contém arquivos:** restauração recusada sem sobrescrever;
- **manifesto ou payload alterado:** verificação recusada;
- **symlink/hardlink:** operação recusada;
- **versão de writer futura:** mundo não inicia com código antigo.

Preserve tanto o mundo migrado quanto o backup recusado para diagnóstico. Não tente
“consertar” `manifest.bin` ou `migration-state.bin` manualmente.
