package com.cienet.deliverydemo.oracle

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Command
import net.corda.core.contracts.CommandData
import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.*
import net.corda.core.internal.ThreadBox
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.FilteredTransaction
import javax.annotation.concurrent.ThreadSafe
import kotlin.collections.HashSet


object Oracle {

    @ThreadSafe
    @CordaService
    class PuzzleOracle(private val services: AppServiceHub) : SingletonSerializeAsToken() {

        data class IntVal(val of: Int, val value: Int) : CommandData

        private val mutex = ThreadBox(InnerState())
        private var value = 20

        init {
            addDefaultValue()
        }

        private class InnerState {
            val values = HashSet<Int>()
        }


        @Suspendable
        fun query(queries: Int): Int {
            //require(queries.isNotEmpty())
            return mutex.locked {
                queries+10
            }
        }

        fun sign(ftx: FilteredTransaction): TransactionSignature {
            ftx.verify()
            // Performing validation of obtained filtered components.
            fun commandValidator(elem: Command<*>): Boolean {
                require(services.myInfo.legalIdentities.first().owningKey in elem.signers && elem.value is IntVal) {
                    "Oracle received unknown command (not in signers or not IntVal)."
                }
                val query = elem.value as IntVal
                if (query.of+10 != query.value)
                    throw FlowException("Not match.")
                return true
            }

            fun check(elem: Any): Boolean {
                return when (elem) {
                    is Command<*> -> commandValidator(elem)
                    else -> throw IllegalArgumentException("Oracle received data of different type than expected.")
                }
            }

            require(ftx.checkWithFun(::check))
            ftx.checkCommandVisibility(services.myInfo.legalIdentities.first().owningKey)

            return services.createSignature(ftx, services.myInfo.legalIdentities.first().owningKey)
        }

        private fun addDefaultValue() {
            this.value = 10
        }
    }
}