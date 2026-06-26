package com.randomdrops.command;

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
import net.minecraft.world.level.block.Block;

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
        // /randomdrops query <block-id>
        // e.g. /randomdrops query minecraft:coal_ore
        dispatcher.register(
            Commands.literal("randomdrops")
                .then(Commands.literal("query")
                    .then(Commands.argument("block", IdentifierArgument.id())
                        .executes(QueryCommand::execute)
                    )
                )
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Identifier blockId = IdentifierArgument.getId(ctx, "block");

        if (!BuiltInRegistries.BLOCK.containsKey(blockId)) {
            source.sendFailure(Component.literal("Unknown block: " + blockId));
            return 0;
        }

        Block block = BuiltInRegistries.BLOCK.getValue(blockId);
        Identifier blockItemId = BuiltInRegistries.ITEM.getKey(block.asItem());

        DropMappingState state = DropMappingState.get(source.getServer());
        Item replacement = state.getOrCompute(blockId, blockItemId);
        Identifier replacementId = BuiltInRegistries.ITEM.getKey(replacement);

        source.sendSuccess(
            () -> Component.literal(blockId + " → " + replacementId),
            false
        );
        return 1;
    }
}
