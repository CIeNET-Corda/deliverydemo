package java_bootcamp;

import co.paralleluniverse.fibers.Suspendable;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.CommandData;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;

import java.security.PublicKey;
import java.util.List;

/* Our flow, automating the process of updating the ledger.
 * See src/main/java/examples/IAmAFlowPair.java for an example. */
@InitiatingFlow
@StartableByRPC
public class TokenIssueFlow extends FlowLogic<SignedTransaction> {
    private final ProgressTracker progressTracker = new ProgressTracker();
    private final Party owner;
    private final int amount;

    public TokenIssueFlow(Party owner, int amount) {
        this.owner = owner;
        this.amount = amount;
    }

    @Override
    public ProgressTracker getProgressTracker() {
        return progressTracker;
    }

    @Suspendable
    @Override
    public SignedTransaction call() throws FlowException {
        // We choose our transaction's notary (the notary prevents double-spends).
        Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);
        // We get a reference to our own identity.
        Party issuer = getOurIdentity();

        /* ============================================================================
         *         TODO 1 - Create our TokenState to represent on-ledger tokens!
         * ===========================================================================*/
        // We create our new TokenState.
        TokenState tokenState = new TokenState(issuer, owner, amount);

        /* ============================================================================
         *      TODO 3 - Build our token issuance transaction to update the ledger!
         * ===========================================================================*/
        // We build our transaction.
//        TransactionBuilder transactionBuilder = new TransactionBuilder();
//        transactionBuilder.setNotary(notary);
        TransactionBuilder transactionBuilder = new TransactionBuilder(notary);

        transactionBuilder.addOutputState(tokenState, TokenContract.ID, notary);

        CommandData commandData = new TokenContract.Issue();
//        List<PublicKey> requiredSigners = ImmutableList.of(issuer.getOwningKey());
//        Command command = new Command<>(commandData, requiredSigners);
//        transactionBuilder.addCommand(command);
        transactionBuilder.addCommand(commandData, issuer.getOwningKey());


        /* ============================================================================
         *          TODO 2 - Write our TokenContract to control token issuance!
         * ===========================================================================*/
        // We check our transaction is valid based on its contracts.
        transactionBuilder.verify(getServiceHub());

        // We sign the transaction with our private key, making it immutable.
        SignedTransaction signedTransaction = getServiceHub().signInitialTransaction(transactionBuilder);

        // We get the transaction notarised and recorded automatically by the platform.
        return subFlow(new FinalityFlow(signedTransaction));
    }
}