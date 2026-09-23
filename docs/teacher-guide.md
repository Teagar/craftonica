# Guia do professor

## Preparacao

1. Instale cliente/servidor conforme [`installation-1.1.md`](installation-1.1.md).
2. Crie um mundo de teste separado e valide os seis laboratorios publicados.
3. Entregue aos alunos o [guia do aluno](student-guide.md) e as limitacoes do modelo.
4. Em multiplayer, mantenha professores como operadores nivel 2 para o bypass
   administrativo de RoboBoards; cada placa pertence ao jogador que a colocou.

## Licoes

```text
/craftonica lesson list
/craftonica lesson start ohm-led-220
/craftonica lesson status
/craftonica lesson check <x> <y> <z>
```

O avaliador aceita alternativas eletricamente corretas e nunca confia em valores
calculados pelo cliente.

## Robótica modular 2.0

Use o [`currículo progressivo`](curriculum/2.0-robotica-modular.md) em um mundo de
teste separado. Não entregue comandos de geração ou direção. Avalie se o aluno:

1. conclui 2WD, 4WD e uma articulação usando somente blocos públicos;
2. distingue suporte estrutural, rede elétrica e transmissão mecânica;
3. encontra uma falha em cada domínio sem pedir uma física alternativa;
4. explica por que atuadores aplicam esforço em vez de atribuir pose;
5. salva, reabre e desmonta sem duplicar componentes.

As coordenadas podem variar. A evidência é o diagnóstico do servidor e o
comportamento físico, não a cópia exata de um desenho.

## Atividades autorais

```text
/craftonica teacher create lamp s:1:1,g:1:1,r:1:3,l:1:2 l:i:.013:.001:.05:all
/craftonica teacher assign teacher-lamp
/craftonica lesson start assigned
/craftonica teacher list
/craftonica teacher export
```

O CSV agregado fica em `craftonica/exports/lesson-completions.csv` dentro do save
e nao inclui UUID, nome, coordenadas ou medicoes individuais. Preserve uma copia
do save antes de atualizar o mod e consulte a matriz de acessibilidade em
[`reports/crl-33-performance-accessibility-1.0.md`](reports/crl-33-performance-accessibility-1.0.md).
