package br.com.craftonica.sketch.server;

import java.util.Arrays;
import java.util.UUID;

/** Pure stale-completion gate used immediately before installing compiler output. */
public final class CompilationTarget {
    private CompilationTarget() {}

    public static boolean matches(UUID expectedId, long expectedGeneration, long expectedRevision,
                                  byte[] expectedSourceHash, UUID actualId, long actualGeneration,
                                  long actualRevision, byte[] actualSourceHash) {
        return expectedId != null && expectedId.equals(actualId)
                && expectedGeneration == actualGeneration && expectedRevision == actualRevision
                && expectedSourceHash != null && Arrays.equals(expectedSourceHash, actualSourceHash);
    }
}
