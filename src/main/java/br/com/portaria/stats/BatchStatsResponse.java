package br.com.portaria.stats;

public record BatchStatsResponse(String name, int sold, long checkedIn) {

    static BatchStatsResponse from(BatchStatsProjection projection) {
        return new BatchStatsResponse(projection.name(), projection.sold(), projection.checkedIn());
    }
}
