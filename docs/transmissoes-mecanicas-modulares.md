# Transmissões mecânicas modulares

Esta etapa implementa o primeiro subconjunto do grafo de transmissão do RFC 0005.
O servidor deriva a cadeia a partir das portas físicas da montagem; nomes de
blueprint, quantidade de rodas ou posições fixas não participam da análise.

## Componentes públicos

- **eixo mecânico**: liga duas portas rotativas em relação 1:1;
- **mancal mecânico**: suporta e transmite o eixo em 1:1, com rendimento de 99%;
- **engrenagem reta de 12 dentes**;
- **engrenagem reta de 36 dentes**;
- motores e rodas do núcleo terrestre continuam sendo entrada e carga.

Os blocos usam texturas provisórias existentes. A orientação de colocação define
as faces das portas e o eixo de rotação. Proximidade sem portas compatíveis não
transmite potência.

## Montagem de uma redução 3:1

```text
motor -- eixo 12T == malha == eixo 36T -- eixo/mancal -- roda
```

A engrenagem de 12 dentes como entrada e a de 36 como saída produzem:

- velocidade da saída = `-velocidade do motor / 3`;
- torque ideal multiplicado por 3 e depois limitado pelo rendimento de 96%;
- inversão do sentido por existir um engrenamento externo;
- carga e inércia da saída refletidas ao motor pelo quadrado da relação.

Montar a cadeia no sentido inverso produz uma multiplicação de velocidade 1:3 e
redução correspondente de torque. O limite mais baixo de qualquer porta continua
valendo depois de convertido para o referencial da saída.

## Regras e falhas seguras

- engrenagens só casam com perfil/módulo compatível e eixos paralelos;
- uma cadeia aceita no máximo 64 arestas e oito engrenamentos;
- a relação total deve permanecer entre 1:256 e 256:1;
- loops, ramificações de potência e duas entradas para a mesma roda são recusados;
- eixo aberto deixa o motor girar eletricamente, mas não cria tração;
- exceder um budget desacopla a cadeia inteira; nenhuma aresta é ignorada;
- toda ordenação e propagação é determinística no servidor.

Os diagnósticos derivados são `OPEN_PATH`, `UNKNOWN_PORT`, `INCOMPATIBLE_CONNECTION`,
`INCOMPATIBLE_GEAR_MESH`, `TRANSMISSION_LOOP_UNSUPPORTED`,
`TRANSMISSION_BRANCH_UNSUPPORTED`, `TRANSMISSION_LIMIT_EXCEEDED` e
`MULTIPLE_INPUTS`.

## Acoplamento com o drive

O `CoupledDriveLoop` transforma a velocidade observada na roda para o eixo do
motor antes de calcular back-EMF. O torque calculado no motor atravessa relação e
rendimento antes de virar força de contato. A reação da roda retorna ao motor no
referencial correto, e a inércia rotativa refletida é somada à inércia do rotor.

Assim, redução não é apenas informação visual: altera aceleração, corrente,
velocidade, torque, sentido e carga no mesmo passo autoritativo.

## Limitações atuais

- somente transmissões rotativas em árvore, sem divisão de potência;
- engrenagens retas externas, sem planetárias, diferenciais ou correias;
- sem folga dinâmica, elasticidade, vibração de dentes ou quebra;
- a fase visual ainda é agregada, sem fase individual por eixo;
- texturas são provisórias;
- juntas e servos entram nas etapas seguintes da missão.
