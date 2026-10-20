package dev.rajce.ketchupZombies.models;

import java.util.ArrayList;
import java.util.List;

public record ArmorOffer(String set, List<String> pieces, int price) {

    public ArmorOffer {
        pieces = List.copyOf(pieces);
    }

    public ArmorOffer copy() {
        return new ArmorOffer(set, new ArrayList<>(pieces), price);
    }

    public String label() {
        return set + " " + String.join(", ", pieces);
    }
}
