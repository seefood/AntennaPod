package de.danoeh.antennapod.storage.database;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Result of a queue refill operation.
 *
 * @author AntennaPod Team
 */
public class RefillResult implements Serializable {

    private static final long serialVersionUID = 1L;

    public final int episodesAdded;
    public final int episodesRemoved;
    public final int rulesProcessed;
    public final int rulesSkipped;
    public final ArrayList<String> errors;

    public RefillResult(int episodesAdded, int episodesRemoved,
                       int rulesProcessed, int rulesSkipped,
                       List<String> errors) {
        this.episodesAdded = episodesAdded;
        this.episodesRemoved = episodesRemoved;
        this.rulesProcessed = rulesProcessed;
        this.rulesSkipped = rulesSkipped;
        this.errors = errors != null ? new ArrayList<>(errors) : new ArrayList<>();
    }

    public RefillResult(int episodesAdded, int episodesRemoved,
                       int rulesProcessed, int rulesSkipped) {
        this(episodesAdded, episodesRemoved, rulesProcessed, rulesSkipped, null);
    }

    @Override
    public String toString() {
        return "RefillResult{episodesAdded=" + episodesAdded
                + ", episodesRemoved=" + episodesRemoved
                + ", rulesProcessed=" + rulesProcessed
                + ", rulesSkipped=" + rulesSkipped
                + ", errors=" + errors.size() + "}";
    }
}
