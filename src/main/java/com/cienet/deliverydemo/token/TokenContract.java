package com.cienet.deliverydemo.token;

import net.corda.core.contracts.*;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.List;

public class TokenContract implements Contract {
    public static String ID = "com.cienet.deliverydemo.token.TokenContract";

    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {


        List<Command<Issue>> issueCommand = tx.commandsOfType(Issue.class);
        List<Command<Pay>> payCommand = tx.commandsOfType(Pay.class);
        if (issueCommand.size() + payCommand.size() != 1) {
            throw new IllegalArgumentException("must only 1 command, Issue or Pay.");
        }

        issueCommand.forEach( commandWithData -> {
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

        payCommand.forEach(commandWithData -> {
            //input
            if (tx.inputsOfType(TokenState.class).size() != 1) {
                //TODO Do not consider TokenState unite for now.
                throw new IllegalArgumentException("must only 1 input.");
            }

            ContractState inputState = tx.inputsOfType(TokenState.class).get(0);
            TokenState inputTokenState = (TokenState) inputState;

            //output
            List<TokenState> outputTokenStateList = tx.outputsOfType(TokenState.class);
            if (outputTokenStateList.size() == 1) {
                TokenState outputTokenState = outputTokenStateList.get(0);
                if (outputTokenState.getAmount() <= 0) {
                    throw new IllegalArgumentException("amount of outputTokenState must positive.");
                }

                if (outputTokenState.getAmount() != inputTokenState.getAmount()) {
                    throw new IllegalArgumentException("amount of In/out put TokenState must be equal.");
                }

                if (outputTokenState.getOwner() == inputTokenState.getOwner()) {
                    throw new IllegalArgumentException("Owner of In/out put TokenState must be not equal.");
                }
            } else if (outputTokenStateList.size() == 2) {
                TokenState outputState_0 = outputTokenStateList.get(0);
                TokenState outputState_1 = outputTokenStateList.get(1);

                if (inputTokenState.getAmount() != (outputState_0.getAmount() + outputState_1.getAmount())) {
                    throw new IllegalArgumentException("amount of In/out put TokenState must be equal.");
                }

                if (outputState_0.getOwner() == outputState_1.getOwner()) {
                    throw new IllegalArgumentException("Owners of outputs of TokenState must be not equal.");
                }

//                if (outputState_0.getOwner() != inputTokenState.getOwner()
//                 && outputState_1.getOwner() != inputTokenState.getOwner()) {
//                    throw new IllegalArgumentException("One of Owner of outputs of TokenState must be equal with the input.");
//                }

            } else {
                throw new IllegalArgumentException("must 1 or 2 Token output.");
            }

            outputTokenStateList.forEach(outputState -> {
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