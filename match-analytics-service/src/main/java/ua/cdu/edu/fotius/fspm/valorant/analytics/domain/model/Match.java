package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model;

import java.util.Objects;
import java.util.UUID;

public record Match(
    String id,
    String rosterId,
    String playerId,
    String mapName,
    Result result,
    MatchStats stats,
    int deltaRR,
    String tierStatus
) {
    public Match {
        requireText(id, "id");
        requireText(rosterId, "rosterId");
        requireText(playerId, "playerId");
        requireText(mapName, "mapName");
        Objects.requireNonNull(result, "result is required");
        Objects.requireNonNull(stats, "stats is required");
        requireText(tierStatus, "tierStatus");
    }

    public static Match create(
        String rosterId,
        String playerId,
        String mapName,
        MatchStats stats,
        int deltaRR,
        String tierStatus
    ) {
        Result result = stats.isWin() ? Result.WIN : Result.LOSS;
        return new Match(UUID.randomUUID().toString(), rosterId, playerId, mapName,
            result, stats, deltaRR, tierStatus);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    public enum Result {
        WIN,
        LOSS
    }
}
