package com.cienet.deliverydemo.order;

import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;

import java.util.List;

public class OrderState implements LinearState, Comparable<OrderState> {
    private final String data;
    private final Party buyer;
    private final Party seller;
    private final UniqueIdentifier linearId;
    private final float sellingPrice;

    public OrderState(String data, Party buyer, Party seller, UniqueIdentifier linearId, float sellingPrice) {
        this.data = data;
        this.buyer = buyer;
        this.seller = seller;
        this.linearId = linearId;
        this.sellingPrice = sellingPrice;
    }

    public String getData() {
        return data;
    }

    public Party getBuyer() {
        return buyer;
    }

    public Party getSeller() {
        return seller;
    }

    public float getSellingPrice() {
        return sellingPrice;
    }

    @Override
    public UniqueIdentifier getLinearId() {
        return linearId;
    }

    // Overrides participants, the only field defined by ContractState.
    @Override
    public List<AbstractParty> getParticipants() {
        return ImmutableList.of(buyer, seller);
    }

    // Can implement additional functions as well.
    @Override
    public int compareTo(OrderState other) {
        return linearId.compareTo(other.linearId);
    }
}