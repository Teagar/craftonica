package br.com.craftonica.persistence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;

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

    @Test
    public void restoresVerifiedSnapshotOnlyIntoMissingOrEmptyDirectory() throws Exception {
        Path world = temporary.newFolder("restore-source").toPath();
        byte[] level = "level-with-world-uuid".getBytes(StandardCharsets.UTF_8);
        byte[] player = "player-uuid-inventory-slots".getBytes(StandardCharsets.UTF_8);
        Files.write(world.resolve("level.dat"), level);
        Files.createDirectories(world.resolve("playerdata"));
        Files.write(world.resolve("playerdata/00000000-0000-0000-0000-000000000092.dat"), player);
        Path backup = WorldBackupService.prepare(world);

        Path missing = world.getParent().resolve("restored-missing");
        assertEquals(missing.toAbsolutePath().normalize(), WorldBackupService.restore(backup, missing));
        assertArrayEquals(level, Files.readAllBytes(missing.resolve("level.dat")));
        assertArrayEquals(player, Files.readAllBytes(missing.resolve(
                "playerdata/00000000-0000-0000-0000-000000000092.dat")));
        assertFalse(Files.exists(missing.resolve("craftonica/migration-state.bin")));

        Path empty = Files.createDirectory(world.getParent().resolve("restored-empty"));
        WorldBackupService.restore(backup, empty);
        assertArrayEquals(level, Files.readAllBytes(empty.resolve("level.dat")));

        Path occupied = Files.createDirectory(world.getParent().resolve("restored-occupied"));
        Files.write(occupied.resolve("do-not-overwrite"), new byte[] {99});
        try {
            WorldBackupService.restore(backup, occupied);
            fail("expected non-empty target rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("not empty"));
            assertArrayEquals(new byte[] {99}, Files.readAllBytes(occupied.resolve("do-not-overwrite")));
        }
        try {
            WorldBackupService.restore(backup,backup.resolve("nested-save"));
            fail("expected overlapping target rejection");
        }catch(IOException expected){assertTrue(expected.getMessage().contains("overlap"));}
        WorldBackupService.verifyBackup(backup);
    }

    @Test
    public void insufficientSpaceNeverPublishesBackupOrMigrationMarker() throws Exception {
        Path world = temporary.newFolder("no-space-backup").toPath();
        Files.write(world.resolve("level.dat"), new byte[] {1,2,3});
        try {
            WorldBackupService.prepare(world, noSpace());
            fail("expected space rejection");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("space"));
            assertFalse(Files.exists(world.resolve("craftonica/migration-state.bin")));
        }
        Path root=world.getParent().resolve(".craftonica-backups/no-space-backup");
        if(Files.exists(root))assertFalse(hasEntryEnding(root,".partial"));
    }

    @Test
    public void restoreInterruptionAndNoSpaceLeaveTargetAndBackupUntouched() throws Exception {
        Path world = temporary.newFolder("rollback-source").toPath();
        Files.write(world.resolve("level.dat"), new byte[] {4,5,6});
        Path backup = WorldBackupService.prepare(world);
        Path missing = world.getParent().resolve("interrupted-target");
        try {
            WorldBackupService.restore(backup,missing,plentyOfSpace(),new WorldBackupService.RestoreHook(){
                @Override public void beforePublish(Path partial,Path target)throws IOException{
                    assertArrayEquals(new byte[]{4,5,6},Files.readAllBytes(partial.resolve("level.dat")));
                    throw new IOException("simulated interruption");
                }});
            fail("expected interrupted restore");
        }catch(IOException expected){assertEquals("simulated interruption",expected.getMessage());}
        assertFalse(Files.exists(missing));
        assertFalse(hasEntryStarting(world.getParent(),".craftonica-restore-interrupted-target-"));
        WorldBackupService.verifyBackup(backup);

        Path empty=Files.createDirectory(world.getParent().resolve("no-space-target"));
        try{
            WorldBackupService.restore(backup,empty,noSpace(),new WorldBackupService.RestoreHook(){
                @Override public void beforePublish(Path partial,Path target){ }});
            fail("expected restore space rejection");
        }catch(IOException expected){assertTrue(expected.getMessage().contains("space"));}
        assertTrue(Files.isDirectory(empty));assertTrue(isEmpty(empty));
        assertFalse(hasEntryStarting(world.getParent(),".craftonica-restore-no-space-target-"));
    }

    private static WorldBackupService.SpaceProbe noSpace(){return new WorldBackupService.SpaceProbe(){
        @Override public long usableSpace(Path path){return 0L;}};}
    private static WorldBackupService.SpaceProbe plentyOfSpace(){return new WorldBackupService.SpaceProbe(){
        @Override public long usableSpace(Path path){return Long.MAX_VALUE;}};}
    private static boolean hasEntryEnding(Path directory,String suffix)throws IOException{
        DirectoryStream<Path> entries=Files.newDirectoryStream(directory);try{for(Path entry:entries)
            if(entry.getFileName().toString().endsWith(suffix))return true;return false;}finally{entries.close();}}
    private static boolean hasEntryStarting(Path directory,String prefix)throws IOException{
        DirectoryStream<Path> entries=Files.newDirectoryStream(directory);try{for(Path entry:entries)
            if(entry.getFileName().toString().startsWith(prefix))return true;return false;}finally{entries.close();}}
    private static boolean isEmpty(Path directory)throws IOException{
        DirectoryStream<Path> entries=Files.newDirectoryStream(directory);try{return !entries.iterator().hasNext();}
        finally{entries.close();}}
}
