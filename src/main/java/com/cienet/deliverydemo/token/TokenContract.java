package com.cienet.deliverydemo.token;

import com.cienet.deliverydemo.order.OrderContract;
import net.corda.core.contracts.*;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/* Our contract, governing how our state will evolve over time.
 * See src/main/kotlin/examples/ExampleContract.java for an example. */
public class TokenContract implements Contract {
    public static String ID = "com.cienet.deliverydemo.token.TokenContract";

    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {


        List<Command<Issue>> issueCommand = tx.commandsOfType(Issue.class);
        List<Command<Pay>> payCommand = tx.commandsOfType(Pay.class);
        if (issueCommand.size() + payCommand.size() != 1) {
            throw new IllegalArgumentException("must only 1 command, Issue or Pay.");
        }

        issueCommand.stream().forEach( commandWithData -> {
            if (tx.getInputs().size() != 0) {
                throw new IllegalArgumentException("must no input.");
            }

            if (tx.getOutputs().size() != 1) {
                throw new IllegalArgumentException("must only 1 output.");
            }

            ContractState outputState = tx.getOutput(0);
            if (!(outputState instanceof TokenState)) {
                throw new IllegalArgumentException("OutputState must TokenState.");
            }

            TokenState outputTokenState = (TokenState) outputState;
            if (outputTokenState.getAmount() <= 0) {
                throw new IllegalArgumentException("amount of outputTokenState must positive.");
            }

            List<PublicKey> requiredSigners = commandWithData.getSigners();
            PublicKey issuerKey = outputTokenState.getIssuer().getOwningKey();
            if (!requiredSigners.contains(issuerKey)) {
                throw new IllegalArgumentException("Issue did not sign this.");
            }
        });

        payCommand.stream().forEach(commandWithData -> {
            if (tx.getInputs().size() != 1) {
                throw new IllegalArgumentException("must only 1 input.");
            }

            ContractState inputState = tx.getInput(0);
            if (!(inputState instanceof TokenState)) {
                throw new IllegalArgumentException("InputState must TokenState.");
            }
            TokenState inputTokenState = (TokenState) inputState;

            if (tx.getOutputs().size() == 1) {
                ContractState outputState = tx.getOutput(0);
                if (!(outputState instanceof TokenState)) {
                    throw new IllegalArgumentException("OutputState must TokenState.");
                }

                TokenState outputTokenState = (TokenState) outputState;
                if (outputTokenState.getAmount() <= 0) {
                    throw new IllegalArgumentException("amount of outputTokenState must positive.");
                }

                if (outputTokenState.getAmount() != inputTokenState.getAmount()) {
                    throw new IllegalArgumentException("amount of In/out put TokenState must be equal.");
                }

                if (outputTokenState.getOwner() == inputTokenState.getOwner()) {
                    throw new IllegalArgumentException("Owner of In/out put TokenState must be not equal.");
                }
            } else if (tx.getOutputs().size() == 2) {
                TokenState outputState_0 = (TokenState) tx.getOutput(0);
                TokenState outputState_1 = (TokenState) tx.getOutput(1);

                if (inputTokenState.getAmount() != (outputState_0.getAmount() + outputState_1.getAmount())) {
                    throw new IllegalArgumentException("amount of In/out put TokenState must be equal.");
                }

                if (outputState_0.getOwner() == outputState_1.getOwner()) {
                } else {
                    throw new IllegalArgumentException("Owners of outputs of TokenState must be not equal.");
                }

                if (outputState_0.getOwner() == inputTokenState.getOwner()
                        || outputState_1.getOwner() == inputTokenState.getOwner()) {
                } else {
                    throw new IllegalArgumentException("One of Owner of outputs of TokenState must be equal with the input.");
                }

            } else {
                throw new IllegalArgumentException("must 1 or 2 output.");
            }

            List<TokenState> outputStateList = tx.outputsOfType(TokenState.class);
            outputStateList.stream().forEach(outputState -> {
                List<PublicKey> requiredSigners = commandWithData.getSigners();
                PublicKey issuerKey = outputState.getIssuer().getOwningKey();
                if (!requiredSigners.contains(issuerKey)) {
                    throw new IllegalArgumentException("Issue did not sign this.");
                }
            });

        });
    }

    public static class Issue implements CommandData {}
    public static class Pay implements CommandData {}
}