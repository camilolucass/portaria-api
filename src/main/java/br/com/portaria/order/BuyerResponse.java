package br.com.portaria.order;

import java.util.UUID;

public record BuyerResponse(UUID id, String name, String email, String document) {

    public static BuyerResponse from(Buyer buyer) {
        return new BuyerResponse(buyer.getPublicId(), buyer.getName(),
                buyer.getEmail(), buyer.getDocument());
    }
}
