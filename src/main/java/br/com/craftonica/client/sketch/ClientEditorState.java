package br.com.craftonica.client.sketch;

import br.com.craftonica.sketch.network.EditorStateMessage;

import java.util.UUID;

/** Immutable client-owned copy of a packet received on the Netty thread. */
final class ClientEditorState {
    final int dimension, x, y, z, status;
    final UUID boardId;
    final long generation, revision, serialStart, serialEnd;
    final String fault, compilationState, diagnostics;
    private final byte[] source, serial;
    final boolean serialTruncated;

    private ClientEditorState(EditorStateMessage message) {
        dimension = message.getDimension();
        x = message.getX();
        y = message.getY();
        z = message.getZ();
        status = message.getStatus();
        boardId = message.getBoardId();
        generation = message.getGeneration();
        revision = message.getRevision();
        fault = message.getFault();
        compilationState = message.getCompilationState();
        diagnostics = message.getDiagnostics();
        source = message.getSource();
        serial = message.getSerial();
        serialStart = message.getSerialStart();
        serialEnd = message.getSerialEnd();
        serialTruncated = message.isSerialTruncated();
    }

    static ClientEditorState copyOf(EditorStateMessage message) {
        if (message == null || !message.isValid()) throw new IllegalArgumentException("Valid editor state required");
        return new ClientEditorState(message);
    }

    boolean sameBoard(ClientEditorState other) {
        return other != null && dimension == other.dimension && x == other.x && y == other.y && z == other.z
                && boardId.equals(other.boardId);
    }

    byte[] sourceBytes() { return source.clone(); }
    byte[] serialBytes() { return serial.clone(); }
}
