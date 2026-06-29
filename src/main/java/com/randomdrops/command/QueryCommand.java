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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


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
                        .then(Commands.literal("reverse")
                            .executes(QueryCommand::executeReverse)
                        )
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

    /** Reverse lookup: finds all pool items whose randomised drop equals the queried item. */
    private static int executeReverse(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Identifier targetId = IdentifierArgument.getId(ctx, "item");

        if (!BuiltInRegistries.ITEM.containsKey(targetId)) {
            source.sendFailure(Component.literal("Unknown item: " + targetId));
            return 0;
        }

        Item target = BuiltInRegistries.ITEM.getValue(targetId);
        DropMappingState state = DropMappingState.get(source.getServer());
        long seed = state.getWorldSeed();

        List<Identifier> sources = new ArrayList<>();
        for (Item item : DropMappingGenerator.getItemPool()) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) continue;
            if (DropMappingGenerator.getItem(seed, id) == target) {
                sources.add(id);
            }
        }

        if (sources.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nothing maps to " + targetId), false);
            return 1;
        }

        String list = sources.stream().map(Identifier::toString).collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal("→ " + targetId + ": " + list), false);
        return 1;
    }
}
