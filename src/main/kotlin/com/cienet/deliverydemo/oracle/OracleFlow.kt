package com.cienet.deliverydemo.oracle

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.crypto.TransactionSignature
import net.corda.core.crypto.isFulfilledBy
import net.corda.core.serialization.CordaSerializable

object OracleFlow{

    @InitiatingFlow
    class QueryFlow(val of: Int, val oracle: Party) : FlowLogic<Int>() {
        @Suspendable
        override fun call(): Int {
            val oracleSession = initiateFlow(oracle)
            // TODO: add deadline to receive
            val resp = oracleSession.sendAndReceive<Int>(of)

            return resp.unwrap {
                val value = it
                // Check the returned fix is for what we asked for.
                //check(fix.of == fixOf)
                value
            }
        }
    }

    @InitiatedBy(QueryFlow::class)
    class QueryHandler(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        object RECEIVED : ProgressTracker.Step("Received fix request")
        object SENDING : ProgressTracker.Step("Sending fix response")

        override val progressTracker = ProgressTracker(RECEIVED, SENDING)

        @Suspendable
        override fun call() {
            val request = otherPartySession.receive<Int>().unwrap { it }
            progressTracker.currentStep = RECEIVED
            val oracle = serviceHub.cordaService(Oracle.PuzzleOracle::class.java)
            val answers = oracle.query(request)
            progressTracker.currentStep = SENDING
            otherPartySession.send(answers)
        }
    }

    @CordaSerializable
    data class SignRequest(val ftx: FilteredTransaction)

    @InitiatingFlow
    class SignFlow(private val tx: TransactionBuilder,
                   private val oracle: Party,
                   private val partialMerkleTx: FilteredTransaction) : FlowLogic<TransactionSignature>() {
        @Suspendable
        override fun call(): TransactionSignature {
            val oracleSession = initiateFlow(oracle)
            val resp =
                    oracleSession.sendAndReceive<TransactionSignature>(SignRequest(partialMerkleTx))
            return resp.unwrap { sig ->
                check(oracleSession.counterparty.owningKey.isFulfilledBy(listOf(sig.by)))
                tx.toWireTransaction(serviceHub).checkSignature(sig)
                sig
            }
        }
    }

    @InitiatedBy(SignFlow::class)
    class SignHandler(private val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val request = otherPartySession.receive<SignRequest>().unwrap { it }
            val oracle = serviceHub.cordaService(Oracle.PuzzleOracle::class.java)
            otherPartySession.send(oracle.sign(request.ftx))
        }
    }

}