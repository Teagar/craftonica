package br.com.craftonica.robot.modular.transaction;

import br.com.craftonica.robot.modular.GridVector;

public final class AssemblyTransactionResult {
    public enum Status { COMMITTED, PRECONDITION_FAILED, MUTATION_FAILED, RECOVERY_REQUIRED }
    public final Status status;
    public final GridVector position;
    public final String detail;

    private AssemblyTransactionResult(Status status, GridVector position, String detail) {
        this.status = status; this.position = position; this.detail = detail;
    }

    static AssemblyTransactionResult of(Status status, GridVector position, String detail) {
        return new AssemblyTransactionResult(status, position, detail);
    }

    public boolean committed() { return status == Status.COMMITTED; }
}
