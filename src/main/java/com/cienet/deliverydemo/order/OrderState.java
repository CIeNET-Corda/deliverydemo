package com.cienet.deliverydemo.order;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class OrderState implements LinearState, Comparable<OrderState> {
    private final String data;
    private final Party buyer;
    private final Party seller;
    private final UniqueIdentifier linearId;
    private final float sellingPrice;
    private final float downPayments;
    private final String state;

    public OrderState(
            String data,
            Party buyer, Party seller,
            UniqueIdentifier linearId,
            float sellingPrice, float downPayments,
            String state) {
        this.data = data;
        this.buyer = buyer;
        this.seller = seller;
        this.linearId = linearId;
        this.sellingPrice = sellingPrice;
        this.downPayments = downPayments;
        this.state = state;
    }

    String getData() {
        return data;
    }

    Party getBuyer() {
        return buyer;
    }

    Party getSeller() {
        return seller;
    }

    float getSellingPrice() {
        return sellingPrice;
    }

    float getDownPayments() {
        return downPayments;
    }

    String getState() { return state; }

    @Override
    @NotNull
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    // Overrides participants, the only field defined by ContractState.
    @Override
    @NotNull
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(buyer, seller);
    }

    // Can implement additional functions as well.
    @Override
    public int compareTo(@NotNull OrderState other) {
        return linearId.compareTo(other.linearId);
    }
}