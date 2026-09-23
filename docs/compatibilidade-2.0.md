# Compatibilidade e limitações 2.0

| Área | Suporte 2.0 |
|---|---|
| Plataforma | Minecraft 1.7.10, Forge 10.13.4.1614, Java 8, Linux x86_64 |
| Eletricidade | DC nodal limitada e fiação física separada de Redstone |
| Firmware | perfil Arduino-compatible limitado, AVR isolado, Servo.h em D9/D10 |
| Estrutura | montagem arbitrária limitada, massa/inércia/centro derivados |
| Locomoção | 2WD, 3 rodas, 4WD, skid steer, esteira, omni e mecanum |
| Mecanismos | árvores de corpos com juntas de 1 GDL; loops recusados |
| Sensores | HC-SR04, encoder, fim de curso e IMU alimentados por portas reais |
| Multiplayer | servidor autoritativo; dois clientes reais validados |
| Migração | 1.2 preservada como legado inerte; backup e rollback verificados |

## Não suportado

- mecanismos cinemáticos fechados, diferenciais e divisão de potência;
- suspensão, deformação de pneus, roll/pitch contínuos e momento entre robôs;
- precisão garantida de motor, servo, atrito ou sensor comercial;
- bibliotecas Arduino arbitrárias, rede, filesystem e Serial RX;
- carregamento de chunks por movimento ou sensores;
- downgrade do mundo 2.0 sem restaurar o snapshot pré-migração.
- compilação AVR quando algum componente do host diverge do manifesto SHA-256;
  a falha é explícita e exige um ambiente suportado, nunca atualização silenciosa.

Consulte `auditoria-fisica-metrologica-2.0.md` para erros e fenômenos omitidos,
`validacao-soak-2.0.md` para budgets e `validacao-runtime-movel.md` para a matriz
dedicada/multiplayer.
