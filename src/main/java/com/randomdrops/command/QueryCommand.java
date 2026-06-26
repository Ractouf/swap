package com.randomdrops.command;

import com.randomdrops.mapping.DropMappingGenerator;
import com.randomdrops.mapping.DropMappingState;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.Commands.CommandSelection;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;


public final class QueryCommand {

    private QueryCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register(QueryCommand::onRegister);
    }

    private static void onRegister(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext,
            CommandSelection selection
    ) {
        // /randomdrops query <item-id>
        // e.g. /randomdrops query minecraft:dirt
        //      /randomdrops query minecraft:mutton
        dispatcher.register(
            Commands.literal("randomdrops")
                .then(Commands.literal("query")
                    .then(Commands.argument("item", IdentifierArgument.id())
                        .executes(QueryCommand::execute)
                    )
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Identifier itemId = IdentifierArgument.getId(ctx, "item");

        if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
            source.sendFailure(Component.literal("Unknown item: " + itemId));
            return 0;
        }

        DropMappingState state = DropMappingState.get(source.getServer());
        Item replacement = DropMappingGenerator.getItem(state.getWorldSeed(), itemId);
        Identifier replacementId = BuiltInRegistries.ITEM.getKey(replacement);

        source.sendSuccess(
            () -> Component.literal(itemId + " → " + replacementId),
            false
        );
        return 1;
    }
}
