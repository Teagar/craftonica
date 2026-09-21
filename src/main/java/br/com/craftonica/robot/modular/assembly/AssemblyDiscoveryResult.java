package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.GridVector;

public final class AssemblyDiscoveryResult {
    public enum Status {
        VALID, ANCHOR_UNLOADED, ANCHOR_NOT_COMPONENT, UNLOADED_BOUNDARY,
        UNKNOWN_COMPONENT, UNSUPPORTED_SCHEMA, LIMIT_EXCEEDED
    }

    public final Status status;
    public final AssemblyGraph graph;
    public final GridVector diagnosticPosition;
    public final String detail;

    private AssemblyDiscoveryResult(Status status, AssemblyGraph graph,
                                    GridVector diagnosticPosition, String detail) {
        this.status = status;
        this.graph = graph;
        this.diagnosticPosition = diagnosticPosition;
        this.detail = detail;
    }

    static AssemblyDiscoveryResult valid(AssemblyGraph graph) {
        return new AssemblyDiscoveryResult(Status.VALID, graph, null, null);
    }

    static AssemblyDiscoveryResult failure(Status status, GridVector position, String detail) {
        return new AssemblyDiscoveryResult(status, null, position, detail);
    }

    public boolean isValid() { return status == Status.VALID; }
}
