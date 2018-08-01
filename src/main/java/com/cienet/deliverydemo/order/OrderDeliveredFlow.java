package com.cienet.deliverydemo.order;

import co.paralleluniverse.fibers.Suspendable;
import com.cienet.deliverydemo.token.TokenContract;
import com.cienet.deliverydemo.token.TokenState;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.*;
import net.corda.core.crypto.SecureHash;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.serialization.CordaSerializable;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.UntrustworthyData;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class OrderDeliveredFlow {

    @CordaSerializable
    public static class TokenRequest {
        public int amount;
        public Party buyer;

        public TokenRequest(int amount, Party buyer) {
            this.amount = amount;
            this.buyer = buyer;
        }

        @Override
        public int hashCode() {
            return Objects.hash(this);
        }

    }

    @InitiatingFlow
    @StartableByRPC
    public static class Request extends FlowLogic<SignedTransaction> {
        private final String orderID;

        private final ProgressTracker.Step GRABBING_ORDER = new ProgressTracker.Step("Grabbing the order by the id.");
        private final ProgressTracker.Step CHECKING_TOKEN_AMOUNT = new ProgressTracker.Step("Checking Buyer's token amount for this order.");
        private final ProgressTracker.Step GENERATING_TRANSACTION = new ProgressTracker.Step("Generating transaction.");
        private final ProgressTracker.Step VERIFYING_TRANSACTION = new ProgressTracker.Step("Verifying contract constraints.");
        private final ProgressTracker.Step SIGNING_TRANSACTION = new ProgressTracker.Step("Signing transaction with our private key.");
        private final ProgressTracker.Step GATHERING_SIGS = new ProgressTracker.Step("Gathering the counterparty's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final ProgressTracker.Step FINALISING_TRANSACTION = new ProgressTracker.Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private final ProgressTracker progressTracker = new ProgressTracker(
                GRABBING_ORDER,
                CHECKING_TOKEN_AMOUNT,
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        public Request(String orderID) {
            this.orderID = orderID;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
            Party me = getServiceHub().getMyInfo().getLegalIdentities().get(0);

            progressTracker.setCurrentStep(GRABBING_ORDER);
            //find a Token State for order
            QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
            Vault.Page<OrderState> results = getServiceHub().getVaultService().queryBy(OrderState.class, criteria);
            List<StateAndRef<OrderState>> orderStates = results.getStates();
            StateAndRef<OrderState> orderStateRef = orderStates.stream()
                    .filter(state -> state.getState().getData().getLinearId().getExternalId().equals(orderID)
                            && state.getState().getData().getSeller().equals(me))
                    .findAny()
                    .orElse(null);
            if (orderStateRef == null) {
                //TODO Do not consider TokenState unite for now.
                throw new FlowException("No Such Order, ID:" + this.orderID);
            }
            //TODO, Ignore the duplicate
            OrderState inputOrderState = orderStateRef.getState().getData();
            int balancePayment =
                    //TODO float to int, here is a bug, and I will fix this later.
                    (int)(inputOrderState.getSellingPrice()
                            - inputOrderState.getSellingPrice()*inputOrderState.getDownPayments());
            Party buyer = inputOrderState.getBuyer();


//            ////for get class
//            TransactionState<TokenState> tempTS = new TransactionState<TokenState>(
//                    new TokenState(me, me, 0), TokenContract.ID, me);
//            StateAndRef<TokenState> temp_1 = new StateAndRef<>(
//                    tempTS, new StateRef(orderStateRef.getRef().getTxhash(), 0));
//
//            ////

            progressTracker.setCurrentStep(CHECKING_TOKEN_AMOUNT);
            //ask the buyer for the token
            FlowSession buyerPartySession = initiateFlow(buyer);
            UntrustworthyData<StateAndRef> tokenStateRefUn = buyerPartySession.sendAndReceive(
                    StateAndRef.class,
                    new TokenRequest(balancePayment, buyer)
            );
            // TODO we still got "missing parameter name at index 0 {}" error.
            // If comment out the "unwrap" line, this error will disappear.
            StateAndRef<TokenState> tokenStateRef = tokenStateRefUn.unwrap(data -> data);
            //StateAndRef<TokenState> tokenStateRef = new StateAndRef<>(null, null);
            if (tokenStateRef == null) {
                throw new FlowException("Cannot get any token state from buyer.");
            }

            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);
            //input state
            transactionBuilder.addInputState(orderStateRef);
            transactionBuilder.addInputState(tokenStateRef);

            //output state
            TokenState tokenState = tokenStateRef.getState().getData();
            TokenState tokenState4Seller = new TokenState(tokenState.getIssuer(), me, balancePayment);
            transactionBuilder.addOutputState(tokenState4Seller, TokenContract.ID, notary);
            if (tokenState.getAmount() > balancePayment) {
                //Add for a change
                TokenState tokenState4Buyer =
                        new TokenState(tokenState.getIssuer(), me, tokenState.getAmount() - balancePayment);
                transactionBuilder.addOutputState(tokenState4Buyer, TokenContract.ID, notary);
            }

            //command
            CommandData tokenCommandData = new TokenContract.Pay();
            CommandData orderCommandData = new OrderContract.Commands.OrderPlacingCommand();

            transactionBuilder.addCommand(
                    tokenCommandData,
                    tokenState.getIssuer().getOwningKey(),
                    me.getOwningKey(),
                    buyer.getOwningKey());
            transactionBuilder.addCommand(orderCommandData, me.getOwningKey(), buyer.getOwningKey());

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            transactionBuilder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(transactionBuilder);

            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparty, and receive it back with their signature.

            List<FlowSession> otherPartySession =
                    //Create FlowSession for Seller and Token Issuer
                    ImmutableList.of(initiateFlow(buyer), initiateFlow(tokenState.getIssuer()));
            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(
                            partSignedTx,
                            otherPartySession,
                            CollectSignaturesFlow.Companion.tracker()));

            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(fullySignedTx));
        }
    }

    @InitiatedBy(Request.class)
    public static class Confirm extends FlowLogic<SignedTransaction> {

        private final FlowSession otherPartyFlow;

        public Confirm(FlowSession otherPartyFlow) {
            this.otherPartyFlow = otherPartyFlow;
        }

        private static final ProgressTracker.Step RECEIVING_AND_SENDING_DATA = new ProgressTracker.Step("Sending data between parties.");
        private static final ProgressTracker.Step SIGNING = new ProgressTracker.Step("Responding to CollectSignaturesFlow.");
        private static final ProgressTracker.Step FINALISATION = new ProgressTracker.Step("Finalising a transaction.");

        private final ProgressTracker progressTracker = new ProgressTracker(
                RECEIVING_AND_SENDING_DATA,
                SIGNING,
                FINALISATION
        );

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                    super(otherPartyFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                    requireThat(require -> {
                        //final List<StateRef> inputStateList = stx.getTx().getInputs();
                        //TODO only has hash key and ID, how to check amount in TokenState?
                        final List<TransactionState<ContractState>> outputTxStateList = stx.getTx().getOutputs();
                        List<TransactionState<ContractState>> outputOrderStateList =
                                outputTxStateList.stream()
                                        .filter(state -> state.getData() instanceof OrderState)
                                        .collect(Collectors.toList());
                        require.using("Must have a output OrderState",outputOrderStateList.size() == 1);
                        //TODO add more check
                        return null;
                    });
                }
            }

            progressTracker.setCurrentStep(RECEIVING_AND_SENDING_DATA);
            TokenRequest tokenReq = otherPartyFlow.receive(TokenRequest.class).unwrap(data -> data);
            //find a Token State for order
            QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
            Vault.Page<TokenState> results = getServiceHub().getVaultService().queryBy(TokenState.class, criteria);
            List<StateAndRef<TokenState>> tokenStates = results.getStates();
            StateAndRef<TokenState> tokenStateRef = tokenStates.stream()
                    .filter(state -> state.getState().getData().getAmount() >= tokenReq.amount
                            && state.getState().getData().getOwner().equals(tokenReq.buyer))
                    .findAny()
                    .orElse(null);
            if (tokenStateRef == null) {
                //TODO Do not consider TokenState unite for now.
                throw new FlowException("The buyer has no enough amount.");
            }
            otherPartyFlow.send(tokenStateRef);

            progressTracker.setCurrentStep(SIGNING);
            subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));


            progressTracker.setCurrentStep(FINALISATION);
            return null;
        }
    }

}