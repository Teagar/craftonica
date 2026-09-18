package br.com.craftonica.sketch.server;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CompilationTargetTest {
    @Test
    public void acceptsOnlyExactIdentityRevisionAndSourceHash() {
        UUID id = UUID.randomUUID();
        byte[] hash = new byte[] {1, 2, 3};
        assertTrue(CompilationTarget.matches(id, 4, 5, hash, id, 4, 5, hash.clone()));
        assertFalse(CompilationTarget.matches(id, 4, 5, hash, id, 4, 6, hash));
        assertFalse(CompilationTarget.matches(id, 4, 5, hash, id, 5, 5, hash));
        assertFalse(CompilationTarget.matches(id, 4, 5, hash, UUID.randomUUID(), 4, 5, hash));
        assertFalse(CompilationTarget.matches(id, 4, 5, hash, id, 4, 5, new byte[] {1, 2, 4}));
    }
}
