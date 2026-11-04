package pl.jollycart.offer;

public enum OfferStatus {
    ACTIVE,
    INACTIVE,
    SOLD,
    // Ktoś właśnie płaci / zapłacił, a transakcja jeszcze trwa
    RESERVED
}