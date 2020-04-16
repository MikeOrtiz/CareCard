package com.coronacarecard.service.payment;

import com.coronacarecard.model.Currency;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Transfer;
import com.stripe.model.checkout.Session;
import com.stripe.model.oauth.TokenResponse;
import com.stripe.net.OAuth;
import com.stripe.net.RequestOptions;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class StripeCallsImpl implements StripeCalls {

    @Override
    public TokenResponse token(String code) throws StripeException {
        Map<String, Object> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        return OAuth.token(params, null);
    }

    @Override
    public Session generateSession(SessionCreateParams params) throws StripeException {
        return Session.create(params);
    }

    @Override
    public Session retrieveSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    @Override
    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        return PaymentIntent.retrieve(paymentIntentId);
    }

    @Override
    public void transferFund(String stripeBusinessId, Long dollarAmount, UUID orderId, Currency currency) throws StripeException {
        TransferCreateParams transferParams =
                TransferCreateParams.builder()
                        .setAmount(dollarAmount * 100)
                        .setCurrency(currency.name())
                        .setDestination(stripeBusinessId)
                        .setTransferGroup(orderId.toString())
                        .build();

        RequestOptions options = RequestOptions.getDefault().toBuilder()
                .setIdempotencyKey(orderId + "-" + stripeBusinessId)
                .build();
        Transfer.create(transferParams, options);

    }
}
