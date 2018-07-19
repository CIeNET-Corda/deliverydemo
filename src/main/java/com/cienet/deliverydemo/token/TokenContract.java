package java_bootcamp;

import net.corda.core.contracts.Command;
import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.contracts.ContractState;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireThat;

/* Our contract, governing how our state will evolve over time.
 * See src/main/kotlin/examples/ExampleContract.java for an example. */
public class TokenContract implements Contract {
    public static String ID = "java_bootcamp.TokenContract";

    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
        if(tx.getInputs().size() != 0) {
            throw new IllegalArgumentException("must no input.");
        }

        if(tx.getOutputs().size() != 1) {
            throw new IllegalArgumentException("must only 1 output.");
        }

        if(tx.getCommands().size() != 1) {
            throw new IllegalArgumentException("must only 1 command.");
        }

        ContractState outputState = tx.getOutput(0);
        if(!(outputState instanceof TokenState)) {
            throw new IllegalArgumentException("OutputState must TokenState.");
        }

        TokenState outputTokenState = (TokenState)outputState;
        if(outputTokenState.getAmount() <= 0) {
            throw new IllegalArgumentException("amount of outputTokenState must positive.");
        }

        Command command = tx.getCommand(0);
        CommandData commandType = command.getValue();
        if(!(commandType instanceof Issue)) {
            throw new IllegalArgumentException("Issue command.");
        }

        List<PublicKey> requiredSigners = command.getSigners();
        PublicKey issuerKey = outputTokenState.getIssuer().getOwningKey();
        if(!requiredSigners.contains(issuerKey)) {
            throw new IllegalArgumentException("Issue did not sign this.");
        }

    }

    public static class Issue implements CommandData {}
}