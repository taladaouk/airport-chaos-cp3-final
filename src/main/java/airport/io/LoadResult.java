package airport.io;

import airport.model.Flight;

import java.util.ArrayList;
import java.util.List;

public final class LoadResult {
    public int loaded;
    public final List<String> errors = new ArrayList<>();
    /** The accepted flights, in file order. */
    public final List<Flight> flights = new ArrayList<>();

    @Override
    public String toString() {
        return "Loaded " + loaded + " flights, " + errors.size() + " errors";
    }
}
