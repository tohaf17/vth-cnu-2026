package ua.cdu.edu.fotius.fspm.valorant.analytics.domain.model;

public record MatchHistoryItem(
    String matchId,
    String mapName,
    String result,
    int deltaRR,
    String tierStatus
) {}
