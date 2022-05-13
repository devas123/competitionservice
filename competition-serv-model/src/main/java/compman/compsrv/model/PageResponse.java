package compman.compsrv.model;

public record PageResponse<T>(String competitionId, long total, int page, T[] data) { }