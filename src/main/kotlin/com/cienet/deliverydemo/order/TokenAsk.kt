package com.cienet.deliverydemo.order

import co.paralleluniverse.fibers.Suspendable
import com.cienet.deliverydemo.token.TokenState
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowSession
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap


class TokenAsk(private val otherPartyFlow: FlowSession) {

    @Suspendable
    fun askTokenState(amount: Int, owner: Party): StateAndRef<TokenState> {
        otherPartyFlow.send(amount)
        otherPartyFlow.send(owner)
        return otherPartyFlow.receive<StateAndRef<TokenState>>().unwrap { it }
    }

    @Suspendable
    fun receiveAmount(): Int =
        otherPartyFlow.receive<Int>().unwrap{it}

    @Suspendable
    fun receiveOwner(): Party =
            otherPartyFlow.receive<Party>().unwrap{it}

    @Suspendable
    fun sendStateAndRef(tokenStateAndRef: StateAndRef<TokenState>) =
        otherPartyFlow.send(tokenStateAndRef)
}