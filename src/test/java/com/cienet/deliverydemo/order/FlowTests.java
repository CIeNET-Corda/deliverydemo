package com.cienet.deliverydemo.order;

import com.cienet.deliverydemo.token.TokenContract;
import com.cienet.deliverydemo.token.TokenState;
import com.google.common.collect.ImmutableList;
import net.corda.core.concurrent.CordaFuture;
import net.corda.core.contracts.*;
import net.corda.core.flows.FlowException;
import net.corda.core.node.services.Vault;
import net.corda.core.node.services.vault.QueryCriteria;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.testing.node.MockNetwork;
import net.corda.testing.node.StartedMockNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;

public class FlowTests {
    private MockNetwork network;
    private StartedMockNode nodeA;
    private StartedMockNode nodeB;
    private StartedMockNode nodeC;

    @Before
    public void setup() {
        network = new MockNetwork(ImmutableList.of("com.cienet.deliverydemo"));
        nodeA = network.createPartyNode(null);
        nodeB = network.createPartyNode(null);
        nodeC = network.createPartyNode(null);
        //ImmutableList.of(nodeA, nodeB).forEach(node -> node.registerInitiatedFlow(TokenIssueFlow.class));
        network.runNetwork();
    }

    @After
    public void tearDown() {
        network.stopNodes();
    }

//    @Test
//    public void orderPlaceFlow() throws Exception {
//        //PartyA issues TokenState to PartyB
//
//        TokenState tokenState = new TokenState(
//                nodeA.getInfo().getLegalIdentities().get(0),
//                nodeB.getInfo().getLegalIdentities().get(0),
//                99);
//        TransactionBuilder transactionBuilder = new TransactionBuilder(network.getDefaultNotaryIdentity());
//        transactionBuilder.addOutputState(tokenState, TokenContract.ID, network.getDefaultNotaryIdentity());
//        CommandData commandData = new TokenContract.Issue();
//        transactionBuilder.addCommand(commandData, nodeA.getInfo().getLegalIdentities().get(0).getOwningKey());
//
//        nodeA.transaction(() -> {
//            try {
//                transactionBuilder.verify(nodeA.getServices());
//            } catch (TransactionVerificationException e) {
//                assertEquals(1, 1);
//            } catch (TransactionResolutionException e) {
//                assertEquals(2, 2);
//            } catch (AttachmentResolutionException e) {
//                assertEquals(3, 3);
//            }
//            return null;
//        });
//
//        SignedTransaction partSignedTransaction = nodeA.getServices().signInitialTransaction(transactionBuilder);
//        SignedTransaction signedTransaction = nodeB.getServices().addSignature(partSignedTransaction);
//
//        nodeA.getServices().recordTransactions(signedTransaction);
//        nodeB.getServices().recordTransactions(signedTransaction);
//
//        OrderPlaceFlow.Request flow =
//                new OrderPlaceFlow.Request(nodeC.getInfo().getLegalIdentities().get(0),
//                        "test", 10f, 0.1f);
//        CordaFuture<SignedTransaction> futureB = nodeB.startFlow(flow);
//        network.runNetwork();
//        futureB.get();
//    }

    @Test
    public void success() throws Exception {
        //PartyA issues TokenState to PartyB

        TokenState tokenState = new TokenState(
                nodeA.getInfo().getLegalIdentities().get(0),
                nodeB.getInfo().getLegalIdentities().get(0),
                99);
        TransactionBuilder transactionBuilder = new TransactionBuilder(network.getDefaultNotaryIdentity());
        transactionBuilder.addOutputState(tokenState, TokenContract.ID, network.getDefaultNotaryIdentity());
        CommandData commandData = new TokenContract.Issue();
        transactionBuilder.addCommand(commandData, nodeA.getInfo().getLegalIdentities().get(0).getOwningKey());

        nodeA.transaction(() -> {
            try {
                transactionBuilder.verify(nodeA.getServices());
            } catch (TransactionVerificationException e) {
                assertEquals(1, 1);
            } catch (TransactionResolutionException e) {
                assertEquals(2, 2);
            } catch (AttachmentResolutionException e) {
                assertEquals(3, 3);
            }
            return null;
        });

        SignedTransaction partSignedTransaction = nodeA.getServices().signInitialTransaction(transactionBuilder);
        SignedTransaction signedTransaction = nodeB.getServices().addSignature(partSignedTransaction);

        nodeA.getServices().recordTransactions(signedTransaction);
        nodeB.getServices().recordTransactions(signedTransaction);

        //PartyB places a OrderState with PartyC, includes some change.

        //find a Token State for order
        QueryCriteria.VaultQueryCriteria criteria = new QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED);
        Vault.Page<TokenState> results = nodeB.getServices().getVaultService().queryBy(TokenState.class, criteria);
        List<StateAndRef<TokenState>> tokenStates = results.getStates();
        if (tokenStates.size() == 0) {
            throw new FlowException("tokenStates.size() is 0");
        }

        StateAndRef<TokenState> inputTokenStateRef = tokenStates.stream()
                .filter(state -> state.getState().getData().getAmount() == 99
                        && state.getState().getData().getOwner().equals(nodeB.getInfo().getLegalIdentities().get(0)))
                .findAny()
                .orElse(null);
        if (inputTokenStateRef == null) {
            //TODO Do not consider TokenState unite for now.
            throw new FlowException("The buyer has no enough amount XXX.");
        }

        TokenState outputTokenState = new TokenState(
                nodeA.getInfo().getLegalIdentities().get(0),
                nodeB.getInfo().getLegalIdentities().get(0),
                98);
        TokenState outputTokenStateChange = new TokenState(
                nodeA.getInfo().getLegalIdentities().get(0),
                nodeC.getInfo().getLegalIdentities().get(0),
                1);
        OrderState outputOrderState = new OrderState(
                "This is an UT order.",
                nodeB.getInfo().getLegalIdentities().get(0),
                nodeC.getInfo().getLegalIdentities().get(0),
                new UniqueIdentifier("ut_order", UUID.randomUUID()),
                10, 0.1f,
                "shipping");
        TransactionBuilder transactionBuilder4Order = new TransactionBuilder(network.getDefaultNotaryIdentity());
        transactionBuilder4Order.addInputState(inputTokenStateRef);
        transactionBuilder4Order.addOutputState(
                outputTokenState, TokenContract.ID, network.getDefaultNotaryIdentity());
        transactionBuilder4Order.addOutputState(
                outputTokenStateChange, TokenContract.ID, network.getDefaultNotaryIdentity());
        transactionBuilder4Order.addOutputState(
                outputOrderState, OrderContract.CONTRACT_ID, network.getDefaultNotaryIdentity());

        //command
        CommandData tokenCommandData = new TokenContract.Pay();
        CommandData orderCommandData = new OrderContract.Commands.OrderPlacingCommand();

        transactionBuilder4Order.addCommand(
                tokenCommandData,
                //tokenState.getIssuer().getOwningKey(),
                nodeB.getInfo().getLegalIdentities().get(0).getOwningKey(),
                nodeC.getInfo().getLegalIdentities().get(0).getOwningKey());
        transactionBuilder4Order.addCommand(
                orderCommandData,
                nodeB.getInfo().getLegalIdentities().get(0).getOwningKey(),
                nodeC.getInfo().getLegalIdentities().get(0).getOwningKey());


        nodeB.transaction(() -> {
            try {
                transactionBuilder4Order.verify(nodeB.getServices());
            } catch (TransactionVerificationException
                    | TransactionResolutionException
                    | AttachmentResolutionException e) {
                e.printStackTrace();
            }
            return null;
        });

        partSignedTransaction = nodeB.getServices().signInitialTransaction(transactionBuilder4Order);
        signedTransaction = nodeA.getServices().addSignature(partSignedTransaction);
        signedTransaction = nodeC.getServices().addSignature(signedTransaction);

        nodeA.getServices().recordTransactions(signedTransaction);
        nodeB.getServices().recordTransactions(signedTransaction);
        nodeC.getServices().recordTransactions(signedTransaction);

        System.out.println("my testing flow below...");

//        TokenIssueFlow tokenIssueFlow = new TokenIssueFlow(
//                nodeB.getInfo().getLegalIdentities().get(0),
//                99);
////        CordaFuture<SignedTransaction> futureA = nodeA.startFlow(tokenIssueFlow);
////        network.runNetwork(1);
////        futureA.get();
//        nodeA.startFlow(tokenIssueFlow);
//
//        OrderPlaceFlow.Request orderPlaceFlow = new OrderPlaceFlow.Request(
//                nodeC.getInfo().getLegalIdentities().get(0),
//                "ut_order",
//                12,
//                1
//                );
////        CordaFuture<SignedTransaction> futureB = nodeB.startFlow(orderPlaceFlow);
////        network.runNetwork(1);
////        futureB.get();
//        nodeB.startFlow(orderPlaceFlow);

        OrderDeliveredFlow.Request flow = new OrderDeliveredFlow.Request("ut_order");
        CordaFuture<SignedTransaction> futureC = nodeC.startFlow(flow);
        network.runNetwork();
        futureC.get();

    }
}