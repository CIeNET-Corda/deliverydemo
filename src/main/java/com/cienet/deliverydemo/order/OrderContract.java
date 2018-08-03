package com.cienet.deliverydemo.order;

import com.cienet.deliverydemo.token.TokenState;
import net.corda.core.contracts.*;
import net.corda.core.transactions.LedgerTransaction;

import java.security.PublicKey;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireThat;

public class OrderContract implements Contract {
    // Used to reference the contract in transactions.
    static final String CONTRACT_ID = "com.cienet.deliverydemo.order.OrderContract";

    public interface Commands extends CommandData {

        class OrderPlacingCommand extends TypeOnlyCommandData implements Commands {
        }

        class OrderDeliveredCommand extends TypeOnlyCommandData implements Commands {
        }

    }

    @Override
    public void verify(LedgerTransaction tx) throws IllegalArgumentException {
        requireThat(require -> {

//            require.using("Must have a timestamp", tx.getTimeWindow() != null);
//            final TimeWindow timewindow = tx.getTimeWindow();
//            require.using("Must be a valid timestamp", timewindow.contains(Instant.now()));

            List<Command<Commands.OrderPlacingCommand>> orderPlacingCommand =
                    tx.commandsOfType(Commands.OrderPlacingCommand.class);

            List<Command<Commands.OrderDeliveredCommand>> orderDeliveredCommand =
                    tx.commandsOfType(Commands.OrderDeliveredCommand.class);

            require.using("At least one Order command, OrderPlacingCommand or OrderDeliveredCommand",
                    orderPlacingCommand.size() + orderDeliveredCommand.size() == 1);

            orderPlacingCommand.forEach( commandWithData -> {
                //input

                final List<TokenState> iTokenStateList = tx.inputsOfType(TokenState.class);
                require.using("Must have a input TokenState", iTokenStateList.size() == 1);
                //TokenState iTokenState = iTokenStateList.get(0);

                final List<OrderState> iOrderStateList = tx.inputsOfType(OrderState.class);
                require.using("Must have a input OrderState", iOrderStateList.size() == 1);
                OrderState iOrderState = iOrderStateList.get(0);

                //output

                final List<TokenState> oTokenStateList = tx.outputsOfType(TokenState.class);
                require.using("Must have a output TokenState",
                        oTokenStateList.size() == 1 || oTokenStateList.size() == 2);
                //TokenState oTokenState = oTokenStateList.get(0);

                final List<OrderState> oOrderStateList = tx.outputsOfType(OrderState.class);
                require.using("Must have a output OrderState", oOrderStateList.size() == 1);
                OrderState oOrderState = oOrderStateList.get(0);

                //TODO
                //amount in Token: amount > sellingPrice, amount = 10% sellingPrice + balance

                final List<PublicKey> commandWithDataSigners = commandWithData.getSigners();
                require.using("The input OrderState's party is a required signer",
                    commandWithDataSigners.contains(iOrderState.getBuyer().getOwningKey()));
                require.using("The output OrderState's party is a required signer",
                        commandWithDataSigners.contains(oOrderState.getBuyer().getOwningKey()));

            });

            orderDeliveredCommand.forEach( commandWithData -> {
                //TODO
            });

            return null;
        });
    }
}