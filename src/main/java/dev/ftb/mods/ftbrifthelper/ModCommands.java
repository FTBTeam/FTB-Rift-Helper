package dev.ftb.mods.ftbrifthelper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ModCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(literal("ftbrifthelper")
                .then(literal("send_to_rift")
                        .requires(cs -> cs.hasPermission(2))
                        .then(argument("player", EntityArgument.player())
                                .then(argument("radius", IntegerArgumentType.integer(1, 128))
                                        .executes(ctx -> sendPlayerToRift(ctx, EntityArgument.getPlayer(ctx, "player"), IntegerArgumentType.getInteger(ctx, "radius")))
                                )
                        )
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> sendPlayerToRift(ctx, EntityArgument.getPlayer(ctx, "player"), 32))
                        )
                )
        );
    }

    private static int sendPlayerToRift(CommandContext<CommandSourceStack> ctx, ServerPlayer player, int radius) {
        RiftHelperUtil.sendPlayerToRift(player, radius);

        return 1;
    }
}
