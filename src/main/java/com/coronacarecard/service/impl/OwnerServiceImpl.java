package com.coronacarecard.service.impl;

import com.coronacarecard.dao.BusinessRepository;
import com.coronacarecard.dao.UserRepository;
import com.coronacarecard.dao.entity.User;
import com.coronacarecard.exceptions.*;
import com.coronacarecard.mapper.BusinessEntityMapper;
import com.coronacarecard.model.Business;
import com.coronacarecard.model.BusinessRegistrationRequest;
import com.coronacarecard.model.BusinessState;
import com.coronacarecard.model.PaymentSystem;
import com.coronacarecard.notifications.NotificationSender;
import com.coronacarecard.notifications.NotificationType;
import com.coronacarecard.service.GooglePlaceService;
import com.coronacarecard.service.OwnerService;
import com.coronacarecard.service.PaymentService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Service
public class OwnerServiceImpl implements OwnerService {
    private static Log log = LogFactory.getLog(OwnerServiceImpl.class);
    @Autowired
    private BusinessRepository businessRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GooglePlaceService googlePlaceService;

    @Autowired
    private BusinessEntityMapper businessEntityMapper;

    @Autowired
    private NotificationSender<Business> notificationSender;

    @Autowired
    private PaymentService paymentService;

    @Override
    @Transactional
    public Business claimBusiness(BusinessRegistrationRequest request) throws
            BusinessAlreadyClaimedException, InternalException, BusinessNotFoundException {
        String externalId = request.getBusinessId();
        String email = request.getEmail();
        String phone = request.getPhone();
        Optional<com.coronacarecard.dao.entity.Business> businessDAO =
                businessRepository.findByExternalId(externalId);
        if (businessDAO.isPresent()
                && !BusinessState.Draft.equals(businessDAO.get().getState())) {
            if (Objects.isNull(businessDAO.get().getOwner())) {
                log.error(String.format("A business %s is %s without an owner", externalId, businessDAO.get().getState()));
                throw new InternalException("There is something wrong with this business please contact us.");
            }
            if (isSameOwner(businessDAO.get().getOwner(), email, phone)) {
                return businessEntityMapper.toModel(businessDAO.get());
            } else {
                logAndThrowBusinessClaimedException(externalId, email);
            }

        }
        if (businessDAO.isPresent()
                && !Objects.isNull(businessDAO.get().getOwner())
                && !isSameOwner(businessDAO.get().getOwner(), email, phone)) {
            logAndThrowBusinessClaimedException(externalId, email);
        }


        if (!businessDAO.isPresent()) {
            Business business = googlePlaceService.getBusiness(externalId);
            User owner = userRepository.findByEmail(email);
            if (owner == null) {
                owner = userRepository.save(User
                        .builder()
                        .phoneNumber(phone)
                        .email(email)
                        .build());
            }
            businessDAO = Optional.of(businessRepository.save(
                    businessEntityMapper.toDAOBuilder(business)
                            .state(BusinessState.Claimed)
                            .description(request.getDescription())
                            .owner(owner).build()));


        }
        Business claimedBusiness = businessEntityMapper.toModel(businessDAO.get());
        notificationSender.sendNotification(NotificationType.BUSINESS_CLAIMED, claimedBusiness);
        return claimedBusiness;
    }

    private Business logAndThrowBusinessClaimedException(String externalId, String email) throws BusinessAlreadyClaimedException {
        log.info(String.format("User %s is trying to claim %s, but its already claimed",
                email, externalId));
        throw new BusinessAlreadyClaimedException();
    }

    private boolean isSameOwner(User owner, String email, String phone) {
        return owner.getEmail().equals(email)
                && owner.getPhoneNumber().equals(phone);

    }

    @Override
    public void registerOwner(String encryptedDetails, String externalRefId) {

    }

    @Override
    @Transactional
    public String approveClaim(PaymentSystem paymentSystem, Long id) throws CustomerException {
        Optional<com.coronacarecard.dao.entity.Business> business = businessRepository.findById(id);
        if(!business.isPresent()) {
            log.error(String.format("No business with id %s exists. You cannot approve it.", id));
            throw new BusinessNotFoundException();
        }
        com.coronacarecard.dao.entity.Business businessDAO = business.get();
        if (BusinessState.Draft.equals(businessDAO.getState())) {
            log.error(String.format("Business id = %s is in Draft state, you cannot approve it", id));
            throw new BusinessClaimException("Draft business cannot be approved");
        }

        if (BusinessState.Active.equals(businessDAO)) {
            log.error(String.format("Business id = %s is in Active State so already approved and registered", id));
            throw new BusinessClaimException("Active business is already approved");
        }

        if(BusinessState.Claimed.equals(businessDAO.getState())) {
            log.info("Business is claimed now, will wait for owner to enter payment details");
            businessDAO = businessRepository.save(businessDAO.toBuilder().state(BusinessState.Pending).build());
        }

        return paymentService.generateOnBoardingUrl(paymentSystem, businessDAO);
    }
}
