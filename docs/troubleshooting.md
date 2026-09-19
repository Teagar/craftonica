# Solucao de problemas

| Sintoma | Causa provavel | Acao |
| --- | --- | --- |
| Forge nao inicia | Java diferente de 8 ou Forge incorreto | Use Forge 10.13.4.1614 e `scripts/gradle-java8.sh` |
| `COMPILER_ARTIFACTS_MISSING` | Instalacao copiou apenas o mod JAR | Rode `scripts/install-instance.sh` com jogo/servidor fechado |
| `RUNTIME_ARTIFACTS_MISSING` | Worker ou launcher ausente | Confira `craftonica-runtime` e permissao executavel do launcher |
| Compilador indisponivel | `bubblewrap`/`systemd --user` ausente | Instale ambos e rode `scripts/firmware/test-compiler.sh` |
| Mundo recusa migracao | Backup sem espaco, symlink ou alteracao concorrente | Feche outras instancias, libere espaco e preserve o save para diagnostico |
| Editor informa revisao antiga | Outro fluxo alterou a placa | Use `F7`, revise o fonte e compile novamente |
| Medicao pendente | Rede ainda esta na fila limitada | Aguarde alguns ticks sem alterar blocos |
| Resistencia recusada em LED/diodo | Elemento nao linear no modo auxiliar | Remova o semicondutor ou use tensao/corrente |
| Sem audio do buzzer/motor | Comportamento intencional da 1.0 | Use a textura de atividade; audio/mecanica nao sao simulados |

Para um relatorio reproduzivel, registre versao do mod/Forge/Java, sistema
operacional, trecho do log, coordenadas aproximadas, passos e hash SHA-256 do JAR.
Nunca envie saves contendo dados pessoais sem revisao.
