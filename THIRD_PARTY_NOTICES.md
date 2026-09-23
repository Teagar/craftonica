# Third-party notices

Craftonica does not redistribute Minecraft or Forge. Users obtain them under
their own terms. Minecraft is a trademark of Microsoft/Mojang; Arduino is a
trademark of Arduino SA. No affiliation or endorsement is implied.

| Component | Use | License/source |
| --- | --- | --- |
| Minecraft Java 1.7.10 | Host game, not distributed | Minecraft EULA, `minecraft.net/eula` |
| Minecraft Forge 10.13.4.1614 / ForgeGradle 1.2 | Mod API/build tooling, not distributed in the mod JAR | LGPL-2.1; `github.com/MinecraftForge` |
| Eclipse Temurin 8 | Downloaded build JDK | GPL-2.0 with Classpath Exception; `adoptium.net` |
| Arduino AVR core 1.8.6 | Downloaded and patched compiler input | LGPL-2.1; license files in the verified archive from `downloads.arduino.cc` |
| Arduino AVR GCC toolchain 7.3.0-atmel3.6.1-arduino7 | Downloaded compiler toolchain | Mixed GPL/LGPL/BSD components; authoritative notices are bundled in the verified archive |
| JUnit 4.13.2 | Test-only dependency | EPL-1.0; `junit.org` |

The bootstrap records archive hashes and installed-tree hashes. Before
redistributing a toolchain archive, preserve every license/notice included by
its upstream distribution and complete a separate compliance review. Craftonica
2.0 downloads these archives into the local cache and does not place them in the
mod JAR.
