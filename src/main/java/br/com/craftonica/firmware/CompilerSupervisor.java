package br.com.craftonica.firmware;

/**
 * Boundary to an externally isolated compiler implementation. Implementations must enforce the
 * RFC sandbox; invoking native tools directly from this JVM is not isolation.
 */
public interface CompilerSupervisor {
    CompilationResult compile(CompilationRequest request) throws Exception;
}
