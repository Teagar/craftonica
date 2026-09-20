package br.com.craftonica.runtime.server;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrFault;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import br.com.craftonica.tile.RoboBoardState;

import java.util.concurrent.RejectedExecutionException;

/** Reusable asynchronous AVR scheduler with no Forge world or entity references. */
public final class RoboBoardRuntimeHost {
    public interface OutputListener { void committed(RoboBoardState state, RuntimeProtocol.Result result); }

    private RuntimeSupervisor.Submission inFlight;
    private Metadata metadata;

    public void tick(RuntimeProtocol.Identity identity, long logicalTick, RoboBoardState state,
                     br.com.craftonica.runtime.core.AvrInputs inputs, OutputListener listener) {
        if (identity == null || logicalTick < 0 || state == null || inputs == null) throw new IllegalArgumentException();
        if (!state.isRunning()) { cancel(); return; }
        if (inFlight != null) {
            RuntimeSupervisor.Completion completion = inFlight.poll();
            if (completion == null) return;
            Metadata pending = metadata; inFlight = null; metadata = null;
            if (!matches(identity, state, pending)) return;
            if (completion.failure != null || completion.result == null) {
                fault(state, pending, "RUNTIME_WORKER_FAILED"); return;
            }
            commit(identity, state, pending, completion.result, listener);
            return;
        }
        RuntimeSupervisor supervisor = RuntimeServer.get();
        if (supervisor == null) { RuntimeServer.start(); supervisor = RuntimeServer.get(); }
        long generation = state.getGeneration(), revision = state.getRevision();
        byte[] checkpoint;
        long cycles;
        try {
            checkpoint = state.prepareRuntimeCheckpoint(generation, revision);
            cycles = AvrCheckpointCodec.decode(checkpoint).getCycles();
        } catch (Exception invalid) { return; }
        if (supervisor == null) return;
        long batchTarget = cycles > Long.MAX_VALUE - 800000L ? Long.MAX_VALUE : cycles + 800000L;
        long target = Math.min(cycles + 50000L, batchTarget);
        RuntimeProtocol.Identity requestIdentity = identity.kind == RuntimeProtocol.Identity.MOBILE_ROBOT
                ? RuntimeProtocol.Identity.mobile(identity.dimension, identity.hostId, generation) : identity;
        Metadata next = new Metadata(requestIdentity, generation, revision, checkpoint, batchTarget);
        try {
            inFlight = supervisor.submit(new RuntimeProtocol.Request(requestIdentity, target,
                    state.getFirmware(), checkpoint, inputs), batchTarget);
            metadata = next;
        } catch (RejectedExecutionException busy) { fault(state, next, "RUNTIME_BUSY"); }
    }

    public void cancel() {
        RuntimeSupervisor.Submission submission = inFlight; inFlight = null; metadata = null;
        if (submission != null) submission.cancel();
    }

    public boolean needsInput() { return inFlight == null; }

    private static void commit(RuntimeProtocol.Identity expected, RoboBoardState state, Metadata metadata,
                               RuntimeProtocol.Result result, OutputListener listener) {
        if (!sameHost(expected, result.identity) || !matches(expected, state, metadata)) return;
        try {
            AvrMachineState machine = AvrCheckpointCodec.decode(result.checkpoint);
            CRLFirmware firmware = CRLFirmware.decode(state.getFirmware());
            long first = AvrCheckpointCodec.decode(metadata.checkpoint).getCycles();
            if (machine.getWordPc() >= firmware.getFlash().length / 2 || result.completedAtCycle < first
                    || result.completedAtCycle > metadata.absoluteTarget + 7L
                    || !RuntimeResultValidator.valid(result, machine, metadata.checkpoint)) {
                fault(state, metadata, "RUNTIME_PROTOCOL"); return;
            }
            RoboBoardState.Status status = result.fault == null
                    ? RoboBoardState.Status.RUNNING : RoboBoardState.Status.FAULT;
            state.commitRuntimeCheckpoint(metadata.generation, metadata.revision, result.checkpoint, status,
                    result.fault == null ? "" : "AVR_FAULT_" + result.fault.code,
                    status == RoboBoardState.Status.RUNNING, result.d13High, result.gpio, result.pwm, result.tx);
            if (listener != null) listener.committed(state, result);
        } catch (Exception invalid) { fault(state, metadata, "RUNTIME_PROTOCOL"); }
    }

    private static void fault(RoboBoardState state, Metadata metadata, String code) {
        if (metadata == null || state.getGeneration() != metadata.generation
                || state.getRevision() != metadata.revision) return;
        try {
            byte[] checkpoint = metadata.checkpoint;
            if (checkpoint.length == 0) checkpoint = AvrCheckpointCodec.encode(new AvrMachineState());
            state.commitRuntimeCheckpoint(metadata.generation, metadata.revision, checkpoint,
                    RoboBoardState.Status.FAULT, code, false, false);
        } catch (RuntimeException stale) { }
    }

    private static boolean matches(RuntimeProtocol.Identity identity, RoboBoardState state, Metadata metadata) {
        return metadata != null && sameHost(identity, metadata.identity)
                && state.getGeneration() == metadata.generation && state.getRevision() == metadata.revision;
    }
    private static boolean sameHost(RuntimeProtocol.Identity a, RuntimeProtocol.Identity b) {
        return a != null && b != null && a.kind == b.kind && a.dimension == b.dimension && a.hostId.equals(b.hostId);
    }

    private static final class Metadata {
        final RuntimeProtocol.Identity identity; final long generation, revision, absoluteTarget; final byte[] checkpoint;
        Metadata(RuntimeProtocol.Identity identity, long generation, long revision, byte[] checkpoint, long target) {
            this.identity = identity; this.generation = generation; this.revision = revision;
            this.checkpoint = checkpoint.clone(); this.absoluteTarget = target;
        }
    }
}
