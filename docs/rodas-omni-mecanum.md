# Rodas omni e mecanum

O perfil holonômico deriva forças das rodas realmente instaladas. Não existe um
comando especial de “andar de lado”, uma identificação de blueprint X/O ou uma
correção automática da montagem.

## Componentes

- **Roda omni 100 mm:** roletes passivos liberam quase todo o esforço lateral;
  a tração motriz permanece no plano da roda.
- **Mecanum esquerda 100 mm:** roletes com mão esquerda a 45 graus.
- **Mecanum direita 100 mm:** roletes com mão direita a 45 graus.

Cada roda possui massa, volume, raio, largura, porta física `hub`, limite de torque,
atrito na direção restringida pelos roletes e atrito reduzido na direção livre. As
duas mãos mecanum são blocos diferentes para impedir que uma etiqueta ou posição no
chassi escolha silenciosamente o sentido dos roletes.

## Projeção física

A orientação do `hub` define o eixo. O servidor obtém primeiro a direção tangencial
da roda e então a gira pelo ângulo e pela mão dos roletes. Essa direção projetada é
usada para força de contato, reação, atrito e velocidade medida pelo motor.

Para mecanum a 45 graus, o braço efetivo entre torque e força é
`raio * cos(45 graus)`. Portanto corrente, back-EMF, velocidade e força usam a mesma
geometria; o ângulo não é apenas uma animação visual. Na roda omni o ângulo projetado
é zero e o braço efetivo coincide com o raio físico.

## Bases X e O

Uma base X e uma base O diferem somente pela escolha das mãos em cada canto e pela
orientação física dos blocos. Combinações de sinais nos quatro motores somam os
vetores de contato em avanço, deslocamento lateral ou yaw. Trocar X por O inverte os
vetores laterais esperados. Girar ou instalar uma única roda incorretamente introduz
força lateral e/ou momento indesejado; o simulador não corrige o erro.

## Limites

- roletes são contatos anisotrópicos equivalentes, não corpos articulados individuais;
- deslizamento é limitado por Coulomb simplificado e pelo material do piso;
- deformação dos roletes e distribuição dinâmica de carga não são modeladas;
- o limite terrestre continua sendo 24 contatos e 16 canais de drive por robô;
- toda integração e decisão de contato permanece autoritativa no servidor.
