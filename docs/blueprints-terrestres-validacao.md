# Blueprints terrestres de validação

Estes blueprints são instruções reproduzíveis e fixtures sob `src/test`; não são
receitas reconhecidas pelo runtime. O runtime recebe somente módulos, arestas e
terminais. Nenhuma classe de produção contém nomes como “2WD”, “4WD” ou “skid”.

## Montagens exercitadas

| Fixture | Contatos | Drives | Geometria distintiva |
|---|---:|---:|---|
| 2WD + rodízio | 3 | 2 | rodas em `x=-2/+2,z=-1`; rodízio em `z=2` |
| três rodas | 3 | 3 | terceira cadeia motor–eixo–roda em `z=2` |
| 4WD | 4 | 4 | rodas nos quatro cantos `x=-2/+2,z=-2/+2` |
| skid-steer | 4 | 4 | bitola ampliada para `x=-3/+3` e chassi longitudinal |

Cada drive usa blocos públicos distintos de ponte H, motor CC, eixo e roda. VCC e
GND compartilham redes físicas; PWM, direção e saídas usam redes independentes e
RoboPorts `D2..D9`. As cadeias mecânicas são arestas `shaft → shaft_in → shaft_out
→ hub`, não proximidade.

## Variações comprovadas

- roda de 100 mm trocada por 150 mm altera raio de contato e inércia;
- dois blocos de chassi adicionais aumentam massa;
- remoção da aresta eixo–roda elimina exatamente um caminho de drive;
- remoção do terminal PWM produz `HBRIDGE_PWM_OPEN`.

## Gates automáticos

`TerrestrialBlueprintGateTest` passa todas as quatro montagens pelos mesmos
`RigidBodyProperties`, `MechanicalAssemblyAnalyzer`, `MobileElectricalEvaluator` e
`CoupledDriveLoop`. Um soak puro de 20.000 passos compara resultados bit a bit e
verifica finitude. O snapshot visual é decodificado por dois consumidores passivos
independentes e permanece no limite de 8 KiB.

Colisão composta, chunks descarregados, persistência/quarentena, orçamento global
de 64 robôs e rollback transacional permanecem cobertos pelos gates especializados
anteriores da mesma suíte. Este gate não abre clientes gráficos nem servidor
dedicado reais; essa validação de processo é deliberadamente reservada ao CRL-94.

## Limites publicados do núcleo terrestre

- 256 componentes, 1.024 arestas e extensão de 16 blocos por eixo;
- 128 volumes de colisão, 24 contatos e 128 consultas por subpasso;
- 16 canais de drive e 32 sensores instalados (máximo de 8 ativos);
- 1 quadro AVR, 1 avaliação elétrica, 64 passos de drive e 4 subpassos por robô;
- 64 robôs por dimensão/tick, com seleção circular determinística;
- snapshot visual de até 8 KiB, tracking de 96 blocos a cada 2 ticks;
- nenhuma busca, colisão ou medição solicita carregamento de chunk.
