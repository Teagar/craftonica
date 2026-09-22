# Fixtures de mecanismos avançados

Estes quatro roteiros são montagens reproduzíveis feitas somente com componentes
públicos. O runtime não reconhece nomes de blueprint: ele recebe os mesmos módulos,
arestas, portas e perfis usados por qualquer robô. As representações exatas ficam em
`AdvancedMechanismFixtures`; o gate verifica que toda aresta mecânica ou estrutural
liga faces de blocos realmente adjacentes.

Use mundo criativo novo, área livre e uma cópia do mundo. `F` indica a face frontal
e `U` a face superior da orientação instalada.

## 1. Braço serial com garra independente

1. Monte `chassi → junta revoluta → elo → junta revoluta → antebraço` no eixo Z.
2. Prolongue o antebraço por um bloco para formar a palma.
3. Em cada lado da palma instale uma junta revoluta e um bloco de chassi como dedo.
4. Instale um servo na porta `drive` de cada junta. Cada servo precisa também de
   suporte estrutural próprio até o corpo pai; não prenda o servo no corpo filho.
5. Ligue todos os servos a 5 V e GND. Use D9 para ombro, D10 para cotovelo, D11 e
   D12 para os dedos. Não una os dois dedos por engrenagem: o perfil inicial exige
   atuadores independentes.

O firmware usa `Servo.h`. Pulsos de 1.000–2.000 µs geram torque e nunca atribuem a
posição diretamente. Carga, gravidade, batentes e colisão determinam o movimento.

## 2. Carro prismático com servo linear

1. Monte `chassi → junta prismática → carro` no eixo Z.
2. Instale o **servo linear educacional** na porta `drive` e apoie sua face
   `mount_down` por chassis até o corpo raiz.
3. Ligue VCC/GND e sinal D9. O curso físico publicado é de 1 m, centrado entre
   -0,5 m e +0,5 m; o esforço máximo nominal é 0,7 N.
4. Instale um fim de curso no corpo raiz, alimentado, com saída em D4 e êmbolo
   apontado para o carro.

O mesmo decodificador de pulsos do servo rotativo converte o alvo para coordenada
linear. A saída é força; colisão ou batente mantém a última pose válida, aumenta a
carga refletida e pode aquecer/desligar o atuador. Sem energia, a força é zero.

## 3. Base diferencial com duas esteiras

1. Coloque duas esteiras com `mount_up` apoiado por chassi.
2. Em cada `sprocket`, conecte `eixo → motor CC` e apoie `mount_down` do motor na
   estrutura. A correia sem uma dessas duas conexões não transmite esforço.
3. Use uma ponte H por lado. Ambas recebem 5 V/GND, mas PWM, direção e saídas são
   redes independentes: lado esquerdo D2/D3 e direito D4/D5.
4. Comande ambos para frente para avanço e sentidos opostos para skid-steer.

A área de contato, material sob a correia e orientação instalada determinam força e
aderência; não existe roda invisível nem comando especial de esteira.

## 4. Base mecanum em X

1. Monte quatro cadeias `motor CC → eixo → roda mecanum` nos quatro cantos.
2. Vistas de cima, use mãos `esquerda, direita, direita, esquerda` para frente
   esquerda, frente direita, traseira esquerda e traseira direita.
3. Oriente os quatro planos de roda de forma paralela. Ligue as pontes H aos pares
   D2/D3, D4/D5, D6/D7 e D8/D9.
4. Aplique combinações de PWM por roda. Translação lateral e rotação devem emergir
   das forças anisotrópicas dos roletes; não há API holonômica especial.

## Falhas obrigatórias

Para cada roteiro:

- retire VCC: atuadores ficam sem esforço e o diagnóstico torna-se explícito;
- mantenha comando contra batente: corrente e temperatura sobem até proteção, sem
  atravessar o limite;
- coloque um bloco no caminho: a pose proposta é recusada sem teleportar;
- descarregue o chunk de destino: o solver mantém a pose anterior e não solicita
  carregamento;
- salve e recarregue: juntas, drives, sensores e feedback térmico retornam do
  envelope checksummed sem reaplicar impulso ou quadro AVR.

## Gates automatizados

`AdvancedMechanismGateTest` confirma:

- árvore válida de cinco corpos e quatro servos para braço/garra;
- alimentação ausente removendo todo esforço;
- servo linear produzindo força a partir de um pulso AVR confirmado;
- colisão e fronteira descarregada preservando o último estado autoritativo;
- duas esteiras e quatro drives mecanum usando os mesmos analisadores elétricos e
  mecânicos;
- round-trip completo de persistência;
- soak determinístico de 4.000 passos dentro dos budgets;
- snapshots idênticos consumidos por dois clientes passivos, abaixo de 8 KiB.

O ensaio com servidor dedicado e dois processos gráficos reais permanece no
`CRL-94`; esta etapa valida o contrato compartilhado e não declara esse gate externo
como executado.
