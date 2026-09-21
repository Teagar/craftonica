package br.com.craftonica.robot.modular.electrical;

import br.com.craftonica.robot.modular.GridVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MobileElectricalEvaluation {
    private final Map<GridVector, DriveBinding> drives;
    private final List<MobileElectricalDiagnostic> diagnostics;

    MobileElectricalEvaluation(Map<GridVector, DriveBinding> drives,
                               List<MobileElectricalDiagnostic> diagnostics) {
        this.drives = Collections.unmodifiableMap(new LinkedHashMap<GridVector, DriveBinding>(drives));
        List<MobileElectricalDiagnostic> sorted = new ArrayList<MobileElectricalDiagnostic>(diagnostics);
        Collections.sort(sorted); this.diagnostics = Collections.unmodifiableList(sorted);
    }

    public Map<GridVector, DriveBinding> getDrives() { return drives; }
    public List<MobileElectricalDiagnostic> getDiagnostics() { return diagnostics; }

    public static final class DriveBinding {
        public final boolean enabled;
        public final String pwmRole;
        public final String directionRole;
        public final GridVector motorPosition;

        public DriveBinding(boolean enabled, String pwmRole, String directionRole, GridVector motorPosition) {
            if (enabled && (pwmRole == null || directionRole == null || motorPosition == null))
                throw new IllegalArgumentException("enabled drive binding");
            this.enabled = enabled; this.pwmRole = pwmRole; this.directionRole = directionRole;
            this.motorPosition = motorPosition;
        }
    }
}
