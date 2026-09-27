package dev.zynema.payment.mapper;

import dev.zynema.payment.domain.Payment;
import dev.zynema.payment.domain.Plan;
import dev.zynema.payment.domain.Subscription;
import dev.zynema.payment.dto.PaymentDto;
import dev.zynema.payment.dto.PlanDto;
import dev.zynema.payment.dto.SubscriptionDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    PlanDto toDto(Plan plan);

    List<PlanDto> toPlanDtoList(List<Plan> plans);

    SubscriptionDto toDto(Subscription subscription);

    List<SubscriptionDto> toSubscriptionDtoList(List<Subscription> subscriptions);

    @Mapping(target = "subscriptionId", source = "subscription.id")
    @Mapping(target = "planCode", source = "subscription.plan.code")
    PaymentDto toDto(Payment payment);

    List<PaymentDto> toPaymentDtoList(List<Payment> payments);
}
