package br.com.craftonica.persistence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public final class WorldBackupServiceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void publishesVerifiedBackupOnceAndExcludesLiveLockAndControl() throws Exception {
        Path world = temporary.newFolder("world").toPath();
        Files.write(world.resolve("level.dat"), "level".getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(world.resolve("region"));
        Files.write(world.resolve("region/r.0.0.mca"), new byte[] {1, 2, 3});
        Files.write(world.resolve("session.lock"), new byte[] {9});

        Path backup = WorldBackupService.prepare(world);
        WorldBackupService.verifyBackup(backup);
        Path snapshot = backup.resolve("world");
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(snapshot.resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(snapshot.resolve("session.lock")));
        assertFalse(Files.exists(snapshot.resolve("craftonica/migration-state.bin")));
        assertEquals(backup, WorldBackupService.prepare(world));
    }

    @Test
    public void changedManifestBlocksStartupInsteadOfTrustingDamagedBackup() throws Exception {
        Path world = temporary.newFolder("damaged").toPath();
        Files.write(world.resolve("level.dat"), new byte[] {1});
        Path backup = WorldBackupService.prepare(world);
        Files.write(backup.resolve("manifest.bin"), new byte[] {0});
        try {
            WorldBackupService.prepare(world);
            fail("expected damaged backup rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("manifest"));
        }
    }

    @Test
    public void changedPayloadAndUnlistedFilesBlockRepeatedStartup() throws Exception {
        Path world = temporary.newFolder("payload").toPath();
        Files.write(world.resolve("level.dat"), new byte[] {1});
        Path backup = WorldBackupService.prepare(world);
        Files.write(backup.resolve("world/level.dat"), new byte[] {2});
        try {
            WorldBackupService.prepare(world);
            fail("expected damaged payload rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("verification"));
        }

        Path secondWorld = temporary.newFolder("extra").toPath();
        Files.write(secondWorld.resolve("level.dat"), new byte[] {1});
        Path secondBackup = WorldBackupService.prepare(secondWorld);
        Files.write(secondBackup.resolve("world/injected.dat"), new byte[] {3});
        try {
            WorldBackupService.verifyBackup(secondBackup);
            fail("expected unlisted file rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("differs"));
        }
    }

    @Test
    public void movedWorldFindsBackupThroughRelativeIdentity() throws Exception {
        Path container = temporary.newFolder("move-source").toPath();
        Path world = Files.createDirectory(container.resolve("world"));
        Files.write(world.resolve("level.dat"), new byte[] {1});
        Path backup = WorldBackupService.prepare(world);

        Path movedContainer = temporary.newFolder("move-target").toPath();
        Files.move(world, movedContainer.resolve("world"));
        Files.move(container.resolve(".craftonica-backups"), movedContainer.resolve(".craftonica-backups"));
        Path resolved = WorldBackupService.prepare(movedContainer.resolve("world"));
        assertEquals(backup.getFileName(), resolved.getFileName());
        assertTrue(resolved.startsWith(movedContainer));
    }

    @Test
    public void symbolicLinkAbortsWithoutAdvancingMigrationControl() throws Exception {
        Path world = temporary.newFolder("linked").toPath();
        Path outside = temporary.newFile("outside").toPath();
        try {
            Files.createSymbolicLink(world.resolve("escape"), outside);
        } catch (UnsupportedOperationException unavailable) {
            return;
        }
        try {
            WorldBackupService.prepare(world);
            fail("expected symbolic link rejection");
        } catch (IOException expected) {
            assertFalse(Files.exists(world.resolve("craftonica/migration-state.bin")));
        }
    }

    @Test
    public void hardLinkedBackupPayloadIsRejected() throws Exception {
        Path world = temporary.newFolder("hardlink-world").toPath();
        Files.write(world.resolve("level.dat"), new byte[] {1});
        Path backup = WorldBackupService.prepare(world);
        Path payload = backup.resolve("world/level.dat");
        Path replacement = backup.resolve("world/replacement");
        Files.createLink(replacement, payload);
        try {
            WorldBackupService.verifyBackup(backup);
            fail("expected hard link rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Hard-linked"));
        }
    }
}
