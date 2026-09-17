package br.com.craftonica.electrical.nodal;

/** Tick-safe state machine: observation never opens the branch immediately. */
public final class ProtectionState {
    private boolean tripped;
    private boolean pendingTrip;

    public boolean isTripped() { return tripped; }
    public boolean isPendingTrip() { return pendingTrip; }
    public void observe(double current, double power) {
        if (!tripped && ProtectionModel.mustTrip(current, power)) pendingTrip = true;
    }
    public boolean applyPendingTrip() {
        if (!pendingTrip) return false;
        pendingTrip = false;
        if (tripped) return false;
        tripped = true;
        return true;
    }
    public boolean reset() {
        if (!tripped && !pendingTrip) return false;
        tripped = false;
        pendingTrip = false;
        return true;
    }
    public void setTripped(boolean value) { tripped = value; pendingTrip = false; }
    public void setPendingTrip(boolean value) { pendingTrip = value && !tripped; }
}
