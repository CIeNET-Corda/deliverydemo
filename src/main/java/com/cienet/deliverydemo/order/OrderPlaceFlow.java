package com.cienet.deliverydemo.order;

import co.paralleluniverse.fibers.Suspendable;
import com.cienet.deliverydemo.token.TokenContract;
import com.cienet.deliverydemo.token.TokenState;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.*;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/* Our flow, automating the process of updating the ledger.
 * See src/main/java/examples/IAmAFlowPair.java for an example. */
public class OrderPlaceFlow {
    @InitiatingFlow
    @StartableByRPC
    public static class Request extends FlowLogic<SignedTransaction> {
        private final Party seller;
        private final float sellingPrice;
        private final float downPayments;

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
                CHECKING_TOKEN_AMOUNT,
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        public Request(Party seller, float sellingPrice, float downPayments) {
            this.seller = seller;
            this.sellingPrice = sellingPrice;
            this.downPayments = downPayments;
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

            int deposit = (int) (sellingPrice * downPayments);

            progressTracker.setCurrentStep(CHECKING_TOKEN_AMOUNT);
            //find a Token State for order
            QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
            Vault.Page<TokenState> results = getServiceHub().getVaultService().queryBy(TokenState.class, criteria);
            List<StateAndRef<TokenState>> tokenStates = results.getStates();
            StateAndRef<TokenState> tokenStateRef = tokenStates.stream()
                    .filter(state -> state.getState().getData().getAmount() >= deposit
                            && state.getState().getData().getOwner().equals(me))
                    .findAny()
                    .orElse(null);
            if (tokenStateRef == null) {
                //TODO Do not consider TokenState unite for now.
                throw new FlowException("The buyer has no enough amount.");
            }

            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            TransactionBuilder transactionBuilder = new TransactionBuilder(notary);
            //input state
            transactionBuilder.addInputState(tokenStateRef);

            //output state
            OrderState orderState = new OrderState(
                    "this is an order",
                    me, seller,
                    new UniqueIdentifier("ID_test", UUID.randomUUID()),
                    sellingPrice, downPayments);

            transactionBuilder.addOutputState(orderState, TokenContract.ID, notary);

            TokenState tokenState = tokenStateRef.getState().getData();
            TokenState tokenState4Seller = new TokenState(tokenState.getIssuer(), seller, deposit);
            transactionBuilder.addOutputState(tokenState4Seller, TokenContract.ID, notary);
            if (tokenState.getAmount() > deposit) {
                //Add for a change
                TokenState tokenState4Buyer =
                        new TokenState(tokenState.getIssuer(), me, tokenState.getAmount() - deposit);
                transactionBuilder.addOutputState(tokenState4Buyer, TokenContract.ID, notary);
            }

            //command
            CommandData tokenCommandData = new TokenContract.Pay();
            CommandData orderCommandData = new OrderContract.Commands.OrderPlacingCommand();

            transactionBuilder.addCommand(
                    tokenCommandData,
                    tokenState.getIssuer().getOwningKey(),
                    me.getOwningKey(),
                    seller.getOwningKey());
            transactionBuilder.addCommand(orderCommandData, me.getOwningKey(), seller.getOwningKey());

            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            transactionBuilder.verify(getServiceHub());

            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(transactionBuilder);

            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparty, and receive it back with their signature.

            List<FlowSession> otherPartySession =
                    //Create FlowSession for Seller and Token Issuer
                    ImmutableList.of(initiateFlow(seller), initiateFlow(tokenState.getIssuer()));
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

            return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
        }
    }

}