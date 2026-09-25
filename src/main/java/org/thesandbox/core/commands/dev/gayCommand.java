package org.thesandbox.core.commands.dev;

import com.earth2me.essentials.perm.impl.LuckPermsHandler;
import net.luckperms.api.LuckPermsProvider;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.thesandbox.core.TheSandboxCore;
import org.thesandbox.core.commands.ISubCommand;
import org.thesandbox.core.fun.Utils;
import org.thesandbox.core.login.LoginService;
import net.luckperms.api.LuckPerms;

import java.util.List;

public class gayCommand implements ISubCommand {


    private static final TheSandboxCore plugin = Utils.getTheSandboxCore();
    private final LoginService loginService = plugin.getLoginService();

    @Override
    public boolean execute(CommandSender sender, Command command, String label, String[] args) {
        if(!(sender instanceof Player player)) {
            return true;
        }
        sender.sendMessage("Loginservice thinks you are : " + loginService.getRank(player).toString());
        sender.sendMessage("bukkit : " + LuckPermsProvider.get().getUserManager().getUser(sender.getName()).getCachedData());
        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
