package br.com.craftonica.firmware;

public interface CompilationHandle {
    CompilationResult await() throws InterruptedException;
    boolean cancel();
    boolean isDone();
}
