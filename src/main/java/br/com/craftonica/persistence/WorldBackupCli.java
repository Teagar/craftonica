package br.com.craftonica.persistence;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Offline verifier/restorer. Minecraft and the dedicated server must be stopped before use. */
public final class WorldBackupCli {
    private WorldBackupCli() { }

    public static void main(String[] arguments) {
        try {
            if (arguments.length == 2 && "verify".equals(arguments[0])) {
                Path backup = Paths.get(arguments[1]);
                WorldBackupService.verifyBackup(backup);
                System.out.println("Backup verificado: " + backup.toAbsolutePath().normalize());
                return;
            }
            if (arguments.length == 3 && "restore".equals(arguments[0])) {
                Path restored = WorldBackupService.restore(Paths.get(arguments[1]), Paths.get(arguments[2]));
                System.out.println("Mundo restaurado: " + restored);
                return;
            }
            System.err.println("Uso: WorldBackupCli verify <backup> | restore <backup> <save-vazio-ou-ausente>");
            System.exit(2);
        } catch (Exception failure) {
            System.err.println("ROLLBACK_RECUSADO: " + failure.getMessage());
            System.exit(1);
        }
    }
}
