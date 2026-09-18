package br.com.craftonica.runtime.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable bounded output of one absolute-target execution slice. */
public final class AvrExecutionResult {
    private final long completedAtCycle;
    private final List<GpioChange> gpioChanges;
    private final List<PwmDescriptor> pwmDescriptors;
    private final byte[] transmittedBytes;

    AvrExecutionResult(long completedAtCycle, List<GpioChange> gpioChanges,
                       List<PwmDescriptor> pwmDescriptors, byte[] transmittedBytes) {
        this.completedAtCycle = completedAtCycle;
        this.gpioChanges = Collections.unmodifiableList(new ArrayList<GpioChange>(gpioChanges));
        this.pwmDescriptors = Collections.unmodifiableList(new ArrayList<PwmDescriptor>(pwmDescriptors));
        this.transmittedBytes = transmittedBytes.clone();
    }

    public long getCompletedAtCycle() { return completedAtCycle; }
    public List<GpioChange> getGpioChanges() { return gpioChanges; }
    public List<PwmDescriptor> getPwmDescriptors() { return pwmDescriptors; }
    public byte[] getTransmittedBytes() { return transmittedBytes.clone(); }
}
