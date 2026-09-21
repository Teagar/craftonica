# Renderização e sincronização modular

O servidor envia uma projeção visual imutável do manifesto apenas no spawn da
entidade. Ela contém uma paleta de nomes de blocos e, por módulo, índice da paleta,
posição local, orientação, metadata visual e flags de animação. Tile NBT, firmware,
checkpoint, netlist, massa, forças, decisões de colisão e medições não entram nesse
payload.

Limites do snapshot visual:

- 256 módulos;
- 64 blocos distintos na paleta;
- 64 bytes UTF-8 por nome de bloco;
- coordenadas locais entre -16 e 16;
- 8 KiB no payload adicional de spawn.

Com o perfil atual de 256 módulos usando uma paleta comum, o payload fica abaixo
de 2 KiB. A pose usa o rastreamento padrão do Forge (`96` blocos, atualização a
cada `2` ticks). O cliente interpola em até cinco passos e nunca integra física.

Três valores visuais limitados usam `DataWatcher`: status, máscara diagnóstica e
fase mecânica. A máscara distingue falha elétrica, drive aberto, proteção térmica,
política sensorial e quarentena. Rodas e eixos usam a fase produzida no servidor;
o cliente apenas desenha. Nesta primeira projeção, a fase é a média do módulo das
velocidades dos canais e anima todos os elementos rotativos; sentido e fase por eixo
não são transmitidos. Geometria, orientação e material vêm de cada entrada do
snapshot, portanto não há modelo ou blueprint fixo por layout.
