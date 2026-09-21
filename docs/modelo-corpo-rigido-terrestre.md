# Modelo de corpo rígido terrestre modular

O perfil terrestre modular usa SI internamente (`1 bloco = 1 m`) e é executado
somente no servidor. Massa, centro de massa, inércia diagonal, contatos e volumes
de colisão são recalculados deterministicamente a partir dos módulos e versões do
catálogo presentes no manifesto.

## Propriedades derivadas

Para cada módulo de massa `mi`, centro instalado `ri` e inércia principal rotacionada
`Ii`, o extrator calcula:

```text
M = sum(mi)
center = sum(mi * ri) / M
I = sum(Ii + parallelAxis(mi, ri - center))
```

As caixas locais são rotacionadas em incrementos ortogonais, transladadas pela pose
do módulo e fundidas apenas quando compartilham uma face completa. O perfil aceita
no máximo 128 volumes resultantes e 24 contatos.

## Integração e contato

Cada tick de 50 ms usa dois subpassos de 25 ms. O estado dinâmico contém posição,
velocidade linear em m/s, yaw em radianos e velocidade angular em rad/s. A gravidade
é `9,80665 m/s²`. Contatos apoiados dividem igualmente a carga normal neste perfil.

Força longitudinal só é aceita em uma roda realmente apoiada. Ela é limitada pelo
menor valor entre a força disponível no eixo e `mu * normal`. Atrito lateral e
resistência de rolamento usam os coeficientes versionados do componente. Rodízios
fornecem apoio e resistência de rolamento, mas nunca recebem força comandada.

## Colisão e chunks

A colisão usa uma AABB conservadora varrida entre as poses inicial e final para
cada volume composto após aplicar o yaw, evitando atravessar paredes entre subpassos.
Antes de consultar blocos, todas as regiões tocadas devem estar em chunks já
carregados. Uma fronteira descarregada interrompe o movimento e zera velocidades;
ela nunca causa carregamento de chunk. Colisões verticais e planares são resolvidas
separadamente para permitir apoio sem atravessar paredes. Cada subpasso possui um
orçamento fail-safe de 128 consultas de colisão.

## Aproximações declaradas

- não há suspensão, deformação de pneus ou capotamento contínuo;
- pitch e roll não são integrados no perfil terrestre inicial;
- a carga normal é dividida igualmente entre contatos apoiados;
- colisões rotacionadas usam caixas envolventes conservadoras;
- não há conservação de momento entre robôs;
- propriedades físicas são recalculadas do manifesto em cada carga; a migração e
  redundância persistente dessas propriedades pertencem ao formato seguro do
  `CRL-80`.
