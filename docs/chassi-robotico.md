# Chassi robótico móvel

O primeiro chassi móvel do Craftônica já existe como uma entidade persistente e
servidor-autoritativa. Nesta etapa ele permanece parado: ponte H, motores e tração
diferencial serão conectados nas etapas seguintes da missão.

Operadores podem criar temporariamente um chassi de validação com:

```text
/craftonica robot create
```

O comando só cria a entidade quando todo o volume de colisão está em chunks já
carregados, sem blocos, entidades ou líquidos. A montagem física substituirá esse
caminho temporário quando o sistema de conversão for implementado.

## Estado persistente

Cada entidade guarda:

- UUID próprio e UUID do proprietário;
- pose contínua e dimensões do chassi;
- manifesto canônico de até 32 módulos internos;
- fingerprint SHA-256 do manifesto;
- geração, quadro de simulação e estado operacional;
- indicação de retomada e diagnóstico limitado.

Schemas desconhecidos, fingerprints alterados, poses inválidas e manifestos
inconsistentes entram em `QUARANTINED`. Nesse estado o chassi fica inerte e não
solta componentes automaticamente.

Ao descarregar o chunk, o estado é suspenso, a geração é incrementada e nenhum
quadro de simulação avança. Depois da recarga há um tick de guarda antes de o
chassi voltar ao estado parado. Movimento não usa o relógio de parede.

## Multiplayer

O servidor registra e rastreia a entidade pelo canal padrão do Forge. Clientes
recebem pose, rotação e somente dois valores visuais limitados: estado operacional
e hash curto do manifesto. A pose é interpolada no cliente; proprietário,
manifesto completo e decisões futuras de física permanecem no servidor.

O modelo atual é um protótipo sem textura própria. Ele serve para validar escala,
orientação, persistência e tracking; o acabamento visual será feito no marco final.
