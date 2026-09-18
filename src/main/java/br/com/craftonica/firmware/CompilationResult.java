package br.com.craftonica.firmware;

import java.util.Collection;
import java.util.Collections;

public final class CompilationResult {
    public enum Status {
        SUCCESS,
        COMPILE_ERROR,
        COMPILE_LIMIT,
        VERIFY_REJECTED,
        COMPILER_UNAVAILABLE,
        REJECTED,
        CANCELLED
    }

    private final Status status;
    private final CRLFirmware firmware;
    private final CompilationDiagnostics diagnostics;

    private CompilationResult(Status status, CRLFirmware firmware, CompilationDiagnostics diagnostics) {
        if (status == null || diagnostics == null || status == Status.SUCCESS != (firmware != null))
            throw new IllegalArgumentException("Invalid compilation result");
        this.status = status;
        this.firmware = firmware;
        this.diagnostics = diagnostics;
    }

    public static CompilationResult success(CRLFirmware firmware, Collection<String> diagnostics) {
        return new CompilationResult(Status.SUCCESS, firmware, CompilationDiagnostics.sanitize(diagnostics));
    }

    public static CompilationResult failure(Status status, Collection<String> diagnostics) {
        if (status == Status.SUCCESS) throw new IllegalArgumentException("A failure cannot have SUCCESS status");
        return new CompilationResult(status, null, CompilationDiagnostics.sanitize(diagnostics));
    }

    public static CompilationResult failure(Status status, String diagnostic) {
        return failure(status, Collections.singletonList(diagnostic));
    }

    public Status getStatus() { return status; }
    public boolean isSuccess() { return status == Status.SUCCESS; }
    public CRLFirmware getFirmware() { return firmware; }
    public CompilationDiagnostics getDiagnostics() { return diagnostics; }
}
