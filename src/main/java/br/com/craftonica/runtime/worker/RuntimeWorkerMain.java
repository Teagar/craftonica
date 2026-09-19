package br.com.craftonica.runtime.worker;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrExecutionResult;
import br.com.craftonica.runtime.core.AvrFault;
import br.com.craftonica.runtime.core.AvrInterpreter;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;

/** Persistent worker. Stdout is reserved exclusively for RuntimeProtocol frames. */
public final class RuntimeWorkerMain {
    private RuntimeWorkerMain() {}

    public static void main(String[] args) {
        try {
            while (true) {
                RuntimeProtocol.Request request;
                try { request = RuntimeProtocol.readRequest(System.in); }
                catch (RuntimeProtocol.EndOfStreamException cleanShutdown) { return; }
                CRLFirmware firmware = CRLFirmware.decode(request.firmware);
                AvrMachineState state = AvrCheckpointCodec.decode(request.checkpoint);
                AvrExecutionResult execution = null;
                AvrFault fault = null;
                try {
                    execution = new AvrInterpreter(firmware.getFlash()).executeToAbsoluteTarget(state, request.absoluteTarget, request.inputs);
                } catch (AvrFault value) {
                    fault = value;
                }
                RuntimeProtocol.writeResult(System.out, RuntimeProtocol.fromExecution(request.identity, state, execution, fault));
            }
        } catch (Throwable failure) {
            String message = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
            if (message.length() > RuntimeProtocol.MAX_FAULT_BYTES) message = message.substring(0, RuntimeProtocol.MAX_FAULT_BYTES);
            System.err.println(message);
            System.exit(70);
        }
    }
}
