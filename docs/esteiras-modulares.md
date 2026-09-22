# Esteiras modulares

O perfil inicial usa um módulo educacional declarado de 1 m. O bloco representa,
sem ocultar a aproximação, uma correia contínua com roda motriz, roda-guia e trecho
inferior de contato. Ele não é reinterpretado como uma roda invisível: possui massa,
volume, raio da roda motriz, comprimento e largura de contato, atrito longitudinal,
atrito lateral e resistência ao rolamento próprios.

## Montagem física

O módulo expõe duas conexões obrigatórias:

- `sprocket`: entrada rotativa lateral para motor, eixo ou transmissão;
- `mount_up`: fixação estrutural superior ao chassi.

O caminho entre motor e roda motriz é resolvido pelo mesmo grafo de eixos,
mancais e engrenagens usado pelas rodas convencionais. A esteira só transmite
esforço quando há exatamente um caminho mecânico suportado **e** a fixação
estrutural está presente. Uma montagem incompleta permanece como massa e contato,
mas produz `DRIVE_PATH_OPEN` ou `STRUCTURAL_MOUNT_OPEN`, não aplica tração e aparece
no diagnóstico do robô.

## Contato e skid-steer

O contato é uma área retangular de 0,90 m por 0,38 m. A sondagem conservadora gira
essa área com o robô e consulta somente blocos em chunks já carregados. A aderência
é derivada da `slipperiness` do piso e limita a força que o conjunto motor/transmissão
pode entregar. Gelo reduz a tração; pisos com maior aderência preservam mais força.
Partes da área sobre o vazio contribuem aderência zero, portanto apoiar apenas uma
fração da correia também reduz proporcionalmente o esforço disponível.

Duas esteiras laterais usam seus pontos de contato físicos. Comandos iguais geram
translação; comandos diferenciais ou opostos geram momento de yaw. O atrito lateral
finito resiste ao deslizamento sem impedir o skid-steer. Não existe teleporte,
velocidade prescrita nem correção calculada pelo cliente.

## Limites

- no máximo oito módulos de esteira por montagem;
- até 24 contatos no corpo rígido, conforme o budget terrestre existente;
- a correia é um elemento contínuo equivalente declarado, não uma cadeia de elos
  rígidos individuais;
- deformação da correia, suspensão e perda de tensão ainda não são modeladas;
- a textura representa correia e rodas internamente, sem animação individual dos elos.
