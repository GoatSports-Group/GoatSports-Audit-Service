package iuh.fit.auditservice.application.dto;

public record PageFilter(
    String filter,
    int page,
    int size
) {}
