
public class FlightBookingFacade {

    private final SeatInventorySystem seatSystem;
    private final BaggagePricingEngine baggageEngine;
    private final CurrencyConverter currencyConverter;
    private final PaymentProcessor paymentProcessor;
    private final TicketIssuer ticketIssuer;

    public FlightBookingFacade() {
        this.seatSystem = new SeatInventorySystem();
        this.baggageEngine = new BaggagePricingEngine();
        this.currencyConverter = new CurrencyConverter();
        this.paymentProcessor = new PaymentProcessor();
        this.ticketIssuer = new TicketIssuer();
    }

    // Original 8-parameter method
    // Defaults loyalty tier to STANDARD
    public String bookFlight(String passengerName,
                             String flightCode,
                             String seatNumber,
                             double baseFareUSD,
                             int bagCount,
                             double bagWeightKg,
                             String currency,
                             String cardNumber) {

        return bookFlight(
                passengerName,
                flightCode,
                seatNumber,
                baseFareUSD,
                bagCount,
                bagWeightKg,
                currency,
                cardNumber,
                "STANDARD"
        );
    }

    // New 9-parameter method with loyalty tier (Phase 2)
    public String bookFlight(String passengerName,
                             String flightCode,
                             String seatNumber,
                             double baseFareUSD,
                             int bagCount,
                             double bagWeightKg,
                             String currency,
                             String cardNumber,
                             String loyaltyTier) {

        String lockToken = null;

        try {
            // Step 1: Connect and lock seat
            seatSystem.connectToReservationServer();

            lockToken = seatSystem.lockSeat(
                    flightCode,
                    seatNumber,
                    passengerName
            );

            // Step 2: Calculate baggage fee
            double baggageFee = 0.0;

            if (!"SILVER".equalsIgnoreCase(loyaltyTier)
                    && !"GOLD".equalsIgnoreCase(loyaltyTier)) {

                baggageFee = baggageEngine.calculateBaggageFeeUSD(
                        bagCount,
                        bagWeightKg
                );
            }

            double totalUSD = baseFareUSD + baggageFee;

            // Step 3: Convert currency
            double convertedTotal = currencyConverter.convertFromUSD(
                    totalUSD,
                    currency
            );

            // Step 4: Initialize payment gateway
            paymentProcessor.initializeGateway();

            // Apply promo code for SILVER passengers
            if ("SILVER".equalsIgnoreCase(loyaltyTier)) {
                paymentProcessor.applyPromoCode("SILVER-FREE-BAG");
            }

            // Charge card
            String receiptId = paymentProcessor.chargeCard(
                    cardNumber,
                    convertedTotal,
                    currency
            );

            // Step 5: Generate ticket
            return ticketIssuer.generateTicket(
                    passengerName,
                    flightCode,
                    lockToken,
                    receiptId
            );

        } catch (RuntimeException ex) {

            // Release seat if booking fails
            if (lockToken != null) {
                seatSystem.releaseSeatLock(lockToken);
            }

            System.out.println("[Facade] Booking failed: " + ex.getMessage());

            return "BOOKING_FAILED: " + ex.getMessage();

        } finally {

            // Always disconnect
            seatSystem.disconnect();
        }
    }
}
