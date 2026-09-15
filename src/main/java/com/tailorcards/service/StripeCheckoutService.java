package com.tailorcards.service;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.tailorcards.api.dto.CheckoutSessionRequest;
import com.tailorcards.api.dto.CheckoutSessionResponse;
import com.tailorcards.api.entity.CartItem;
import com.tailorcards.api.entity.Order;
import com.tailorcards.api.entity.Product;
import com.tailorcards.api.repository.CartItemRepository;
import com.tailorcards.api.repository.OrderRepository;
import com.tailorcards.api.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class StripeCheckoutService {

    private static final Logger log = LoggerFactory.getLogger(StripeCheckoutService.class);

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${frontend.url:https://tailorcards.com}")
    private String frontendUrl;

    @Value("${stripe.success-url:}")
    private String successUrl;

    @Value("${stripe.cancel-url:}")
    private String cancelUrl;

    private final ProductRepository productRepository;
    private final CartItemRepository cartItemRepository;
    private final OrderRepository orderRepository;

    public StripeCheckoutService(
            ProductRepository productRepository,
            CartItemRepository cartItemRepository,
            OrderRepository orderRepository
    ) {
        this.productRepository = productRepository;
        this.cartItemRepository = cartItemRepository;
        this.orderRepository = orderRepository;
    }

    public CheckoutSessionResponse createCheckoutSession(CheckoutSessionRequest request) {
        String cartId = request != null ? request.cartId() : null;
        List<CartItemSnapshot> items = new ArrayList<>();

        if (cartId != null && !cartId.isBlank()) {
            List<CartItem> cartItems = cartItemRepository.findByCartSessionId(cartId);
            for (CartItem ci : cartItems) {
                items.add(new CartItemSnapshot(ci.getProduct().getId(), ci.getQuantity()));
            }
        }

        if (items.isEmpty() && request != null && request.productIds() != null && !request.productIds().isEmpty()) {
            for (Long pId : request.productIds()) {
                items.add(new CartItemSnapshot(pId, 1));
            }
        }

        if (items.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty or no valid products found for checkout.");
        }

        // Validate price and availability against the database (never trust client amounts)
        List<SessionCreateParams.LineItem> lineItems = new ArrayList<>();
        List<Long> verifiedProductIds = new ArrayList<>();

        for (CartItemSnapshot item : items) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + item.productId()));

            if ("SOLD".equalsIgnoreCase(product.getStatus())) {
                throw new IllegalStateException(String.format("Product '%s' is already sold.", product.getName()));
            }

            int requestedQty = item.quantity() > 0 ? item.quantity() : 1;
            if (product.getStock() == null || product.getStock() < requestedQty) {
                throw new IllegalStateException(String.format(
                        "Product '%s' is out of stock (available: %d, requested: %d).",
                        product.getName(), product.getStock() != null ? product.getStock() : 0, requestedQty
                ));
            }

            if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalStateException(String.format("Invalid price for product '%s'.", product.getName()));
            }

            verifiedProductIds.add(product.getId());

            // Unit amount in CAD cents (price * 100)
            long unitAmountCents = product.getPrice().multiply(BigDecimal.valueOf(100)).longValue();

            SessionCreateParams.LineItem.PriceData.ProductData.Builder productData =
                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(product.getName());

            if (product.getDescription() != null && !product.getDescription().isBlank()) {
                String desc = product.getDescription();
                if (desc.length() > 500) {
                    desc = desc.substring(0, 497) + "...";
                }
                productData.setDescription(desc);
            }

            if (product.getImageUrl() != null && (product.getImageUrl().startsWith("http://") || product.getImageUrl().startsWith("https://"))) {
                productData.addImage(product.getImageUrl());
            }

            SessionCreateParams.LineItem lineItem = SessionCreateParams.LineItem.builder()
                    .setQuantity((long) requestedQty)
                    .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency("cad")
                                    .setUnitAmount(unitAmountCents)
                                    .setProductData(productData.build())
                                    .build()
                    )
                    .build();

            lineItems.add(lineItem);
        }

        String productIdsJoined = verifiedProductIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        String finalSuccessUrl = resolveSuccessUrl();
        String finalCancelUrl = resolveCancelUrl();

        // Build SessionCreateParams
        SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(finalSuccessUrl)
                .setCancelUrl(finalCancelUrl)
                .putMetadata("cartId", cartId != null ? cartId : "")
                .putMetadata("productIds", productIdsJoined)
                .addAllLineItem(lineItems)
                .setShippingAddressCollection(
                        SessionCreateParams.ShippingAddressCollection.builder()
                                .addAllowedCountry(SessionCreateParams.ShippingAddressCollection.AllowedCountry.CA)
                                .addAllowedCountry(SessionCreateParams.ShippingAddressCollection.AllowedCountry.US)
                                .build()
                )
                .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED);

        SessionCreateParams params = paramsBuilder.build();

        Stripe.apiKey = secretKey;
        try {
            Session session = Session.create(params);
            if (session == null || session.getUrl() == null || session.getUrl().isBlank()) {
                throw new IllegalStateException("Stripe session was created without a checkout URL.");
            }
            return new CheckoutSessionResponse(session.getUrl(), session.getId());
        } catch (StripeException e) {
            log.error("Stripe API error creating checkout session: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to initiate Stripe Checkout: " + e.getMessage(), e);
        }
    }

    public void handleWebhook(String payload, String sigHeader) {
        Event event = null;
        if (webhookSecret != null && !webhookSecret.isBlank() && !"whsec_placeholder".equals(webhookSecret)) {
            try {
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            } catch (SignatureVerificationException e) {
                log.error("Stripe webhook signature verification failed: {}", e.getMessage());
                throw new IllegalArgumentException("Invalid Stripe signature: " + e.getMessage());
            } catch (Exception e) {
                log.error("Stripe webhook error: {}", e.getMessage());
                throw new IllegalArgumentException("Webhook error: " + e.getMessage());
            }
        }

        if (event != null) {
            if ("checkout.session.completed".equals(event.getType())) {
                EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
                Session session = null;
                if (dataObjectDeserializer.getObject().isPresent()) {
                    StripeObject stripeObject = dataObjectDeserializer.getObject().get();
                    if (stripeObject instanceof Session s) {
                        session = s;
                    }
                }
                if (session == null) {
                    try {
                        session = (Session) dataObjectDeserializer.deserializeUnsafe();
                    } catch (Exception ignored) {}
                }

                if (session != null) {
                    processCompletedCheckout(session);
                } else {
                    log.error("Could not deserialize Session from checkout.session.completed event");
                }
            } else {
                log.info("Received Stripe event: {}", event.getType());
            }
        }
    }

    public void processCompletedCheckout(Session session) {
        String sessionId = session.getId();
        if (orderRepository.existsByStripeSessionId(sessionId)) {
            log.info("Stripe session {} has already been recorded. Skipping duplicate.", sessionId);
            return;
        }

        Map<String, String> metadata = session.getMetadata();
        String cartId = metadata != null ? metadata.get("cartId") : null;
        String productIdsStr = metadata != null ? metadata.get("productIds") : null;

        List<Long> productIds = new ArrayList<>();
        if (productIdsStr != null && !productIdsStr.isBlank()) {
            for (String idStr : productIdsStr.split(",")) {
                try {
                    productIds.add(Long.parseLong(idStr.trim()));
                } catch (NumberFormatException ignored) {}
            }
        }

        if (productIds.isEmpty() && cartId != null && !cartId.isBlank()) {
            List<CartItem> cartItems = cartItemRepository.findByCartSessionId(cartId);
            for (CartItem ci : cartItems) {
                productIds.add(ci.getProduct().getId());
            }
        }

        // Mark purchased products as SOLD in database and set stock to 0
        for (Long pId : productIds) {
            productRepository.findById(pId).ifPresent(product -> {
                product.setStock(0);
                product.setStatus("SOLD");
                productRepository.save(product);
                log.info("Marked product #{} ({}) as SOLD", product.getId(), product.getName());
            });
        }

        // Clear buyer's cart
        if (cartId != null && !cartId.isBlank()) {
            cartItemRepository.deleteByCartSessionId(cartId);
            log.info("Cleared cart session: {}", cartId);
        }

        // Record the sale
        String customerEmail = null;
        String customerName = null;
        if (session.getCustomerDetails() != null) {
            customerEmail = session.getCustomerDetails().getEmail();
            customerName = session.getCustomerDetails().getName();
        }

        BigDecimal amountTotal = BigDecimal.ZERO;
        if (session.getAmountTotal() != null) {
            amountTotal = BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100));
        }

        String currency = session.getCurrency() != null ? session.getCurrency().toUpperCase() : "CAD";

        Order order = Order.builder()
                .stripeSessionId(sessionId)
                .customerEmail(customerEmail)
                .customerName(customerName)
                .amountTotal(amountTotal)
                .currency(currency)
                .status("PAID")
                .cartSessionId(cartId)
                .productIds(productIdsStr)
                .createdAt(LocalDateTime.now())
                .build();

        orderRepository.save(order);
        log.info("Recorded sale in Order #{} for Stripe session {}", order.getId(), sessionId);
    }

    public String resolveSuccessUrl() {
        if (successUrl != null && !successUrl.isBlank() && !successUrl.contains("localhost:5173")) {
            return successUrl;
        }
        String base = (frontendUrl != null && !frontendUrl.isBlank()) ? frontendUrl : "https://tailorcards.com";
        return base.replaceAll("/+$", "") + "/checkout/success?session_id={CHECKOUT_SESSION_ID}";
    }

    public String resolveCancelUrl() {
        if (cancelUrl != null && !cancelUrl.isBlank() && !cancelUrl.contains("localhost:5173")) {
            return cancelUrl;
        }
        String base = (frontendUrl != null && !frontendUrl.isBlank()) ? frontendUrl : "https://tailorcards.com";
        return base.replaceAll("/+$", "") + "/cart";
    }

    private record CartItemSnapshot(Long productId, int quantity) {}
}

