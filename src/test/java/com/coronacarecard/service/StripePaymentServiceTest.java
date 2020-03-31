package com.coronacarecard.service;

import com.coronacarecard.config.StripeConfiguration;
import com.coronacarecard.dao.BusinessRepository;
import com.coronacarecard.exceptions.BusinessAlreadyClaimedException;
import com.coronacarecard.exceptions.BusinessNotFoundException;
import com.coronacarecard.dao.UserRepository;
import com.coronacarecard.dao.entity.BusinessAccountDetail;
import com.coronacarecard.dao.entity.User;
import com.coronacarecard.exceptions.InternalException;
import com.coronacarecard.exceptions.PayementServiceException;
import com.coronacarecard.model.Business;
import com.coronacarecard.model.BusinessRegistrationRequest;
import com.coronacarecard.service.payment.StripeCalls;
import com.google.gson.JsonObject;
import com.stripe.exception.StripeException;
import com.stripe.model.oauth.TokenResponse;
import com.coronacarecard.model.CheckoutResponse;
import com.coronacarecard.model.Currency;
import com.coronacarecard.model.orders.Item;
import com.coronacarecard.model.orders.OrderDetail;
import com.coronacarecard.model.orders.OrderLine;
import com.coronacarecard.model.orders.OrderStatus;
import com.coronacarecard.util.TestHelper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
public class StripePaymentServiceTest {

    private static final String AUTH_CODE = "auth";
    private static final String STRIPE_USER_ID = "StripeId";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String ACCESS_TOEKN = "access_token";

    @Autowired
    private PaymentService paymentService;

    private String business_id = "ChIJKV8LiAcPkFQRgaK8WZdjnuY";

    @Autowired
    private StripeConfiguration stripeConfiguration;

    @Autowired
    @Qualifier("StripePaymentService")
    private PaymentService stripePayementService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BusinessRepository businessRepository;

    @Before
    public void init() throws InternalException {
        when(cryptoService.encrypt(any())).thenReturn(business_id.getBytes());
        when(cryptoService.decrypt(any())).thenReturn(business_id);
    }

    @MockBean
    private StripeCalls stripeCalls;

    @Autowired
    private OwnerService ownerService;

    @Autowired
    private CryptoService cryptoService;

    @Test
    public void generate_onboarding_url() {
        String connectId = stripeConfiguration.getClientId();
        String expected = stripeConfiguration.getConnectUrl();

        Business business = Business.builder().id(UUID.randomUUID()).externalRefId(business_id).build();
        String onboardingUrl = paymentService.generateOnBoardingUrl(business);
        System.out.println(onboardingUrl);
        assertEquals(String.format(expected, connectId, (business.getId().toString())), onboardingUrl);

    }

    @Test
    public void importBusinessTest() throws InternalException, PayementServiceException,
            BusinessNotFoundException, BusinessAlreadyClaimedException, StripeException {
        Business calimedBusiness = ownerService.claimBusiness(BusinessRegistrationRequest.builder()
                .businessId(business_id)
                .description("test")
                .email("test@gmail.com")
                .phone("911")
                .build());
        TokenResponse response = mock(TokenResponse.class);
        when(response.getStripeUserId()).thenReturn(STRIPE_USER_ID);
        JsonObject json = new JsonObject();
        json.addProperty("refresh_token", REFRESH_TOKEN);
        json.addProperty("access_token", ACCESS_TOEKN);
        when(response.getRawJsonObject()).thenReturn(json);
        when(stripeCalls.token(AUTH_CODE)).thenReturn(response);
        stripePayementService.importBusiness(AUTH_CODE, calimedBusiness.getId().toString());
        com.coronacarecard.dao.entity.Business businessWithAccount =
                businessRepository.findByExternalId(business_id).get();
        assertEquals(STRIPE_USER_ID, businessWithAccount.getOwner().getAccount().getExternalRefId());
        ;
        assertEquals(ACCESS_TOEKN,
                cryptoService.decrypt(businessWithAccount.getOwner().getAccount().getAccessToken()));
        assertEquals(REFRESH_TOKEN,
                cryptoService.decrypt(businessWithAccount.getOwner().getAccount().getRefreshToken()));

    }


    @Test
    public void create_stripe_session() throws Exception{
        User user = userRepository.save(User.builder()
                .email("testuser@xyz.com")
                .phoneNumber("12345")
                .account(BusinessAccountDetail.builder()
                        .externalRefId("acct_1GSRdxIsoQ5ULXuu")
                        .build())
                .build());
        com.coronacarecard.dao.entity.Business business = TestHelper.createEntry(businessRepository,"23456789" ,
                "1234", "Food for Friends");
        businessRepository.save(business.toBuilder().owner(user).build());
        OrderDetail order= OrderDetail.builder()
                .customerEmail("cust@email.com")
                .customerMobile("773")
                .status(OrderStatus.PENDING)
                .processingFee(1.2)
                .orderLine(createLine(business.getId()))
                .currency(Currency.USD)
                .id(100L)
                .contribution(20.0)
                .build();

        CheckoutResponse checkoutResponse= paymentService.generateCheckoutSession(order);
        assertNotNull(checkoutResponse.getSessionId());

    }

    private List<Item> createItems(){
        ArrayList<Item> items=new ArrayList<>();
        for(int i=0;i<5;i++){
            items.add(Item.builder()
                    .unitPrice(10.0)
                    .quantity(1)
                    .build());
        }
        items.add(Item.builder()
                .unitPrice(100.0)
                .quantity(2)
                .build());
        return items;
    }
    private List<OrderLine> createLine(UUID id){
        ArrayList<OrderLine> line=new ArrayList<>();
        for(int i=0;i<2;i++){
            line.add(OrderLine.builder()
                    .businessId(id)
                    .tip(10.0)
                    .items(createItems())
                    .build());
        }
        return line;
    }

}
