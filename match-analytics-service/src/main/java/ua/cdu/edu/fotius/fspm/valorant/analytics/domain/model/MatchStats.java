package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model;

public record MatchStats(
    boolean isWin,
    int roundMargin,
    int kills,
    int deaths,
    int assists,
    int combatScore,
    boolean isAfk
) {
    public MatchStats {
        if (roundMargin < 1 || roundMargin > 13) {
            throw new IllegalArgumentException("roundMargin must be between 1 and 13");
        }
        if (kills < 0 || deaths < 0 || assists < 0 || combatScore < 0) {
            throw new IllegalArgumentException("Match statistics cannot be negative");
        }
        if (deaths > 24) {
            throw new IllegalArgumentException("Deaths cannot exceed 24 in a regulation match");
        }
    }
}
