package br.com.craftonica.robot.modular.assembly;

import br.com.craftonica.robot.modular.GridVector;

/** Read-only boundary. componentAt is called only after isLoaded returns true. */
public interface AssemblyWorldView {
    boolean isLoaded(GridVector worldPosition);
    PlacedComponent componentAt(GridVector worldPosition);
}
